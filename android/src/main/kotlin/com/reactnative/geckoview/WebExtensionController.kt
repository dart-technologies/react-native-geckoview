package com.reactnative.geckoview

import android.util.Log
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import com.facebook.react.bridge.Arguments
import org.json.JSONObject
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.WebExtension

class WebExtensionController(private val runtime: GeckoRuntime) {
    private var extension: WebExtension? = null
    private var activePort: WebExtension.Port? = null

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
                Log.e(TAG, "Failed to simulate click", e)
            }
        }
    }

    private val messageDelegate = object : WebExtension.MessageDelegate {
        override fun onMessage(nativeApp: String, message: Any, sender: WebExtension.MessageSender): GeckoResult<Any>? {
            Log.d(TAG, "Received message from extension (nativeApp=$nativeApp): $message")
            try {
                if (message is JSONObject) {
                    val type = message.optString("type")
                    if (type == "simulateClick") {
                        val x = message.optDouble("x", Double.NaN)
                        val y = message.optDouble("y", Double.NaN)

                        if (x.isFinite() && y.isFinite()) {
                            val view = findBestTargetView()
                            if (view != null) {
                                simulateClick(view, x, y)
                            } else {
                                Log.w(TAG, "simulateClick requested but no GeckoView instances are mounted")
                            }
                        } else {
                            Log.w(TAG, "simulateClick requested without x/y: $message")
                        }

                        return null
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
