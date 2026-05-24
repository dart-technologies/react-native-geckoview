package com.reactnative.geckoview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for the New-Architecture (Fabric) [GeckoViewManager].
 *
 * Pins the view name and the full set of exported direct-event constants —
 * notably `topFocusExit`/`onFocusExit`, which the spatial-navigation bridge
 * relies on. A missing or mis-registered entry silently drops the JS callback,
 * so we assert the exact registration-name mapping and the total count.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class GeckoViewManagerNewArchTest {

    private val expectedEvents = mapOf(
        "topGeckoPageStart" to "onGeckoPageStart",
        "topGeckoPageStop" to "onGeckoPageStop",
        "topGeckoProgressChange" to "onGeckoProgressChange",
        "topGeckoSecurityChange" to "onGeckoSecurityChange",
        "topGeckoLocationChange" to "onGeckoLocationChange",
        "topPageError" to "onPageError",
        "topMediaSessionAction" to "onMediaSessionAction",
        "topGeckoAlert" to "onGeckoAlert",
        "topGeckoConfirm" to "onGeckoConfirm",
        "topGeckoPrompt" to "onGeckoPrompt",
        "topGeckoPopup" to "onGeckoPopup",
        "topGeckoBeforeUnload" to "onGeckoBeforeUnload",
        "topGeckoAuth" to "onGeckoAuth",
        "topPermissionRequest" to "onPermissionRequest",
        "topWebExtensionMessage" to "onWebExtensionMessage",
        "topTitleChange" to "onTitleChange",
        "topContextMenu" to "onContextMenu",
        "topFocusExit" to "onFocusExit",
    )

    @Test
    fun `getName returns GeckoView`() {
        assertEquals("GeckoView", GeckoViewManager().getName())
    }

    @Test
    fun `exported direct events expose all 18 registrations`() {
        val events = GeckoViewManager().getExportedCustomDirectEventTypeConstants()
        assertEquals("expected exactly 18 direct events", 18, events.size)
        assertEquals(expectedEvents.keys, events.keys)
    }

    @Test
    fun `every exported event maps top-name to the correct registrationName`() {
        val events = GeckoViewManager().getExportedCustomDirectEventTypeConstants()
        for ((top, registration) in expectedEvents) {
            @Suppress("UNCHECKED_CAST")
            val entry = events[top] as? Map<String, Any>
            assertTrue("missing event mapping for $top", entry != null)
            assertEquals(
                "registrationName mismatch for $top",
                registration,
                entry!!["registrationName"],
            )
        }
    }

    @Test
    fun `focusExit event is registered for spatial-navigation`() {
        val events = GeckoViewManager().getExportedCustomDirectEventTypeConstants()
        @Suppress("UNCHECKED_CAST")
        val focusExit = events["topFocusExit"] as? Map<String, Any>
        assertEquals("onFocusExit", focusExit?.get("registrationName"))
    }
}
