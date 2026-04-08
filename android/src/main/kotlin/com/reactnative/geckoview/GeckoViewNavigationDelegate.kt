package com.reactnative.geckoview

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.facebook.react.uimanager.ThemedReactContext
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession

class GeckoViewNavigationDelegate(
    private val context: ThemedReactContext,
    private val module: GeckoViewModule,
    private val viewId: Int
) : GeckoSession.NavigationDelegate {

    override fun onLocationChange(
        session: GeckoSession,
        url: String?,
        perms: MutableList<GeckoSession.PermissionDelegate.ContentPermission>,
        hasUserGesture: Boolean
    ) {
        Log.d(TAG, "onLocationChange called with url: $url, viewId: $viewId")
        if (url == null) return
        
        // Emit location change event to the view
        val params = com.facebook.react.bridge.Arguments.createMap().apply {
            putString("url", url)
        }
        Log.d(TAG, "Emitting onGeckoLocationChange event to viewId: $viewId")
        module.sendViewEvent(viewId, "onGeckoLocationChange", params)

        handleExternalNavigation(url)
    }

    override fun onNewSession(
        session: GeckoSession,
        uri: String
    ): GeckoResult<GeckoSession>? {
        Log.d(TAG, "onNewSession called with uri: $uri")
        
        val params = com.facebook.react.bridge.Arguments.createMap().apply {
            putString("targetUri", uri)
            putString("type", "popup")
            putString("promptId", java.util.UUID.randomUUID().toString())
        }
        module.sendViewEvent(viewId, "onGeckoPopup", params)
        
        return null
    }

    private fun handleExternalNavigation(url: String) {
        when {
            url.startsWith("intent://") -> handleIntentScheme(url)
            url.contains("play.google.com") || url.contains("market://") -> handlePlayStore(url)
            url.startsWith("geo:") || url.contains("maps.app.goo.gl") || url.contains("maps.google.com") -> handleMapsIntent(url)
        }
    }

    private fun handleIntentScheme(url: String) {
        try {
            val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
            if (context.packageManager.queryIntentActivities(intent, 0).isNotEmpty()) {
                context.currentActivity?.runOnUiThread {
                    AlertDialog.Builder(context.currentActivity)
                        .setTitle("Open External App?")
                        .setMessage("Allow opening ${intent.`package` ?: "external app"}?")
                        .setPositiveButton("Open") { _, _ -> context.startActivity(intent) }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling intent scheme", e)
        }
    }

    private fun handlePlayStore(url: String) {
        val appId = url.substringAfter("id=").substringBefore("&")
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$appId"))
        context.currentActivity?.runOnUiThread {
            AlertDialog.Builder(context.currentActivity)
                .setTitle("Open Play Store?")
                .setMessage("Open $appId in Play Store?")
                .setPositiveButton("Open") { _, _ -> context.startActivity(intent) }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun handleMapsIntent(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.currentActivity?.startActivity(intent)
    }

    override fun onLoadError(
        session: GeckoSession,
        uri: String?,
        error: org.mozilla.geckoview.WebRequestError
    ): org.mozilla.geckoview.GeckoResult<String>? {
        // Emit error event to React Native
        val params = com.facebook.react.bridge.Arguments.createMap().apply {
            putString("uri", uri)
            putInt("errorCode", error.code)
            putString("errorCategory", getErrorCategory(error.category))
            putString("errorMessage", getErrorMessage(error))
        }
        module.sendViewEvent(viewId, "onPageError", params)
        
        // Return null to use default error page, or return custom HTML
        return null
    }

    private fun getErrorCategory(category: Int): String {
        return when (category) {
            org.mozilla.geckoview.WebRequestError.ERROR_CATEGORY_UNKNOWN -> "unknown"
            org.mozilla.geckoview.WebRequestError.ERROR_CATEGORY_SECURITY -> "security"
            org.mozilla.geckoview.WebRequestError.ERROR_CATEGORY_NETWORK -> "network"
            org.mozilla.geckoview.WebRequestError.ERROR_CATEGORY_CONTENT -> "content"
            org.mozilla.geckoview.WebRequestError.ERROR_CATEGORY_URI -> "uri"
            org.mozilla.geckoview.WebRequestError.ERROR_CATEGORY_PROXY -> "proxy"
            org.mozilla.geckoview.WebRequestError.ERROR_CATEGORY_SAFEBROWSING -> "safebrowsing"
            else -> "unknown"
        }
    }

    private fun getErrorMessage(error: org.mozilla.geckoview.WebRequestError): String {
        return when (error.code) {
            org.mozilla.geckoview.WebRequestError.ERROR_UNKNOWN -> "Unknown error"
            org.mozilla.geckoview.WebRequestError.ERROR_SECURITY_SSL -> "SSL certificate error"
            org.mozilla.geckoview.WebRequestError.ERROR_SECURITY_BAD_CERT -> "Invalid certificate"
            org.mozilla.geckoview.WebRequestError.ERROR_NET_RESET -> "Connection reset"
            org.mozilla.geckoview.WebRequestError.ERROR_NET_TIMEOUT -> "Connection timeout"
            org.mozilla.geckoview.WebRequestError.ERROR_CONNECTION_REFUSED -> "Connection refused"
            org.mozilla.geckoview.WebRequestError.ERROR_UNKNOWN_PROTOCOL -> "Unknown protocol"
            org.mozilla.geckoview.WebRequestError.ERROR_UNKNOWN_HOST -> "Unknown host"
            org.mozilla.geckoview.WebRequestError.ERROR_MALFORMED_URI -> "Malformed URI"
            org.mozilla.geckoview.WebRequestError.ERROR_FILE_NOT_FOUND -> "File not found"
            org.mozilla.geckoview.WebRequestError.ERROR_FILE_ACCESS_DENIED -> "File access denied"
            org.mozilla.geckoview.WebRequestError.ERROR_UNKNOWN_PROXY_HOST -> "Unknown proxy host"
            org.mozilla.geckoview.WebRequestError.ERROR_SAFEBROWSING_MALWARE_URI -> "Malware detected"
            org.mozilla.geckoview.WebRequestError.ERROR_SAFEBROWSING_UNWANTED_URI -> "Unwanted software detected"
            org.mozilla.geckoview.WebRequestError.ERROR_SAFEBROWSING_HARMFUL_URI -> "Harmful site detected"
            org.mozilla.geckoview.WebRequestError.ERROR_SAFEBROWSING_PHISHING_URI -> "Phishing site detected"
            else -> "Error code: ${error.code}"
        }
    }

    companion object {
        private const val TAG = "GeckoNavDelegate"
    }
}
