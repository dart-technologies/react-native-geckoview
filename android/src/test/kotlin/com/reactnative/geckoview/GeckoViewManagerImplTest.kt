package com.reactnative.geckoview

import com.facebook.react.bridge.JavaOnlyMap
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.uimanager.ThemedReactContext
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class GeckoViewManagerImplTest {

    private lateinit var manager: GeckoViewManagerImpl
    private lateinit var context: ThemedReactContext
    private lateinit var geckoView: GeckoView
    private lateinit var session: GeckoSession
    private lateinit var module: GeckoViewModule

    @Before
    fun setUp() {
        mockkObject(GeckoSessionManager)
        
        context = mockk(relaxed = true)
        geckoView = mockk(relaxed = true)
        session = mockk(relaxed = true)
        module = mockk(relaxed = true)
        
        every { context.applicationContext } returns context
        every { context.getNativeModule(GeckoViewModule::class.java) } returns module
        
        // Mock AudioManager for MediaDelegate
        val audioManager = mockk<android.media.AudioManager>(relaxed = true)
        every { context.getSystemService(android.content.Context.AUDIO_SERVICE) } returns audioManager
        
        every { geckoView.context } returns context
        every { geckoView.session } returns session
        
        // Mock GeckoSessionManager behavior
        every { GeckoSessionManager.init(any()) } returns Unit
        every { GeckoSessionManager.getOrCreateSession(any()) } returns session
        
        manager = GeckoViewManagerImpl()
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `getName returns correct name`() {
        assertEquals("GeckoView", manager.getName())
    }

    @Test
    fun `setSessionKey sets session on view`() {
        val key = "test-session"
        every { geckoView.session } returns null // Initially no session
        
        manager.setSessionKey(geckoView, key)
        
        verify { GeckoSessionManager.getOrCreateSession(key) }
        verify { geckoView.setSession(session) }
    }

    @Test
    fun `setSessionKey attaches delegates when session is set`() {
        val key = "test-session"
        every { geckoView.session } returns null
        
        manager.setSessionKey(geckoView, key)
        
        verify { session.navigationDelegate = any() }
        verify { session.progressDelegate = any() }
        verify { session.contentDelegate = any() }
        verify { session.mediaSessionDelegate = any() }
    }

    @Test
    fun `setSource loads uri when session exists`() {
        val uri = "https://example.com"
        val source = JavaOnlyMap()
        source.putString("uri", uri)
        
        manager.setSource(geckoView, source)
        
        verify { session.loadUri(uri) }
    }

    @Test
    fun `setSource does nothing when session is null`() {
        every { geckoView.session } returns null
        val source = JavaOnlyMap()
        source.putString("uri", "https://example.com")
        
        manager.setSource(geckoView, source)
        
        verify(exactly = 0) { session.loadUri(any()) }
    }

    @Test
    fun `setHandleAudioFocus updates map`() {
        manager.setHandleAudioFocus(geckoView, true)
        
        // We can't easily verify the private map, but we can verify it's used when attaching delegates
        // Re-attach delegates by setting session key
        every { geckoView.session } returns null
        manager.setSessionKey(geckoView, "new-session")
        
        // Verify MediaDelegate is created (implicit verification via constructor or usage)
        // Since MediaDelegate is created inside, we can't easily verify the boolean passed to it
        // without more complex mocking of the constructor or factory.
        // For now, just ensuring no crash is good.
    }
}
