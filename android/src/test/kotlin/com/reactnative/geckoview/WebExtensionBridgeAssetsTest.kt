package com.reactnative.geckoview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression guard: pin the trusted internal-bridge WebExtension assets in
 * place. A previous review flagged `evaluateJavaScript` as broken because
 * `manifest.json` and `bridge.js` were "missing"; this test ensures any
 * accidental rename, deletion, or move of those files fails fast in CI.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class WebExtensionBridgeAssetsTest {

    @Test
    fun `bridge folder exists under main assets`() {
        val dir = java.io.File("src/main/assets/${WebExtensionController.INTERNAL_BRIDGE_ASSET_PATH}")
        assertTrue("Bridge directory missing at ${dir.absolutePath}", dir.isDirectory)
    }

    @Test
    fun `bridge manifest is present`() {
        val f = java.io.File(
            "src/main/assets/${WebExtensionController.INTERNAL_BRIDGE_ASSET_PATH}/manifest.json"
        )
        assertTrue("manifest.json missing at ${f.absolutePath}", f.isFile)
        assertTrue("manifest.json must be non-empty", f.length() > 0)
    }

    @Test
    fun `bridge js is present`() {
        val f = java.io.File(
            "src/main/assets/${WebExtensionController.INTERNAL_BRIDGE_ASSET_PATH}/bridge.js"
        )
        assertTrue("bridge.js missing at ${f.absolutePath}", f.isFile)
        assertTrue("bridge.js must be non-empty", f.length() > 0)
    }

    @Test
    fun `INTERNAL_BRIDGE_ASSET_PATH matches the shipped folder name`() {
        // Pin the exact constant so renames on either side break the build.
        assertEquals("react_native_geckoview_bridge", WebExtensionController.INTERNAL_BRIDGE_ASSET_PATH)
    }
    @Test
    fun `trusted content bridge declares GeckoView native messaging permissions`() {
        val manifest = org.json.JSONObject(java.io.File("src/main/assets/${WebExtensionController.INTERNAL_BRIDGE_ASSET_PATH}/manifest.json").readText())
        val permissions = manifest.getJSONArray("permissions")
        val declared = (0 until permissions.length()).map { permissions.getString(it) }.toSet()
        assertTrue(declared.containsAll(setOf("nativeMessaging", "nativeMessagingFromContent", "geckoViewAddons")))
        assertEquals(WebExtensionController.INTERNAL_BRIDGE_EXTENSION_ID, manifest.getJSONObject("browser_specific_settings").getJSONObject("gecko").getString("id"))
        assertEquals(false, manifest.getJSONArray("content_scripts").getJSONObject(0).getBoolean("all_frames"))
    }

}
