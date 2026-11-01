package com.reactnative.geckoview

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class GeckoSessionManagerTest {

    private lateinit var context: Context
    private lateinit var mockRuntime: GeckoRuntime
    private lateinit var mockSession: GeckoSession

    @Before
    fun setUp() {
        // Create mocks
        context = mockk(relaxed = true)
        mockRuntime = mockk(relaxed = true)
        mockSession = mockk(relaxed = true)
        
        // Mock context.applicationContext to return itself
        every { context.applicationContext } returns context
        
        // Mock GeckoRuntimeManager static object
        mockkObject(GeckoRuntimeManager)
        every { GeckoRuntimeManager.getRuntime(any()) } returns mockRuntime
        
        GeckoSessionManager.setPlaybackAllowed(true)

        // Initialize the manager
        GeckoSessionManager.init(context)
        
        // Clear any existing sessions from previous tests
        GeckoSessionManager.getAllSessions().keys.forEach { key ->
            GeckoSessionManager.releaseSession(key)
        }
    }
    
    @After
    fun tearDown() {
        GeckoSessionManager.setPlaybackAllowed(true)
        GeckoSessionManager.closeAllSessions()
        // Clean up all mocks
        unmockkAll()
    }

    @Test
    fun `init stores context`() {
        GeckoSessionManager.init(context)
        
        // Verify by creating a session (which requires context)
        val session = GeckoSessionManager.getOrCreateSession("test-key")
        assertNotNull(session)
    }

    @Test
    fun `getOrCreateSession creates new session for new key`() {
        GeckoSessionManager.init(context)
        
        val session1 = GeckoSessionManager.getOrCreateSession("session-1")
        val session2 = GeckoSessionManager.getOrCreateSession("session-2")
        
        assertNotNull(session1)
        assertNotNull(session2)
        assertTrue("Different keys should create different sessions", session1 !== session2)
    }

    @Test
    fun `getOrCreateSession returns existing session for same key`() {
        GeckoSessionManager.init(context)
        
        val session1 = GeckoSessionManager.getOrCreateSession("same-key")
        val session2 = GeckoSessionManager.getOrCreateSession("same-key")
        
        assertSame("Same key should return same session instance", session1, session2)
    }

    @Test
    fun `getOrCreateSession creates desktop mode by default`() {
        GeckoSessionManager.init(context)
        
        val session = GeckoSessionManager.getOrCreateSession("desktop-test")
        
        assertNotNull(session)
        // Session should be created (can't easily verify desktop mode without accessing private settings)
    }

    @Test
    fun `getOrCreateSession can create mobile mode session`() {
        GeckoSessionManager.init(context)
        
        val session = GeckoSessionManager.getOrCreateSession("mobile-test", desktopMode = false)
        
        assertNotNull(session)
    }

    @Test
    fun `releaseSession removes session from map`() {
        GeckoSessionManager.init(context)
        
        val key = "release-test"
        GeckoSessionManager.getOrCreateSession(key)
        
        val sizeBefore = GeckoSessionManager.getAllSessions().size
        GeckoSessionManager.releaseSession(key)
        val sizeAfter = GeckoSessionManager.getAllSessions().size
        
        assertEquals("Session should be removed", sizeBefore - 1, sizeAfter)
    }

    @Test
    fun `releaseSession handles non-existent key gracefully`() {
        GeckoSessionManager.init(context)
        
        // Should not throw exception
        GeckoSessionManager.releaseSession("non-existent-key")
    }

    @Test
    fun `getAllSessions returns copy of sessions map`() {
        GeckoSessionManager.init(context)
        
        GeckoSessionManager.getOrCreateSession("test-1")
        GeckoSessionManager.getOrCreateSession("test-2")
        
        val sessions = GeckoSessionManager.getAllSessions()
        
        assertTrue("Should contain created sessions", sessions.size >= 2)
        assertTrue(sessions.containsKey("test-1"))
        assertTrue(sessions.containsKey("test-2"))
    }

    /**
     * Concurrency regression for Finding #12.
     *
     * Previously `sessions` was a plain `mutableMapOf<String, GeckoSession>()`
     * and `init()` iterated without synchronization — a second thread
     * mutating `sessions` mid-iteration would throw
     * `ConcurrentModificationException`. `init()` no longer walks the pool at
     * all (see `init never opens pooled sessions`); the stress test below
     * keeps the race covered and must never surface a CME.
     *
     * We exercise the specific pattern that previously failed: one thread
     * hammering `init()` (which used to iterate `sessions.forEach`) while another
     * mutates the session map via `releaseSession`. `GeckoSession`
     * instantiation is skipped because it would require running on Robolectric's
     * main thread; instead we pre-populate with real sessions from the main
     * thread, then race init() against releases from worker threads.
     */
    @Test
    fun `init + releaseSession must not throw ConcurrentModificationException`() {
        GeckoSessionManager.init(context)

        // Pre-populate from the main thread (only place GeckoSession() is safe).
        val keys = (0 until 200).map { "concurrent-$it" }
        keys.forEach { GeckoSessionManager.getOrCreateSession(it) }

        val errors = java.util.Collections.synchronizedList(mutableListOf<Throwable>())
        val initHammerThreads = List(10) {
            Thread {
                try {
                    repeat(100) { GeckoSessionManager.init(context) }
                } catch (t: Throwable) { errors.add(t) }
            }
        }
        val releaseThreads = keys.chunked(20).map { chunk ->
            Thread {
                try {
                    chunk.forEach { GeckoSessionManager.releaseSession(it) }
                } catch (t: Throwable) { errors.add(t) }
            }
        }

        (initHammerThreads + releaseThreads).forEach { it.start() }
        (initHammerThreads + releaseThreads).forEach { it.join() }

        val cmeErrors = errors.filterIsInstance<java.util.ConcurrentModificationException>()
        assertTrue(
            "Concurrent init + releaseSession must not throw CME; got ${cmeErrors.size}: ${cmeErrors.firstOrNull()}",
            cmeErrors.isEmpty(),
        )
    }

    /**
     * Companion regression: the race where `init()` used to iterate while
     * `getOrCreateSession` (on the main thread, since GeckoSession requires
     * it) inserts a new key. We simulate the insertion side by poking the
     * underlying ConcurrentHashMap through a separate thread that hammers
     * `getAllSessions()`, without needing GeckoSession construction off the
     * UI thread.
     */
    @Test
    fun `init iteration is safe under concurrent readers`() {
        GeckoSessionManager.init(context)
        (0 until 100).forEach { GeckoSessionManager.getOrCreateSession("seed-$it") }

        val errors = java.util.Collections.synchronizedList(mutableListOf<Throwable>())
        val readers = List(10) {
            Thread {
                try {
                    repeat(500) { GeckoSessionManager.getAllSessions() }
                } catch (t: Throwable) { errors.add(t) }
            }
        }
        val initter = Thread {
            try {
                repeat(200) { GeckoSessionManager.init(context) }
            } catch (t: Throwable) { errors.add(t) }
        }

        (readers + initter).forEach { it.start() }
        (readers + initter).forEach { it.join() }

        assertTrue(
            "init + getAllSessions concurrently must not throw; got ${errors.firstOrNull()}",
            errors.none { it is java.util.ConcurrentModificationException },
        )
    }

    // ========== UI-thread confinement (self-review M1) ==========

    @Test
    fun `getOrCreateSession refuses to build a session off the UI thread`() {
        // Ref methods run on the native-modules thread. There GeckoSession.open
        // (and GeckoRuntime.create) throw IllegalThreadStateException, which
        // used to leave an unopened orphan in the pool.
        var failure: Throwable? = null
        val nativeModulesThread = Thread {
            try {
                GeckoSessionManager.getOrCreateSession("worker-key")
            } catch (t: Throwable) {
                failure = t
            }
        }
        nativeModulesThread.start()
        nativeModulesThread.join()

        assertTrue("expected IllegalStateException, got $failure", failure is IllegalStateException)
        assertFalse(GeckoSessionManager.getAllSessions().containsKey("worker-key"))
        verify(exactly = 0) { GeckoRuntimeManager.getRuntime(any()) }
    }

    @Test
    fun `getOpenSession only returns a session a view opened and never creates one`() {
        assertNull(GeckoSessionManager.getOpenSession("never-bound"))
        assertFalse(GeckoSessionManager.getAllSessions().containsKey("never-bound"))

        val session = GeckoSessionManager.getOrCreateSession("bound")
        assertSame(session, GeckoSessionManager.getOpenSession("bound"))

        GeckoSessionManager.releaseSession("bound")
        assertNull(GeckoSessionManager.getOpenSession("bound"))
    }

    @Test
    fun `init never opens pooled sessions`() {
        // Leave a session in the pool unopened (the runtime failed to start).
        // init used to open every pooled session, which revived orphans with no
        // view attached on the next createViewInstance or module init.
        every { GeckoRuntimeManager.getRuntime(any()) } throws IllegalStateException("runtime unavailable")
        try {
            GeckoSessionManager.getOrCreateSession("orphan")
        } catch (_: IllegalStateException) {
        }
        assertTrue(GeckoSessionManager.getAllSessions().containsKey("orphan"))
        every { GeckoRuntimeManager.getRuntime(any()) } returns mockRuntime

        GeckoSessionManager.init(context)

        verify(exactly = 1) { GeckoRuntimeManager.getRuntime(any()) }
        assertNull(GeckoSessionManager.getOpenSession("orphan"))
    }

    @Test
    fun `session pooling - reuses sessions correctly`() {
        GeckoSessionManager.init(context)

        // Create sessions
        val session1a = GeckoSessionManager.getOrCreateSession("pool-1")
        val session2a = GeckoSessionManager.getOrCreateSession("pool-2")

        // Reuse sessions
        val session1b = GeckoSessionManager.getOrCreateSession("pool-1")
        val session2b = GeckoSessionManager.getOrCreateSession("pool-2")

        assertSame("Should reuse pool-1", session1a, session1b)
        assertSame("Should reuse pool-2", session2a, session2b)
    }

    // ========== view registry (synchronized WeakHashMap) ==========

    @Test
    fun `registerView then getView returns the registered view`() {
        val session: GeckoSession = mockk(relaxed = true)
        val view: org.mozilla.geckoview.GeckoView = mockk(relaxed = true)

        GeckoSessionManager.registerView(session, view)

        assertSame(view, GeckoSessionManager.getView(session))
    }

    @Test
    fun `getView returns null for an unregistered session`() {
        val session: GeckoSession = mockk(relaxed = true)
        assertEquals(null, GeckoSessionManager.getView(session))
    }

    @Test
    fun `getAllViews returns a snapshot containing every registered view`() {
        val sessionA: GeckoSession = mockk(relaxed = true)
        val sessionB: GeckoSession = mockk(relaxed = true)
        val viewA: org.mozilla.geckoview.GeckoView = mockk(relaxed = true)
        val viewB: org.mozilla.geckoview.GeckoView = mockk(relaxed = true)

        GeckoSessionManager.registerView(sessionA, viewA)
        GeckoSessionManager.registerView(sessionB, viewB)

        val all = GeckoSessionManager.getAllViews()
        assertTrue("snapshot must contain viewA", all.contains(viewA))
        assertTrue("snapshot must contain viewB", all.contains(viewB))
    }

    @Test
    fun `getAllViews returns a detached copy safe to iterate`() {
        val session: GeckoSession = mockk(relaxed = true)
        val view: org.mozilla.geckoview.GeckoView = mockk(relaxed = true)
        GeckoSessionManager.registerView(session, view)

        val snapshot = GeckoSessionManager.getAllViews()
        // Mutating the registry after the snapshot must not affect the returned
        // list (it's a copy taken under the map monitor). We assert by relative
        // membership rather than absolute size, since `views` is process-wide
        // shared state and other tests may have registered entries.
        val session2: GeckoSession = mockk(relaxed = true)
        val laterView: org.mozilla.geckoview.GeckoView = mockk(relaxed = true)
        GeckoSessionManager.registerView(session2, laterView)

        assertTrue("snapshot taken before the later register must include view", snapshot.contains(view))
        assertFalse("frozen snapshot must NOT see the later registration", snapshot.contains(laterView))
        // Keep strong refs so the WeakHashMap doesn't evict mid-assert.
        assertSame(view, GeckoSessionManager.getView(session))
    }

    @Test
    fun `registerView + getAllViews must not throw under concurrent access`() {
        // getAllViews snapshots under the synchronized monitor while
        // registerView mutates from worker threads — must never surface a
        // ConcurrentModificationException from the underlying WeakHashMap.
        val sessions = (0 until 200).map { mockk<GeckoSession>(relaxed = true) }
        val views = sessions.map { mockk<org.mozilla.geckoview.GeckoView>(relaxed = true) }
        val errors = java.util.Collections.synchronizedList(mutableListOf<Throwable>())

        val writers = sessions.indices.chunked(20).map { idxChunk ->
            Thread {
                try {
                    idxChunk.forEach { GeckoSessionManager.registerView(sessions[it], views[it]) }
                } catch (t: Throwable) { errors.add(t) }
            }
        }
        val readers = List(8) {
            Thread {
                try {
                    repeat(500) { GeckoSessionManager.getAllViews() }
                } catch (t: Throwable) { errors.add(t) }
            }
        }

        (writers + readers).forEach { it.start() }
        (writers + readers).forEach { it.join() }

        assertTrue(
            "concurrent registerView + getAllViews must not throw; got ${errors.firstOrNull()}",
            errors.none { it is java.util.ConcurrentModificationException },
        )
        // Keep strong refs alive so the WeakHashMap doesn't GC them mid-assert.
        assertEquals(200, sessions.size)
    }

    // ========== getSessionKey reverse lookup ==========

    @Test
    fun `getSessionKey returns the key for a known session`() {
        GeckoSessionManager.init(context)
        val session = GeckoSessionManager.getOrCreateSession("reverse-lookup")

        assertEquals("reverse-lookup", GeckoSessionManager.getSessionKey(session))
    }

    @Test
    fun `getSessionKey returns null for an unknown session`() {
        val stranger: GeckoSession = mockk(relaxed = true)
        assertEquals(null, GeckoSessionManager.getSessionKey(stranger))
    }
    @Test
    fun `restrictions close existing sessions before JavaScript can act`() {
        GeckoSessionManager.getOrCreateSession("playing-browser")
        assertTrue(GeckoSessionManager.getAllSessions().isNotEmpty())
        GeckoSessionManager.setPlaybackAllowed(false)
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertFalse(GeckoSessionManager.isPlaybackAllowed())
        assertTrue(GeckoSessionManager.getAllSessions().isEmpty())
    }

    @Test
    fun `parking does not reopen sessions or resume browser media`() {
        GeckoSessionManager.setPlaybackAllowed(false)
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        GeckoSessionManager.setPlaybackAllowed(true)
        assertTrue(GeckoSessionManager.isPlaybackAllowed())
        assertTrue(GeckoSessionManager.getAllSessions().isEmpty())
    }

    @Test
    fun `dropping a view removes its Activity association but preserves a pooled session`() {
        val session = GeckoSessionManager.getOrCreateSession("pooled")
        val view = mockk<org.mozilla.geckoview.GeckoView>(relaxed = true)
        GeckoSessionManager.registerView(session, view)
        GeckoSessionManager.unregisterView(view)
        assertEquals(null, GeckoSessionManager.getView(session))
        assertTrue(GeckoSessionManager.getAllViews().isEmpty())
        assertSame(session, GeckoSessionManager.getAllSessions()["pooled"])
    }

    @Test
    fun `releasing a session removes its view association immediately`() {
        val session = GeckoSessionManager.getOrCreateSession("released")
        val view = mockk<org.mozilla.geckoview.GeckoView>(relaxed = true)
        GeckoSessionManager.registerView(session, view)
        GeckoSessionManager.releaseSession("released")
        assertEquals(null, GeckoSessionManager.getView(session))
        assertTrue(GeckoSessionManager.getAllViews().isEmpty())
    }

    @Test
    fun `closing sessions mid-playback releases the audio focus their delegates hold`() {
        // closeSession (dart-tv's stop path) and a UX restriction both close a
        // session whose page may still be playing; no onPause/onStop follows.
        val released = GeckoSessionManager.getOrCreateSession("released-while-playing")
        val releasedDelegate = mockk<MediaDelegate>(relaxed = true)
        released.mediaSessionDelegate = releasedDelegate
        val restricted = GeckoSessionManager.getOrCreateSession("restricted-while-playing")
        val restrictedDelegate = mockk<MediaDelegate>(relaxed = true)
        restricted.mediaSessionDelegate = restrictedDelegate

        GeckoSessionManager.releaseSession("released-while-playing")
        verify(exactly = 1) { releasedDelegate.release() }

        GeckoSessionManager.closeAllSessions()
        verify(exactly = 1) { restrictedDelegate.release() }
    }

    @Test
    fun `a restriction removes all view associations without relying on GC or JS`() {
        val session = GeckoSessionManager.getOrCreateSession("restricted")
        val view = mockk<org.mozilla.geckoview.GeckoView>(relaxed = true)
        GeckoSessionManager.registerView(session, view)
        GeckoSessionManager.setPlaybackAllowed(false)
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertTrue(GeckoSessionManager.getAllViews().isEmpty())
    }

}
