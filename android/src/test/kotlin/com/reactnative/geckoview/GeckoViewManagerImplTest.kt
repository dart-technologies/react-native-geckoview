package com.reactnative.geckoview

import android.media.AudioFocusRequest
import android.media.AudioManager
import com.facebook.react.bridge.JavaOnlyMap
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.WritableMap
import com.facebook.react.uimanager.ReactStylesDiffMap
import com.facebook.react.uimanager.ThemedReactContext
import io.mockk.MockKMatcherScope
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.MediaSession
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
    private lateinit var audioManager: android.media.AudioManager

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
        audioManager = mockk(relaxed = true)
        every { context.getSystemService(android.content.Context.AUDIO_SERVICE) } returns audioManager
        
        every { geckoView.context } returns context
        every { geckoView.session } returns session
        // The pool hands views open sessions; a relaxed mock would report closed.
        every { session.isOpen } returns true

        // Mock GeckoSessionManager behavior
        every { GeckoSessionManager.init(any()) } returns Unit
        every { GeckoSessionManager.getOrCreateSession(any()) } returns session
        // The UX gate is process-wide and init is stubbed out: open it explicitly
        // so setSessionKey doesn't depend on which test class ran first.
        GeckoSessionManager.setPlaybackAllowed(true)
        
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
    fun `unsupported cookie consent prop does not crash the view manager`() {
        mockkObject(GeckoRuntimeManager)
        every { GeckoRuntimeManager.getRuntime(any()) } returns mockk(relaxed = true)
        // The imperative API rejects unsupported modes; a view prop logs the
        // error without crashing rendering or pretending to click consent.
        manager.setCookieBannerMode(geckoView, "reject")
        manager.setCookieBannerMode(geckoView, "accept")
        manager.setCookieBannerMode(geckoView, "none")
    }

    // ========== onDropViewInstance ==========

    @Test
    fun `onDropViewInstance nulls the page delegates and releases the session`() {
        every { geckoView.session } returns session

        manager.onDropViewInstance(geckoView)

        verify { session.navigationDelegate = null }
        verify { session.promptDelegate = null }
        verify { session.permissionDelegate = null }
        verify { session.progressDelegate = null }
        verify { session.contentDelegate = null }
        // A media delegate this library didn't attach is dropped too.
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

        // The catch must absorb the failure rather than crashing view teardown.
        manager.onDropViewInstance(geckoView)
    }

    @Test
    fun `onDropViewInstance swallows an Error from releaseSession too`() {
        every { geckoView.session } returns session
        every { geckoView.releaseSession() } throws NoSuchMethodError("release boom")

        // The catch takes any Throwable: a LinkageError from GeckoView would
        // crash view teardown just the same.
        manager.onDropViewInstance(geckoView)
    }

    // The media and content delegates the code last set on [session].
    private var mediaDelegateSet: MediaSession.Delegate? = null
    private var contentDelegateSet: GeckoSession.ContentDelegate? = null

    private fun recordSessionDelegates() {
        every { session.mediaSessionDelegate = any() } answers { mediaDelegateSet = firstArg() }
        every { session.mediaSessionDelegate } answers { mediaDelegateSet }
        every { session.contentDelegate = any() } answers { contentDelegateSet = firstArg() }
        every { session.contentDelegate } answers { contentDelegateSet }
    }

    // The session each tracked view shows, and the view that owns each session.
    private val boundSessions = java.util.IdentityHashMap<GeckoView, GeckoSession>()
    private val sessionOwners = java.util.IdentityHashMap<GeckoSession, GeckoView>()

    /**
     * Makes [view] report the session bound to it, as a real GeckoView does.
     * GeckoView 159's setSession also takes the session from the view that
     * showed it: the previous owner gets onRelease, which releases the session
     * there, so that view's session is null from then on.
     */
    private fun trackSession(view: GeckoView) {
        every { view.session } answers { boundSessions[view] }
        every { view.setSession(any()) } answers {
            val s: GeckoSession = firstArg()
            if (boundSessions[view] !== s) {
                boundSessions[view]?.let { old -> if (sessionOwners[old] === view) sessionOwners.remove(old) }
                sessionOwners[s]?.let { previous -> boundSessions.remove(previous) }
                boundSessions[view] = s
                sessionOwners[s] = view
            }
        }
        every { view.releaseSession() } answers {
            boundSessions.remove(view)?.also { if (sessionOwners[it] === view) sessionOwners.remove(it) }
        }
    }

    /** A view the host mounts later, like [geckoView]. */
    private fun newView(id: Int): GeckoView = mockk<GeckoView>(relaxed = true).also {
        every { it.context } returns context
        every { it.id } returns id
        trackSession(it)
    }

    /**
     * One props transaction mounting [view] on the pooled [session]:
     * handleAudioFocus on and the sessionKey, in whichever order React Native
     * applies them, then onAfterUpdateTransaction.
     */
    private fun mount(view: GeckoView, key: String, sessionKeyFirst: Boolean = false) {
        if (sessionKeyFirst) {
            manager.setSessionKey(view, key)
            manager.setHandleAudioFocus(view, true)
        } else {
            manager.setHandleAudioFocus(view, true)
            manager.setSessionKey(view, key)
        }
        manager.onAfterUpdateTransaction(view)
    }

    /** Mounts [view] and starts [media] playing on the media delegate the session then has. */
    private fun mountPlaying(
        view: GeckoView,
        key: String,
        media: MediaSession,
        sessionKeyFirst: Boolean = false,
    ): MediaDelegate {
        trackSession(view)
        mount(view, key, sessionKeyFirst)
        return (mediaDelegateSet as MediaDelegate).also { it.onPlay(session, media) }
    }

    private fun MockKMatcherScope.action(name: String) = match<WritableMap> { it.getString("action") == name }

    @Test
    fun `onDropViewInstance keeps handling audio focus for a session still playing`() {
        // Codex final review F1: the drop gave up the focus the page was playing
        // under, but the pooled session kept playing, with nothing left to
        // pause it when another app took focus.
        TestHelpers.mockArguments()
        try {
            recordSessionDelegates()
            val media: MediaSession = mockk(relaxed = true)
            val delegate = mountPlaying(geckoView, "playing", media)
            verify(exactly = 1) { audioManager.requestAudioFocus(any<AudioFocusRequest>()) }

            // The view is dropped mid-playback; the pooled session plays on.
            manager.onDropViewInstance(geckoView)

            assertSame(delegate, mediaDelegateSet)
            verify(exactly = 0) { audioManager.abandonAudioFocusRequest(any<AudioFocusRequest>()) }

            // Another app takes focus: the page is paused, and once GeckoView
            // reports the pause the delegate gives focus up, with no event for
            // the dropped view.
            delegate.audioFocusChangeListener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS)
            verify(exactly = 1) { media.pause() }
            delegate.onPause(session, media)
            verify(exactly = 1) { audioManager.abandonAudioFocusRequest(any<AudioFocusRequest>()) }
            verify(exactly = 1) { module.sendViewEvent(any(), "onMediaSessionAction", any()) }
        } finally {
            TestHelpers.unmockArguments()
        }
    }

    @Test
    fun `handleAudioFocus applies whichever of the view's props React Native sets first`() {
        // React Native applies a batch's prop setters in no fixed order; release
        // builds tend to apply sessionKey, which the JS wrapper passes last,
        // first. The delegate it binds then started without focus handling.
        TestHelpers.mockArguments()
        try {
            recordSessionDelegates()

            mountPlaying(geckoView, "key-first", mockk(relaxed = true), sessionKeyFirst = true)

            verify(exactly = 1) { audioManager.requestAudioFocus(any<AudioFocusRequest>()) }
        } finally {
            TestHelpers.unmockArguments()
        }
    }

    @Test
    fun `the view manager applies handleAudioFocus once a batch of props lands`() {
        // Through React Native's own path: ViewManager.updateProperties runs the
        // setters in the props map's order, then onAfterUpdateTransaction.
        TestHelpers.mockArguments()
        try {
            recordSessionDelegates()
            trackSession(geckoView)
            every { geckoView.getTag(any()) } returns null
            val props: ReadableMap = mockk(relaxed = true)
            every { props.entryIterator } answers {
                linkedMapOf<String, Any>("sessionKey" to "batched", "handleAudioFocus" to true)
                    .entries.iterator()
            }

            GeckoViewManager().updateProperties(geckoView, ReactStylesDiffMap(props))
            (mediaDelegateSet as MediaDelegate).onPlay(session, mockk(relaxed = true))

            verify(exactly = 1) { audioManager.requestAudioFocus(any<AudioFocusRequest>()) }
        } finally {
            TestHelpers.unmockArguments()
        }
    }

    @Test
    fun `turning handleAudioFocus off on a mounted view gives its focus up, and on again takes it`() {
        TestHelpers.mockArguments()
        try {
            recordSessionDelegates()
            mountPlaying(geckoView, "toggled", mockk(relaxed = true))

            manager.setHandleAudioFocus(geckoView, false)
            manager.onAfterUpdateTransaction(geckoView)
            verify(exactly = 1) { audioManager.abandonAudioFocusRequest(any<AudioFocusRequest>()) }

            manager.setHandleAudioFocus(geckoView, true)
            manager.onAfterUpdateTransaction(geckoView)
            verify(exactly = 2) { audioManager.requestAudioFocus(any<AudioFocusRequest>()) }
        } finally {
            TestHelpers.unmockArguments()
        }
    }

    private fun assertRemountKeepsFocus(sessionKeyFirst: Boolean) {
        recordSessionDelegates()
        every { geckoView.id } returns 7
        val media: MediaSession = mockk(relaxed = true)
        val delegate = mountPlaying(geckoView, "pooled", media)
        manager.onDropViewInstance(geckoView)

        // The host remounts: a new view binds the same pooled session.
        mount(newView(42), "pooled", sessionKeyFirst)

        // The same delegate now reports to the new view and keeps the focus:
        // a fresh request would interrupt whatever else is playing, such as
        // another app's navigation prompt.
        assertSame(delegate, mediaDelegateSet)
        verify(exactly = 1) { audioManager.requestAudioFocus(any<AudioFocusRequest>()) }
        verify(exactly = 0) { audioManager.abandonAudioFocusRequest(any<AudioFocusRequest>()) }
        // The new view hears that the page is playing, and the page is now its
        // to pause on a focus loss.
        verify(exactly = 1) { module.sendViewEvent(42, "onMediaSessionAction", action("play")) }
        delegate.audioFocusChangeListener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS)
        verify(exactly = 1) { media.pause() }
        delegate.onPause(session, media)
        verify(exactly = 1) { module.sendViewEvent(42, "onMediaSessionAction", action("pause")) }
        verify(exactly = 1) { module.sendViewEvent(7, "onMediaSessionAction", any()) }
    }

    @Test
    fun `a view that binds a dropped view's pooled session keeps its audio focus without a new request`() {
        TestHelpers.mockArguments()
        try {
            assertRemountKeepsFocus(sessionKeyFirst = false)
        } finally {
            TestHelpers.unmockArguments()
        }
    }

    @Test
    fun `a remount whose sessionKey lands first keeps the focus too`() {
        // The view's handleAudioFocus isn't set yet when its sessionKey binds the
        // session; the delegate must not give focus up in between.
        TestHelpers.mockArguments()
        try {
            assertRemountKeepsFocus(sessionKeyFirst = true)
        } finally {
            TestHelpers.unmockArguments()
        }
    }

    @Test
    fun `onDropViewInstance gives up the focus of a session GeckoView already closed`() {
        // The content process died while the view showed the page: GeckoView
        // closed the session, and no pause or stop followed.
        TestHelpers.mockArguments()
        try {
            recordSessionDelegates()
            mountPlaying(geckoView, "crashed", mockk(relaxed = true))
            every { session.isOpen } returns false

            manager.onDropViewInstance(geckoView)

            verify(exactly = 1) { audioManager.abandonAudioFocusRequest(any<AudioFocusRequest>()) }
            assertNull(mediaDelegateSet)
            assertNull(contentDelegateSet)
        } finally {
            TestHelpers.unmockArguments()
        }
    }

    private fun assertHiddenSessionDeathGivesUpFocus(death: (GeckoSession.ContentDelegate) -> Unit) {
        recordSessionDelegates()
        mountPlaying(geckoView, "hidden", mockk(relaxed = true))
        manager.onDropViewInstance(geckoView)
        verify(exactly = 0) { audioManager.abandonAudioFocusRequest(any<AudioFocusRequest>()) }

        // GeckoView reports a dead content process, and closes the session,
        // only through the session's content delegate.
        death(contentDelegateSet!!)

        verify(exactly = 1) { audioManager.abandonAudioFocusRequest(any<AudioFocusRequest>()) }
        // The dropped view is gone, so nothing tells it.
        verify(exactly = 0) { module.sendViewEvent(any(), "onContentProcessGone", any()) }
    }

    @Test
    fun `a pooled session whose content process crashes while no view shows it gives its focus up`() {
        TestHelpers.mockArguments()
        try {
            assertHiddenSessionDeathGivesUpFocus { it.onCrash(session) }
        } finally {
            TestHelpers.unmockArguments()
        }
    }

    @Test
    fun `a pooled session whose content process is killed while no view shows it gives its focus up`() {
        TestHelpers.mockArguments()
        try {
            assertHiddenSessionDeathGivesUpFocus { it.onKill(session) }
        } finally {
            TestHelpers.unmockArguments()
        }
    }

    private fun assertShownSessionDeathTellsTheView(
        reason: String,
        death: (GeckoSession.ContentDelegate) -> Unit,
    ) {
        recordSessionDelegates()
        every { geckoView.id } returns 7
        mountPlaying(geckoView, "shown", mockk(relaxed = true))

        // Through the content delegate the view attached when it bound the
        // session, which GeckoView has closed by then.
        every { session.isOpen } returns false
        death(contentDelegateSet!!)

        verify(exactly = 1) {
            module.sendViewEvent(7, "onContentProcessGone", match<WritableMap> { it.getString("reason") == reason })
        }
        verify(exactly = 1) { module.sendViewEvent(any(), "onContentProcessGone", any()) }
    }

    @Test
    fun `a crash of the session a view shows tells that view once`() {
        TestHelpers.mockArguments()
        try {
            assertShownSessionDeathTellsTheView("crash") { it.onCrash(session) }
        } finally {
            TestHelpers.unmockArguments()
        }
    }

    @Test
    fun `a kill of the session a view shows tells that view once`() {
        TestHelpers.mockArguments()
        try {
            assertShownSessionDeathTellsTheView("kill") { it.onKill(session) }
        } finally {
            TestHelpers.unmockArguments()
        }
    }

    /**
     * A remount: a new view binds the pooled session [geckoView] shows, and
     * [geckoView] is dropped, in either order. Fabric applies the new view's
     * props before it deletes the old view.
     */
    private fun assertRemountedViewHearsContentProcessGone(newViewBindsFirst: Boolean) {
        recordSessionDelegates()
        every { geckoView.id } returns 7
        mountPlaying(geckoView, "pooled", mockk(relaxed = true))

        val next = newView(42)
        if (newViewBindsFirst) {
            mount(next, "pooled")
            manager.onDropViewInstance(geckoView)
        } else {
            manager.onDropViewInstance(geckoView)
            mount(next, "pooled")
        }
        // GeckoView closes the session before it reports the crash.
        every { session.isOpen } returns false
        contentDelegateSet!!.onCrash(session)

        // The new view's content delegate replaced the one the session had,
        // and the dropped view hears nothing.
        verify(exactly = 1) { module.sendViewEvent(42, "onContentProcessGone", any()) }
        verify(exactly = 0) { module.sendViewEvent(7, "onContentProcessGone", any()) }
    }

    @Test
    fun `a view that binds a dropped view's pooled session hears its content process die`() {
        TestHelpers.mockArguments()
        try {
            assertRemountedViewHearsContentProcessGone(newViewBindsFirst = false)
        } finally {
            TestHelpers.unmockArguments()
        }
    }

    @Test
    fun `a new view that binds the session before the old view drops hears its content process die`() {
        TestHelpers.mockArguments()
        try {
            assertRemountedViewHearsContentProcessGone(newViewBindsFirst = true)
        } finally {
            TestHelpers.unmockArguments()
        }
    }

    @Test
    fun `a view that binds a session reopened after a crash reuses its released media delegate`() {
        // GeckoView closes the session when its content process dies, the view's
        // content delegate releases the media delegate, and the pool reopens the
        // session for the next view that binds its key.
        TestHelpers.mockArguments()
        try {
            recordSessionDelegates()
            val media: MediaSession = mockk(relaxed = true)
            val first = mountPlaying(geckoView, "pooled", media)
            first.onActivated(session, media)
            every { session.isOpen } returns false
            contentDelegateSet!!.onCrash(session)
            verify(exactly = 1) { audioManager.abandonAudioFocusRequest(any<AudioFocusRequest>()) }
            every { session.isOpen } returns true

            mount(newView(42), "pooled")

            // The released delegate carries no stale state to the new view...
            assertSame(first, mediaDelegateSet)
            verify(exactly = 0) { module.sendViewEvent(42, "onMediaSessionAction", any()) }
            // ...and new playback takes focus again, reporting to that view.
            first.onPlay(session, media)
            verify(exactly = 2) { audioManager.requestAudioFocus(any<AudioFocusRequest>()) }
            verify(exactly = 1) { module.sendViewEvent(42, "onMediaSessionAction", action("play")) }
        } finally {
            TestHelpers.unmockArguments()
        }
    }

    @Test
    fun `a view that binds a session still closed gets a fresh media delegate`() {
        TestHelpers.mockArguments()
        try {
            recordSessionDelegates()
            val first = mountPlaying(geckoView, "pooled", mockk(relaxed = true))
            // GeckoView closed the session when its content process died, and the
            // pool couldn't reopen it before another view bound the same key.
            every { session.isOpen } returns false

            mount(newView(42), "pooled")

            // The dead page's focus is given up, and none is requested for it.
            assertNotSame(first, mediaDelegateSet)
            verify(exactly = 1) { audioManager.abandonAudioFocusRequest(any<AudioFocusRequest>()) }
            verify(exactly = 1) { audioManager.requestAudioFocus(any<AudioFocusRequest>()) }
        } finally {
            TestHelpers.unmockArguments()
        }
    }

    // ========== sessions keyed by the JS wrapper ==========

    /** [session] is pooled under [key]; closing it is recorded, not run. */
    private fun pooledUnder(key: String) {
        every { GeckoSessionManager.getSessionKey(session) } returns key
        every { GeckoSessionManager.releaseSession(any()) } just Runs
    }

    @Test
    fun `a view whose sessionKey the JS wrapper generated closes its session when dropped`() {
        // No view can bind a generated key again, so the session stayed pooled
        // and open, playing on unheard with its focus held, until shutdown.
        TestHelpers.mockArguments()
        try {
            recordSessionDelegates()
            pooledUnder("generated")
            manager.setCloseSessionOnDrop(geckoView, true)
            mountPlaying(geckoView, "generated", mockk(relaxed = true))

            manager.onDropViewInstance(geckoView)

            // Closed once the view has let go of it.
            verifyOrder {
                geckoView.releaseSession()
                GeckoSessionManager.releaseSession("generated")
            }
            // The page stops with its session: its focus is given up, and no
            // delegate is left on the session to take it again.
            verify(exactly = 1) { audioManager.abandonAudioFocusRequest(any<AudioFocusRequest>()) }
            assertNull(mediaDelegateSet)
            assertNull(contentDelegateSet)
        } finally {
            TestHelpers.unmockArguments()
        }
    }

    @Test
    fun `a view with the host's sessionKey leaves its session pooled when dropped`() {
        // The wrapper passes closeSessionOnDrop = false with a host's key.
        TestHelpers.mockArguments()
        try {
            recordSessionDelegates()
            pooledUnder("host-key")
            manager.setCloseSessionOnDrop(geckoView, false)
            val delegate = mountPlaying(geckoView, "host-key", mockk(relaxed = true))

            manager.onDropViewInstance(geckoView)

            verify(exactly = 0) { GeckoSessionManager.releaseSession(any()) }
            assertSame(delegate, mediaDelegateSet)
            verify(exactly = 0) { audioManager.abandonAudioFocusRequest(any<AudioFocusRequest>()) }
        } finally {
            TestHelpers.unmockArguments()
        }
    }

    @Test
    fun `a generated key's session still closes when letting go of the view fails`() {
        pooledUnder("generated")
        manager.setCloseSessionOnDrop(geckoView, true)
        every { geckoView.releaseSession() } throws IllegalStateException("release boom")

        manager.onDropViewInstance(geckoView)

        verify(exactly = 1) { GeckoSessionManager.releaseSession("generated") }
    }

    @Test
    fun `the view manager closes a dropped view's session when the wrapper generated its key`() {
        // Through React Native's own path: the codegen delegate applies the
        // batch of props, then GeckoViewManager forwards the drop.
        pooledUnder("generated")
        trackSession(geckoView)
        every { geckoView.getTag(any()) } returns null
        val props: ReadableMap = mockk(relaxed = true)
        every { props.entryIterator } answers {
            linkedMapOf<String, Any>("sessionKey" to "generated", "closeSessionOnDrop" to true)
                .entries.iterator()
        }
        val viewManager = GeckoViewManager()

        viewManager.updateProperties(geckoView, ReactStylesDiffMap(props))
        viewManager.onDropViewInstance(geckoView)

        verify(exactly = 1) { GeckoSessionManager.releaseSession("generated") }
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
