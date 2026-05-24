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
import org.mozilla.geckoview.GeckoSessionSettings
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

    // ========== setDesktopMode / applyDesktopMode ==========

    @Test
    fun `setDesktopMode(true) applies desktop UA and viewport to the live session`() {
        val settings = mockk<GeckoSessionSettings>(relaxed = true)
        every { session.settings } returns settings
        every { geckoView.session } returns session

        manager.setDesktopMode(geckoView, true)

        verify { settings.userAgentMode = GeckoSessionSettings.USER_AGENT_MODE_DESKTOP }
        verify { settings.viewportMode = GeckoSessionSettings.VIEWPORT_MODE_DESKTOP }
    }

    @Test
    fun `setDesktopMode(false) applies mobile UA and viewport to the live session`() {
        val settings = mockk<GeckoSessionSettings>(relaxed = true)
        every { session.settings } returns settings
        every { geckoView.session } returns session

        manager.setDesktopMode(geckoView, false)

        verify { settings.userAgentMode = GeckoSessionSettings.USER_AGENT_MODE_MOBILE }
        verify { settings.viewportMode = GeckoSessionSettings.VIEWPORT_MODE_MOBILE }
    }

    @Test
    fun `setDesktopMode without a live session does not throw and applies on setSessionKey`() {
        val settings = mockk<GeckoSessionSettings>(relaxed = true)
        every { session.settings } returns settings
        // No session yet when the prop arrives.
        every { geckoView.session } returns null

        manager.setDesktopMode(geckoView, false)

        // When the session key is later applied, the stored mobile preference
        // must be pushed onto the freshly-bound session.
        manager.setSessionKey(geckoView, "desktop-mode-session")

        verify { settings.userAgentMode = GeckoSessionSettings.USER_AGENT_MODE_MOBILE }
        verify { settings.viewportMode = GeckoSessionSettings.VIEWPORT_MODE_MOBILE }
    }

    @Test
    fun `setSessionKey defaults to desktop mode when no preference was set`() {
        val settings = mockk<GeckoSessionSettings>(relaxed = true)
        every { session.settings } returns settings
        every { geckoView.session } returns null

        manager.setSessionKey(geckoView, "default-mode-session")

        // desktopModeMap[view] ?: true -> desktop defaults preserved.
        verify { settings.userAgentMode = GeckoSessionSettings.USER_AGENT_MODE_DESKTOP }
        verify { settings.viewportMode = GeckoSessionSettings.VIEWPORT_MODE_DESKTOP }
    }

    // ========== setSessionKey edge cases ==========

    @Test
    fun `setSessionKey ignores a null key`() {
        manager.setSessionKey(geckoView, null)
        verify(exactly = 0) { GeckoSessionManager.getOrCreateSession(any()) }
    }

    @Test
    fun `setSessionKey does not rebind when the view already holds the session`() {
        every { geckoView.session } returns session // already the same instance
        every { GeckoSessionManager.getOrCreateSession("same") } returns session

        manager.setSessionKey(geckoView, "same")

        verify(exactly = 0) { geckoView.setSession(any()) }
    }

    // ========== setSource scheme gate ==========

    @Test
    fun `setSource rejects a disallowed scheme without loading`() {
        val source = JavaOnlyMap().apply { putString("uri", "file:///etc/passwd") }
        manager.setSource(geckoView, source)
        verify(exactly = 0) { session.loadUri(any<String>()) }
    }

    @Test
    fun `setSource ignores a source without a uri`() {
        manager.setSource(geckoView, JavaOnlyMap())
        verify(exactly = 0) { session.loadUri(any<String>()) }
    }

    // ========== setContentBlockingEnabled / setCookieBannerMode ==========

    @Test
    fun `setContentBlockingEnabled(true) applies strict ETP`() {
        mockkObject(GeckoRuntimeManager)
        val runtime = mockk<org.mozilla.geckoview.GeckoRuntime>(relaxed = true)
        val rtSettings = mockk<org.mozilla.geckoview.GeckoRuntimeSettings>(relaxed = true)
        val cb = mockk<org.mozilla.geckoview.ContentBlocking.Settings>(relaxed = true)
        every { runtime.settings } returns rtSettings
        every { rtSettings.contentBlocking } returns cb
        every { GeckoRuntimeManager.getRuntime(any()) } returns runtime

        manager.setContentBlockingEnabled(geckoView, true)

        verify {
            cb.setEnhancedTrackingProtectionLevel(org.mozilla.geckoview.ContentBlocking.EtpLevel.STRICT)
        }
    }

    @Test
    fun `setContentBlockingEnabled(false) applies no ETP`() {
        mockkObject(GeckoRuntimeManager)
        val runtime = mockk<org.mozilla.geckoview.GeckoRuntime>(relaxed = true)
        val rtSettings = mockk<org.mozilla.geckoview.GeckoRuntimeSettings>(relaxed = true)
        val cb = mockk<org.mozilla.geckoview.ContentBlocking.Settings>(relaxed = true)
        every { runtime.settings } returns rtSettings
        every { rtSettings.contentBlocking } returns cb
        every { GeckoRuntimeManager.getRuntime(any()) } returns runtime

        manager.setContentBlockingEnabled(geckoView, false)

        verify {
            cb.setEnhancedTrackingProtectionLevel(org.mozilla.geckoview.ContentBlocking.EtpLevel.NONE)
        }
    }

    @Test
    fun `setCookieBannerMode maps reject string to mode 1`() {
        mockkObject(GeckoRuntimeManager)
        val runtime = mockk<org.mozilla.geckoview.GeckoRuntime>(relaxed = true)
        val rtSettings = mockk<org.mozilla.geckoview.GeckoRuntimeSettings>(relaxed = true)
        val cb = mockk<org.mozilla.geckoview.ContentBlocking.Settings>(relaxed = true)
        every { runtime.settings } returns rtSettings
        every { rtSettings.contentBlocking } returns cb
        every { GeckoRuntimeManager.getRuntime(any()) } returns runtime

        manager.setCookieBannerMode(geckoView, "reject")

        verify {
            cb.setCookieBannerMode(
                org.mozilla.geckoview.ContentBlocking.CookieBannerMode.COOKIE_BANNER_MODE_REJECT
            )
        }
    }

    @Test
    fun `setCookieBannerMode maps an unknown string to disabled`() {
        mockkObject(GeckoRuntimeManager)
        val runtime = mockk<org.mozilla.geckoview.GeckoRuntime>(relaxed = true)
        val rtSettings = mockk<org.mozilla.geckoview.GeckoRuntimeSettings>(relaxed = true)
        val cb = mockk<org.mozilla.geckoview.ContentBlocking.Settings>(relaxed = true)
        every { runtime.settings } returns rtSettings
        every { rtSettings.contentBlocking } returns cb
        every { GeckoRuntimeManager.getRuntime(any()) } returns runtime

        manager.setCookieBannerMode(geckoView, "garbage")

        verify {
            cb.setCookieBannerMode(
                org.mozilla.geckoview.ContentBlocking.CookieBannerMode.COOKIE_BANNER_MODE_DISABLED
            )
        }
    }

    // ========== onDropViewInstance ==========

    @Test
    fun `onDropViewInstance nulls every delegate and releases the session`() {
        every { geckoView.session } returns session

        manager.onDropViewInstance(geckoView)

        verify { session.navigationDelegate = null }
        verify { session.promptDelegate = null }
        verify { session.permissionDelegate = null }
        verify { session.progressDelegate = null }
        verify { session.contentDelegate = null }
        verify { session.mediaSessionDelegate = null }
        verify { geckoView.releaseSession() }
    }

    @Test
    fun `onDropViewInstance still releases when there is no session`() {
        every { geckoView.session } returns null

        manager.onDropViewInstance(geckoView)

        verify { geckoView.releaseSession() }
    }

    @Test
    fun `onDropViewInstance swallows a throwing releaseSession`() {
        every { geckoView.session } returns session
        every { geckoView.releaseSession() } throws RuntimeException("release boom")

        // The catch (L178-179) must absorb the failure rather than crashing
        // view teardown.
        manager.onDropViewInstance(geckoView)
    }

    // ========== catch-path coverage ==========

    @Test
    fun `setSessionKey swallows exceptions from session creation`() {
        every { geckoView.session } returns null
        every { GeckoSessionManager.getOrCreateSession("boom") } throws RuntimeException("create boom")

        // The catch (L56-57) must absorb the failure; no setSession occurs.
        manager.setSessionKey(geckoView, "boom")

        verify(exactly = 0) { geckoView.setSession(any()) }
    }

    @Test
    fun `setContentBlockingEnabled swallows runtime resolution failures`() {
        mockkObject(GeckoRuntimeManager)
        every { GeckoRuntimeManager.getRuntime(any()) } throws RuntimeException("no runtime")

        // The catch (L79-80) must absorb the failure rather than propagating.
        manager.setContentBlockingEnabled(geckoView, true)
    }

    @Test
    fun `setCookieBannerMode swallows runtime resolution failures`() {
        mockkObject(GeckoRuntimeManager)
        every { GeckoRuntimeManager.getRuntime(any()) } throws RuntimeException("no runtime")

        // The catch (L94-95) must absorb the failure rather than propagating.
        manager.setCookieBannerMode(geckoView, "reject")
    }

    @Test
    fun `setDesktopMode swallows failures from a throwing settings accessor`() {
        every { geckoView.session } returns session
        every { session.settings } throws RuntimeException("settings boom")

        // applyDesktopMode's catch (L116-117) absorbs the failure.
        manager.setDesktopMode(geckoView, true)
    }

    @Test
    fun `setSessionKey falls back to package module when context lookups throw`() {
        mockkObject(GeckoViewPackage.Companion)
        val fallbackModule: GeckoViewModule = mockk(relaxed = true)
        // getModule's primary lookup throws -> catch (L126-128) returns the
        // package singleton, and delegate attachment proceeds on that module.
        every { context.getNativeModule(GeckoViewModule::class.java) } throws RuntimeException("lookup boom")
        every { GeckoViewPackage.getModuleInstance() } returns fallbackModule
        every { geckoView.session } returns null
        val settings = mockk<GeckoSessionSettings>(relaxed = true)
        every { session.settings } returns settings

        manager.setSessionKey(geckoView, "fallback-module-session")

        // Delegates were attached using the fallback module (no crash, session bound).
        verify { session.navigationDelegate = any() }
    }

    // ========== pending-URI queueing across setSource -> setSessionKey ==========

    @Test
    fun `a uri set before the session binds is flushed once setSessionKey runs`() {
        // Model the real prop race: source arrives first (no session yet), then
        // the session key binds. The queued URI must load on bind.
        var boundSession: GeckoSession? = null
        every { geckoView.session } answers { boundSession }
        every { geckoView.setSession(any()) } answers { boundSession = firstArg() }
        every { GeckoSessionManager.getOrCreateSession("late-bind") } returns session

        manager.setSource(geckoView, JavaOnlyMap().apply { putString("uri", "https://queued.example") })
        // Nothing loaded yet — no session.
        verify(exactly = 0) { session.loadUri(any<String>()) }

        manager.setSessionKey(geckoView, "late-bind")

        verify { session.loadUri("https://queued.example") }
    }

    // ========== attachDelegates retry when the module is not yet available ==========

    @Test
    fun `setSessionKey schedules a delegate-attach retry when the module is unavailable`() {
        mockkObject(GeckoViewPackage.Companion)
        // Every module-resolution avenue fails -> getModule() returns null.
        val reactAppContext = mockk<com.facebook.react.bridge.ReactApplicationContext>(relaxed = true)
        every { reactAppContext.getNativeModule(GeckoViewModule::class.java) } returns null
        every { context.getNativeModule(GeckoViewModule::class.java) } returns null
        every { context.reactApplicationContext } returns reactAppContext
        every { GeckoViewPackage.getModuleInstance() } returns null
        every { geckoView.session } returns null
        every { geckoView.isAttachedToWindow } returns false
        val settings = mockk<GeckoSessionSettings>(relaxed = true)
        every { session.settings } returns settings

        manager.setSessionKey(geckoView, "no-module-yet")
        // Drain the postDelayed retry; isAttachedToWindow=false stops further retries.
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
            .idleFor(java.time.Duration.ofMillis(500))

        // No delegate could be attached because no module resolved.
        verify(exactly = 0) { session.navigationDelegate = any() }
    }
}
