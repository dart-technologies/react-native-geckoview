package com.reactnative.geckoview

import android.app.Activity
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import com.facebook.react.uimanager.ThemedReactContext
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import android.content.Intent
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.WebRequestError
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for [GeckoViewNavigationDelegate].
 *
 * Play Store dialog paths are not covered here because `AlertDialog.Builder`
 * requires a real activity to instantiate; those are verified via device
 * integration tests. `WebRequestError` is final with final fields, so
 * `onLoadError` is covered structurally but the category/message mapping is
 * tested through integration.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class GeckoViewNavigationDelegateTest {

    private val module: GeckoViewModule = mockk(relaxed = true)
    private val session: GeckoSession = mockk(relaxed = true)
    private val error: WebRequestError = mockk(relaxed = true)

    private lateinit var context: ThemedReactContext
    private lateinit var activity: Activity
    private lateinit var packageManager: PackageManager
    private lateinit var navigationDelegate: GeckoViewNavigationDelegate
    private val testViewId = 123

    @Before
    fun setUp() {
        TestHelpers.mockArguments()

        context = mockk(relaxed = true)
        activity = mockk(relaxed = true)
        packageManager = mockk(relaxed = true)

        every { context.currentActivity } returns activity
        every { context.packageManager } returns packageManager
        every { activity.runOnUiThread(any()) } answers { firstArg<Runnable>().run() }

        navigationDelegate = GeckoViewNavigationDelegate(context, module, testViewId)
    }

    @After
    fun tearDown() {
        TestHelpers.unmockArguments()
    }

    // onLocationChange — external-navigation routing

    @Test
    fun `onLocationChange ignores null url`() {
        navigationDelegate.onLocationChange(session, null, mutableListOf(), false)
        verify(exactly = 0) { context.startActivity(any()) }
    }

    @Test
    fun `onLocationChange ignores normal http urls`() {
        navigationDelegate.onLocationChange(session, "https://example.com", mutableListOf(), false)
        verify(exactly = 0) { context.startActivity(any()) }
    }

    @Test
    fun `onLocationChange ignores normal https urls with path`() {
        navigationDelegate.onLocationChange(
            session, "https://example.com/path/to/page?query=value", mutableListOf(), false
        )
        verify(exactly = 0) { context.startActivity(any()) }
    }

    @Test
    fun `onLocationChange handles maps google com url`() {
        navigationDelegate.onLocationChange(
            session, "https://maps.google.com/maps?q=37.7749,-122.4194", mutableListOf(), false
        )
        verify { activity.startActivity(any()) }
    }

    @Test
    fun `onLocationChange handles geo scheme url`() {
        navigationDelegate.onLocationChange(session, "geo:37.7749,-122.4194", mutableListOf(), false)
        verify { activity.startActivity(any()) }
    }

    @Test
    fun `onLocationChange handles maps app goo gl url`() {
        navigationDelegate.onLocationChange(
            session, "https://maps.app.goo.gl/abc123", mutableListOf(), false
        )
        verify { activity.startActivity(any()) }
    }

    @Test
    fun `onLocationChange handles intent scheme with available app`() {
        val url = "intent://example#Intent;scheme=https;package=com.example.app;end"
        every { packageManager.queryIntentActivities(any(), any<Int>()) } returns listOf(mockk<ResolveInfo>())

        navigationDelegate.onLocationChange(session, url, mutableListOf(), false)
        verify { activity.runOnUiThread(any()) }
    }

    @Test
    fun `onLocationChange ignores intent scheme with no available app`() {
        every { packageManager.queryIntentActivities(any(), any<Int>()) } returns emptyList()

        navigationDelegate.onLocationChange(
            session,
            "intent://example#Intent;scheme=myapp;package=com.unavailable.app;end",
            mutableListOf(),
            false,
        )
        verify(exactly = 0) { context.startActivity(any()) }
    }

    @Test
    fun `onLocationChange handles malformed intent url gracefully`() {
        navigationDelegate.onLocationChange(
            session, "intent://malformed-intent-url", mutableListOf(), false
        )
        // No crash = pass
    }

    // onGeckoLocationChange event dispatch

    @Test
    fun `onLocationChange emits onGeckoLocationChange event with url`() {
        navigationDelegate.onLocationChange(
            session, "https://example.com/page", mutableListOf(), false
        )
        verify { module.sendViewEvent(testViewId, "onGeckoLocationChange", any()) }
    }

    @Test
    fun `onLocationChange sends correct viewId`() {
        navigationDelegate.onLocationChange(session, "https://example.com", mutableListOf(), false)
        verify { module.sendViewEvent(testViewId, "onGeckoLocationChange", any()) }
    }

    @Test
    fun `onLocationChange emits event for about blank url`() {
        navigationDelegate.onLocationChange(session, "about:blank", mutableListOf(), false)
        verify { module.sendViewEvent(testViewId, "onGeckoLocationChange", any()) }
    }

    @Test
    fun `onLocationChange emits event before handling external navigation`() {
        navigationDelegate.onLocationChange(
            session, "https://maps.google.com/maps?q=test", mutableListOf(), false
        )
        verify { module.sendViewEvent(testViewId, "onGeckoLocationChange", any()) }
    }

    // onLoadError

    @Test
    fun `onLoadError emits onPageError event`() {
        navigationDelegate.onLoadError(session, "https://example.com", error)
        verify { module.sendViewEvent(testViewId, "onPageError", any()) }
    }

    @Test
    fun `onLoadError returns null for default error handling`() {
        val result = navigationDelegate.onLoadError(session, "https://example.com", error)
        assert(result == null)
    }

    @Test
    fun `onLoadError handles null uri`() {
        navigationDelegate.onLoadError(session, null, error)
        verify(atLeast = 1) { module.sendViewEvent(testViewId, "onPageError", any()) }
    }

    // ========== onLoadRequest — top-frame scheme gate (Finding #6) ==========

    private fun loadResult(uri: String): AllowOrDeny =
        navigationDelegate.decideNavigation(uri, isTopFrame = true)

    private fun subframeResult(uri: String): AllowOrDeny =
        navigationDelegate.decideSubframeNavigation(uri)

    @Test
    fun `onLoadRequest allows http`() {
        assertEquals(AllowOrDeny.ALLOW, loadResult("http://example.com"))
    }

    @Test
    fun `onLoadRequest allows https`() {
        assertEquals(AllowOrDeny.ALLOW, loadResult("https://example.com/path"))
    }

    @Test
    fun `onLoadRequest allows about`() {
        assertEquals(AllowOrDeny.ALLOW, loadResult("about:blank"))
    }

    @Test
    fun `onLoadRequest denies javascript URI`() {
        assertEquals(AllowOrDeny.DENY, loadResult("javascript:alert(document.cookie)"))
    }

    @Test
    fun `onLoadRequest denies data URI`() {
        assertEquals(AllowOrDeny.DENY, loadResult("data:text/html,<script>alert(1)</script>"))
    }

    @Test
    fun `onLoadRequest denies file URI`() {
        assertEquals(AllowOrDeny.DENY, loadResult("file:///data/data/com.example/cookies"))
    }

    @Test
    fun `onLoadRequest denies content URI`() {
        assertEquals(AllowOrDeny.DENY, loadResult("content://com.android.contacts/1"))
    }

    @Test
    fun `onLoadRequest denies blob URI`() {
        assertEquals(AllowOrDeny.DENY, loadResult("blob:https://example.com/abc"))
    }

    @Test
    fun `onLoadRequest denies top-frame intent but routes to handler`() {
        every { packageManager.queryIntentActivities(any(), any<Int>()) } returns emptyList()
        // Top-frame intent:// navigations must NOT be allowed by Gecko —
        // the delegate returns DENY and pops a confirmation dialog instead.
        assertEquals(AllowOrDeny.DENY, loadResult("intent://x#Intent;package=com.example;end"))
    }

    @Test
    fun `onLoadRequest routes market URL through external handler`() {
        assertEquals(AllowOrDeny.DENY, loadResult("market://details?id=com.example.app"))
    }

    @Test
    fun `onLoadRequest denies unknown schemes by default`() {
        assertEquals(AllowOrDeny.DENY, loadResult("customscheme://example"))
    }

    @Test
    fun `onLoadRequest is case-insensitive on scheme`() {
        assertEquals(AllowOrDeny.DENY, loadResult("JavaScript:alert(1)"))
        assertEquals(AllowOrDeny.ALLOW, loadResult("HTTPS://example.com"))
    }

    // The override entry points (onLoadRequest / onSubframeLoadRequest) wrap the
    // pure decide* helpers in a GeckoResult. Drive them with a mocked LoadRequest
    // whose final `uri` field is set via reflection, then unwrap the result.

    private fun loadRequest(uri: String): GeckoSession.NavigationDelegate.LoadRequest {
        val req: GeckoSession.NavigationDelegate.LoadRequest = mockk(relaxed = true)
        TestHelpers.setField(req, "uri", uri)
        return req
    }

    @Suppress("UNCHECKED_CAST")
    private fun unwrap(result: Any?): AllowOrDeny =
        (result as org.mozilla.geckoview.GeckoResult<AllowOrDeny>).poll(1000)!!

    @Test
    fun `onLoadRequest override allows https and denies javascript`() {
        assertEquals(AllowOrDeny.ALLOW, unwrap(navigationDelegate.onLoadRequest(session, loadRequest("https://example.com"))))
        assertEquals(AllowOrDeny.DENY, unwrap(navigationDelegate.onLoadRequest(session, loadRequest("javascript:alert(1)"))))
    }

    @Test
    fun `onSubframeLoadRequest override allows https and denies intent`() {
        assertEquals(AllowOrDeny.ALLOW, unwrap(navigationDelegate.onSubframeLoadRequest(session, loadRequest("https://example.com/frame"))))
        assertEquals(AllowOrDeny.DENY, unwrap(navigationDelegate.onSubframeLoadRequest(session, loadRequest("intent://x#Intent;package=com.example;end"))))
    }

    @Test
    fun `handleMapsIntent swallows a throwing startActivity`() {
        every { activity.startActivity(any()) } throws RuntimeException("no maps app")

        // The maps catch (L250-251) must absorb the failure rather than crashing
        // the location-change callback.
        navigationDelegate.onLocationChange(session, "geo:37.7749,-122.4194", mutableListOf(), false)

        verify { activity.startActivity(any()) }
    }

    // ========== onSubframeLoadRequest — stricter gate ==========

    @Test
    fun `onSubframeLoadRequest allows http`() {
        assertEquals(AllowOrDeny.ALLOW, subframeResult("http://example.com/frame"))
    }

    @Test
    fun `onSubframeLoadRequest allows https`() {
        assertEquals(AllowOrDeny.ALLOW, subframeResult("https://example.com/frame"))
    }

    @Test
    fun `onSubframeLoadRequest denies intent (never from iframe)`() {
        assertEquals(AllowOrDeny.DENY, subframeResult("intent://x#Intent;package=com.example;end"))
    }

    @Test
    fun `onSubframeLoadRequest denies market from iframe`() {
        assertEquals(AllowOrDeny.DENY, subframeResult("market://details?id=com.example.app"))
    }

    @Test
    fun `onSubframeLoadRequest denies javascript URI`() {
        assertEquals(AllowOrDeny.DENY, subframeResult("javascript:alert(1)"))
    }

    @Test
    fun `onSubframeLoadRequest denies data URI`() {
        assertEquals(AllowOrDeny.DENY, subframeResult("data:text/html,<script>alert(1)</script>"))
    }

    // ========== sanitizeParsedIntent — intent-scheme sanitization (Finding #3) ==========

    @Test
    fun `sanitizeParsedIntent clears selector`() {
        val raw = Intent.parseUri(
            "intent://target#Intent;scheme=myapp;package=com.target;SEL;scheme=evil;package=com.evil;end;end",
            Intent.URI_INTENT_SCHEME,
        )!!
        val sanitized = navigationDelegate.sanitizeParsedIntent(raw)!!
        assertNull("selector must be stripped", sanitized.selector)
    }

    @Test
    fun `sanitizeParsedIntent clears explicit component`() {
        val raw = Intent.parseUri(
            "intent://target#Intent;scheme=myapp;component=com.evil/.Activity;end",
            Intent.URI_INTENT_SCHEME,
        )!!
        val sanitized = navigationDelegate.sanitizeParsedIntent(raw)!!
        assertNull("component must be stripped", sanitized.component)
    }

    @Test
    fun `sanitizeParsedIntent clears grant URI flags`() {
        val raw = Intent()
        raw.flags = (Intent.FLAG_GRANT_READ_URI_PERMISSION or
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
            Intent.FLAG_GRANT_PREFIX_URI_PERMISSION or
            Intent.FLAG_ACTIVITY_NEW_TASK)
        val sanitized = navigationDelegate.sanitizeParsedIntent(raw)!!
        assertEquals(
            "All FLAG_GRANT_* must be cleared; FLAG_ACTIVITY_NEW_TASK may remain",
            0,
            sanitized.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                Intent.FLAG_GRANT_PREFIX_URI_PERMISSION),
        )
    }

    @Test
    fun `sanitizeParsedIntent adds BROWSABLE category`() {
        val raw = Intent()
        val sanitized = navigationDelegate.sanitizeParsedIntent(raw)!!
        assertTrue(sanitized.hasCategory(Intent.CATEGORY_BROWSABLE))
    }

    @Test
    fun `sanitizeParsedIntent removes DEFAULT category`() {
        val raw = Intent().apply { addCategory(Intent.CATEGORY_DEFAULT) }
        val sanitized = navigationDelegate.sanitizeParsedIntent(raw)!!
        assertFalse(sanitized.hasCategory(Intent.CATEGORY_DEFAULT))
    }

    // ========== Intent data-scheme filter ==========

    @Test
    fun `intent with file data scheme is rejected`() {
        every { packageManager.queryIntentActivities(any(), any<Int>()) } returns listOf(mockk())
        navigationDelegate.onLocationChange(
            session,
            "intent://#Intent;scheme=file;package=com.target;end",
            mutableListOf(),
            false,
        )
        verify(exactly = 0) { activity.runOnUiThread(any()) }
    }

    @Test
    fun `intent with content data scheme is rejected`() {
        every { packageManager.queryIntentActivities(any(), any<Int>()) } returns listOf(mockk())
        navigationDelegate.onLocationChange(
            session,
            "intent://x#Intent;scheme=content;package=com.target;end",
            mutableListOf(),
            false,
        )
        verify(exactly = 0) { activity.runOnUiThread(any()) }
    }

    @Test
    fun `intent with custom data scheme is rejected`() {
        every { packageManager.queryIntentActivities(any(), any<Int>()) } returns listOf(mockk())
        navigationDelegate.onLocationChange(
            session,
            "intent://track/abc#Intent;scheme=spotify;package=com.spotify.music;end",
            mutableListOf(),
            false,
        )
        verify(exactly = 0) { activity.runOnUiThread(any()) }
    }

    // ========== Play Store appId validation (Finding #11) ==========

    @Test
    fun `play store rejects URL-encoded traversal in appId`() {
        navigationDelegate.onLocationChange(
            session,
            "https://play.google.com/store/apps/details?id=%20../../evil",
            mutableListOf(),
            false,
        )
        verify(exactly = 0) { activity.runOnUiThread(any()) }
    }

    @Test
    fun `play store rejects empty appId`() {
        navigationDelegate.onLocationChange(
            session,
            "https://play.google.com/store/apps/details?id=",
            mutableListOf(),
            false,
        )
        verify(exactly = 0) { activity.runOnUiThread(any()) }
    }

    @Test
    fun `play store rejects appId with slash`() {
        navigationDelegate.onLocationChange(
            session,
            "https://play.google.com/store/apps/details?id=com/example/evil",
            mutableListOf(),
            false,
        )
        verify(exactly = 0) { activity.runOnUiThread(any()) }
    }

    @Test
    fun `play store rejects appId missing dot`() {
        navigationDelegate.onLocationChange(
            session,
            "https://play.google.com/store/apps/details?id=singleword",
            mutableListOf(),
            false,
        )
        verify(exactly = 0) { activity.runOnUiThread(any()) }
    }

    @Test
    fun `play store accepts well-formed appId`() {
        navigationDelegate.onLocationChange(
            session,
            "https://play.google.com/store/apps/details?id=com.example.app&hl=en",
            mutableListOf(),
            false,
        )
        verify(exactly = 1) { activity.runOnUiThread(any()) }
    }

    @Test
    fun `play store substring host spoof is ignored`() {
        navigationDelegate.onLocationChange(
            session,
            "https://evil.example/?next=https://play.google.com/store/apps/details?id=com.example.app",
            mutableListOf(),
            false,
        )
        verify(exactly = 0) { activity.runOnUiThread(any()) }
    }

    @Test
    fun `maps substring host spoof is ignored`() {
        navigationDelegate.onLocationChange(
            session,
            "https://evil.example/?next=https://maps.google.com/maps?q=test",
            mutableListOf(),
            false,
        )
        verify(exactly = 0) { activity.startActivity(any()) }
    }

    // ========== onNewSession — popup URI scheme filter (Finding #19) ==========

    @Test
    fun `onNewSession drops javascript popup without emitting event`() {
        navigationDelegate.onNewSession(session, "javascript:alert(1)")
        verify(exactly = 0) { module.sendViewEvent(any(), any(), any()) }
    }

    @Test
    fun `onNewSession drops data popup without emitting event`() {
        navigationDelegate.onNewSession(session, "data:text/html,<script>alert(1)</script>")
        verify(exactly = 0) { module.sendViewEvent(any(), any(), any()) }
    }

    @Test
    fun `onNewSession drops file popup without emitting event`() {
        navigationDelegate.onNewSession(session, "file:///etc/passwd")
        verify(exactly = 0) { module.sendViewEvent(any(), any(), any()) }
    }

    @Test
    fun `onNewSession emits event for https popup`() {
        navigationDelegate.onNewSession(session, "https://example.com/popup")
        verify(exactly = 1) { module.sendViewEvent(testViewId, "onGeckoPopup", any()) }
    }

    @Test
    fun `onNewSession emits event for http popup`() {
        navigationDelegate.onNewSession(session, "http://example.com/popup")
        verify(exactly = 1) { module.sendViewEvent(testViewId, "onGeckoPopup", any()) }
    }

    // ========== getErrorCategory — WebRequestError category mapping ==========

    @Test
    fun `getErrorCategory maps every known category`() {
        assertEquals("unknown", navigationDelegate.getErrorCategory(WebRequestError.ERROR_CATEGORY_UNKNOWN))
        assertEquals("security", navigationDelegate.getErrorCategory(WebRequestError.ERROR_CATEGORY_SECURITY))
        assertEquals("network", navigationDelegate.getErrorCategory(WebRequestError.ERROR_CATEGORY_NETWORK))
        assertEquals("content", navigationDelegate.getErrorCategory(WebRequestError.ERROR_CATEGORY_CONTENT))
        assertEquals("uri", navigationDelegate.getErrorCategory(WebRequestError.ERROR_CATEGORY_URI))
        assertEquals("proxy", navigationDelegate.getErrorCategory(WebRequestError.ERROR_CATEGORY_PROXY))
        assertEquals("safebrowsing", navigationDelegate.getErrorCategory(WebRequestError.ERROR_CATEGORY_SAFEBROWSING))
    }

    @Test
    fun `getErrorCategory maps an unrecognized category to unknown`() {
        assertEquals("unknown", navigationDelegate.getErrorCategory(Int.MAX_VALUE))
    }

    // ========== getErrorMessage — WebRequestError code mapping ==========
    //
    // WebRequestError.code is a public-final field MockK cannot stub; set it via
    // reflection on a relaxed mock (the same pattern used for ContentPermission).

    private fun errorWithCode(code: Int): WebRequestError {
        val e: WebRequestError = mockk(relaxed = true)
        TestHelpers.setField(e, "code", code)
        return e
    }

    @Test
    fun `getErrorMessage maps known error codes`() {
        assertEquals("Unknown error", navigationDelegate.getErrorMessage(errorWithCode(WebRequestError.ERROR_UNKNOWN)))
        assertEquals("SSL certificate error", navigationDelegate.getErrorMessage(errorWithCode(WebRequestError.ERROR_SECURITY_SSL)))
        assertEquals("Invalid certificate", navigationDelegate.getErrorMessage(errorWithCode(WebRequestError.ERROR_SECURITY_BAD_CERT)))
        assertEquals("Connection reset", navigationDelegate.getErrorMessage(errorWithCode(WebRequestError.ERROR_NET_RESET)))
        assertEquals("Connection timeout", navigationDelegate.getErrorMessage(errorWithCode(WebRequestError.ERROR_NET_TIMEOUT)))
        assertEquals("Connection refused", navigationDelegate.getErrorMessage(errorWithCode(WebRequestError.ERROR_CONNECTION_REFUSED)))
        assertEquals("Unknown protocol", navigationDelegate.getErrorMessage(errorWithCode(WebRequestError.ERROR_UNKNOWN_PROTOCOL)))
        assertEquals("Unknown host", navigationDelegate.getErrorMessage(errorWithCode(WebRequestError.ERROR_UNKNOWN_HOST)))
        assertEquals("Malformed URI", navigationDelegate.getErrorMessage(errorWithCode(WebRequestError.ERROR_MALFORMED_URI)))
        assertEquals("File not found", navigationDelegate.getErrorMessage(errorWithCode(WebRequestError.ERROR_FILE_NOT_FOUND)))
        assertEquals("File access denied", navigationDelegate.getErrorMessage(errorWithCode(WebRequestError.ERROR_FILE_ACCESS_DENIED)))
        assertEquals("Unknown proxy host", navigationDelegate.getErrorMessage(errorWithCode(WebRequestError.ERROR_UNKNOWN_PROXY_HOST)))
    }

    @Test
    fun `getErrorMessage maps safebrowsing error codes`() {
        assertEquals("Malware detected", navigationDelegate.getErrorMessage(errorWithCode(WebRequestError.ERROR_SAFEBROWSING_MALWARE_URI)))
        assertEquals("Unwanted software detected", navigationDelegate.getErrorMessage(errorWithCode(WebRequestError.ERROR_SAFEBROWSING_UNWANTED_URI)))
        assertEquals("Harmful site detected", navigationDelegate.getErrorMessage(errorWithCode(WebRequestError.ERROR_SAFEBROWSING_HARMFUL_URI)))
        assertEquals("Phishing site detected", navigationDelegate.getErrorMessage(errorWithCode(WebRequestError.ERROR_SAFEBROWSING_PHISHING_URI)))
    }

    @Test
    fun `getErrorMessage falls back to error code for unrecognized codes`() {
        val message = navigationDelegate.getErrorMessage(errorWithCode(987654))
        assertEquals("Error code: 987654", message)
    }
}
