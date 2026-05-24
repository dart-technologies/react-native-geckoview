package com.reactnative.geckoview

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
     * @throws IllegalArgumentException if [level] is not one of the three documented values.
     *   Silent fallback to DEFAULT on invalid input was intentionally removed — a caller
     *   expecting "strictest" (e.g. `setEnhancedTrackingProtectionLevel(99)`) would have
     *   silently received DEFAULT, which is a weaker-than-requested posture.
     */
    fun setEnhancedTrackingProtectionLevel(level: Int) {
        val etpLevel = when (level) {
            0 -> org.mozilla.geckoview.ContentBlocking.EtpLevel.NONE
            1 -> org.mozilla.geckoview.ContentBlocking.EtpLevel.DEFAULT
            2 -> org.mozilla.geckoview.ContentBlocking.EtpLevel.STRICT
            else -> throw IllegalArgumentException(
                "Invalid ETP level: $level (must be 0=NONE, 1=DEFAULT, or 2=STRICT)"
            )
        }

        geckoRuntime.settings.contentBlocking.setEnhancedTrackingProtectionLevel(etpLevel)
        GeckoLog.d(TAG, "Enhanced Tracking Protection level set")
    }

    /**
     * Sets the cookie banner handling mode. Requires GeckoView 108+.
     *
     * @param mode 0 = DISABLED, 1 = REJECT, 2 = REJECT_OR_ACCEPT
     * @throws IllegalArgumentException if [mode] is not one of the three documented values.
     * @throws Exception any failure from the underlying GeckoView call (e.g.
     *   when the runtime is older than 108 and lacks the API). Callers wrap
     *   this into the React promise rejection — the inner try/catch only
     *   exists to log before re-throwing, not to swallow.
     */
    fun setCookieBannerMode(mode: Int) {
        val cookieBannerMode = when (mode) {
            0 -> org.mozilla.geckoview.ContentBlocking.CookieBannerMode.COOKIE_BANNER_MODE_DISABLED
            1 -> org.mozilla.geckoview.ContentBlocking.CookieBannerMode.COOKIE_BANNER_MODE_REJECT
            2 -> org.mozilla.geckoview.ContentBlocking.CookieBannerMode.COOKIE_BANNER_MODE_REJECT_OR_ACCEPT
            else -> throw IllegalArgumentException(
                "Invalid cookie banner mode: $mode (must be 0=DISABLED, 1=REJECT, or 2=REJECT_OR_ACCEPT)"
            )
        }

        try {
            geckoRuntime.settings.contentBlocking.setCookieBannerMode(cookieBannerMode)
            GeckoLog.d(TAG, "Cookie banner mode set")
        } catch (e: Exception) {
            GeckoLog.e(TAG, "Error setting cookie banner mode (requires GeckoView 108+)", e)
            throw e
        }
    }

    companion object {
        private const val TAG = "ContentBlockingController"
    }
}
