package com.reactnative.geckoview

import io.mockk.mockk
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.geckoview.GeckoSession
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for [GeckoViewProgressDelegate] — verifies page-load progress events
 * dispatch to JS with the correct name and viewId.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class GeckoViewProgressDelegateTest {

    private val module: GeckoViewModule = mockk(relaxed = true)
    private val session: GeckoSession = mockk(relaxed = true)
    private lateinit var progressDelegate: GeckoViewProgressDelegate
    private val testViewId = 456

    @Before
    fun setUp() {
        TestHelpers.mockArguments()
        progressDelegate = GeckoViewProgressDelegate(module, testViewId)
    }

    @After
    fun tearDown() {
        TestHelpers.unmockArguments()
    }

    // Page Start Events

    @Test
    fun `onPageStart emits onGeckoPageStart event with viewId`() {
        progressDelegate.onPageStart(session, TestHelpers.Fixtures.TEST_URL)

        verify { module.sendViewEvent(testViewId, "onGeckoPageStart", any()) }
    }

    @Test
    fun `onPageStart handles different URL formats`() {
        val urls = listOf(
            TestHelpers.Fixtures.TEST_URL,
            TestHelpers.Fixtures.TEST_URL_MOZILLA,
            "https://www.example.com/path/to/page",
            "http://localhost:8080",
            "file:///android_asset/index.html",
        )

        urls.forEach { progressDelegate.onPageStart(session, it) }

        verify(exactly = urls.size) {
            module.sendViewEvent(testViewId, "onGeckoPageStart", any())
        }
    }

    @Test
    fun `onPageStart handles empty URL`() {
        progressDelegate.onPageStart(session, "")
        verify { module.sendViewEvent(testViewId, "onGeckoPageStart", any()) }
    }

    // Page Stop Events

    @Test
    fun `onPageStop emits onGeckoPageStop event with success`() {
        progressDelegate.onPageStop(session, true)
        verify { module.sendViewEvent(testViewId, "onGeckoPageStop", any()) }
    }

    @Test
    fun `onPageStop emits onGeckoPageStop event with failure`() {
        progressDelegate.onPageStop(session, false)
        verify { module.sendViewEvent(testViewId, "onGeckoPageStop", any()) }
    }

    @Test
    fun `onPageStop handles different success states`() {
        val states = listOf(true, false, true, false, true)
        states.forEach { progressDelegate.onPageStop(session, it) }

        verify(exactly = states.size) {
            module.sendViewEvent(testViewId, "onGeckoPageStop", any())
        }
    }

    // Progress Change Events

    @Test
    fun `onProgressChange emits onGeckoProgressChange event`() {
        progressDelegate.onProgressChange(session, 50)
        verify { module.sendViewEvent(testViewId, "onGeckoProgressChange", any()) }
    }

    @Test
    fun `onProgressChange handles various progress values`() {
        val progressValues = listOf(0, 10, 25, 50, 75, 90, 100)
        progressValues.forEach { progressDelegate.onProgressChange(session, it) }

        verify(exactly = progressValues.size) {
            module.sendViewEvent(testViewId, "onGeckoProgressChange", any())
        }
    }

    @Test
    fun `onProgressChange handles edge case progress values`() {
        val edgeCases = listOf(-1, 0, 100, 101, 999)
        edgeCases.forEach { progressDelegate.onProgressChange(session, it) }

        verify(exactly = edgeCases.size) {
            module.sendViewEvent(testViewId, "onGeckoProgressChange", any())
        }
    }

    // Security Change Events

    @Ignore(
        "SecurityInformation is a final class that cannot be mocked in Robolectric's sandbox. " +
            "Covered by device integration tests instead."
    )
    @Test
    fun `onSecurityChange emits onGeckoSecurityChange event`() {
        // Left as documentation only.
    }

    // Integration Tests

    @Test
    fun `full page load cycle emits correct sequence of events`() {
        progressDelegate.onPageStart(session, TestHelpers.Fixtures.TEST_URL)
        progressDelegate.onProgressChange(session, 25)
        progressDelegate.onProgressChange(session, 50)
        progressDelegate.onProgressChange(session, 75)
        progressDelegate.onProgressChange(session, 100)
        progressDelegate.onPageStop(session, true)

        verify(exactly = 1) { module.sendViewEvent(testViewId, "onGeckoPageStart", any()) }
        verify(exactly = 4) { module.sendViewEvent(testViewId, "onGeckoProgressChange", any()) }
        verify(exactly = 1) { module.sendViewEvent(testViewId, "onGeckoPageStop", any()) }
    }

    @Test
    fun `failed page load emits correct sequence of events`() {
        progressDelegate.onPageStart(session, TestHelpers.Fixtures.TEST_URL)
        progressDelegate.onProgressChange(session, 10)
        progressDelegate.onPageStop(session, false)

        verify(exactly = 1) { module.sendViewEvent(testViewId, "onGeckoPageStart", any()) }
        verify(exactly = 1) { module.sendViewEvent(testViewId, "onGeckoProgressChange", any()) }
        verify(exactly = 1) { module.sendViewEvent(testViewId, "onGeckoPageStop", any()) }
    }

    @Test
    fun `multiple page loads emit events correctly`() {
        val urls = listOf(
            TestHelpers.Fixtures.TEST_URL,
            TestHelpers.Fixtures.TEST_URL_MOZILLA,
            "https://github.com",
        )

        urls.forEach {
            progressDelegate.onPageStart(session, it)
            progressDelegate.onProgressChange(session, 100)
            progressDelegate.onPageStop(session, true)
        }

        verify(exactly = urls.size) { module.sendViewEvent(testViewId, "onGeckoPageStart", any()) }
        verify(exactly = urls.size) { module.sendViewEvent(testViewId, "onGeckoProgressChange", any()) }
        verify(exactly = urls.size) { module.sendViewEvent(testViewId, "onGeckoPageStop", any()) }
    }
}
