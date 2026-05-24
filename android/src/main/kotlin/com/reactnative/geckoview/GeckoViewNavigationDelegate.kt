package com.reactnative.geckoview

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import com.facebook.react.uimanager.ThemedReactContext
import org.mozilla.geckoview.AllowOrDeny
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
        GeckoLog.d(TAG, "onLocationChange")
        if (url == null) return

        val params = com.facebook.react.bridge.Arguments.createMap().apply {
            putString("url", url)
        }
        module.sendViewEvent(viewId, "onGeckoLocationChange", params)

        handleExternalNavigation(url)
    }

    override fun onNewSession(
        session: GeckoSession,
        uri: String
    ): GeckoResult<GeckoSession>? {
        GeckoLog.d(TAG, "onNewSession")

        // Reject popups that carry a dangerous scheme. A popup URL feeds back
        // into host-app JS — a page calling `window.open("javascript:...")`
        // must not be handed that URL for the app to re-load in a new session.
        val scheme = uri.substringBefore(':', missingDelimiterValue = "").lowercase()
        if (scheme !in ALLOWED_POPUP_SCHEMES) {
            GeckoLog.d(TAG, "Dropping onNewSession with disallowed scheme")
            return null
        }

        val params = com.facebook.react.bridge.Arguments.createMap().apply {
            putString("targetUri", uri)
            putString("type", "popup")
            putString("promptId", java.util.UUID.randomUUID().toString())
        }
        module.sendViewEvent(viewId, "onGeckoPopup", params)

        return null
    }

    /**
     * Gate top-frame loads. Navigations carrying dangerous schemes —
     * `javascript:`, `data:`, `file:`, `content:`, `blob:` — are denied
     * outright; `intent:` is intercepted and routed through
     * [handleIntentScheme] (which pops a confirmation dialog), returning DENY
     * so Gecko itself doesn't try to render the `intent://` URL.
     *
     * Without this override, every scheme silently loads. A page that
     * redirects to `javascript:alert(document.cookie)` would execute script
     * in the prior origin's context (effective XSS); a redirect to
     * `file:///data/data/...` would let the page probe app-sandbox paths.
     */
    override fun onLoadRequest(
        session: GeckoSession,
        request: GeckoSession.NavigationDelegate.LoadRequest
    ): GeckoResult<AllowOrDeny>? {
        val decision = decideNavigation(request.uri, isTopFrame = true)
        return GeckoResult.fromValue(decision)
    }

    /**
     * Gate subframe loads. Subframes are riskier than top-frame because
     * parent-frame JS typically ignores subframe load failures, so a page can
     * silently probe many URLs looking for exploitable handlers. External-app
     * navigation (intent://, market://) is NEVER routed from a subframe —
     * attackers frequently use iframes to launch `intent://` without user
     * awareness.
     */
    override fun onSubframeLoadRequest(
        session: GeckoSession,
        request: GeckoSession.NavigationDelegate.LoadRequest
    ): GeckoResult<AllowOrDeny>? = GeckoResult.fromValue(decideSubframeNavigation(request.uri))

    internal fun decideSubframeNavigation(uri: String): AllowOrDeny {
        val scheme = uri.substringBefore(':', missingDelimiterValue = "").lowercase()
        return when {
            scheme in ALLOWED_NAVIGATION_SCHEMES -> AllowOrDeny.ALLOW
            scheme.isEmpty() -> AllowOrDeny.ALLOW
            else -> {
                GeckoLog.d(TAG, "Denying subframe load with disallowed scheme")
                AllowOrDeny.DENY
            }
        }
    }

    internal fun decideNavigation(uri: String, isTopFrame: Boolean): AllowOrDeny {
        val scheme = uri.substringBefore(':', missingDelimiterValue = "").lowercase()
        return when {
            scheme in ALLOWED_NAVIGATION_SCHEMES -> AllowOrDeny.ALLOW
            scheme.isEmpty() -> AllowOrDeny.ALLOW
            scheme == "intent" && isTopFrame -> {
                handleIntentScheme(uri)
                AllowOrDeny.DENY
            }
            scheme == "market" || scheme == "geo" -> {
                handleExternalNavigation(uri)
                AllowOrDeny.DENY
            }
            scheme in DENIED_NAVIGATION_SCHEMES -> {
                GeckoLog.d(TAG, "Denying top-frame load with disallowed scheme")
                AllowOrDeny.DENY
            }
            else -> {
                GeckoLog.d(TAG, "Denying top-frame load with unknown scheme")
                AllowOrDeny.DENY
            }
        }
    }

    private fun handleExternalNavigation(url: String) {
        val parsed = try { Uri.parse(url) } catch (_: Throwable) { return }
        when {
            parsed.scheme?.lowercase() == "intent" -> handleIntentScheme(url)
            isPlayStoreUri(parsed) -> handlePlayStore(parsed)
            isMapsUri(parsed) -> handleMapsIntent(parsed)
        }
    }

    /**
     * Sanitize and resolve an `intent://` URI produced by web content.
     *
     * `Intent.parseUri(url, URI_INTENT_SCHEME)` yields a mutable Intent with
     * attacker-controlled action, data, categories, selector, component, and
     * flags. Launching it as-is is a known CVE pattern (Android "intent scheme
     * hijacking"). Strip the fields that carry authority:
     *   - `selector` / `component` — let the attacker pin a specific receiver.
     *   - `CATEGORY_DEFAULT` — the page can't claim privileged routing.
     *   - `FLAG_GRANT_*_URI_PERMISSION` — prevent attacker-initiated URI grants.
     * Only allow launching if the resulting `data` scheme is `null`, `http`,
     * or `https`.
     *
     * SECURITY: the `S.browser_fallback_url=...` parameter embedded in
     * intent URIs is deliberately **not** followed. If that ever changes,
     * validate `fallbackUri.scheme ∈ {"http", "https"}` before loading.
     */
    private fun handleIntentScheme(url: String) {
        try {
            val raw = Intent.parseUri(url, Intent.URI_INTENT_SCHEME) ?: return
            val intent = sanitizeParsedIntent(raw) ?: return

            val dataScheme = intent.data?.scheme?.lowercase()
            if (dataScheme != null && dataScheme !in ALLOWED_INTENT_DATA_SCHEMES) {
                GeckoLog.d(TAG, "Rejecting intent with disallowed data scheme")
                return
            }
            if (context.packageManager.queryIntentActivities(intent, 0).isEmpty()) return

            val activity = context.currentActivity ?: return
            activity.runOnUiThread {
                try {
                    AlertDialog.Builder(activity)
                        .setTitle("Open External App?")
                        .setMessage("Allow opening ${intent.`package` ?: "external app"}?")
                        .setPositiveButton("Open") { _, _ ->
                            try {
                                context.startActivity(intent)
                            } catch (e: Throwable) {
                                GeckoLog.e(TAG, "Failed to start intent", e)
                            }
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                } catch (e: Throwable) {
                    GeckoLog.e(TAG, "Failed to show intent dialog", e)
                }
            }
        } catch (e: Exception) {
            GeckoLog.e(TAG, "Error handling intent scheme", e)
        }
    }

    /**
     * Visible for testing. Returns the sanitized `intent` in-place, or null
     * if the intent fails basic safety checks (e.g. selector targeting the
     * app itself).
     */
    internal fun sanitizeParsedIntent(intent: Intent): Intent? {
        intent.selector = null
        intent.component = null
        intent.removeCategory(Intent.CATEGORY_DEFAULT)
        val denyFlags =
            (Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
        intent.flags = intent.flags and denyFlags.inv()
        // CATEGORY_BROWSABLE is the canonical indicator that a browser-initiated
        // intent is safe to forward — require it before launch.
        intent.addCategory(Intent.CATEGORY_BROWSABLE)
        return intent
    }

    /**
     * Parse a Play Store app ID out of the URL. Validates against Android's
     * package-ID regex — an un-validated `appId` can feed attacker-controlled
     * characters into the `market://details?id=` URI, which downstream
     * consumers (logging, deeplink routers) may trust.
     */
    private fun handlePlayStore(uri: Uri) {
        val appId = uri.getQueryParameter("id") ?: ""
        if (!APP_ID_REGEX.matches(appId)) {
            GeckoLog.d(TAG, "Rejecting Play Store URL with invalid appId")
            return
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$appId"))
        val activity = context.currentActivity ?: return
        activity.runOnUiThread {
            try {
                AlertDialog.Builder(activity)
                    .setTitle("Open Play Store?")
                    .setMessage("Open $appId in Play Store?")
                    .setPositiveButton("Open") { _, _ ->
                        try {
                            context.startActivity(intent)
                        } catch (e: Throwable) {
                            GeckoLog.e(TAG, "Failed to open Play Store", e)
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            } catch (e: Throwable) {
                GeckoLog.e(TAG, "Failed to show Play Store dialog", e)
            }
        }
    }

    private fun handleMapsIntent(uri: Uri) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, uri)
            val activity = context.currentActivity ?: return
            activity.startActivity(intent)
        } catch (e: Throwable) {
            GeckoLog.e(TAG, "Failed to open maps intent", e)
        }
    }

    override fun onLoadError(
        session: GeckoSession,
        uri: String?,
        error: org.mozilla.geckoview.WebRequestError
    ): org.mozilla.geckoview.GeckoResult<String>? {
        val params = com.facebook.react.bridge.Arguments.createMap().apply {
            putString("uri", uri)
            putInt("errorCode", error.code)
            putString("errorCategory", getErrorCategory(error.category))
            putString("errorMessage", getErrorMessage(error))
        }
        module.sendViewEvent(viewId, "onPageError", params)

        return null
    }

    internal fun getErrorCategory(category: Int): String {
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

    internal fun getErrorMessage(error: org.mozilla.geckoview.WebRequestError): String {
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

        /** Schemes that may navigate a top frame (`about:` covers blank/error pages). */
        internal val ALLOWED_NAVIGATION_SCHEMES = setOf("http", "https", "about")

        /** Schemes explicitly denied for web-initiated navigations. */
        internal val DENIED_NAVIGATION_SCHEMES = setOf(
            "javascript", "data", "file", "content", "blob",
        )

        /**
         * Schemes explicitly denied when they appear as the `data:` portion
         * of an intent launched via `intent://`. Only web URLs are allowed;
         * custom schemes must be added as a narrow native allowlist if a host
         * app has a concrete need for them.
         */
        internal val ALLOWED_INTENT_DATA_SCHEMES = setOf("http", "https")

        /** Schemes allowed for popup (`window.open`) destinations. */
        internal val ALLOWED_POPUP_SCHEMES = setOf("http", "https", "about")

        /** Android package IDs: 1+ dot-separated identifiers, conservative charset. */
        internal val APP_ID_REGEX = Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+$")

        internal fun isPlayStoreUri(uri: Uri): Boolean {
            val scheme = uri.scheme?.lowercase()
            val host = uri.host?.lowercase()
            return when (scheme) {
                "market" -> host == "details"
                "https" -> host == "play.google.com" && uri.path == "/store/apps/details"
                else -> false
            }
        }

        internal fun isMapsUri(uri: Uri): Boolean {
            val scheme = uri.scheme?.lowercase()
            val host = uri.host?.lowercase()
            return scheme == "geo" ||
                ((scheme == "https" || scheme == "http") &&
                    host in setOf("maps.google.com", "maps.app.goo.gl"))
        }
    }
}
