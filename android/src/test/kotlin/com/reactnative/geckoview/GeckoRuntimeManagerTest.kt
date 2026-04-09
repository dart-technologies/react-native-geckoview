package com.reactnative.geckoview

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class GeckoRuntimeManagerTest {

    private lateinit var context: Context
    private lateinit var mockRuntime: GeckoRuntime

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        mockRuntime = mockk(relaxed = true)
        
        every { context.applicationContext } returns context
        
        // Mock static creation of GeckoRuntime
        mockkStatic(GeckoRuntime::class)
        every { GeckoRuntime.create(any(), any()) } returns mockRuntime
        
        // Ensure we start fresh
        GeckoRuntimeManager.shutdown()
    }

    @After
    fun tearDown() {
        GeckoRuntimeManager.shutdown()
        unmockkAll()
    }

    @Test
    fun `getRuntime creates new runtime when null`() {
        val runtime = GeckoRuntimeManager.getRuntime(context)
        
        assertNotNull(runtime)
        assertSame(mockRuntime, runtime)
        verify { GeckoRuntime.create(context, any()) }
    }

    @Test
    fun `getRuntime returns existing runtime`() {
        val runtime1 = GeckoRuntimeManager.getRuntime(context)
        val runtime2 = GeckoRuntimeManager.getRuntime(context)
        
        assertSame(runtime1, runtime2)
        // Should only be created once
        verify(exactly = 1) { GeckoRuntime.create(any(), any()) }
    }

    @Test
    fun `shutdown clears runtime`() {
        GeckoRuntimeManager.getRuntime(context)
        assertNotNull(GeckoRuntimeManager.getRuntime())
        
        GeckoRuntimeManager.shutdown()
        
        assertNull(GeckoRuntimeManager.getRuntime())
        verify { mockRuntime.shutdown() }
    }
    
    @Test
    fun `getWebExtensionController returns controller after init`() {
        GeckoRuntimeManager.getRuntime(context)
        assertNotNull(GeckoRuntimeManager.getWebExtensionController())
    }
}
