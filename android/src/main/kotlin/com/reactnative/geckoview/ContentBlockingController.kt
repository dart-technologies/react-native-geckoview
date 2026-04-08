package com.reactnative.geckoview

import android.util.Log
import org.mozilla.geckoview.GeckoRuntime

/**
 * Controller for content blocking features including Enhanced Tracking Protection
 * and cookie banner handling.
 */
class ContentBlockingController(private val geckoRuntime: GeckoRuntime) {

    /**
     * Sets the Enhanced Tracking Protection (ETP) level.
     *
     * @param level 0 = NONE, 1 = DEFAULT, 2 = STRICT
     */
    fun setEnhancedTrackingProtectionLevel(level: Int) {
        val etpLevel = when (level) {
            0 -> org.mozilla.geckoview.ContentBlocking.EtpLevel.NONE
            1 -> org.mozilla.geckoview.ContentBlocking.EtpLevel.DEFAULT
            2 -> org.mozilla.geckoview.ContentBlocking.EtpLevel.STRICT
            else -> {
                Log.w(TAG, "Unknown ETP level: $level, defaulting to DEFAULT")
                org.mozilla.geckoview.ContentBlocking.EtpLevel.DEFAULT
            }
        }

        geckoRuntime.settings.contentBlocking.setEnhancedTrackingProtectionLevel(etpLevel)
        Log.d(TAG, "Enhanced Tracking Protection level set to: $etpLevel")
    }

    /**
     * Sets the cookie banner handling mode.
     * Requires GeckoView 108+
     *
     * @param mode 0 = DISABLED, 1 = REJECT, 2 = REJECT_OR_ACCEPT
     */
    fun setCookieBannerMode(mode: Int) {
        try {
            val cookieBannerMode = when (mode) {
                0 -> org.mozilla.geckoview.ContentBlocking.CookieBannerMode.COOKIE_BANNER_MODE_DISABLED
                1 -> org.mozilla.geckoview.ContentBlocking.CookieBannerMode.COOKIE_BANNER_MODE_REJECT
                2 -> org.mozilla.geckoview.ContentBlocking.CookieBannerMode.COOKIE_BANNER_MODE_REJECT_OR_ACCEPT
                else -> {
                    Log.w(TAG, "Unknown cookie banner mode: $mode, defaulting to DISABLED")
                    org.mozilla.geckoview.ContentBlocking.CookieBannerMode.COOKIE_BANNER_MODE_DISABLED
                }
            }

            geckoRuntime.settings.contentBlocking.setCookieBannerMode(cookieBannerMode)
            Log.d(TAG, "Cookie banner mode set to: $cookieBannerMode")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting cookie banner mode (requires GeckoView 108+)", e)
        }
    }

    /**
     * Sets global cookie banner handling rules.
     * Requires GeckoView 108+
     *
     * @param rulesJson JSON string containing cookie banner rules
     */
    /*
    fun setCookieBannerGlobalRules(rulesJson: String) {
        try {
            geckoRuntime.settings.contentBlocking.setCookieBannerGlobalRules(rulesJson)
            Log.d(TAG, "Cookie banner global rules set")
        } catch (e: Exception) {
            Log.e(TAG, "Error setting cookie banner rules (requires GeckoView 108+)", e)
        }
    }
    */

    companion object {
        private const val TAG = "ContentBlockingController"
    }
}
