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
    fun `disabled cookie banner policy remains a no-op`() {
        controller.setCookieBannerMode(0)
    }

    @Test
    fun `removed cookie banner policies reject rather than silently accepting`() {
        for (mode in listOf(1, 2)) {
            org.junit.Assert.assertThrows(UnsupportedOperationException::class.java) {
                controller.setCookieBannerMode(mode)
            }
        }
    }

    @Test
    fun `invalid cookie banner modes remain rejected`() {
        for (mode in listOf(-1, 3, 999)) {
            org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
                controller.setCookieBannerMode(mode)
            }
        }
    }
}
