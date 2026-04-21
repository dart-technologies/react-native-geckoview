package com.reactnative.geckoview

import android.util.Log
import com.facebook.react.bridge.*
import com.facebook.react.modules.core.DeviceEventManagerModule
import com.facebook.react.uimanager.UIManagerHelper
import com.facebook.react.uimanager.events.Event
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession

/**
 * Generic view-level event for GeckoView. Wraps an event name + payload so a
 * single class can carry every `onGeckoXxx` → `topGeckoXxx` dispatch. Uses the
 * `Event(viewTag)` constructor (surfaceId defaults to -1); the EventDispatcher
 * resolves the surfaceId from the viewTag for both Fabric and legacy renderers.
 */
private class GeckoGenericEvent(
    viewTag: Int,
    private val nativeEventName: String,
    private val params: WritableMap?,
) : Event<GeckoGenericEvent>(viewTag) {
    override fun getEventName(): String = nativeEventName
    override fun getEventData(): WritableMap? = params
}

class GeckoViewModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {
    private val pendingPrompts = mutableMapOf<String, PromptInfo>()
    private val pendingPermissions = mutableMapOf<String, GeckoResult<Int>>()
    
    data class PromptInfo(val prompt: Any, val result: GeckoResult<Any>)

    init {
        // Ensure GeckoSessionManager has a Context even if sessions are created via module methods
        // before a GeckoView instance is mounted.
        GeckoSessionManager.init(reactContext.applicationContext)
    }

    override fun getName() = "GeckoViewModule"

    @ReactMethod
    fun reload(sessionKey: String) {
        GeckoSessionManager.getOrCreateSession(sessionKey).reload()
    }

    @ReactMethod
    fun goBack(sessionKey: String) {
        GeckoSessionManager.getOrCreateSession(sessionKey).goBack()
    }

    @ReactMethod
    fun goForward(sessionKey: String) {
        GeckoSessionManager.getOrCreateSession(sessionKey).goForward()
    }

    @ReactMethod
    fun stop(sessionKey: String) {
        GeckoSessionManager.getOrCreateSession(sessionKey).stop()
    }

    @ReactMethod
    fun closeSession(sessionKey: String) {
        // GeckoSession.close() asserts on the UI thread (ThreadUtils.assertOnUiThread).
        // @ReactMethod calls run on the native modules queue (`mqt_v_native`), so
        // calling through unguarded throws IllegalThreadStateException — which the
        // bridgeless runtime treats as a fatal host exception and destroys the
        // ReactHost, leaving the user on a white screen after back navigation.
        UiThreadUtil.runOnUiThread {
            GeckoSessionManager.releaseSession(sessionKey)
        }
    }

    @ReactMethod
    fun shutdown() {
        GeckoRuntimeManager.shutdown()
    }

    @ReactMethod
    fun loadUrl(sessionKey: String, url: String) {
        GeckoSessionManager.getOrCreateSession(sessionKey).loadUri(url)
    }

    @ReactMethod
    fun setUserAgent(sessionKey: String, userAgent: String) {
        val session = GeckoSessionManager.getOrCreateSession(sessionKey)
        if (userAgent.isEmpty()) {
            session.settings.userAgentMode = org.mozilla.geckoview.GeckoSessionSettings.USER_AGENT_MODE_MOBILE
            session.settings.userAgentOverride = null
        } else {
            session.settings.userAgentMode = org.mozilla.geckoview.GeckoSessionSettings.USER_AGENT_MODE_DESKTOP
            session.settings.userAgentOverride = userAgent
        }
    }

    @ReactMethod
    fun resolvePrompt(promptId: String, confirm: Boolean, text: String?, username: String?) {
        pendingPrompts.remove(promptId)?.let { pending ->
            try {
                val response = when (val prompt = pending.prompt) {
                    is GeckoSession.PromptDelegate.AlertPrompt -> {
                        val method = prompt.javaClass.getDeclaredMethod("confirm")
                        method.isAccessible = true
                        method.invoke(prompt) as? GeckoSession.PromptDelegate.PromptResponse
                    }
                    is GeckoSession.PromptDelegate.ButtonPrompt -> {
                        val method = prompt.javaClass.getDeclaredMethod("confirm", GeckoSession.PromptDelegate.ButtonPrompt.Type::class.java)
                        method.isAccessible = true
                        if (confirm) method.invoke(prompt, GeckoSession.PromptDelegate.ButtonPrompt.Type.POSITIVE) as? GeckoSession.PromptDelegate.PromptResponse
                        else method.invoke(prompt, GeckoSession.PromptDelegate.ButtonPrompt.Type.NEGATIVE) as? GeckoSession.PromptDelegate.PromptResponse
                    }
                    is GeckoSession.PromptDelegate.TextPrompt -> {
                        if (confirm && text != null) {
                            val method = prompt.javaClass.getDeclaredMethod("confirm", String::class.java)
                            method.isAccessible = true
                            method.invoke(prompt, text) as? GeckoSession.PromptDelegate.PromptResponse
                        } else {
                            val method = prompt.javaClass.getDeclaredMethod("dismiss")
                            method.isAccessible = true
                            method.invoke(prompt) as? GeckoSession.PromptDelegate.PromptResponse
                        }
                    }
                    is PopupPromptWrapper -> {
                        prompt.respond(confirm)
                    }
                    is BeforeUnloadPromptWrapper -> {
                        prompt.confirm(confirm)
                    }
                    is AuthPromptWrapper -> {
                        if (confirm && username != null && text != null) {
                            prompt.confirm(username, text)
                        } else {
                            prompt.dismiss()
                        }
                    }
                    else -> null
                }
                response?.let { pending.result.complete(it) }
            } catch (e: Exception) {
                Log.e(TAG, "Error resolving prompt", e)
            }
        }
    }

    @ReactMethod
    fun resolvePermission(requestId: String, allow: Boolean) {
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
        try {
            // Ensure runtime/controller exist even if a session hasn't been opened yet.
            GeckoRuntimeManager.getRuntime(reactApplicationContext)
            val controller = GeckoRuntimeManager.getWebExtensionController()
            if (controller == null) {
                promise.reject("GECKO_RUNTIME", "WebExtensionController not available")
                return
            }

            controller.installExtension(assetPath) { success, error ->
                if (success) {
                    promise.resolve(null)
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

    @ReactMethod
    fun sendWebExtensionMessage(message: String) {
        GeckoRuntimeManager.getWebExtensionController()?.sendMessage(message)
    }

    @ReactMethod
    fun setEnhancedTrackingProtectionLevel(level: Int) {
        val runtime = GeckoRuntimeManager.getRuntime()
        runtime?.let {
            ContentBlockingController(it).setEnhancedTrackingProtectionLevel(level)
        }
    }

    @ReactMethod
    fun setCookieBannerMode(mode: Int) {
        val runtime = GeckoRuntimeManager.getRuntime()
        runtime?.let {
            ContentBlockingController(it).setCookieBannerMode(mode)
        }
    }

    @ReactMethod
    fun captureSnapshot(sessionKey: String, promise: Promise) {
        val session = GeckoSessionManager.getOrCreateSession(sessionKey)
        val view = GeckoSessionManager.getView(session)
        
        if (view == null) {
            promise.reject("VIEW_NOT_FOUND", "No GeckoView attached to session: $sessionKey")
            return
        }

        view.capturePixels().then { bitmap ->
            if (bitmap != null) {
                try {
                    val file = java.io.File.createTempFile("snapshot", ".png", reactApplicationContext.cacheDir)
                    val stream = java.io.FileOutputStream(file)
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
                    stream.close()
                    promise.resolve(file.absolutePath)
                } catch (e: Exception) {
                    promise.reject("SNAPSHOT_ERROR", "Error saving snapshot: ${e.message}")
                }
            } else {
                promise.reject("SNAPSHOT_ERROR", "Failed to capture snapshot: Bitmap is null")
            }
            GeckoResult.fromValue(null)
        }
    }

    @ReactMethod
    fun evaluateJavaScript(sessionKey: String, code: String, promise: Promise) {
        val session = GeckoSessionManager.getOrCreateSession(sessionKey)
        val uuid = java.util.UUID.randomUUID().toString()
        
        // Wrapper to capture result and send back via alert
        val wrappedCode = """
            (function() {
                try {
                    var result = (function() { $code })();
                    alert("__EVAL_RESULT__:$uuid:" + JSON.stringify(result));
                } catch (e) {
                    alert("__EVAL_ERROR__:$uuid:" + e.message);
                }
            })();
        """.trimIndent()

        // Store promise to resolve later when alert is intercepted
        pendingPrompts[uuid] = PromptInfo(promise, GeckoResult())
        
        session.loadUri("javascript:$wrappedCode")
    }

    internal fun resolveEval(uuid: String, result: String?, error: String?) {
        pendingPrompts.remove(uuid)?.let { info ->
            val promise = info.prompt as? Promise
            if (promise != null) {
                if (error != null) {
                    promise.reject("EVAL_ERROR", error)
                } else {
                    promise.resolve(result)
                }
            }
        }
    }

    internal fun storePendingPrompt(promptId: String, prompt: Any, result: GeckoResult<Any>) {
        pendingPrompts[promptId] = PromptInfo(prompt, result)
    }

    internal fun storePendingPermission(requestId: String, result: GeckoResult<Int>) {
        pendingPermissions[requestId] = result
    }

    internal fun sendEvent(eventName: String, params: WritableMap?) {
        Log.d(TAG, "Sending global event: $eventName")
        reactApplicationContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
            .emit(eventName, params)
    }

    internal fun sendViewEvent(viewId: Int, eventName: String, params: WritableMap?) {
        // Convert "onXxx" to "topXxx" for native event dispatching
        val nativeEventName = if (eventName.startsWith("on")) {
            "top" + eventName.substring(2)
        } else {
            eventName
        }
        Log.d(TAG, "Sending view event: $nativeEventName to viewId: $viewId")
        // Legacy `getJSModule(RCTEventEmitter)` throws IllegalArgumentException on
        // BridgelessReactContext (New Architecture) — every GeckoView event (page
        // start/stop/location-change) would emit an unhandled SoftException, and
        // enough of them during unmount destroys the React host, leaving a white
        // screen. UIManagerHelper.getEventDispatcherForReactTag works in both
        // bridge and bridgeless modes; it returns null if the view is unmounted.
        val dispatcher = UIManagerHelper.getEventDispatcherForReactTag(reactApplicationContext, viewId)
        if (dispatcher == null) {
            Log.w(TAG, "No EventDispatcher for viewTag=$viewId, dropping $nativeEventName")
            return
        }
        dispatcher.dispatchEvent(GeckoGenericEvent(viewId, nativeEventName, params))
    }

    companion object {
        private const val TAG = "GeckoViewModule"
    }
}
