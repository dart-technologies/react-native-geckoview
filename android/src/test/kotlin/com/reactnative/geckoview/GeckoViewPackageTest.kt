package com.reactnative.geckoview

import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.soloader.SoLoader
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for [GeckoViewPackage.getModule] — the TurboReactPackage module
 * factory. Verifies lazy single-instance creation for the known module name
 * and a null result for any other name.
 *
 * The package's companion `init {}` calls `SoLoader.loadLibrary`, which is
 * stubbed here so class init doesn't require a real native library. The
 * private `moduleInstance` singleton is reset via reflection between tests so
 * the lazy-init assertion is deterministic regardless of test order.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class GeckoViewPackageTest {

    @Before
    fun setUp() {
        // Must precede the first reference to GeckoViewPackage so its companion
        // `init {}` invokes the stubbed loader rather than a real .so lookup.
        mockkStatic(SoLoader::class)
        every { SoLoader.loadLibrary(any()) } returns true
        resetModuleSingleton()
    }

    @After
    fun tearDown() {
        resetModuleSingleton()
        unmockkAll()
    }

    private fun resetModuleSingleton() {
        val field = GeckoViewPackage::class.java.getDeclaredField("moduleInstance")
        field.isAccessible = true
        field.set(null, null)
    }

    private fun newContext(): ReactApplicationContext {
        val ctx: ReactApplicationContext = mockk(relaxed = true)
        every { ctx.applicationContext } returns ctx
        return ctx
    }

    @Test
    fun `getModule lazily creates the GeckoViewModule`() {
        val pkg = GeckoViewPackage()
        assertNull("singleton starts null", GeckoViewPackage.getModuleInstance())

        val module = pkg.getModule("GeckoViewModule", newContext())

        assertNotNull(module)
        assertSame(module, GeckoViewPackage.getModuleInstance())
    }

    @Test
    fun `getModule returns the same singleton on repeated lookups`() {
        val pkg = GeckoViewPackage()
        val first = pkg.getModule("GeckoViewModule", newContext())
        val second = pkg.getModule("GeckoViewModule", newContext())

        assertSame("module must be a process-wide singleton", first, second)
    }

    @Test
    fun `getModule returns null for an unknown module name`() {
        val pkg = GeckoViewPackage()
        assertNull(pkg.getModule("SomeOtherModule", newContext()))
        assertNull(pkg.getModule("", newContext()))
        // The unknown lookup must not have created the singleton.
        assertNull(GeckoViewPackage.getModuleInstance())
    }
}
