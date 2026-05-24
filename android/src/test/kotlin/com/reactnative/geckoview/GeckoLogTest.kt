package com.reactnative.geckoview

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression guard: pin the existence and surface of `GeckoLog`.
 *
 * The Kotlin compiler already catches removal — every production file calls
 * `GeckoLog.{d,w,e}` and would fail to compile if the object went away. These
 * cases additionally exercise each entry point so a renamed signature (e.g.
 * dropping the optional `Throwable` overload) doesn't pass behind unfortunate
 * unused-import suppressions.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class GeckoLogTest {

    @Test
    fun `d() runs without throwing`() {
        GeckoLog.d("tag", "message")
    }

    @Test
    fun `w() with no throwable runs without throwing`() {
        GeckoLog.w("tag", "warning")
    }

    @Test
    fun `w() with throwable runs without throwing`() {
        GeckoLog.w("tag", "warning", RuntimeException("boom"))
    }

    @Test
    fun `e() with no throwable runs without throwing`() {
        GeckoLog.e("tag", "error")
    }

    @Test
    fun `e() with throwable runs without throwing`() {
        GeckoLog.e("tag", "error", RuntimeException("boom"))
    }
}
