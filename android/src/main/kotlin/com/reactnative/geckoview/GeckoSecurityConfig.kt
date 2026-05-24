package com.reactnative.geckoview

/**
 * Runtime security knobs that must be configured before GeckoRuntime starts.
 *
 * Defaults are intentionally conservative. Host apps can opt in before the
 * first GeckoView mount via `configureGeckoViewSecurity(...)`.
 */
object GeckoSecurityConfig {
    data class Options(
        val allowDrm: Boolean = false,
        val allowAutoplay: Boolean = false,
        val enableConsoleOutput: Boolean = false,
        /**
         * GeckoView `ContentBlocking.CookieBehavior` constant applied at runtime
         * init. Defaults to `ACCEPT_NON_TRACKERS`; host apps can override before
         * the first GeckoView mount.
         */
        val cookieBehavior: Int =
            org.mozilla.geckoview.ContentBlocking.CookieBehavior.ACCEPT_NON_TRACKERS,
        /**
         * Max captured snapshot size in pixels before `captureSnapshot` rejects
         * with `SNAPSHOT_TOO_LARGE`. Defaults to 16,777,216 (4096×4096).
         */
        val maxSnapshotPixels: Long = 4096L * 4096L,
    )

    @Volatile
    private var options: Options = Options()

    fun current(): Options = options

    @Synchronized
    fun set(next: Options) {
        options = next
        GeckoViewPermissionDelegate.autoGrantDrm = next.allowDrm
        GeckoViewPermissionDelegate.autoGrantAutoplay = next.allowAutoplay
    }

    @Synchronized
    internal fun resetForTests() {
        set(Options())
    }
}
