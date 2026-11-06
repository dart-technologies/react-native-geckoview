package com.reactnative.geckoview

import android.view.View
import com.facebook.react.uimanager.SimpleViewManager
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.viewmanagers.GeckoViewManagerInterface
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
 * so we assert the exact registration-name mapping and the total count. The
 * exported native props are pinned the same way.
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
        "topContentProcessGone" to "onContentProcessGone",
    )

    @Test
    fun `getName returns GeckoView`() {
        assertEquals("GeckoView", GeckoViewManager().getName())
    }

    @Test
    fun `exported direct events expose all 19 registrations`() {
        val events = GeckoViewManager().getExportedCustomDirectEventTypeConstants()
        assertEquals("expected exactly 19 direct events", 19, events.size)
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

    /**
     * The spec's props, with the type React Native derives from each
     * `@ReactProp` setter. On bridgeless, native view configs list only
     * annotated props, and React Native sends a view only the props its view
     * config lists: a setter that loses its annotation can stop receiving its
     * prop.
     */
    private val expectedNativeProps = mapOf(
        "sessionKey" to "String",
        "closeSessionOnDrop" to "boolean",
        "source" to "Map",
        "handleAudioFocus" to "boolean",
        "contentBlockingEnabled" to "boolean",
        "cookieBannerMode" to "String",
        "desktopMode" to "boolean",
    )

    /** Exports only the props every SimpleViewManager does. */
    private class BaseOnlyViewManager : SimpleViewManager<View>() {
        override fun getName() = "BaseOnly"

        override fun createViewInstance(context: ThemedReactContext) = View(context)
    }

    @Test
    fun `the native view config exports every spec prop with its type`() {
        val base = BaseOnlyViewManager().nativeProps.keys
        assertEquals(expectedNativeProps, GeckoViewManager().nativeProps.filterKeys { it !in base })
    }

    @Test
    fun `the expected native props are the codegen spec's`() {
        // The interface codegen generates from src/GeckoViewNativeComponent.ts.
        val specProps = GeckoViewManagerInterface::class.java.declaredMethods
            .map { it.name.removePrefix("set").replaceFirstChar(Char::lowercaseChar) }
            .toSet()
        assertEquals(specProps, expectedNativeProps.keys)
    }
}
