package com.reactnative.geckoview

import io.mockk.mockk
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** GeckoView 159 removed the cookie-banner API; never report false success. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CookieBannerTest {
    @Test fun `consent actions are explicitly unsupported`() {
        val controller = ContentBlockingController(mockk(relaxed = true))
        for (mode in listOf(1, 2)) {
            assertThrows(UnsupportedOperationException::class.java) { controller.setCookieBannerMode(mode) }
        }
    }
    @Test fun `disabled mode remains a harmless no-op`() {
        ContentBlockingController(mockk(relaxed = true)).setCookieBannerMode(0)
    }
}
