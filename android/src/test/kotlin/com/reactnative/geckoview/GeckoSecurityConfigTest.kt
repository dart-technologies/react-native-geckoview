package com.reactnative.geckoview

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression guard: pin the contract that `GeckoSecurityConfig.set()` writes
 * `allowDrm` / `allowAutoplay` onto the static `GeckoViewPermissionDelegate`
 * flags. A previous review flagged the propagation as missing; this test
 * guarantees the propagation can't silently regress.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class GeckoSecurityConfigTest {

    @Before
    fun setUp() {
        GeckoSecurityConfig.resetForTests()
    }

    @After
    fun tearDown() {
        GeckoSecurityConfig.resetForTests()
    }

    @Test
    fun `defaults are conservative (no auto-grant)`() {
        val opts = GeckoSecurityConfig.current()
        assertFalse(opts.allowDrm)
        assertFalse(opts.allowAutoplay)
        assertFalse(opts.enableConsoleOutput)
        assertFalse(GeckoViewPermissionDelegate.autoGrantDrm)
        assertFalse(GeckoViewPermissionDelegate.autoGrantAutoplay)
    }

    @Test
    fun `set() with allowDrm=true propagates to permission delegate`() {
        GeckoSecurityConfig.set(GeckoSecurityConfig.Options(allowDrm = true))
        assertTrue(GeckoViewPermissionDelegate.autoGrantDrm)
        assertFalse(GeckoViewPermissionDelegate.autoGrantAutoplay)
    }

    @Test
    fun `set() with allowAutoplay=true propagates to permission delegate`() {
        GeckoSecurityConfig.set(GeckoSecurityConfig.Options(allowAutoplay = true))
        assertFalse(GeckoViewPermissionDelegate.autoGrantDrm)
        assertTrue(GeckoViewPermissionDelegate.autoGrantAutoplay)
    }

    @Test
    fun `set() with both flags true propagates both to permission delegate`() {
        GeckoSecurityConfig.set(
            GeckoSecurityConfig.Options(allowDrm = true, allowAutoplay = true)
        )
        assertTrue(GeckoViewPermissionDelegate.autoGrantDrm)
        assertTrue(GeckoViewPermissionDelegate.autoGrantAutoplay)
    }

    @Test
    fun `set() resets delegate flags when options are reverted`() {
        GeckoSecurityConfig.set(
            GeckoSecurityConfig.Options(allowDrm = true, allowAutoplay = true)
        )
        assertTrue(GeckoViewPermissionDelegate.autoGrantDrm)
        assertTrue(GeckoViewPermissionDelegate.autoGrantAutoplay)

        GeckoSecurityConfig.set(
            GeckoSecurityConfig.Options(allowDrm = false, allowAutoplay = false)
        )
        assertFalse(GeckoViewPermissionDelegate.autoGrantDrm)
        assertFalse(GeckoViewPermissionDelegate.autoGrantAutoplay)
    }

    @Test
    fun `current() reflects last set() options`() {
        val opts = GeckoSecurityConfig.Options(
            allowDrm = true,
            allowAutoplay = false,
            enableConsoleOutput = true,
        )
        GeckoSecurityConfig.set(opts)
        assertEquals(opts, GeckoSecurityConfig.current())
    }
}
