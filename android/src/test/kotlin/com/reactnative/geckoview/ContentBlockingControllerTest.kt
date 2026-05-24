package com.reactnative.geckoview

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for [ContentBlockingController] — Enhanced Tracking Protection
 * level + Cookie Banner mode mapping.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ContentBlockingControllerTest {

    private val runtime: GeckoRuntime = mockk(relaxed = true)
    private val settings: GeckoRuntimeSettings = mockk(relaxed = true)
    private val contentBlocking: ContentBlocking.Settings = mockk(relaxed = true)

    private lateinit var controller: ContentBlockingController

    @Before
    fun setUp() {
        every { runtime.settings } returns settings
        every { settings.contentBlocking } returns contentBlocking
        controller = ContentBlockingController(runtime)
    }

    @Test
    fun `setEnhancedTrackingProtectionLevel - NONE (0)`() {
        controller.setEnhancedTrackingProtectionLevel(0)
        verify { contentBlocking.setEnhancedTrackingProtectionLevel(ContentBlocking.EtpLevel.NONE) }
    }

    @Test
    fun `setEnhancedTrackingProtectionLevel - DEFAULT (1)`() {
        controller.setEnhancedTrackingProtectionLevel(1)
        verify { contentBlocking.setEnhancedTrackingProtectionLevel(ContentBlocking.EtpLevel.DEFAULT) }
    }

    @Test
    fun `setEnhancedTrackingProtectionLevel - STRICT (2)`() {
        controller.setEnhancedTrackingProtectionLevel(2)
        verify { contentBlocking.setEnhancedTrackingProtectionLevel(ContentBlocking.EtpLevel.STRICT) }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `setEnhancedTrackingProtectionLevel throws on out-of-range level`() {
        controller.setEnhancedTrackingProtectionLevel(999)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `setEnhancedTrackingProtectionLevel throws on negative level`() {
        controller.setEnhancedTrackingProtectionLevel(-1)
    }

    @Test
    fun `setEnhancedTrackingProtectionLevel does not call runtime on invalid input`() {
        try {
            controller.setEnhancedTrackingProtectionLevel(3)
        } catch (_: IllegalArgumentException) { /* expected */ }
        io.mockk.verify(exactly = 0) { contentBlocking.setEnhancedTrackingProtectionLevel(any()) }
    }

    @Test
    fun `setCookieBannerMode - DISABLED (0)`() {
        controller.setCookieBannerMode(0)
        verify {
            contentBlocking.setCookieBannerMode(
                ContentBlocking.CookieBannerMode.COOKIE_BANNER_MODE_DISABLED
            )
        }
    }

    @Test
    fun `setCookieBannerMode - REJECT (1)`() {
        controller.setCookieBannerMode(1)
        verify {
            contentBlocking.setCookieBannerMode(
                ContentBlocking.CookieBannerMode.COOKIE_BANNER_MODE_REJECT
            )
        }
    }

    @Test
    fun `setCookieBannerMode - REJECT_OR_ACCEPT (2)`() {
        controller.setCookieBannerMode(2)
        verify {
            contentBlocking.setCookieBannerMode(
                ContentBlocking.CookieBannerMode.COOKIE_BANNER_MODE_REJECT_OR_ACCEPT
            )
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `setCookieBannerMode throws on out-of-range mode`() {
        controller.setCookieBannerMode(999)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `setCookieBannerMode throws on negative mode`() {
        controller.setCookieBannerMode(-1)
    }

    // Regression: previously the inner try/catch swallowed exceptions from
    // `setCookieBannerMode` (e.g. when the runtime is older than GeckoView 108
    // and lacks the API), so the caller's React promise resolved as if the call
    // had succeeded. The controller now re-throws so the JS-side promise rejects.

    @Test(expected = RuntimeException::class)
    fun `setCookieBannerMode re-throws when underlying call fails`() {
        io.mockk.every {
            contentBlocking.setCookieBannerMode(any())
        } throws RuntimeException("setCookieBannerMode requires GeckoView 108+")
        controller.setCookieBannerMode(2)
    }

    @Test
    fun `setCookieBannerMode preserves the underlying exception message`() {
        io.mockk.every {
            contentBlocking.setCookieBannerMode(any())
        } throws RuntimeException("api unsupported on this GeckoView build")
        var thrown: Throwable? = null
        try {
            controller.setCookieBannerMode(1)
        } catch (e: Throwable) {
            thrown = e
        }
        assert(thrown is RuntimeException)
        assert(thrown!!.message == "api unsupported on this GeckoView build") {
            "Caller must see the underlying message; got '${thrown.message}'"
        }
    }

    @Test
    fun `setCookieBannerMode does not throw on success`() {
        controller.setCookieBannerMode(0) // contentBlocking is relaxed=true → no throw
        verify {
            contentBlocking.setCookieBannerMode(
                ContentBlocking.CookieBannerMode.COOKIE_BANNER_MODE_DISABLED
            )
        }
    }

    @Test
    fun `can set ETP and cookie banner mode together`() {
        controller.setEnhancedTrackingProtectionLevel(2)
        controller.setCookieBannerMode(1)

        verify {
            contentBlocking.setEnhancedTrackingProtectionLevel(ContentBlocking.EtpLevel.STRICT)
            contentBlocking.setCookieBannerMode(
                ContentBlocking.CookieBannerMode.COOKIE_BANNER_MODE_REJECT
            )
        }
    }
}
