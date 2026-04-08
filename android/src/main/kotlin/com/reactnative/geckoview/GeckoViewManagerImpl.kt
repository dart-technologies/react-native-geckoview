package com.reactnative.geckoview

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import com.facebook.react.bridge.ReactContext
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.uimanager.ThemedReactContext
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView
import java.util.WeakHashMap

class GeckoViewManagerImpl {
    private val audioFocusMap = WeakHashMap<GeckoView, Boolean>()
    private val pendingUris = WeakHashMap<GeckoView, String>()
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
                val themedContext = view.context as? ThemedReactContext
                if (themedContext != null) {
                    attachDelegates(themedContext, view, session)
                }

                // Check for pending URI
                val pendingUri = pendingUris.remove(view)
                if (pendingUri != null) {
                    Log.d(TAG, "Loading pending URI: $pendingUri")
                    loadUriWithRetry(view, pendingUri)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting session key: $sessionKey", e)
        }
    }

    fun setSource(view: GeckoView, source: ReadableMap?) {
        val uri = source?.getString("uri")
        if (uri != null) {
            loadUriWithRetry(view, uri)
        }
    }

    fun setHandleAudioFocus(view: GeckoView, handleAudioFocus: Boolean) {
        audioFocusMap[view] = handleAudioFocus
    }

    fun setContentBlockingEnabled(view: GeckoView, value: Boolean) {
        // TODO: Implement content blocking toggle
    }

    fun setCookieBannerMode(view: GeckoView, value: String?) {
        // TODO: Implement cookie banner mode
    }

    private fun getModule(context: ThemedReactContext): GeckoViewModule? {
        // Try multiple approaches to get the module
        return try {
            // Approach 1: Direct getNativeModule
            context.getNativeModule(GeckoViewModule::class.java)
                ?: run {
                    // Approach 2: Get from ReactApplicationContext
                    val reactContext = context.reactApplicationContext
                    reactContext.getNativeModule(GeckoViewModule::class.java)
                }
                ?: run {
                    // Approach 3: Get from singleton in package
                    Log.d(TAG, "Trying singleton fallback")
                    GeckoViewPackage.getModuleInstance()
                }
        } catch (e: Exception) {
            Log.w(TAG, "Error getting GeckoViewModule: ${e.message}")
            // Final fallback to singleton
            GeckoViewPackage.getModuleInstance()
        }
    }

    private fun attachDelegates(context: ThemedReactContext, view: GeckoView, session: GeckoSession, retryCount: Int = 0) {
        try {
            val module = getModule(context)
            if (module != null) {
                val viewId = view.id
                Log.d(TAG, "Attaching delegates to session, viewId: $viewId")
                session.navigationDelegate = GeckoViewNavigationDelegate(context, module, viewId)
                session.promptDelegate = GeckoViewPromptDelegate(module, viewId)
                session.permissionDelegate = GeckoViewPermissionDelegate(module, viewId)
                session.progressDelegate = GeckoViewProgressDelegate(module, viewId)
                session.contentDelegate = GeckoViewContentDelegate(module, viewId)

                val handleAudioFocus = audioFocusMap[view] ?: false
                session.mediaSessionDelegate = MediaDelegate(context, module, handleAudioFocus, viewId)
                Log.d(TAG, "Delegates attached successfully")
            } else if (retryCount < 3) {
                // Retry after a short delay - module might not be initialized yet
                Log.d(TAG, "GeckoViewModule not available, retrying in 100ms (attempt ${retryCount + 1})")
                mainHandler.postDelayed({
                    if (view.isAttachedToWindow) {
                        attachDelegates(context, view, session, retryCount + 1)
                    }
                }, 100)
            } else {
                Log.w(TAG, "GeckoViewModule not available after $retryCount retries, delegates not attached")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error attaching delegates", e)
        }
    }

    private fun loadUriWithRetry(view: GeckoView, uri: String, retryCount: Int = 0) {
        val session = view.session

        if (session == null) {
            Log.d(TAG, "Session not ready, queuing URI: $uri")
            pendingUris[view] = uri
            return
        }

        try {
            session.loadUri(uri)
            pendingUris.remove(view)
        } catch (e: Exception) {
            pendingUris[view] = uri

            if (retryCount < 3) {
                Log.w(TAG, "Failed to load URI (attempt ${retryCount + 1}): $uri", e)
                mainHandler.postDelayed(
                    {
                        if (view.isAttachedToWindow) {
                            loadUriWithRetry(view, uri, retryCount + 1)
                        }
                    },
                    150
                )
            } else {
                Log.e(TAG, "Failed to load URI after retries: $uri", e)
            }
        }
    }

    companion object {
        const val NAME = "GeckoView"
        private const val TAG = "GeckoViewManager"
    }
}
