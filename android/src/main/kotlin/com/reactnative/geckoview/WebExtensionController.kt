package com.reactnative.geckoview

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import org.json.JSONObject
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.WebExtension
import java.util.Collections
import java.util.UUID

class WebExtensionController(private val runtime: GeckoRuntime) {
    private var extension: WebExtension? = null
    private var internalBridge: WebExtension? = null
    private val ports: MutableMap<PortKey, WebExtension.Port> =
        Collections.synchronizedMap(mutableMapOf())
    private val pendingEval: MutableMap<String, PendingEval> =
        Collections.synchronizedMap(mutableMapOf())
    private val timeoutHandler = Handler(Looper.getMainLooper())

    private data class PortKey(val extensionId: String, val sessionKey: String?)
    private data class PendingEval(
        // The port the request went out on. A disconnect settles only its own
        // requests: ports are keyed by session, and a document that replaced
        // this one may already have connected a new port under the same key.
        val port: WebExtension.Port?,
        val promise: Promise,
        val timeout: Runnable,
    )

    /**
     * Install a bundled WebExtension from the app's `assets/` directory.
     *
     * The caller-supplied [assetPath] is validated before URI construction:
     * path-traversal sequences, URL-encoded traversal, null bytes, backslash
     * separators, absolute paths, and non-alphanumeric characters are
     * rejected. The AOSP `AssetManager` already strips `..` at resolution
     * time, but validating here prevents the gap from re-emerging if the
     * loader is ever refactored away from `resource://`.
     *
     * Failure is delivered exclusively through [onInstalled]; this method
     * does not throw on validation failure.
     */
    fun installExtension(assetPath: String, onInstalled: ((Boolean, Throwable?) -> Unit)? = null) {
        installExtensionInternal(assetPath, onInstalled)
    }

    fun installInternalBridge(onInstalled: ((Boolean, Throwable?) -> Unit)? = null) {
        installExtensionInternal(
            INTERNAL_BRIDGE_ASSET_PATH,
            onInstalled,
            forcedExtensionId = INTERNAL_BRIDGE_EXTENSION_ID,
            reportAsUserExtension = false,
        )
    }

    private fun installExtensionInternal(
        assetPath: String,
        onInstalled: ((Boolean, Throwable?) -> Unit)? = null,
        forcedExtensionId: String? = null,
        reportAsUserExtension: Boolean = true,
    ) {
        if (!isSafeAssetPath(assetPath)) {
            // Single completion path: callback only. A trailing `throw` here
            // would surface the same failure twice — once via the callback
            // (which a Promise wrapper translates to reject) and again as an
            // uncaught exception that would propagate to the caller's outer
            // try/catch and reject a second time.
            GeckoLog.d(TAG, "Rejecting installExtension with unsafe path")
            onInstalled?.invoke(false, IllegalArgumentException("Unsafe extension asset path"))
            return
        }

        val builtInUri = "resource://android/assets/${assetPath.trimEnd('/')}/"
        val extensionId = forcedExtensionId ?: when (assetPath.trimEnd('/')) {
            "spatial_navigation" -> "spatial-navigation@geckoview.dev"
            else -> null
        }

        val installResult = if (extensionId != null) {
            runtime.webExtensionController.ensureBuiltIn(builtInUri, extensionId)
        } else {
            runtime.webExtensionController.installBuiltIn(builtInUri)
        }

        installResult.accept({ ext ->
            if (reportAsUserExtension) extension = ext
            if (forcedExtensionId == INTERNAL_BRIDGE_EXTENSION_ID && ext != null) {
                internalBridge = ext
                // ensureBuiltIn completes asynchronously: bind sessions which
                // were opened before the trusted extension became available.
                GeckoSessionManager.getAllSessions().values.forEach { bindInternalBridge(it) }
            }
            // Register under every native-app id we bridge. The internal eval
            // bridge connects via "react_native_geckoview"; the spatial-nav
            // extension's relay is pinned to "flutter_geckoview" (unoverridable
            // since its 3.0.1 hardening), so we must listen on both.
            for (appId in NATIVE_APP_IDS) {
                ext?.setMessageDelegate(messageDelegate, appId)
            }
            GeckoLog.d(TAG, "Extension installed")
            onInstalled?.invoke(true, null)
        }, { e ->
            GeckoLog.e(TAG, "Error installing extension", e)
            onInstalled?.invoke(false, e)
        })
    }

    /** Content-script messaging requires a session delegate, not only the
     * extension-level background delegate. Only the APK's trusted eval bridge
     * receives this binding. See Mozilla's consumer/docs/web-extensions guide. */
    fun bindInternalBridge(session: org.mozilla.geckoview.GeckoSession) {
        val bridge = internalBridge ?: return
        session.webExtensionController.setMessageDelegate(bridge, messageDelegate, NATIVE_APP_ID)
    }

    fun sendMessage(sessionKey: String, extensionId: String, message: String): Boolean {
        val port = ports[PortKey(extensionId, sessionKey)]
            ?: ports[PortKey(extensionId, null)]
            ?: return false
        return try {
            port.postMessage(JSONObject().apply {
                put("type", HOST_MESSAGE_TYPE)
                put("message", message)
            })
            true
        } catch (e: Exception) {
            GeckoLog.e(TAG, "Failed to send WebExtension message", e)
            false
        }
    }

    fun evaluateJavaScript(sessionKey: String, code: String, promise: Promise): Boolean {
        val port = ports[PortKey(INTERNAL_BRIDGE_EXTENSION_ID, sessionKey)] ?: return false
        val requestId = UUID.randomUUID().toString()
        val timeout = Runnable {
            pendingEval.remove(requestId)?.promise?.reject(
                "EVAL_TIMEOUT",
                "Timed out waiting for trusted GeckoView eval bridge"
            )
        }
        pendingEval[requestId] = PendingEval(port, promise, timeout)
        // Checked after the entry is visible, so either this sees a UX
        // restriction or the restriction's rejectAllPendingEvals sees the entry.
        if (!GeckoSessionManager.isPlaybackAllowed()) {
            pendingEval.remove(requestId)?.promise?.reject(
                GeckoSessionManager.PLAYBACK_RESTRICTED,
                GeckoSessionManager.PLAYBACK_RESTRICTED_MESSAGE,
            )
            return true
        }
        timeoutHandler.postDelayed(timeout, EVAL_TIMEOUT_MS)
        return try {
            port.postMessage(JSONObject().apply {
                put("type", EVAL_REQUEST_TYPE)
                put("requestId", requestId)
                put("code", code)
            })
            true
        } catch (e: Throwable) {
            timeoutHandler.removeCallbacks(timeout)
            GeckoLog.e(TAG, "Failed to send eval request to bridge", e)
            // Already gone means a concurrent rejection settled the promise:
            // report it handled so the caller doesn't reject it a second time.
            pendingEval.remove(requestId) == null
        }
    }

    private fun findBestTargetView(): GeckoView? {
        val views = GeckoSessionManager.getAllViews()
        if (views.isEmpty()) return null

        for (view in views) {
            if (view.hasFocus()) return view
        }

        for (view in views) {
            if (view.isAttachedToWindow) return view
        }

        return views[0]
    }

    private fun isTrustedForInput(extensionId: String): Boolean {
        return extensionId == INTERNAL_BRIDGE_EXTENSION_ID ||
            GeckoRuntimeManager.allowedExtensionIds.contains(extensionId)
    }

    private fun emitFocusExit(direction: String) {
        val module = GeckoViewPackage.getModuleInstance() ?: return
        val view = findBestTargetView()
        if (view == null) {
            GeckoLog.d(TAG, "focusExit received without mounted GeckoView")
            return
        }
        module.sendViewEvent(view.id, "onFocusExit", Arguments.createMap().apply {
            putString("direction", direction)
        })
    }

    private fun simulateClick(view: GeckoView, x: Double, y: Double) {
        view.post {
            try {
                view.requestFocus()

                val maxX = (view.width - 1).coerceAtLeast(0).toFloat()
                val maxY = (view.height - 1).coerceAtLeast(0).toFloat()
                val localX = x.toFloat().coerceIn(0f, maxX)
                val localY = y.toFloat().coerceIn(0f, maxY)

                val downTime = SystemClock.uptimeMillis()
                val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, localX, localY, 0)
                down.source = InputDevice.SOURCE_TOUCHSCREEN

                val upTime = downTime + 16
                val up = MotionEvent.obtain(downTime, upTime, MotionEvent.ACTION_UP, localX, localY, 0)
                up.source = InputDevice.SOURCE_TOUCHSCREEN

                view.dispatchTouchEvent(down)
                view.dispatchTouchEvent(up)

                down.recycle()
                up.recycle()
            } catch (e: Throwable) {
                GeckoLog.e(TAG, "Failed to simulate click", e)
            }
        }
    }

    private val messageDelegate = object : WebExtension.MessageDelegate {
        override fun onMessage(nativeApp: String, message: Any, sender: WebExtension.MessageSender): GeckoResult<Any>? {
            GeckoLog.d(TAG, "Received message from extension")
            val extensionId = sender.webExtension?.id ?: extension?.id ?: "unknown"
            handleIncomingMessage(extensionId, message)
            return null
        }

        override fun onConnect(port: WebExtension.Port) {
            val key = PortKey(
                extensionId = port.sender.webExtension?.id ?: extension?.id ?: "unknown",
                sessionKey = port.sender.session?.let { GeckoSessionManager.getSessionKey(it) },
            )
            ports[key] = port
            port.setDelegate(object : WebExtension.PortDelegate {
                override fun onPortMessage(message: Any, port: WebExtension.Port) {
                    if (message is JSONObject && message.optString("type") == EVAL_RESULT_TYPE) {
                        handleEvalResult(message)
                    } else {
                        GeckoLog.d(TAG, "Received WebExtension port message")
                    }
                }

                override fun onDisconnect(port: WebExtension.Port) {
                    synchronized(ports) { ports.entries.removeAll { it.value === port } }
                    rejectPendingForPort(port, "EVAL_BRIDGE_UNAVAILABLE", "Trusted GeckoView eval bridge disconnected")
                }
            })
        }
    }

    /**
     * Shared message-dispatch logic for [messageDelegate].onMessage. Takes the
     * already-resolved [extensionId] so the gating/branching is identical
     * whether the message arrives via the GeckoView sender or a test hook.
     */
    private fun handleIncomingMessage(extensionId: String, message: Any) {
        try {
            if (message is JSONObject) {
                val type = message.optString("type")
                if (type.isBlank()) {
                    GeckoLog.d(TAG, "Dropping WebExtension message with blank type")
                    return
                }
                if (type == EVAL_RESULT_TYPE) {
                    handleEvalResult(message)
                    return
                }
                if (type == "simulateClick") {
                    // Synthesizing a touch is an input-injection primitive;
                    // only honor it from trusted first-party extensions (the
                    // internal bridge and the allowlisted spatial-nav
                    // extension that drives D-pad activation).
                    if (!isTrustedForInput(extensionId)) {
                        GeckoLog.d(TAG, "Ignoring simulateClick from untrusted extension")
                        return
                    }
                    val x = message.optDouble("x", Double.NaN)
                    val y = message.optDouble("y", Double.NaN)

                    if (x.isFinite() && y.isFinite()) {
                        val view = findBestTargetView()
                        if (view != null) {
                            simulateClick(view, x, y)
                        } else {
                            GeckoLog.d(TAG, "simulateClick requested without mounted GeckoView")
                        }
                    } else {
                        GeckoLog.d(TAG, "simulateClick requested without x/y")
                    }

                    return
                }
                if (type == "focusExit") {
                    // The spatial-nav extension posts focusExit as a native
                    // message; emit the same onFocusExit event the legacy
                    // title-prefix path (GeckoViewContentDelegate) produces.
                    val direction = message.optString("direction").lowercase()
                    if (direction.isNotBlank()) emitFocusExit(direction)
                    return
                }
            }

            val module = GeckoViewPackage.getModuleInstance()
            if (module == null) {
                GeckoLog.d(TAG, "GeckoViewModule not ready; dropping web extension message")
                return
            }

            val messageMap = Arguments.createMap().apply {
                putString("extensionId", extensionId)
                when (message) {
                    is JSONObject -> {
                        putString("type", message.optString("type"))
                        if (message.has("direction")) {
                            putString("direction", message.optString("direction"))
                        }
                        if (message.has("inTrap")) {
                            putBoolean("inTrap", message.optBoolean("inTrap", false))
                        }
                        if (message.has("modality")) {
                            putString("modality", message.optString("modality"))
                        }
                        putString("raw", safeSerialize(message))
                    }
                    else -> {
                        putString("raw", safeSerialize(message))
                    }
                }
            }

            val params = Arguments.createMap().apply {
                putMap("message", messageMap)
            }

            for (view in GeckoSessionManager.getAllViews()) {
                module.sendViewEvent(view.id, "onWebExtensionMessage", params)
            }
        } catch (e: Exception) {
            GeckoLog.e(TAG, "Failed to handle web extension message", e)
        }
    }

    private fun handleEvalResult(message: JSONObject) {
        val requestId = message.optString("requestId")
        if (requestId.isBlank()) return
        val pending = pendingEval.remove(requestId) ?: return
        timeoutHandler.removeCallbacks(pending.timeout)
        if (message.has("error")) {
            pending.promise.reject("EVAL_ERROR", message.optString("error"))
        } else {
            pending.promise.resolve(if (message.has("result")) message.optString("result") else "undefined")
        }
    }

    // ---- test-only hooks (mirroring GeckoSecurityConfig.resetForTests style)
    // `internal` scopes these to the library Gradle module; consumer apps
    // can't see or call them.

    internal fun seedPendingEvalForTest(
        requestId: String,
        promise: Promise,
        port: WebExtension.Port? = null,
    ) {
        pendingEval[requestId] = PendingEval(
            port = port,
            promise = promise,
            timeout = Runnable { /* unused in tests */ },
        )
    }

    internal fun rejectPendingForPortForTest(port: WebExtension.Port, code: String, message: String) {
        rejectPendingForPort(port, code, message)
    }

    internal fun simulateEvalResultForTest(requestId: String, result: String?) {
        val json = JSONObject().apply {
            put("type", EVAL_RESULT_TYPE)
            put("requestId", requestId)
            if (result != null) put("result", result)
        }
        handleEvalResult(json)
    }

    /**
     * Drives the message-dispatch logic with an explicit sender id so tests can
     * exercise the `simulateClick`/`focusExit`/`onWebExtensionMessage` branches
     * without GeckoView's final [WebExtension.MessageSender]. Production behavior
     * is unchanged — `messageDelegate.onMessage` resolves the same `extensionId`
     * from the sender and then calls the identical [handleIncomingMessage] path.
     */
    internal fun handleMessageForTest(extensionId: String, message: Any) {
        handleIncomingMessage(extensionId, message)
    }

    internal fun isTrustedForInputForTest(extensionId: String): Boolean =
        isTrustedForInput(extensionId)

    /**
     * Registers [port] through the real [messageDelegate].onConnect path so
     * tests can exercise port-backed `sendMessage`/`evaluateJavaScript` success
     * and the [WebExtension.PortDelegate] (result handling + disconnect cleanup)
     * without a live extension. Production behavior is unchanged.
     */
    internal fun connectPortForTest(port: WebExtension.Port) {
        messageDelegate.onConnect(port)
    }

    /**
     * Drives the real [messageDelegate].onMessage path (sender-id resolution +
     * dispatch) so tests can cover the [WebExtension.MessageDelegate] wiring
     * without GeckoView delivering the message. Production behavior is unchanged;
     * the SDK calls the identical `onMessage` on a live extension.
     */
    internal fun dispatchOnMessageForTest(
        sender: WebExtension.MessageSender,
        message: Any,
    ): GeckoResult<Any>? = messageDelegate.onMessage("nativeApp", message, sender)

    /**
     * Settles every in-flight evaluation, e.g. with PLAYBACK_RESTRICTED when a
     * UX restriction is about to close all sessions.
     */
    fun rejectAllPendingEvals(code: String, message: String) {
        rejectPending(code, message) { true }
    }

    private fun rejectPendingForPort(port: WebExtension.Port, code: String, message: String) {
        rejectPending(code, message) { it.port === port }
    }

    private fun rejectPending(code: String, message: String, matches: (PendingEval) -> Boolean) {
        // Snapshot the request IDs under the map lock — synchronizedMap requires
        // external sync when iterating any view. Then settle each promise via
        // an atomic remove(): if a concurrent handleEvalResult already pulled
        // the entry, remove() returns null and we skip the second completion.
        val ids = synchronized(pendingEval) {
            pendingEval.entries.filter { matches(it.value) }.map { it.key }
        }
        for (requestId in ids) {
            val pending = pendingEval.remove(requestId) ?: continue
            timeoutHandler.removeCallbacks(pending.timeout)
            pending.promise.reject(code, message)
        }
    }

    companion object {
        private const val TAG = "WebExtensionController"
        internal const val NATIVE_APP_ID = "react_native_geckoview"

        /**
         * The spatial-navigation extension (`@dart-technologies/spatial-navigation-geckoview`)
         * relays to a native-messaging app id hard-pinned to `flutter_geckoview`
         * since its 3.0.1 security hardening — it cannot be reconfigured from the
         * host. We register the message delegate under both ids so its
         * `simulateClick`/`focusExit`/`inputModalityChange` messages are delivered.
         */
        internal const val SPATIAL_NATIVE_APP_ID = "flutter_geckoview"
        internal val NATIVE_APP_IDS = arrayOf(NATIVE_APP_ID, SPATIAL_NATIVE_APP_ID)
        internal const val INTERNAL_BRIDGE_EXTENSION_ID = "react-native-geckoview-bridge@geckoview.dev"
        internal const val INTERNAL_BRIDGE_ASSET_PATH = "react_native_geckoview_bridge"
        private const val HOST_MESSAGE_TYPE = "hostMessage"
        internal const val EVAL_REQUEST_TYPE = "reactNativeGeckoViewEval"
        internal const val EVAL_RESULT_TYPE = "reactNativeGeckoViewEvalResult"
        private const val EVAL_TIMEOUT_MS = 10_000L

        /** Max serialized length of a WebExtension message surfaced to JS. Larger messages are truncated. */
        internal const val MAX_MESSAGE_BYTES = 1_048_576

        /**
         * Strict allowlist for WebExtension asset paths. Rejects traversal,
         * URL-encoded traversal, null bytes, backslash separators, absolute
         * paths, and anything containing characters outside the conservative
         * identifier-plus-extension charset.
         */
        internal fun isSafeAssetPath(path: String): Boolean {
            if (path.isEmpty() || path.length > 256) return false
            if (path.contains("..")) return false
            if (path.contains("%2e", ignoreCase = true)) return false
            if (path.contains("%2f", ignoreCase = true)) return false
            if (path.contains("%5c", ignoreCase = true)) return false
            if (path.contains('\u0000')) return false
            if (path.contains('\\')) return false
            if (path.startsWith('/')) return false
            return path.matches(Regex("^[a-zA-Z0-9_\\-./]+$"))
        }

        /**
         * Serialize a message, capping the output to [MAX_MESSAGE_BYTES] to
         * avoid UI-thread OOM from a buggy or hostile extension. `toString()`
         * on a pathologically deep JSONObject does not recurse in O(n^2),
         * but it can allocate a single very large String — capping the output
         * is the simplest, most portable defense.
         */
        internal fun safeSerialize(message: Any): String {
            val raw = try { message.toString() } catch (_: Throwable) { "" }
            return if (raw.length > MAX_MESSAGE_BYTES) {
                raw.substring(0, MAX_MESSAGE_BYTES) + "\u2026[truncated]"
            } else {
                raw
            }
        }
    }
}
