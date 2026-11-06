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

    /** GeckoView 159 removed the cookie-banner API. Never pretend a requested policy applied. */
    fun setCookieBannerMode(mode: Int) {
        require(mode in 0..2) { "Invalid cookie banner mode: $mode (must be 0=DISABLED, 1=REJECT, or 2=REJECT_OR_ACCEPT)" }
        if (mode != 0) {
            throw UnsupportedOperationException("Cookie banner handling is unavailable in GeckoView 159; use the website's consent controls")
        }
    }

    companion object {
        private const val TAG = "ContentBlockingController"
    }
}
