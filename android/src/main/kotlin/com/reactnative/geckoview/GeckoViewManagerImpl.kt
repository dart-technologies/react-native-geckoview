package com.reactnative.geckoview

import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import com.facebook.react.bridge.ReactContext
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.uimanager.ThemedReactContext
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.GeckoView
import java.util.WeakHashMap

class GeckoViewManagerImpl {
    private val audioFocusMap = WeakHashMap<GeckoView, Boolean>()
    private val pendingUris = WeakHashMap<GeckoView, String>()
    // Default true preserves the prior hardcoded desktop UA/viewport behavior.
    private val desktopModeMap = WeakHashMap<GeckoView, Boolean>()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun getName() = NAME

    fun createViewInstance(context: ThemedReactContext): GeckoView {
        GeckoSessionManager.init(context.applicationContext)
        return GeckoView(context).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS

            // Ensure the view receives D-pad key events on TV.
            post { requestFocus() }
        }
    }

    fun setSessionKey(view: GeckoView, sessionKey: String?) {
        if (sessionKey == null) return
        try {
            val session = GeckoSessionManager.getOrCreateSession(sessionKey)
            if (view.session != session) {
                view.setSession(session)
                view.post { view.requestFocus() }
                GeckoSessionManager.registerView(session, view)
                applyDesktopMode(session, desktopModeMap[view] ?: true)
                val themedContext = view.context as? ThemedReactContext
                if (themedContext != null) {
                    attachDelegates(themedContext, view, session)
                }

                // Check for pending URI
                val pendingUri = pendingUris.remove(view)
                if (pendingUri != null) {
                    GeckoLog.d(TAG, "Loading pending URI")
                    loadUriWithRetry(view, pendingUri)
                }
            }
        } catch (e: Exception) {
            GeckoLog.e(TAG, "Error setting session key", e)
        }
    }

    fun setSource(view: GeckoView, source: ReadableMap?) {
        val uri = source?.getString("uri") ?: return
        if (!GeckoViewModule.isSafeLoadUrl(uri)) {
            GeckoLog.d(TAG, "Rejecting source URI with disallowed scheme")
            return
        }
        loadUriWithRetry(view, uri)
    }

    fun setHandleAudioFocus(view: GeckoView, handleAudioFocus: Boolean) {
        audioFocusMap[view] = handleAudioFocus
    }

    fun setContentBlockingEnabled(view: GeckoView, value: Boolean) {
        try {
            val runtime = GeckoRuntimeManager.getRuntime(view.context.applicationContext)
            val level = if (value) 2 else 0
            ContentBlockingController(runtime).setEnhancedTrackingProtectionLevel(level)
        } catch (e: Exception) {
            GeckoLog.w(TAG, "Failed to toggle content blocking", e)
        }
    }

    fun setCookieBannerMode(view: GeckoView, value: String?) {
        val mode = when (value?.trim()?.lowercase()) {
            "reject" -> 1
            "accept" -> 2
            else -> 0
        }

        try {
            val runtime = GeckoRuntimeManager.getRuntime(view.context.applicationContext)
            ContentBlockingController(runtime).setCookieBannerMode(mode)
        } catch (e: Exception) {
            GeckoLog.w(TAG, "Failed to set cookie banner mode", e)
        }
    }

    fun setDesktopMode(view: GeckoView, value: Boolean) {
        desktopModeMap[view] = value
        // Prop order isn't guaranteed; if the session already exists, apply
        // immediately, otherwise setSessionKey applies the stored value.
        view.session?.let { applyDesktopMode(it, value) }
    }

    private fun applyDesktopMode(session: GeckoSession, desktop: Boolean) {
        try {
            session.settings.userAgentMode = if (desktop)
                GeckoSessionSettings.USER_AGENT_MODE_DESKTOP
            else
                GeckoSessionSettings.USER_AGENT_MODE_MOBILE
            session.settings.viewportMode = if (desktop)
                GeckoSessionSettings.VIEWPORT_MODE_DESKTOP
            else
                GeckoSessionSettings.VIEWPORT_MODE_MOBILE
        } catch (e: Throwable) {
            GeckoLog.w(TAG, "Failed to apply desktopMode", e)
        }
    }

    private fun getModule(context: ThemedReactContext): GeckoViewModule? {
        return try {
            context.getNativeModule(GeckoViewModule::class.java)
                ?: context.reactApplicationContext.getNativeModule(GeckoViewModule::class.java)
                ?: GeckoViewPackage.getModuleInstance()
        } catch (e: Exception) {
            GeckoLog.w(TAG, "Error getting GeckoViewModule", e)
            GeckoViewPackage.getModuleInstance()
        }
    }

    private fun attachDelegates(context: ThemedReactContext, view: GeckoView, session: GeckoSession, retryCount: Int = 0) {
        try {
            val module = getModule(context)
            if (module != null) {
                val viewId = view.id
                GeckoLog.d(TAG, "Attaching delegates to session")
                session.navigationDelegate = GeckoViewNavigationDelegate(context, module, viewId)
                session.promptDelegate = GeckoViewPromptDelegate(module, viewId)
                session.permissionDelegate = GeckoViewPermissionDelegate(module, viewId)
                session.progressDelegate = GeckoViewProgressDelegate(module, viewId)
                session.contentDelegate = GeckoViewContentDelegate(module, viewId)

                val handleAudioFocus = audioFocusMap[view] ?: false
                session.mediaSessionDelegate = MediaDelegate(context, module, handleAudioFocus, viewId)
                GeckoLog.d(TAG, "Delegates attached successfully")
            } else if (retryCount < 3) {
                // Retry after a short delay - module might not be initialized yet
                GeckoLog.d(TAG, "GeckoViewModule not available, retrying")
                mainHandler.postDelayed({
                    if (view.isAttachedToWindow) {
                        attachDelegates(context, view, session, retryCount + 1)
                    }
                }, 100)
            } else {
                GeckoLog.w(TAG, "GeckoViewModule not available after retries, delegates not attached")
            }
        } catch (e: Exception) {
            GeckoLog.e(TAG, "Error attaching delegates", e)
        }
    }

    fun onDropViewInstance(view: GeckoView) {
        try {
            audioFocusMap.remove(view)
            pendingUris.remove(view)
            desktopModeMap.remove(view)
            val session = view.session
            if (session != null) {
                session.navigationDelegate = null
                session.promptDelegate = null
                session.permissionDelegate = null
                session.progressDelegate = null
                session.contentDelegate = null
                session.mediaSessionDelegate = null
            }
            view.releaseSession()
        } catch (e: Throwable) {
            GeckoLog.w(TAG, "Error dropping view instance", e)
        }
    }

    private fun loadUriWithRetry(view: GeckoView, uri: String, retryCount: Int = 0) {
        val session = view.session

        if (session == null) {
            GeckoLog.d(TAG, "Session not ready, queuing URI")
            pendingUris[view] = uri
            return
        }

        try {
            session.loadUri(uri)
            pendingUris.remove(view)
        } catch (e: Exception) {
            pendingUris[view] = uri

            if (retryCount < 3) {
                GeckoLog.d(TAG, "Failed to load URI; retrying")
                mainHandler.postDelayed(
                    {
                        if (view.isAttachedToWindow) {
                            loadUriWithRetry(view, uri, retryCount + 1)
                        }
                    },
                    150
                )
            } else {
                GeckoLog.e(TAG, "Failed to load URI after retries", e)
            }
        }
    }

    companion object {
        const val NAME = "GeckoView"
        private const val TAG = "GeckoViewManager"
    }
}
