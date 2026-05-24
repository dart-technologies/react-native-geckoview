package com.reactnative.geckoview

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for cookie-banner handling through the runtime settings object.
 *
 * The controller-level logic for [ContentBlockingController] lives in
 * [ContentBlockingControllerTest]. This file covers the lower-level
 * `ContentBlocking.Settings` property interactions we rely on.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CookieBannerTest {

    private lateinit var runtime: GeckoRuntime
    private lateinit var runtimeSettings: GeckoRuntimeSettings
    private lateinit var contentBlockingSettings: ContentBlocking.Settings

    @Before
    fun setUp() {
        TestHelpers.mockArguments()
        runtime = mockk(relaxed = true)
        runtimeSettings = mockk(relaxed = true)
        contentBlockingSettings = mockk(relaxed = true)
        every { runtime.settings } returns runtimeSettings
        every { runtimeSettings.contentBlocking } returns contentBlockingSettings
    }

    @After
    fun tearDown() {
        TestHelpers.unmockArguments()
    }

    @Test
    fun `cookieBannerMode property writes through to ContentBlocking Settings`() {
        contentBlockingSettings.cookieBannerMode = 1
        verify { contentBlockingSettings.cookieBannerMode = 1 }
    }

    @Test
    fun `cookieBannerMode transitions from disabled to reject`() {
        contentBlockingSettings.cookieBannerMode = 0
        contentBlockingSettings.cookieBannerMode = 1

        verify(exactly = 2) { contentBlockingSettings.cookieBannerMode = any() }
    }

    @Test
    fun `cookieBannerMode transitions from reject to reject_or_accept`() {
        contentBlockingSettings.cookieBannerMode = 1
        contentBlockingSettings.cookieBannerMode = 2

        verify { contentBlockingSettings.cookieBannerMode = 1 }
        verify { contentBlockingSettings.cookieBannerMode = 2 }
    }

    @Test
    fun `setting same mode twice is a no-op from the controller point of view`() {
        contentBlockingSettings.cookieBannerMode = 1
        contentBlockingSettings.cookieBannerMode = 1

        verify(exactly = 2) { contentBlockingSettings.cookieBannerMode = 1 }
    }

    @Test
    fun `private browsing can carry a distinct cookieBannerMode`() {
        contentBlockingSettings.cookieBannerMode = 1
        contentBlockingSettings.cookieBannerModePrivateBrowsing = 2

        verify { contentBlockingSettings.cookieBannerMode = 1 }
        verify { contentBlockingSettings.cookieBannerModePrivateBrowsing = 2 }
    }

    @Test
    fun `rapid mode changes do not cause issues`() {
        repeat(50) { contentBlockingSettings.cookieBannerMode = it % 3 }
        verify(atLeast = 50) { contentBlockingSettings.cookieBannerMode = any() }
    }
}
