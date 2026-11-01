package com.reactnative.geckoview

import android.content.Context
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.ExperimentalGeckoViewApi
import org.mozilla.geckoview.GeckoPreferenceController
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.WebExtension
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

object GeckoRuntimeManager {
    // Written under this object's monitor, read lock-free by getRuntime() and
    // getWebExtensionController() from the native-modules thread.
    @Volatile private var runtime: GeckoRuntime? = null
    @Volatile private var webExtensionController: WebExtensionController? = null

    /**
     * Extensions the host app is allowed to install or update.
     *
     * `installExtension` only accepts these IDs; any other install prompt
     * surfaced by GeckoView (e.g. page-initiated install) is denied outright.
     * A permission-identical update to a listed extension is allowed; an
     * update that requests *new* permissions or *new* origins is denied to
     * prevent silent privilege escalation via a compromised upstream bundle.
     */
    internal val allowedExtensionIds: Set<String> = setOf(
        "spatial-navigation@geckoview.dev",
    )

    private fun applyDefaultContentBlockingSettings(runtime: GeckoRuntime) {
        try {
            val contentBlocking = runtime.settings.contentBlocking
            val behavior = GeckoSecurityConfig.current().cookieBehavior

            contentBlocking.setCookieBehavior(behavior)
            contentBlocking.setCookieBehaviorPrivateMode(behavior)
            GeckoLog.d(TAG, "Cookie behavior applied")
        } catch (e: Throwable) {
            GeckoLog.w(TAG, "Failed to apply cookie behavior", e)
        }
    }

    /** Creates the runtime on first use, which GeckoRuntime.create allows on the UI thread only. */
    @Synchronized
    fun getRuntime(context: Context): GeckoRuntime {
        if (runtime == null) {
            val securityOptions = GeckoSecurityConfig.current()
            val settings = GeckoRuntimeSettings.Builder()
                .consoleOutput(BuildConfig.DEBUG && securityOptions.enableConsoleOutput)
                .build()

            runtime = GeckoRuntime.create(context.applicationContext, settings)
            applyRuntimePrefs(securityOptions)
            applyDefaultContentBlockingSettings(runtime!!)
            installExtensionPromptDelegate(runtime!!)
            // GeckoView 150+: child process is no longer started eagerly in the
            // constructor. Call warmUp() so the first session opens quickly.
            warmUpIfAvailable(runtime!!)
            webExtensionController = WebExtensionController(runtime!!)
            webExtensionController?.installInternalBridge()
        }
        return runtime!!
    }

    @Synchronized
    fun configureSecurity(options: GeckoSecurityConfig.Options): Boolean {
        if (runtime != null) return false
        GeckoSecurityConfig.set(options)
        return true
    }

    /**
     * Gecko prefs carrying the host's media opt-ins: autoplay and EME stay
     * blocked unless [GeckoSecurityConfig.Options] opts in before runtime init.
     *
     * GeckoView 159 has no typed setting for these, and Gecko has no
     * command-line pref switch: the `--setpref=` runtime arguments used before
     * never took effect. [applyRuntimePrefs] sets them through
     * [GeckoPreferenceController].
     *
     * Never add `xpinstall.signatures.required=false` (unsigned extensions
     * could install through any surface; `installBuiltIn` already covers
     * APK-packed ones) or `extensions.install.requireSecureOrigin=false` (an
     * http page could offer an install). No sandbox-disabling switch exists or
     * is passed.
     */
    internal fun runtimePrefs(
        options: GeckoSecurityConfig.Options = GeckoSecurityConfig.current()
    ): Map<String, Any> = linkedMapOf(
        // 5 blocks audible and inaudible autoplay; 0 allows both.
        "media.autoplay.default" to (if (options.allowAutoplay) 0 else 5),
        // While blocked, autoplay is routed to GeckoViewPermissionDelegate as a request.
        "media.geckoview.autoplay.request" to !options.allowAutoplay,
        "media.eme.enabled" to options.allowDrm,
    )

    /**
     * Applies [runtimePrefs] on the default branch, the branch GeckoView uses
     * for its own runtime settings: nothing persists in the profile, so each
     * launch applies the current configuration. The request waits in Gecko's
     * global event queue until the Gecko thread reaches RUNNING.
     * GeckoPreferenceController is public but marked experimental in 159.
     */
    @androidx.annotation.OptIn(ExperimentalGeckoViewApi::class)
    private fun applyRuntimePrefs(options: GeckoSecurityConfig.Options) {
        try {
            val prefs: List<GeckoPreferenceController.SetGeckoPreference<*>> =
                runtimePrefs(options).map { (name, value) ->
                    when (value) {
                        is Int -> GeckoPreferenceController.SetGeckoPreference.setIntPref(
                            name, value, GeckoPreferenceController.PREF_BRANCH_DEFAULT
                        )
                        is Boolean -> GeckoPreferenceController.SetGeckoPreference.setBoolPref(
                            name, value, GeckoPreferenceController.PREF_BRANCH_DEFAULT
                        )
                        else -> throw IllegalArgumentException("Unsupported pref type for $name")
                    }
                }
            // Both outcomes are consumed: an unhandled failure would surface as
            // GeckoResult.UncaughtException on the UI thread.
            GeckoPreferenceController.setGeckoPrefs(prefs).accept(
                { applied ->
                    val rejected = applied?.filterValues { it != true }?.keys.orEmpty()
                    if (rejected.isNotEmpty()) GeckoLog.w(TAG, "Gecko rejected runtime prefs: $rejected")
                },
                { e -> GeckoLog.w(TAG, "Failed to apply runtime prefs", e) },
            )
        } catch (e: Throwable) {
            GeckoLog.w(TAG, "Failed to apply runtime prefs", e)
        }
    }

    /**
     * Installs a runtime-wide `PromptDelegate` that enforces the
     * `allowedExtensionIds` allowlist on install prompts and denies updates
     * that request new permissions or origins.
     *
     * GeckoView's `WebExtensionController.PromptDelegate` interface shifts
     * signatures across versions — parameter arity on `onInstallPrompt`
     * changed between 128 and 150, and return types migrated between
     * `InstallPromptResponse` and `AllowOrDeny`. To stay compatible across
     * the supported version window, install a `java.lang.reflect.Proxy`
     * that dispatches by method name, yielding `AllowOrDeny.DENY` on
     * unrecognized install signatures. The `spatial_navigation` extension
     * installs via `installBuiltIn`, which does not pass through this
     * delegate — so a strict default here does not break the built-in flow.
     */
    private fun installExtensionPromptDelegate(runtime: GeckoRuntime) {
        try {
            val controller = runtime.webExtensionController
            val setPromptDelegate = controller.javaClass.methods.firstOrNull {
                it.name == "setPromptDelegate" && it.parameterTypes.size == 1
            } ?: run {
                GeckoLog.w(TAG, "setPromptDelegate not found on WebExtensionController; skipping")
                return
            }
            val delegateInterface = setPromptDelegate.parameterTypes[0]
            val proxy = Proxy.newProxyInstance(
                delegateInterface.classLoader,
                arrayOf(delegateInterface),
                ExtensionPromptHandler(allowedExtensionIds)
            )
            setPromptDelegate.invoke(controller, proxy)
            GeckoLog.d(TAG, "WebExtensionController.PromptDelegate installed")
        } catch (e: Throwable) {
            GeckoLog.w(TAG, "Failed to install WebExtensionController.PromptDelegate", e)
        }
    }

    /**
     * Method-name dispatch for GeckoView's `PromptDelegate` interface.
     *
     * Deny-by-default: any install/update prompt whose method we don't
     * explicitly handle resolves to `AllowOrDeny.DENY`. This errs on the side
     * of refusing unexpected prompts rather than silently allowing them.
     */
    internal class ExtensionPromptHandler(private val allowed: Set<String>) : InvocationHandler {
        override fun invoke(proxy: Any, method: Method, args: Array<out Any?>?): Any? {
            val name = method.name
            return when {
                name == "onInstallPrompt" || name == "onInstallPromptRequest" -> {
                    val ext = args?.firstOrNull { it is WebExtension } as? WebExtension
                    val allow = ext != null && ext.id in allowed
                    if (!allow) {
                        GeckoLog.w(TAG, "Denying install prompt for unlisted extension")
                    }
                    resolveInstallPromptResponse(method.returnType, allow)
                }
                name == "onUpdatePrompt" -> onUpdatePromptResult(args, method.returnType)
                name == "onOptionalPrompt" || name == "onOptionalPermissionsPrompt" ->
                    GeckoResult.fromValue(AllowOrDeny.DENY)
                name == "onExtensionDebugPrompt" -> GeckoResult.fromValue(AllowOrDeny.DENY)
                name == "toString" -> "GeckoRuntimeManager.ExtensionPromptHandler"
                name == "hashCode" -> System.identityHashCode(proxy)
                name == "equals" -> (args?.firstOrNull() === proxy)
                else -> null
            }
        }

        private fun onUpdatePromptResult(args: Array<out Any?>?, returnType: Class<*>): Any? {
            val currentExt = args?.firstOrNull { it is WebExtension } as? WebExtension
            if (currentExt?.id !in allowed) {
                GeckoLog.w(TAG, "Denying update prompt for unlisted extension")
                return GeckoResult.fromValue(AllowOrDeny.DENY)
            }
            val anyNewPerms = args?.any { arg ->
                (arg as? Array<*>)?.any { it is String } == true
            } ?: false
            if (anyNewPerms) {
                GeckoLog.w(TAG, "Denying permission-escalating extension update")
                return GeckoResult.fromValue(AllowOrDeny.DENY)
            }
            return GeckoResult.fromValue(AllowOrDeny.ALLOW)
        }

        /**
         * Build the GeckoView-version-appropriate install-prompt response.
         *
         * GeckoView ≤128 expects `InstallPromptResponse` (a nested class with a
         * boolean-argument constructor). GeckoView 150's install prompt uses
         * `PermissionPromptResponse`; optional/debug prompts use `AllowOrDeny`.
         * We detect the best response shape from the declared return type and
         * fail closed if construction is unavailable.
         */
        private fun resolveInstallPromptResponse(returnType: Class<*>, allow: Boolean): Any? {
            return try {
                if (returnType == GeckoResult::class.java) {
                    GeckoResult.fromValue(
                        WebExtension.PermissionPromptResponse(allow, false, false)
                    )
                } else {
                    val nested = findNestedClass(returnType, "InstallPromptResponse")
                    if (nested != null) {
                        val ctor = nested.getDeclaredConstructor(Boolean::class.javaPrimitiveType)
                        ctor.isAccessible = true
                        GeckoResult.fromValue(ctor.newInstance(allow))
                    } else {
                        GeckoResult.fromValue(if (allow) AllowOrDeny.ALLOW else AllowOrDeny.DENY)
                    }
                }
            } catch (e: Throwable) {
                GeckoLog.w(TAG, "Could not construct install prompt response; denying", e)
                GeckoResult.fromValue(AllowOrDeny.DENY)
            }
        }

        private fun findNestedClass(owner: Class<*>, simpleName: String): Class<*>? {
            var cls: Class<*>? = owner
            while (cls != null) {
                cls.declaredClasses.firstOrNull { it.simpleName == simpleName }?.let { return it }
                cls = cls.enclosingClass
            }
            return null
        }
    }

    private fun warmUpIfAvailable(runtime: GeckoRuntime) {
        try {
            val method = runtime.javaClass.getMethod("warmUp")
            method.invoke(runtime)
            GeckoLog.d(TAG, "GeckoRuntime warm-up requested")
        } catch (_: NoSuchMethodException) {
            // warmUp is GeckoView-version-dependent; older pins don't expose it.
        } catch (e: Throwable) {
            GeckoLog.w(TAG, "GeckoRuntime warm-up failed", e)
        }
    }

    fun getRuntime(): GeckoRuntime? = runtime

    fun getWebExtensionController(): WebExtensionController? = webExtensionController

    /**
     * Close every pooled session, then shut the runtime down. UI thread only
     * (GeckoSession.close asserts it); [GeckoViewModule.shutdown] posts here.
     *
     * Must not hold this object's monitor while closing sessions:
     * GeckoSessionManager opens a session under its own monitor and then
     * calls [getRuntime], so taking the monitors in the opposite order here
     * deadlocked a view mounting on the UI thread against a shutdown.
     */
    fun shutdown() {
        try {
            GeckoSessionManager.closeAllSessions()
        } catch (e: Throwable) {
            GeckoLog.w(TAG, "Failed to close sessions during shutdown", e)
        }
        val stopping = synchronized(this) {
            val current = runtime
            runtime = null
            webExtensionController = null
            current
        }
        try {
            stopping?.shutdown()
        } catch (e: Throwable) {
            GeckoLog.w(TAG, "Failed to shutdown runtime", e)
        }
    }

    private const val TAG = "GeckoRuntimeManager"
}
