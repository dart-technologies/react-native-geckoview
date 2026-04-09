package com.reactnative.geckoview
import org.junit.Before
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mozilla.geckoview.GeckoSession
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for GeckoViewProgressDelegate
 * Verifies page load progress events are correctly emitted to JavaScript
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class GeckoViewProgressDelegateTest {

    @Mock private lateinit var module: GeckoViewModule
    @Mock private lateinit var session: GeckoSession

    private lateinit var progressDelegate: GeckoViewProgressDelegate
    private val testViewId = 456

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
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
        val url = TestHelpers.Fixtures.TEST_URL
        progressDelegate.onPageStart(session, url)
        
        verify(module).sendViewEvent(eq(testViewId), eq("onGeckoPageStart"), any())
    }

    @Test
    fun `onPageStart handles different URL formats`() {
        val urls = listOf(
            TestHelpers.Fixtures.TEST_URL,
            TestHelpers.Fixtures.TEST_URL_MOZILLA,
            "https://www.example.com/path/to/page",
            "http://localhost:8080",
            "file:///android_asset/index.html"
        )

        urls.forEach { url ->
            progressDelegate.onPageStart(session, url)
        }

        verify(module, times(urls.size)).sendViewEvent(
            eq(testViewId),
            eq("onGeckoPageStart"),
            any()
        )
    }

    @Test
    fun `onPageStart handles empty URL`() {
        progressDelegate.onPageStart(session, "")
        verify(module).sendViewEvent(eq(testViewId), eq("onGeckoPageStart"), any())
    }

    // Page Stop Events

    @Test
    fun `onPageStop emits onGeckoPageStop event with success`() {
        progressDelegate.onPageStop(session, true)
        
        verify(module).sendViewEvent(eq(testViewId), eq("onGeckoPageStop"), any())
    }

    @Test
    fun `onPageStop emits onGeckoPageStop event with failure`() {
        progressDelegate.onPageStop(session, false)
        
        verify(module).sendViewEvent(eq(testViewId), eq("onGeckoPageStop"), any())
    }

    @Test
    fun `onPageStop handles different success states`() {
        val states = listOf(true, false, true, false, true)

        states.forEach { success ->
            progressDelegate.onPageStop(session, success)
        }

        verify(module, times(states.size)).sendViewEvent(
            eq(testViewId),
            eq("onGeckoPageStop"),
            any()
        )
    }

    // Progress Change Events

    @Test
    fun `onProgressChange emits onGeckoProgressChange event`() {
        progressDelegate.onProgressChange(session, 50)
        
        verify(module).sendViewEvent(eq(testViewId), eq("onGeckoProgressChange"), any())
    }

    @Test
    fun `onProgressChange handles various progress values`() {
        val progressValues = listOf(0, 10, 25, 50, 75, 90, 100)

        progressValues.forEach { progress ->
            progressDelegate.onProgressChange(session, progress)
        }

        verify(module, times(progressValues.size)).sendViewEvent(
            eq(testViewId),
            eq("onGeckoProgressChange"),
            any()
        )
    }

    @Test
    fun `onProgressChange handles edge case progress values`() {
        val edgeCases = listOf(-1, 0, 100, 101, 999)

        edgeCases.forEach { progress ->
            progressDelegate.onProgressChange(session, progress)
        }

        verify(module, times(edgeCases.size)).sendViewEvent(
            eq(testViewId),
            eq("onGeckoProgressChange"),
            any()
        )
    }

    // Security Change Events

    @org.junit.Ignore("SecurityInformation is a final class that cannot be mocked in Robolectric sandbox. " +
            "Tested via integration tests on device.")
    @Test
    fun `onSecurityChange emits onGeckoSecurityChange event`() {
        // Note: This test requires mocking GeckoSession.ProgressDelegate.SecurityInformation
        // which is a final class. Neither MockK nor Mockito can mock it in Robolectric's sandbox.
        // The security change event is verified through device integration tests instead.
        val securityInfo = org.mockito.Mockito.mock(GeckoSession.ProgressDelegate.SecurityInformation::class.java)
        org.mockito.Mockito.`when`(securityInfo.isSecure).thenReturn(true)
        org.mockito.Mockito.`when`(securityInfo.host).thenReturn("example.com")
        
        progressDelegate.onSecurityChange(session, securityInfo)
        
        verify(module).sendViewEvent(eq(testViewId), eq("onGeckoSecurityChange"), any())
    }

    // Integration Tests

    @Test
    fun `full page load cycle emits correct sequence of events`() {
        // Simulate complete page load
        progressDelegate.onPageStart(session, TestHelpers.Fixtures.TEST_URL)
        progressDelegate.onProgressChange(session, 25)
        progressDelegate.onProgressChange(session, 50)
        progressDelegate.onProgressChange(session, 75)
        progressDelegate.onProgressChange(session, 100)
        progressDelegate.onPageStop(session, true)

        // Verify all events were emitted with correct viewId
        verify(module, times(1)).sendViewEvent(eq(testViewId), eq("onGeckoPageStart"), any())
        verify(module, times(4)).sendViewEvent(eq(testViewId), eq("onGeckoProgressChange"), any())
        verify(module, times(1)).sendViewEvent(eq(testViewId), eq("onGeckoPageStop"), any())
    }

    @Test
    fun `failed page load emits correct sequence of events`() {
        // Simulate failed page load
        progressDelegate.onPageStart(session, TestHelpers.Fixtures.TEST_URL)
        progressDelegate.onProgressChange(session, 10)
        progressDelegate.onPageStop(session, false)

        verify(module, times(1)).sendViewEvent(eq(testViewId), eq("onGeckoPageStart"), any())
        verify(module, times(1)).sendViewEvent(eq(testViewId), eq("onGeckoProgressChange"), any())
        verify(module, times(1)).sendViewEvent(eq(testViewId), eq("onGeckoPageStop"), any())
    }

    @Test
    fun `multiple page loads emit events correctly`() {
        val urls = listOf(
            TestHelpers.Fixtures.TEST_URL,
            TestHelpers.Fixtures.TEST_URL_MOZILLA,
            "https://github.com"
        )

        urls.forEach { url ->
            progressDelegate.onPageStart(session, url)
            progressDelegate.onProgressChange(session, 100)
            progressDelegate.onPageStop(session, true)
        }

        verify(module, times(urls.size)).sendViewEvent(eq(testViewId), eq("onGeckoPageStart"), any())
        verify(module, times(urls.size)).sendViewEvent(eq(testViewId), eq("onGeckoProgressChange"), any())
        verify(module, times(urls.size)).sendViewEvent(eq(testViewId), eq("onGeckoPageStop"), any())
    }
}
