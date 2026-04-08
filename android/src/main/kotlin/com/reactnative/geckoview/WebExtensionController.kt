package com.reactnative.geckoview

import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.MotionEvent
import com.facebook.react.bridge.Arguments
import org.json.JSONObject
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.WebExtension

class WebExtensionController(private val runtime: GeckoRuntime) {
    private var extension: WebExtension? = null
    private var activePort: WebExtension.Port? = null

    private fun simulateClick(x: Double, y: Double): Boolean {
        val views = GeckoSessionManager.getAllViews()
        if (views.isEmpty()) {
            Log.w(TAG, "simulateClick: no GeckoView instances available")
            return false
        }

        val targetView = views.firstOrNull { it.hasFocus() } ?: views.first()

        targetView.post {
            try {
                val width = targetView.width
                val height = targetView.height
                if (width <= 0 || height <= 0) {
                    Log.w(TAG, "simulateClick: view not laid out (w=$width h=$height)")
                    return@post
                }

                val maxX = (width - 1).coerceAtLeast(0).toDouble()
                val maxY = (height - 1).coerceAtLeast(0).toDouble()
                val clampedX = x.coerceIn(0.0, maxX).toFloat()
                val clampedY = y.coerceIn(0.0, maxY).toFloat()

                val downTime = SystemClock.uptimeMillis()
                val down = MotionEvent.obtain(
                    downTime,
                    downTime,
                    MotionEvent.ACTION_DOWN,
                    clampedX,
                    clampedY,
                    0
                ).apply {
                    source = InputDevice.SOURCE_TOUCHSCREEN
                }

                val up = MotionEvent.obtain(
                    downTime,
                    downTime + 32,
                    MotionEvent.ACTION_UP,
                    clampedX,
                    clampedY,
                    0
                ).apply {
                    source = InputDevice.SOURCE_TOUCHSCREEN
                }

                targetView.dispatchTouchEvent(down)
                targetView.dispatchTouchEvent(up)

                down.recycle()
                up.recycle()
            } catch (e: Exception) {
                Log.e(TAG, "simulateClick failed", e)
            }
        }

        return true
    }

    fun installExtension(assetPath: String, onInstalled: ((Boolean, Throwable?) -> Unit)? = null) {
        val builtInUri = "resource://android/assets/$assetPath"
        val extensionId = when (assetPath.trimEnd('/')) {
            "spatial_navigation" -> "spatial-navigation@geckoview.dev"
            else -> null
        }

        val installResult = if (extensionId != null) {
            runtime.webExtensionController.ensureBuiltIn(builtInUri, extensionId)
        } else {
            runtime.webExtensionController.installBuiltIn(builtInUri)
        }

        installResult.accept({ ext ->
            extension = ext
            ext?.setMessageDelegate(messageDelegate, NATIVE_APP_ID)
            Log.d(TAG, "Extension installed: ${ext?.metaData?.name}")
            onInstalled?.invoke(true, null)
        }, { e ->
            Log.e(TAG, "Error installing extension", e)
            onInstalled?.invoke(false, e)
        })
    }

    fun sendMessage(message: String) {
        try {
            val json = JSONObject()
            json.put("message", message)
            activePort?.postMessage(json)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send message", e)
        }
    }

    private val messageDelegate = object : WebExtension.MessageDelegate {
        override fun onMessage(nativeApp: String, message: Any, sender: WebExtension.MessageSender): GeckoResult<Any>? {
            Log.d(TAG, "Received message from extension (nativeApp=$nativeApp): $message")
            try {
                if (message is JSONObject) {
                    val type = message.optString("type", "")
                    if (type == "simulateClick") {
                        val x = if (message.has("x")) message.optDouble("x", Double.NaN) else Double.NaN
                        val y = if (message.has("y")) message.optDouble("y", Double.NaN) else Double.NaN
                        if (x.isFinite() && y.isFinite()) {
                            simulateClick(x, y)
                            return GeckoResult.fromValue(null)
                        }
                        Log.w(TAG, "simulateClick: invalid coordinates x=$x y=$y")
                        return GeckoResult.fromValue(null)
                    }
                }

                val module = GeckoViewPackage.getModuleInstance()
                if (module == null) {
                    Log.w(TAG, "GeckoViewModule not ready; dropping web extension message")
                    return null
                }

                val messageMap = Arguments.createMap().apply {
                    when (message) {
                        is JSONObject -> {
                            putString("type", message.optString("type"))
                            if (message.has("direction")) {
                                putString("direction", message.optString("direction"))
                            }
                            if (message.has("inTrap")) {
                                putBoolean("inTrap", message.optBoolean("inTrap", false))
                            }
                            putString("raw", message.toString())
                        }
                        else -> {
                            putString("raw", message.toString())
                        }
                    }
                }

                val params = Arguments.createMap().apply {
                    putMap("message", messageMap)
                }

                // Emit to all mounted GeckoView instances (typically just one).
                for (view in GeckoSessionManager.getAllViews()) {
                    module.sendViewEvent(view.id, "onWebExtensionMessage", params)
                }
                return null
            } catch (e: Exception) {
                Log.e(TAG, "Failed to handle web extension message", e)
                return null
            }
        }

        override fun onConnect(port: WebExtension.Port) {
            activePort = port
            port.setDelegate(object : WebExtension.PortDelegate {
                override fun onPortMessage(message: Any, port: WebExtension.Port) {
                    Log.d(TAG, "Received port message: $message")
                    // Ideally emit this to JS
                }

                override fun onDisconnect(port: WebExtension.Port) {
                    if (activePort == port) activePort = null
                }
            })
        }
    }

    companion object {
        private const val TAG = "WebExtensionController"
        private const val NATIVE_APP_ID = "flutter_geckoview"
    }
}
