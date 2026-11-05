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
        // Dropped, not queued, while playback is restricted: lifting the
        // restriction never reopens a session, so the host remounts the view.
        if (!GeckoSessionManager.isPlaybackAllowed()) return
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

    /**
     * After React Native applies a batch of the view's props. It doesn't order
     * the setters, so `handleAudioFocus` can arrive after `sessionKey` bound
     * the session: apply it to the session's MediaDelegate here. This also
     * covers the prop changing on a mounted view.
     */
    fun onAfterUpdateTransaction(view: GeckoView) {
        try {
            (view.session?.mediaSessionDelegate as? MediaDelegate)
                ?.setHandleAudioFocus(audioFocusMap[view] ?: false)
        } catch (e: Exception) {
            GeckoLog.w(TAG, "Failed to apply handleAudioFocus", e)
        }
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

                // A dropped view leaves its MediaDelegate handling the pooled
                // session's audio focus (see onDropViewInstance). Point it at
                // this view rather than replace it: a fresh delegate would have
                // to request focus again, interrupting other audio. This view's
                // handleAudioFocus reaches it in onAfterUpdateTransaction.
                val media = session.mediaSessionDelegate as? MediaDelegate
                if (media != null && session.isOpen) {
                    media.attachToView(module, viewId)
                } else {
                    // A session that isn't open plays nothing: GeckoView closed it
                    // after its content process died and the pool couldn't reopen
                    // it, or a restriction closed it since.
                    media?.release()
                    session.mediaSessionDelegate =
                        MediaDelegate(context, module, audioFocusMap[view] ?: false, viewId)
                }
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
        GeckoSessionManager.unregisterView(view)
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
                // The pooled session outlives this view and may keep playing.
                // Its MediaDelegate stays, without events, to handle audio focus
                // until the session closes or another view binds it; releasing it
                // here left the page playing with nothing to pause it on a focus
                // loss. A session GeckoView already closed plays nothing.
                val media = session.mediaSessionDelegate
                if (media is MediaDelegate && session.isOpen) {
                    media.detachFromView()
                    session.contentDelegate = DetachedContentDelegate
                } else {
                    (media as? MediaDelegate)?.release()
                    session.mediaSessionDelegate = null
                    session.contentDelegate = null
                }
            }
            view.releaseSession()
        } catch (e: Throwable) {
            GeckoLog.w(TAG, "Error dropping view instance", e)
        }
    }

    private fun loadUriWithRetry(view: GeckoView, uri: String, retryCount: Int = 0) {
        // Like setSessionKey: a source set while restricted is dropped until remount.
        if (!GeckoSessionManager.isPlaybackAllowed()) return
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

    /**
     * Left on a pooled session no view shows. GeckoView reports a dead content
     * process, and closes the session, only through its content delegate; the
     * MediaDelegate then gives up the focus it holds for media that is gone.
     */
    private object DetachedContentDelegate : GeckoSession.ContentDelegate {
        override fun onCrash(session: GeckoSession) = MediaDelegate.releaseFor(session)

        override fun onKill(session: GeckoSession) = MediaDelegate.releaseFor(session)
    }

    companion object {
        const val NAME = "GeckoView"
        private const val TAG = "GeckoViewManager"
    }
}
