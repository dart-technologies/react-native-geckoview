package com.reactnative.geckoview

import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.facebook.react.bridge.*
import com.facebook.react.modules.core.DeviceEventManagerModule
import com.facebook.react.uimanager.UIManagerHelper
import com.facebook.react.uimanager.events.Event
import java.util.Collections
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession

/**
 * Generic view-level event for GeckoView. Wraps an event name + payload so a
 * single class can carry every `onGeckoXxx` → `topGeckoXxx` dispatch. Uses the
 * explicit `Event(surfaceId, viewTag)` constructor required by RN 0.83+; the
 * EventDispatcher resolves surfaceId from the viewTag when callers pass -1.
 */
private class GeckoGenericEvent(
    surfaceId: Int,
    viewTag: Int,
    private val nativeEventName: String,
    private val params: WritableMap?,
) : Event<GeckoGenericEvent>(surfaceId, viewTag) {
    override fun getEventName(): String = nativeEventName
    override fun getEventData(): WritableMap? = params
}

class GeckoViewModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {
    private val pendingPrompts: MutableMap<String, PromptInfo> =
        Collections.synchronizedMap(mutableMapOf())
    private val pendingPermissions: MutableMap<String, GeckoResult<Int>> =
        Collections.synchronizedMap(mutableMapOf())
    private val timeoutHandler = Handler(Looper.getMainLooper())
    private val timeoutRunnables: MutableMap<String, Runnable> =
        Collections.synchronizedMap(mutableMapOf())
    // Snapshots are tracked per-session so closeSession only removes its own
    // captures. A flat Set previously caused closeSession to wipe every
    // session's snapshots whenever any one was closed.
    private val snapshotsBySession: MutableMap<String, MutableSet<java.io.File>> = mutableMapOf()
    private val snapshotsLock = Any()

    data class PromptInfo(val prompt: Any, val result: GeckoResult<Any>, val createdAt: Long = System.currentTimeMillis())

    init {
        GeckoSessionManager.init(reactContext.applicationContext)
    }

    override fun getName() = "GeckoViewModule"

    override fun getConstants(): Map<String, Any> = mapOf(
        "isAutomotive" to reactApplicationContext.packageManager
            .hasSystemFeature(android.content.pm.PackageManager.FEATURE_AUTOMOTIVE)
    )

    @ReactMethod
    fun configureSecurity(options: ReadableMap, promise: Promise) {
        val configured = GeckoRuntimeManager.configureSecurity(
            GeckoSecurityConfig.Options(
                allowDrm = options.getBooleanOrDefault("allowDrm", false),
                allowAutoplay = options.getBooleanOrDefault("allowAutoplay", false),
                enableConsoleOutput = options.getBooleanOrDefault("enableConsoleOutput", false),
                cookieBehavior = options.getIntOrDefault(
                    "cookieBehavior",
                    org.mozilla.geckoview.ContentBlocking.CookieBehavior.ACCEPT_NON_TRACKERS
                ),
                maxSnapshotPixels = options.getLongOrDefault("maxSnapshotPixels", 4096L * 4096L),
            )
        )
        if (configured) {
            promise.resolve(null)
        } else {
            promise.reject(
                "GECKO_RUNTIME_ALREADY_INITIALIZED",
                "configureGeckoViewSecurity must be called before the first GeckoView is mounted"
            )
        }
    }

    @ReactMethod
    fun reload(sessionKey: String) {
        if (!GeckoSessionManager.isPlaybackAllowed()) return
        openSession(sessionKey)?.reload()
    }

    @ReactMethod
    fun goBack(sessionKey: String) {
        if (!GeckoSessionManager.isPlaybackAllowed()) return
        openSession(sessionKey)?.goBack()
    }

    @ReactMethod
    fun goForward(sessionKey: String) {
        if (!GeckoSessionManager.isPlaybackAllowed()) return
        openSession(sessionKey)?.goForward()
    }

    @ReactMethod
    fun stop(sessionKey: String) {
        if (!GeckoSessionManager.isPlaybackAllowed()) return
        openSession(sessionKey)?.stop()
    }

    @ReactMethod
    fun closeSession(sessionKey: String) {
        UiThreadUtil.runOnUiThread {
            GeckoSessionManager.releaseSession(sessionKey)
            cleanupSnapshotsForSession(sessionKey)
        }
    }

    @ReactMethod
    fun shutdown() {
        // Closing sessions asserts the UI thread; React calls this on the
        // native-modules thread.
        UiThreadUtil.runOnUiThread { GeckoRuntimeManager.shutdown() }
    }

    @ReactMethod
    fun loadUrl(sessionKey: String, url: String) {
        if (!GeckoSessionManager.isPlaybackAllowed()) return
        if (!isSafeLoadUrl(url)) {
            GeckoLog.d(TAG, "Rejecting loadUrl with disallowed scheme")
            return
        }
        openSession(sessionKey)?.loadUri(url)
    }

    @ReactMethod
    fun setUserAgent(sessionKey: String, userAgent: String) {
        if (!GeckoSessionManager.isPlaybackAllowed()) return
        val session = openSession(sessionKey) ?: return
        if (userAgent.isEmpty()) {
            session.settings.userAgentMode = org.mozilla.geckoview.GeckoSessionSettings.USER_AGENT_MODE_MOBILE
            session.settings.userAgentOverride = null
        } else {
            session.settings.userAgentMode = org.mozilla.geckoview.GeckoSessionSettings.USER_AGENT_MODE_DESKTOP
            session.settings.userAgentOverride = userAgent
        }
    }

    @ReactMethod
    fun resolvePrompt(promptId: String, confirm: Boolean, text: String?, username: String?, password: String?) {
        cancelTimeout(promptId)
        val pending = pendingPrompts.remove(promptId) ?: return
        val response = try {
            promptResponse(pending.prompt, confirm, text, username, password)
        } catch (e: Exception) {
            // The entry and its timeout are already gone, so nothing else will
            // answer this prompt: dismiss it rather than leave the page blocked.
            GeckoLog.e(TAG, "Error resolving prompt; dismissing it", e)
            try {
                dismissResponse(pending.prompt)
            } catch (dismissError: Exception) {
                GeckoLog.e(TAG, "Error dismissing prompt", dismissError)
                null
            }
        }
        if (response == null) {
            GeckoLog.w(TAG, "Unhandled prompt type; cannot resolve")
            return
        }
        try {
            pending.result.complete(response)
        } catch (e: Exception) {
            GeckoLog.e(TAG, "Error completing prompt", e)
        }
    }

    /** The GeckoView 159 response for a JS decision on a pending prompt. */
    private fun promptResponse(
        prompt: Any,
        confirm: Boolean,
        text: String?,
        username: String?,
        password: String?,
    ): GeckoSession.PromptDelegate.PromptResponse? = when (prompt) {
        // AlertPrompt declares no confirm(); an alert is acknowledged by dismissing it.
        is GeckoSession.PromptDelegate.AlertPrompt -> prompt.dismiss()
        is GeckoSession.PromptDelegate.ButtonPrompt -> prompt.confirm(
            if (confirm) GeckoSession.PromptDelegate.ButtonPrompt.Type.POSITIVE
            else GeckoSession.PromptDelegate.ButtonPrompt.Type.NEGATIVE
        )
        is GeckoSession.PromptDelegate.TextPrompt ->
            if (confirm && text != null) prompt.confirm(text) else prompt.dismiss()
        is PopupPromptWrapper -> prompt.respond(confirm)
        is BeforeUnloadPromptWrapper -> prompt.confirm(confirm)
        is AuthPromptWrapper -> {
            // `password` is the explicit channel; fall back to `text`
            // for older callers that passed the password positionally.
            val pw = password ?: text
            if (confirm && username != null && pw != null) {
                prompt.confirm(username, pw)
            } else {
                prompt.dismiss()
            }
        }
        else -> null
    }

    /** The "cancel" answer for a pending prompt, used on timeout and when resolving fails. */
    private fun dismissResponse(prompt: Any): GeckoSession.PromptDelegate.PromptResponse? = when (prompt) {
        is PopupPromptWrapper -> prompt.respond(false)
        is BeforeUnloadPromptWrapper -> prompt.confirm(false)
        is AuthPromptWrapper -> prompt.dismiss()
        // Alert/Button/Text prompts are stored unwrapped (see
        // GeckoViewPromptDelegate.handlePrompt); dismiss() lives on BasePrompt.
        is GeckoSession.PromptDelegate.BasePrompt -> prompt.dismiss()
        else -> null
    }

    @ReactMethod
    fun resolvePermission(requestId: String, allow: Boolean) {
        cancelTimeout(requestId)
        pendingPermissions.remove(requestId)?.let { result ->
            result.complete(if (allow)
                org.mozilla.geckoview.GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW
            else
                org.mozilla.geckoview.GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY
            )
        }
    }

    @ReactMethod
    fun installWebExtension(assetPath: String, promise: Promise) {
        // This can run before any GeckoView mounts, and creating the runtime
        // asserts the UI thread.
        UiThreadUtil.runOnUiThread {
            try {
                GeckoRuntimeManager.getRuntime(reactApplicationContext)
                val controller = GeckoRuntimeManager.getWebExtensionController()
                if (controller == null) {
                    promise.reject("GECKO_RUNTIME", "WebExtensionController not available")
                    return@runOnUiThread
                }

                controller.installExtension(assetPath) { success, error ->
                    if (success) {
                        promise.resolve(null)
                    } else if (error is IllegalArgumentException) {
                        promise.reject(
                            "INVALID_ASSET_PATH",
                            error.message ?: "Invalid extension asset path"
                        )
                    } else {
                        promise.reject(
                            "WEB_EXTENSION_INSTALL",
                            error?.message ?: "Failed to install web extension"
                        )
                    }
                }
            } catch (e: Exception) {
                promise.reject("WEB_EXTENSION_INSTALL", e)
            }
        }
    }

    @ReactMethod
    fun sendWebExtensionMessage(
        sessionKey: String,
        extensionId: String,
        message: String,
        promise: Promise
    ) {
        val sent = GeckoRuntimeManager.getWebExtensionController()
            ?.sendMessage(sessionKey, extensionId, message) ?: false
        if (sent) {
            promise.resolve(null)
        } else {
            promise.reject(
                "WEB_EXTENSION_PORT_UNAVAILABLE",
                "No connected WebExtension port for the requested extension and session"
            )
        }
    }

    @ReactMethod
    fun setEnhancedTrackingProtectionLevel(level: Int, promise: Promise) {
        try {
            val runtime = GeckoRuntimeManager.getRuntime()
            if (runtime == null) {
                promise.reject("GECKO_RUNTIME", "GeckoRuntime is not initialized")
                return
            }
            ContentBlockingController(runtime).setEnhancedTrackingProtectionLevel(level)
            promise.resolve(null)
        } catch (e: IllegalArgumentException) {
            promise.reject("INVALID_ETP_LEVEL", e.message ?: "Invalid ETP level")
        } catch (e: Throwable) {
            promise.reject("ETP_SET_FAILED", e.message ?: "Failed to set ETP level")
        }
    }

    @ReactMethod
    fun setCookieBannerMode(mode: Int, promise: Promise) {
        try {
            val runtime = GeckoRuntimeManager.getRuntime()
            if (runtime == null) {
                promise.reject("GECKO_RUNTIME", "GeckoRuntime is not initialized")
                return
            }
            ContentBlockingController(runtime).setCookieBannerMode(mode)
            promise.resolve(null)
        } catch (e: IllegalArgumentException) {
            promise.reject("INVALID_COOKIE_BANNER_MODE", e.message ?: "Invalid cookie banner mode")
        } catch (e: Throwable) {
            promise.reject("COOKIE_BANNER_SET_FAILED", e.message ?: "Failed to set cookie banner mode")
        }
    }

    @ReactMethod
    fun captureSnapshot(sessionKey: String, promise: Promise) {
        if (!GeckoSessionManager.isPlaybackAllowed()) {
            promise.reject("PLAYBACK_RESTRICTED", "Media is blocked by vehicle UX restrictions")
            return
        }
        // GeckoView's ScreenshotBuilder.capture asserts the UI thread, and React
        // calls this on the native-modules thread. The capture's GeckoResult
        // also settles on the UI thread, so the promise settles from there.
        UiThreadUtil.runOnUiThread {
            try {
                captureSnapshotOnUiThread(sessionKey, promise)
            } catch (e: Exception) {
                promise.reject("SNAPSHOT_ERROR", e.message ?: "capturePixels failed", e)
            }
        }
    }

    private fun captureSnapshotOnUiThread(sessionKey: String, promise: Promise) {
        if (!GeckoSessionManager.isPlaybackAllowed()) {
            promise.reject("PLAYBACK_RESTRICTED", "Media is blocked by vehicle UX restrictions")
            return
        }
        val view = GeckoSessionManager.getOpenSession(sessionKey)?.let { GeckoSessionManager.getView(it) }

        if (view == null) {
            promise.reject("VIEW_NOT_FOUND", "No GeckoView attached to session: $sessionKey")
            return
        }

        val maxPixels = GeckoSecurityConfig.current().maxSnapshotPixels
        val viewPixels = view.width.toLong() * view.height.toLong()
        if (viewPixels > maxPixels) {
            promise.reject(
                "SNAPSHOT_TOO_LARGE",
                "View exceeds $maxPixels pixels (${view.width}x${view.height})"
            )
            return
        }

        view.capturePixels().then(
            { bitmap ->
                if (bitmap == null) {
                    promise.reject("SNAPSHOT_ERROR", "Failed to capture snapshot: Bitmap is null")
                    return@then GeckoResult.fromValue(null)
                }
                // `try { ... } finally { recycle }` — every exit path (success,
                // SNAPSHOT_TOO_LARGE rejection, file-save catch) must release
                // the native bitmap pixels promptly rather than waiting for GC.
                try {
                    if (bitmap.width.toLong() * bitmap.height.toLong() > maxPixels) {
                        promise.reject(
                            "SNAPSHOT_TOO_LARGE",
                            "Snapshot exceeds $maxPixels pixels (${bitmap.width}x${bitmap.height})"
                        )
                        return@then GeckoResult.fromValue(null)
                    }
                    try {
                        // `noBackupFilesDir` keeps the PNG out of Android auto-backup,
                        // so page screenshots don't sync to cloud even if the host app
                        // enables `allowBackup`. `deleteOnExit` + TTL eviction limits
                        // disk residency.
                        val dir = java.io.File(reactApplicationContext.noBackupFilesDir, "geckoview_snapshots")
                        dir.mkdirs()
                        evictStaleSnapshots(dir)
                        val file = java.io.File.createTempFile("snapshot", ".png", dir)
                        file.deleteOnExit()
                        java.io.FileOutputStream(file).use { stream ->
                            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
                        }
                        synchronized(snapshotsLock) {
                            snapshotsBySession.getOrPut(sessionKey) { mutableSetOf() }.add(file)
                        }
                        promise.resolve(file.absolutePath)
                    } catch (e: Exception) {
                        promise.reject("SNAPSHOT_ERROR", "Error saving snapshot: ${e.message}")
                    }
                } finally {
                    bitmap.recycle()
                }
                GeckoResult.fromValue(null)
            },
            // Without this exception listener the React promise hangs forever
            // when capturePixels() completes with a Throwable.
            GeckoResult.OnExceptionListener<GeckoResult<Void>?> { throwable ->
                promise.reject(
                    "SNAPSHOT_ERROR",
                    throwable.message ?: "capturePixels failed",
                    throwable,
                )
                GeckoResult.fromValue(null)
            },
        )
    }

    @ReactMethod
    fun evaluateJavaScript(sessionKey: String, code: String, promise: Promise) {
        if (!GeckoSessionManager.isPlaybackAllowed()) {
            promise.reject("PLAYBACK_RESTRICTED", "Media is blocked by vehicle UX restrictions")
            return
        }
        tryEvaluateJavaScript(sessionKey, code, promise, attempt = 0)
    }

    private fun tryEvaluateJavaScript(sessionKey: String, code: String, promise: Promise, attempt: Int) {
        // A closed or never-opened session has no bridge to wait for: reject
        // now instead of polling, and never rebuild the session from here.
        if (GeckoSessionManager.getOpenSession(sessionKey) == null) {
            promise.reject(
                "EVAL_BRIDGE_UNAVAILABLE",
                "GeckoView session is closed or has not been opened by a mounted view"
            )
            return
        }
        val controller = GeckoRuntimeManager.getWebExtensionController()
        // Returns true once the eval request is queued (port present); the
        // bridge's own pendingEval then settles `promise`. A false return means
        // no port yet for this session.
        if (controller != null && controller.evaluateJavaScript(sessionKey, code, promise)) {
            return
        }
        if (attempt >= EVAL_BRIDGE_MAX_RETRIES) {
            promise.reject(
                "EVAL_BRIDGE_UNAVAILABLE",
                "Trusted GeckoView eval bridge is not connected for this session"
            )
            return
        }
        // The bridge content script connects at document_idle; on a fresh
        // navigation there's a brief window with no port. Retry briefly before
        // giving up rather than failing the call on a benign timing race.
        timeoutHandler.postDelayed(
            { tryEvaluateJavaScript(sessionKey, code, promise, attempt + 1) },
            EVAL_BRIDGE_RETRY_DELAY_MS
        )
    }

    /**
     * The session a mounted view opened for [sessionKey], or null once it is
     * closed. Ref methods run on the native-modules thread, where opening a
     * GeckoSession throws, so they never create one: void methods no-op and
     * promise methods reject.
     */
    private fun openSession(sessionKey: String): GeckoSession? =
        GeckoSessionManager.getOpenSession(sessionKey).also {
            if (it == null) GeckoLog.d(TAG, "No open session for ref method; ignoring")
        }

    fun storePendingPrompt(promptId: String, prompt: Any, result: GeckoResult<Any>) {
        pendingPrompts[promptId] = PromptInfo(prompt, result)
        schedulePromptTimeout(promptId)
    }

    fun storePendingPermission(requestId: String, result: GeckoResult<Int>) {
        pendingPermissions[requestId] = result
        schedulePermissionTimeout(requestId)
    }

    private fun schedulePromptTimeout(promptId: String) {
        val runnable = Runnable {
            pendingPrompts.remove(promptId)?.let {
                GeckoLog.d(TAG, "Prompt timed out; dropping")
                try {
                    val response = dismissResponse(it.prompt)
                    if (response == null) GeckoLog.w(TAG, "Unhandled prompt type on timeout")
                    response?.let { r -> it.result.complete(r) }
                } catch (e: Throwable) {
                    GeckoLog.w(TAG, "Error dismissing timed-out prompt", e)
                }
            }
            timeoutRunnables.remove(promptId)
        }
        timeoutRunnables[promptId] = runnable
        timeoutHandler.postDelayed(runnable, PROMPT_TIMEOUT_MS)
    }

    private fun schedulePermissionTimeout(requestId: String) {
        val runnable = Runnable {
            pendingPermissions.remove(requestId)?.let {
                GeckoLog.d(TAG, "Permission timed out; denying")
                it.complete(GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY)
            }
            timeoutRunnables.remove(requestId)
        }
        timeoutRunnables[requestId] = runnable
        timeoutHandler.postDelayed(runnable, PROMPT_TIMEOUT_MS)
    }

    private fun cancelTimeout(id: String) {
        timeoutRunnables.remove(id)?.let { timeoutHandler.removeCallbacks(it) }
    }

    private fun evictStaleSnapshots(dir: java.io.File) {
        val cutoff = System.currentTimeMillis() - SNAPSHOT_TTL_MS
        val toRemove = mutableListOf<java.io.File>()
        dir.listFiles()?.forEach { f ->
            if (f.lastModified() < cutoff) {
                try { f.delete() } catch (_: Throwable) {}
                toRemove.add(f)
            }
        }
        if (toRemove.isEmpty()) return
        synchronized(snapshotsLock) {
            for (set in snapshotsBySession.values) {
                set.removeAll(toRemove.toSet())
            }
        }
    }

    private fun cleanupSnapshotsForSession(sessionKey: String) {
        synchronized(snapshotsLock) {
            val set = snapshotsBySession.remove(sessionKey) ?: return
            for (f in set) {
                try { f.delete() } catch (_: Throwable) {}
            }
        }
    }

    // Test-only hooks (internal scope = library module only).
    internal fun seedSnapshotForTest(sessionKey: String, file: java.io.File) {
        synchronized(snapshotsLock) {
            snapshotsBySession.getOrPut(sessionKey) { mutableSetOf() }.add(file)
        }
    }

    internal fun snapshotsForSessionForTest(sessionKey: String): Set<java.io.File> =
        synchronized(snapshotsLock) {
            snapshotsBySession[sessionKey]?.toSet() ?: emptySet()
        }

    internal fun cleanupSnapshotsForSessionForTest(sessionKey: String) {
        cleanupSnapshotsForSession(sessionKey)
    }

    fun sendEvent(eventName: String, params: WritableMap?) {
        GeckoLog.d(TAG, "Sending global event")
        reactApplicationContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
            .emit(eventName, params)
    }

    fun sendViewEvent(viewId: Int, eventName: String, params: WritableMap?) {
        val nativeEventName = if (eventName.startsWith("on")) {
            "top" + eventName.substring(2)
        } else {
            eventName
        }
        GeckoLog.d(TAG, "Sending view event")
        val dispatcher = UIManagerHelper.getEventDispatcher(reactApplicationContext)
        if (dispatcher == null) {
            GeckoLog.d(TAG, "No EventDispatcher for view; dropping event")
            return
        }
        dispatcher.dispatchEvent(GeckoGenericEvent(-1, viewId, nativeEventName, params))
    }

    companion object {
        private const val TAG = "GeckoViewModule"
        private const val PROMPT_TIMEOUT_MS = 60_000L

        /** Eval connect-race retry budget: up to ~2s (20 × 100ms) for the bridge port to appear. */
        private const val EVAL_BRIDGE_MAX_RETRIES = 20
        private const val EVAL_BRIDGE_RETRY_DELAY_MS = 100L

        /** Snapshots older than this are deleted on the next capture. */
        private const val SNAPSHOT_TTL_MS: Long = 5L * 60L * 1000L

        /**
         * Schemes permitted via the public `loadUrl` API.
         *
         * `javascript:` is excluded — host apps that forward user input (deep
         * link, QR code, pasted URL) would otherwise expose an XSS primitive
         * against the currently loaded origin. [evaluateJavaScript] uses the
         * trusted internal WebExtension bridge instead of public URL loading.
         */
        val ALLOWED_LOAD_SCHEMES = setOf("http", "https", "about")

        fun isSafeLoadUrl(url: String): Boolean {
            if (url.isEmpty()) return false
            val parsed = try { Uri.parse(url) } catch (_: Throwable) { return false }
            val scheme = parsed.scheme?.lowercase() ?: return false
            return scheme in ALLOWED_LOAD_SCHEMES
        }
    }
}

private fun ReadableMap.getBooleanOrDefault(name: String, defaultValue: Boolean): Boolean {
    return if (hasKey(name) && !isNull(name)) getBoolean(name) else defaultValue
}

private fun ReadableMap.getIntOrDefault(name: String, defaultValue: Int): Int {
    return if (hasKey(name) && !isNull(name)) getInt(name) else defaultValue
}

private fun ReadableMap.getLongOrDefault(name: String, defaultValue: Long): Long {
    // ReadableMap has no getLong; doubles round-trip integer pixel counts safely.
    return if (hasKey(name) && !isNull(name)) getDouble(name).toLong() else defaultValue
}
