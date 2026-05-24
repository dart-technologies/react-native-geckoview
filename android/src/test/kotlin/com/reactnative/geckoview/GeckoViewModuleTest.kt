package com.reactnative.geckoview

import com.facebook.react.bridge.JavaOnlyMap
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.uimanager.UIManagerHelper
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * Tests for [GeckoViewModule] helpers.
 *
 * The full [GeckoViewModule] lifecycle requires a live React context, so we
 * test the security-critical pure helper [GeckoViewModule.isSafeLoadUrl]
 * directly here, and rely on integration tests for the @ReactMethod surface.
 * Instance-level tests use the `internal *ForTest` hooks plus a mocked
 * [ReactApplicationContext].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class GeckoViewModuleTest {

    private companion object {
        // PROMPT_TIMEOUT_MS in production is 60s. Add a small buffer so the
        // simulated looper crosses the postDelayed deadline.
        const val PROMPT_TIMEOUT_BUFFER_MS = 65_000L

        // EVAL_BRIDGE_MAX_RETRIES (20) × EVAL_BRIDGE_RETRY_DELAY_MS (100ms) = 2s.
        // Add a buffer so the simulated looper drains every retry.
        const val EVAL_RETRY_DRAIN_MS = 2_500L
    }

    // ========== isSafeLoadUrl — URL scheme allowlist (Finding #4) ==========

    @Test
    fun `isSafeLoadUrl accepts http`() {
        assertTrue(GeckoViewModule.isSafeLoadUrl("http://example.com"))
    }

    @Test
    fun `isSafeLoadUrl accepts https`() {
        assertTrue(GeckoViewModule.isSafeLoadUrl("https://example.com/path?query=1"))
    }

    @Test
    fun `isSafeLoadUrl accepts about`() {
        assertTrue(GeckoViewModule.isSafeLoadUrl("about:blank"))
    }

    @Test
    fun `isSafeLoadUrl rejects javascript URI (XSS primitive)`() {
        assertFalse(GeckoViewModule.isSafeLoadUrl("javascript:alert(document.cookie)"))
    }

    @Test
    fun `isSafeLoadUrl rejects data URI (inline script)`() {
        assertFalse(GeckoViewModule.isSafeLoadUrl("data:text/html,<script>alert(1)</script>"))
    }

    @Test
    fun `isSafeLoadUrl rejects file URI (sandbox probe)`() {
        assertFalse(GeckoViewModule.isSafeLoadUrl("file:///data/data/com.example/cookies"))
    }

    @Test
    fun `isSafeLoadUrl rejects content URI`() {
        assertFalse(GeckoViewModule.isSafeLoadUrl("content://com.android.contacts/contacts/1"))
    }

    @Test
    fun `isSafeLoadUrl rejects blob URI`() {
        assertFalse(GeckoViewModule.isSafeLoadUrl("blob:https://example.com/abc"))
    }

    @Test
    fun `isSafeLoadUrl rejects intent URI`() {
        assertFalse(GeckoViewModule.isSafeLoadUrl("intent://example#Intent;scheme=myapp;end"))
    }

    @Test
    fun `isSafeLoadUrl rejects empty string`() {
        assertFalse(GeckoViewModule.isSafeLoadUrl(""))
    }

    @Test
    fun `isSafeLoadUrl rejects whitespace only`() {
        assertFalse(GeckoViewModule.isSafeLoadUrl("   "))
    }

    @Test
    fun `isSafeLoadUrl is case-insensitive on scheme`() {
        assertTrue(GeckoViewModule.isSafeLoadUrl("HTTPS://example.com"))
        assertTrue(GeckoViewModule.isSafeLoadUrl("HtTpS://example.com"))
        assertFalse(GeckoViewModule.isSafeLoadUrl("JavaScript:alert(1)"))
        assertFalse(GeckoViewModule.isSafeLoadUrl("JAVASCRIPT:alert(1)"))
    }

    @Test
    fun `isSafeLoadUrl rejects url missing scheme`() {
        assertFalse(GeckoViewModule.isSafeLoadUrl("example.com"))
    }

    // ========== closeSession scoped snapshot cleanup (Fix 2) ==========

    @After
    fun tearDown() {
        unmockkAll()
        GeckoSecurityConfig.resetForTests()
    }

    @Test
    fun `getName returns the registered module name`() {
        assertEquals("GeckoViewModule", newModule().name)
    }

    private fun newModule(): GeckoViewModule {
        val ctx: ReactApplicationContext = mockk(relaxed = true)
        every { ctx.applicationContext } returns ctx
        return GeckoViewModule(ctx)
    }

    /**
     * Construct a module wired to a real on-disk `noBackupFilesDir` so
     * captureSnapshot's File.createTempFile + FileOutputStream don't try to
     * write to a mocked File. The directory is unique per test and lives in
     * the JUnit temp area.
     */
    private fun newModuleWithRealFilesDir(): Pair<GeckoViewModule, java.io.File> {
        val dir = java.io.File(
            System.getProperty("java.io.tmpdir"),
            "geckoview-test-${java.util.UUID.randomUUID()}",
        ).also { it.mkdirs() }
        val ctx: ReactApplicationContext = mockk(relaxed = true)
        every { ctx.applicationContext } returns ctx
        every { ctx.noBackupFilesDir } returns dir
        return GeckoViewModule(ctx) to dir
    }

    @Test
    fun `cleanupSnapshotsForSession deletes only the closed sessions files`() {
        val module = newModule()
        val tmp = java.io.File.createTempFile("snapshot-a", ".png").also { it.deleteOnExit() }
        val tmp2 = java.io.File.createTempFile("snapshot-b", ".png").also { it.deleteOnExit() }
        module.seedSnapshotForTest("session-a", tmp)
        module.seedSnapshotForTest("session-b", tmp2)

        module.cleanupSnapshotsForSessionForTest("session-a")

        // session-a's tracked files are gone from the in-memory index AND the disk.
        assertTrue(module.snapshotsForSessionForTest("session-a").isEmpty())
        assertFalse("session-a snapshot must be deleted from disk", tmp.exists())
        // session-b survives both the index and the disk check.
        assertEquals(setOf(tmp2), module.snapshotsForSessionForTest("session-b"))
        assertTrue("session-b snapshot must remain", tmp2.exists())
        // Cleanup
        tmp2.delete()
    }

    @Test
    fun `cleanupSnapshotsForSession is a no-op for an unknown session`() {
        val module = newModule()
        val tmp = java.io.File.createTempFile("snapshot-a", ".png").also { it.deleteOnExit() }
        module.seedSnapshotForTest("session-a", tmp)

        module.cleanupSnapshotsForSessionForTest("ghost-session")

        // session-a is untouched.
        assertEquals(setOf(tmp), module.snapshotsForSessionForTest("session-a"))
        assertTrue(tmp.exists())
        tmp.delete()
    }

    @Test
    fun `cleanupSnapshotsForSession can be called twice safely`() {
        val module = newModule()
        val tmp = java.io.File.createTempFile("snapshot-a", ".png").also { it.deleteOnExit() }
        module.seedSnapshotForTest("session-a", tmp)

        module.cleanupSnapshotsForSessionForTest("session-a")
        module.cleanupSnapshotsForSessionForTest("session-a") // second call must not throw

        assertTrue(module.snapshotsForSessionForTest("session-a").isEmpty())
    }

    // ========== captureSnapshot (Fixes 4 + 7) ==========
    //
    // captureSnapshot has four reject branches and one resolve branch:
    //   - VIEW_NOT_FOUND   (no GeckoView attached to the session)
    //   - SNAPSHOT_TOO_LARGE (view dimensions exceed MAX_SNAPSHOT_PIXELS)
    //   - SNAPSHOT_ERROR   (capturePixels reports null)
    //   - SNAPSHOT_ERROR   (capturePixels throws via the new error listener — Fix 7)
    //   - resolve(filePath) (success path; bitmap must be recycled — Fix 4)

    @Test
    fun `captureSnapshot rejects with VIEW_NOT_FOUND when no view is attached`() {
        mockkObject(GeckoSessionManager)
        every { GeckoSessionManager.getOrCreateSession(any()) } returns mockk(relaxed = true)
        every { GeckoSessionManager.getView(any()) } returns null

        val module = newModule()
        val promise: Promise = mockk(relaxed = true)

        module.captureSnapshot("session-a", promise)

        verify { promise.reject("VIEW_NOT_FOUND", any<String>()) }
        verify(exactly = 0) { promise.resolve(any()) }
    }

    @Test
    fun `captureSnapshot rejects with SNAPSHOT_TOO_LARGE when view exceeds MAX_SNAPSHOT_PIXELS`() {
        val view: GeckoView = mockk(relaxed = true)
        every { view.width } returns 5000
        every { view.height } returns 5000  // 25M > 16M cap

        mockkObject(GeckoSessionManager)
        every { GeckoSessionManager.getOrCreateSession(any()) } returns mockk(relaxed = true)
        every { GeckoSessionManager.getView(any()) } returns view

        val module = newModule()
        val promise: Promise = mockk(relaxed = true)

        module.captureSnapshot("session-a", promise)

        verify { promise.reject(eq("SNAPSHOT_TOO_LARGE"), any<String>()) }
        verify(exactly = 0) { view.capturePixels() } // must short-circuit before native capture
        verify(exactly = 0) { promise.resolve(any()) }
    }

    @Test
    fun `captureSnapshot rejects with SNAPSHOT_ERROR when capturePixels resolves to null`() {
        val view: GeckoView = mockk(relaxed = true)
        every { view.width } returns 800
        every { view.height } returns 600
        every { view.capturePixels() } returns GeckoResult.fromValue<android.graphics.Bitmap>(null)

        mockkObject(GeckoSessionManager)
        every { GeckoSessionManager.getOrCreateSession(any()) } returns mockk(relaxed = true)
        every { GeckoSessionManager.getView(any()) } returns view

        val module = newModule()
        val promise: Promise = mockk(relaxed = true)

        module.captureSnapshot("session-a", promise)
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()

        verify { promise.reject(eq("SNAPSHOT_ERROR"), any<String>()) }
        verify(exactly = 0) { promise.resolve(any()) }
    }

    @Test
    fun `captureSnapshot rejects with SNAPSHOT_ERROR when capturePixels throws (Fix 7)`() {
        val view: GeckoView = mockk(relaxed = true)
        every { view.width } returns 800
        every { view.height } returns 600
        every { view.capturePixels() } returns
            GeckoResult.fromException(RuntimeException("capturePixels failed: GPU crashed"))

        mockkObject(GeckoSessionManager)
        every { GeckoSessionManager.getOrCreateSession(any()) } returns mockk(relaxed = true)
        every { GeckoSessionManager.getView(any()) } returns view

        val module = newModule()
        val promise: Promise = mockk(relaxed = true)

        module.captureSnapshot("session-a", promise)
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()

        // The OnExceptionListener path forwards the message through promise.reject(code, message, throwable).
        verify {
            promise.reject(
                eq("SNAPSHOT_ERROR"),
                eq("capturePixels failed: GPU crashed"),
                any<Throwable>(),
            )
        }
        verify(exactly = 0) { promise.resolve(any()) }
    }

    @Test
    fun `captureSnapshot recycles the bitmap on the success path (Fix 4)`() {
        val (module, dir) = newModuleWithRealFilesDir()
        val bitmap = android.graphics.Bitmap.createBitmap(
            64, 64, android.graphics.Bitmap.Config.ARGB_8888
        )

        val view: GeckoView = mockk(relaxed = true)
        every { view.width } returns 64
        every { view.height } returns 64
        every { view.capturePixels() } returns GeckoResult.fromValue(bitmap)

        mockkObject(GeckoSessionManager)
        every { GeckoSessionManager.getOrCreateSession(any()) } returns mockk(relaxed = true)
        every { GeckoSessionManager.getView(any()) } returns view

        val promise: Promise = mockk(relaxed = true)
        module.captureSnapshot("session-a", promise)
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()

        assertTrue("bitmap.recycle() must run on the success path", bitmap.isRecycled)
        verify { promise.resolve(any<String>()) }
        verify(exactly = 0) { promise.reject(any<String>(), any<String>()) }

        // Ensure the success path recorded the file under the closing session,
        // and that closeSession then deletes it.
        val tracked = module.snapshotsForSessionForTest("session-a")
        assertEquals(1, tracked.size)
        assertTrue("Snapshot file must exist on disk", tracked.first().exists())

        module.cleanupSnapshotsForSessionForTest("session-a")
        assertFalse("Snapshot file must be deleted after closeSession", tracked.first().exists())

        dir.deleteRecursively()
    }

    @Test
    fun `captureSnapshot tracks files by sessionKey (Fix 2 integration)`() {
        val (module, dir) = newModuleWithRealFilesDir()
        val bitmapA = android.graphics.Bitmap.createBitmap(
            32, 32, android.graphics.Bitmap.Config.ARGB_8888
        )
        val bitmapB = android.graphics.Bitmap.createBitmap(
            32, 32, android.graphics.Bitmap.Config.ARGB_8888
        )

        val view: GeckoView = mockk(relaxed = true)
        every { view.width } returns 32
        every { view.height } returns 32
        every { view.capturePixels() } returnsMany listOf(
            GeckoResult.fromValue(bitmapA),
            GeckoResult.fromValue(bitmapB),
        )

        mockkObject(GeckoSessionManager)
        every { GeckoSessionManager.getOrCreateSession(any()) } returns mockk(relaxed = true)
        every { GeckoSessionManager.getView(any()) } returns view

        val promise: Promise = mockk(relaxed = true)
        module.captureSnapshot("session-a", promise)
        module.captureSnapshot("session-b", promise)
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()

        val a = module.snapshotsForSessionForTest("session-a")
        val b = module.snapshotsForSessionForTest("session-b")
        assertEquals(1, a.size)
        assertEquals(1, b.size)
        assertTrue("Per-session sets must not overlap", a.intersect(b).isEmpty())

        // Closing only session-a must leave session-b's snapshot intact (Fix 2).
        module.cleanupSnapshotsForSessionForTest("session-a")
        assertFalse(a.first().exists())
        assertTrue("session-b snapshot must survive session-a close", b.first().exists())

        dir.deleteRecursively()
    }

    @Test
    fun `captureSnapshot evicts stale snapshot files and prunes them from the index`() {
        val (module, dir) = newModuleWithRealFilesDir()
        // The capture writes into <noBackupFilesDir>/geckoview_snapshots; pre-seed
        // that directory with a stale PNG (mtime well past SNAPSHOT_TTL_MS) and a
        // fresh one. evictStaleSnapshots must delete only the stale file.
        val snapDir = java.io.File(dir, "geckoview_snapshots").also { it.mkdirs() }
        val stale = java.io.File(snapDir, "stale.png").also {
            it.writeBytes(ByteArray(8))
            // 10 minutes ago — older than the 5-minute TTL.
            it.setLastModified(System.currentTimeMillis() - 10L * 60L * 1000L)
        }
        val fresh = java.io.File(snapDir, "fresh.png").also {
            it.writeBytes(ByteArray(8))
            it.setLastModified(System.currentTimeMillis())
        }
        // Track the stale file in the in-memory index so the prune path (L450-454)
        // is exercised, not just the on-disk delete.
        module.seedSnapshotForTest("session-a", stale)

        val bitmap = android.graphics.Bitmap.createBitmap(
            16, 16, android.graphics.Bitmap.Config.ARGB_8888
        )
        val view: GeckoView = mockk(relaxed = true)
        every { view.width } returns 16
        every { view.height } returns 16
        every { view.capturePixels() } returns GeckoResult.fromValue(bitmap)

        mockkObject(GeckoSessionManager)
        every { GeckoSessionManager.getOrCreateSession(any()) } returns mockk(relaxed = true)
        every { GeckoSessionManager.getView(any()) } returns view

        val promise: Promise = mockk(relaxed = true)
        module.captureSnapshot("session-a", promise)
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()

        assertFalse("stale snapshot must be deleted", stale.exists())
        assertTrue("fresh snapshot must survive eviction", fresh.exists())
        // The evicted file must no longer be tracked for the session.
        assertFalse(
            "evicted file must be pruned from the in-memory index",
            module.snapshotsForSessionForTest("session-a").contains(stale),
        )
        verify { promise.resolve(any<String>()) }

        dir.deleteRecursively()
    }

    // ========== schedulePromptTimeout (Fix 6) ==========
    //
    // The timeout fires after PROMPT_TIMEOUT_MS (60s). Robolectric's idleable
    // looper advances all pending tasks regardless of delay, so we don't need
    // to wait real time.

    @Test
    fun `schedulePromptTimeout dismisses an AlertPrompt and completes its result`() {
        val response: GeckoSession.PromptDelegate.PromptResponse = mockk(relaxed = true)
        val alertPrompt: GeckoSession.PromptDelegate.AlertPrompt = mockk(relaxed = true)
        every { alertPrompt.dismiss() } returns response
        val result: GeckoResult<Any> = mockk(relaxed = true)

        val module = newModule()
        @Suppress("UNCHECKED_CAST")
        module.storePendingPrompt("p-alert", alertPrompt, result)
        Shadows.shadowOf(android.os.Looper.getMainLooper())
            .idleFor(java.time.Duration.ofMillis(PROMPT_TIMEOUT_BUFFER_MS))

        verify { alertPrompt.dismiss() }
        verify { result.complete(response) }
    }

    @Test
    fun `schedulePromptTimeout dismisses a ButtonPrompt and completes its result`() {
        val response: GeckoSession.PromptDelegate.PromptResponse = mockk(relaxed = true)
        val buttonPrompt: GeckoSession.PromptDelegate.ButtonPrompt = mockk(relaxed = true)
        every { buttonPrompt.dismiss() } returns response
        val result: GeckoResult<Any> = mockk(relaxed = true)

        val module = newModule()
        module.storePendingPrompt("p-button", buttonPrompt, result)
        Shadows.shadowOf(android.os.Looper.getMainLooper())
            .idleFor(java.time.Duration.ofMillis(PROMPT_TIMEOUT_BUFFER_MS))

        verify { buttonPrompt.dismiss() }
        verify { result.complete(response) }
    }

    @Test
    fun `schedulePromptTimeout dismisses a TextPrompt and completes its result`() {
        val response: GeckoSession.PromptDelegate.PromptResponse = mockk(relaxed = true)
        val textPrompt: GeckoSession.PromptDelegate.TextPrompt = mockk(relaxed = true)
        every { textPrompt.dismiss() } returns response
        val result: GeckoResult<Any> = mockk(relaxed = true)

        val module = newModule()
        module.storePendingPrompt("p-text", textPrompt, result)
        Shadows.shadowOf(android.os.Looper.getMainLooper())
            .idleFor(java.time.Duration.ofMillis(PROMPT_TIMEOUT_BUFFER_MS))

        verify { textPrompt.dismiss() }
        verify { result.complete(response) }
    }

    @Test
    fun `schedulePromptTimeout still respects PopupPromptWrapper`() {
        val response: GeckoSession.PromptDelegate.PromptResponse = mockk(relaxed = true)
        val wrapper: PopupPromptWrapper = mockk(relaxed = true)
        every { wrapper.respond(false) } returns response
        val result: GeckoResult<Any> = mockk(relaxed = true)

        val module = newModule()
        module.storePendingPrompt("p-popup", wrapper, result)
        Shadows.shadowOf(android.os.Looper.getMainLooper())
            .idleFor(java.time.Duration.ofMillis(PROMPT_TIMEOUT_BUFFER_MS))

        verify { wrapper.respond(false) }
        verify { result.complete(response) }
    }

    @Test
    fun `schedulePromptTimeout still respects BeforeUnloadPromptWrapper`() {
        val response: GeckoSession.PromptDelegate.PromptResponse = mockk(relaxed = true)
        val wrapper: BeforeUnloadPromptWrapper = mockk(relaxed = true)
        every { wrapper.confirm(false) } returns response
        val result: GeckoResult<Any> = mockk(relaxed = true)

        val module = newModule()
        module.storePendingPrompt("p-bu", wrapper, result)
        Shadows.shadowOf(android.os.Looper.getMainLooper())
            .idleFor(java.time.Duration.ofMillis(PROMPT_TIMEOUT_BUFFER_MS))

        verify { wrapper.confirm(false) }
        verify { result.complete(response) }
    }

    @Test
    fun `schedulePromptTimeout still respects AuthPromptWrapper`() {
        val response: GeckoSession.PromptDelegate.PromptResponse = mockk(relaxed = true)
        val wrapper: AuthPromptWrapper = mockk(relaxed = true)
        every { wrapper.dismiss() } returns response
        val result: GeckoResult<Any> = mockk(relaxed = true)

        val module = newModule()
        module.storePendingPrompt("p-auth", wrapper, result)
        Shadows.shadowOf(android.os.Looper.getMainLooper())
            .idleFor(java.time.Duration.ofMillis(PROMPT_TIMEOUT_BUFFER_MS))

        verify { wrapper.dismiss() }
        verify { result.complete(response) }
    }

    @Test
    fun `schedulePromptTimeout logs and skips an unrecognized prompt type`() {
        // A prompt that is neither a wrapper nor a BasePrompt hits the `else`
        // branch (L408-410): it is logged and the GeckoResult is left
        // uncompleted rather than crashing the timeout runnable.
        val result: GeckoResult<Any> = mockk(relaxed = true)

        val module = newModule()
        module.storePendingPrompt("p-unknown", Any(), result)
        Shadows.shadowOf(android.os.Looper.getMainLooper())
            .idleFor(java.time.Duration.ofMillis(PROMPT_TIMEOUT_BUFFER_MS))

        verify(exactly = 0) { result.complete(any()) }
    }

    @Test
    fun `schedulePromptTimeout dismisses a bare BasePrompt`() {
        // Alert/Button/Text prompts are stored unwrapped; on timeout the
        // `is BasePrompt` arm dismisses them (regression guard for the prior
        // `else -> null` leak that hung the UI prompt).
        val response: GeckoSession.PromptDelegate.PromptResponse = mockk(relaxed = true)
        val prompt: GeckoSession.PromptDelegate.BasePrompt = mockk(relaxed = true)
        every { prompt.dismiss() } returns response
        val result: GeckoResult<Any> = mockk(relaxed = true)

        val module = newModule()
        module.storePendingPrompt("p-base", prompt, result)
        Shadows.shadowOf(android.os.Looper.getMainLooper())
            .idleFor(java.time.Duration.ofMillis(PROMPT_TIMEOUT_BUFFER_MS))

        verify { prompt.dismiss() }
        verify { result.complete(response) }
    }

    // ========== schedulePermissionTimeout (timeout -> VALUE_DENY) ==========

    @Test
    fun `schedulePermissionTimeout denies the pending permission after the deadline`() {
        val result: GeckoResult<Int> = mockk(relaxed = true)

        val module = newModule()
        module.storePendingPermission("perm-timeout", result)
        Shadows.shadowOf(android.os.Looper.getMainLooper())
            .idleFor(java.time.Duration.ofMillis(PROMPT_TIMEOUT_BUFFER_MS))

        verify {
            result.complete(GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY)
        }
    }

    @Test
    fun `resolvePermission before the deadline cancels the deny timeout`() {
        val result: GeckoResult<Int> = mockk(relaxed = true)

        val module = newModule()
        module.storePendingPermission("perm-fast", result)
        // Resolve first; this must cancel the scheduled timeout runnable.
        module.resolvePermission("perm-fast", allow = true)
        Shadows.shadowOf(android.os.Looper.getMainLooper())
            .idleFor(java.time.Duration.ofMillis(PROMPT_TIMEOUT_BUFFER_MS))

        // ALLOW completes exactly once; the timeout must NOT also fire VALUE_DENY.
        verify(exactly = 1) {
            result.complete(GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW)
        }
        verify(exactly = 0) {
            result.complete(GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY)
        }
    }

    @Test
    fun `schedulePromptTimeout swallows exceptions from dismiss to keep the timeout drained`() {
        val alertPrompt: GeckoSession.PromptDelegate.AlertPrompt = mockk(relaxed = true)
        every { alertPrompt.dismiss() } throws RuntimeException("dismiss boom")
        val result: GeckoResult<Any> = mockk(relaxed = true)

        val module = newModule()
        // Must not propagate the exception; the timeout runnable swallows it.
        module.storePendingPrompt("p-alert-throws", alertPrompt, result)
        Shadows.shadowOf(android.os.Looper.getMainLooper())
            .idleFor(java.time.Duration.ofMillis(PROMPT_TIMEOUT_BUFFER_MS))

        verify { alertPrompt.dismiss() }
        verify(exactly = 0) { result.complete(any()) }
    }

    // ========== resolvePrompt — auth password channel ==========
    //
    // resolvePrompt(promptId, confirm, text?, username?, password?) routes auth
    // confirmations through `password ?: text`. The explicit `password` arg wins
    // when present; older callers that passed the secret positionally as `text`
    // still work via the fallback.

    @Test
    fun `resolvePrompt auth uses explicit password channel when present`() {
        val response: GeckoSession.PromptDelegate.PromptResponse = mockk(relaxed = true)
        val wrapper: AuthPromptWrapper = mockk(relaxed = true)
        every { wrapper.confirm(any(), any()) } returns response
        val result: GeckoResult<Any> = mockk(relaxed = true)

        val module = newModule()
        module.storePendingPrompt("p-auth", wrapper, result)
        module.resolvePrompt("p-auth", confirm = true, text = "fromText", username = "alice", password = "secret")

        verify { wrapper.confirm("alice", "secret") }
        verify(exactly = 0) { wrapper.confirm("alice", "fromText") }
        verify { result.complete(response) }
    }

    @Test
    fun `resolvePrompt auth falls back to text when password is null`() {
        val response: GeckoSession.PromptDelegate.PromptResponse = mockk(relaxed = true)
        val wrapper: AuthPromptWrapper = mockk(relaxed = true)
        every { wrapper.confirm(any(), any()) } returns response
        val result: GeckoResult<Any> = mockk(relaxed = true)

        val module = newModule()
        module.storePendingPrompt("p-auth", wrapper, result)
        module.resolvePrompt("p-auth", confirm = true, text = "positionalPw", username = "bob", password = null)

        verify { wrapper.confirm("bob", "positionalPw") }
    }

    @Test
    fun `resolvePrompt auth dismisses when not confirmed`() {
        val response: GeckoSession.PromptDelegate.PromptResponse = mockk(relaxed = true)
        val wrapper: AuthPromptWrapper = mockk(relaxed = true)
        every { wrapper.dismiss() } returns response
        val result: GeckoResult<Any> = mockk(relaxed = true)

        val module = newModule()
        module.storePendingPrompt("p-auth", wrapper, result)
        module.resolvePrompt("p-auth", confirm = false, text = null, username = "bob", password = "secret")

        verify { wrapper.dismiss() }
        verify(exactly = 0) { wrapper.confirm(any(), any()) }
    }

    // resolvePrompt routes Alert/Button/Text prompts through reflective
    // confirm()/dismiss() lookups on the real GeckoView prompt class. With a
    // mocked prompt the declared method isn't reachable, so the lookup throws
    // and the outer catch swallows it — exercising the branch entry + catch
    // (L135-156, L177-178) without a live prompt instance.

    @Test
    fun `resolvePrompt swallows reflection failure on a mocked AlertPrompt`() {
        val prompt: GeckoSession.PromptDelegate.AlertPrompt = mockk(relaxed = true)
        val result: GeckoResult<Any> = mockk(relaxed = true)

        val module = newModule()
        module.storePendingPrompt("p-alert", prompt, result)
        // Must not throw; the GeckoResult is left uncompleted because the
        // reflective confirm() lookup fails on the mock.
        module.resolvePrompt("p-alert", confirm = true, text = null, username = null, password = null)

        verify(exactly = 0) { result.complete(any()) }
    }

    @Test
    fun `resolvePrompt swallows reflection failure on a mocked ButtonPrompt for both confirm states`() {
        val module = newModule()
        val resultPos: GeckoResult<Any> = mockk(relaxed = true)
        val resultNeg: GeckoResult<Any> = mockk(relaxed = true)

        module.storePendingPrompt("p-btn-pos", mockk<GeckoSession.PromptDelegate.ButtonPrompt>(relaxed = true), resultPos)
        module.resolvePrompt("p-btn-pos", confirm = true, text = null, username = null, password = null)

        module.storePendingPrompt("p-btn-neg", mockk<GeckoSession.PromptDelegate.ButtonPrompt>(relaxed = true), resultNeg)
        module.resolvePrompt("p-btn-neg", confirm = false, text = null, username = null, password = null)

        verify(exactly = 0) { resultPos.complete(any()) }
        verify(exactly = 0) { resultNeg.complete(any()) }
    }

    @Test
    fun `resolvePrompt confirms a TextPrompt via the reflective confirm(String) path`() {
        // MockK generates an override for the open confirm(String) on the
        // TextPrompt subclass, so the reflective lookup + invoke succeeds and the
        // relaxed PromptResponse is completed. Exercises L147-151 + L176.
        val module = newModule()
        val result: GeckoResult<Any> = mockk(relaxed = true)

        module.storePendingPrompt("p-text-c", mockk<GeckoSession.PromptDelegate.TextPrompt>(relaxed = true), result)
        module.resolvePrompt("p-text-c", confirm = true, text = "answer", username = null, password = null)

        verify(exactly = 1) { result.complete(any()) }
    }

    @Test
    fun `resolvePrompt swallows reflection failure on a TextPrompt dismiss path`() {
        // confirm=false routes to the reflective dismiss() lookup, which is not
        // reachable on the mock -> the outer catch swallows it (L152-156, L177).
        val module = newModule()
        val result: GeckoResult<Any> = mockk(relaxed = true)

        module.storePendingPrompt("p-text-d", mockk<GeckoSession.PromptDelegate.TextPrompt>(relaxed = true), result)
        module.resolvePrompt("p-text-d", confirm = false, text = null, username = null, password = null)

        verify(exactly = 0) { result.complete(any()) }
    }

    @Test
    fun `resolvePrompt routes a PopupPromptWrapper through respond`() {
        val response: GeckoSession.PromptDelegate.PromptResponse = mockk(relaxed = true)
        val wrapper: PopupPromptWrapper = mockk(relaxed = true)
        every { wrapper.respond(true) } returns response
        val result: GeckoResult<Any> = mockk(relaxed = true)

        val module = newModule()
        module.storePendingPrompt("p-popup", wrapper, result)
        module.resolvePrompt("p-popup", confirm = true, text = null, username = null, password = null)

        verify { wrapper.respond(true) }
        verify { result.complete(response) }
    }

    @Test
    fun `resolvePrompt routes a BeforeUnloadPromptWrapper through confirm`() {
        val response: GeckoSession.PromptDelegate.PromptResponse = mockk(relaxed = true)
        val wrapper: BeforeUnloadPromptWrapper = mockk(relaxed = true)
        every { wrapper.confirm(true) } returns response
        val result: GeckoResult<Any> = mockk(relaxed = true)

        val module = newModule()
        module.storePendingPrompt("p-bu", wrapper, result)
        module.resolvePrompt("p-bu", confirm = true, text = null, username = null, password = null)

        verify { wrapper.confirm(true) }
        verify { result.complete(response) }
    }

    @Test
    fun `resolvePrompt is a no-op for an unknown promptId`() {
        val module = newModule()
        // No stored prompt -> remove() returns null -> nothing happens, no throw.
        module.resolvePrompt("ghost-prompt", confirm = true, text = null, username = null, password = null)
    }

    @Test
    fun `resolvePrompt auth dismisses when username missing`() {
        val response: GeckoSession.PromptDelegate.PromptResponse = mockk(relaxed = true)
        val wrapper: AuthPromptWrapper = mockk(relaxed = true)
        every { wrapper.dismiss() } returns response
        val result: GeckoResult<Any> = mockk(relaxed = true)

        val module = newModule()
        module.storePendingPrompt("p-auth", wrapper, result)
        // confirm=true but no username -> cannot authenticate -> dismiss.
        module.resolvePrompt("p-auth", confirm = true, text = null, username = null, password = "secret")

        verify { wrapper.dismiss() }
        verify(exactly = 0) { wrapper.confirm(any(), any()) }
    }

    // ========== configureSecurity — option parsing ==========

    @Test
    fun `configureSecurity parses cookieBehavior and maxSnapshotPixels into Options`() {
        mockkObject(GeckoRuntimeManager)
        val captured = slot<GeckoSecurityConfig.Options>()
        every { GeckoRuntimeManager.configureSecurity(capture(captured)) } returns true

        val module = newModule()
        val opts = JavaOnlyMap().apply {
            putBoolean("allowDrm", true)
            putBoolean("allowAutoplay", true)
            putBoolean("enableConsoleOutput", true)
            putInt("cookieBehavior", ContentBlocking.CookieBehavior.ACCEPT_NONE)
            // ReadableMap has no putLong; doubles round-trip pixel counts.
            putDouble("maxSnapshotPixels", (1024.0 * 1024.0))
        }
        val promise: Promise = mockk(relaxed = true)

        module.configureSecurity(opts, promise)

        assertEquals(ContentBlocking.CookieBehavior.ACCEPT_NONE, captured.captured.cookieBehavior)
        assertEquals(1024L * 1024L, captured.captured.maxSnapshotPixels)
        assertTrue(captured.captured.allowDrm)
        assertTrue(captured.captured.allowAutoplay)
        assertTrue(captured.captured.enableConsoleOutput)
        verify { promise.resolve(null) }
    }

    @Test
    fun `configureSecurity applies conservative defaults when keys absent`() {
        mockkObject(GeckoRuntimeManager)
        val captured = slot<GeckoSecurityConfig.Options>()
        every { GeckoRuntimeManager.configureSecurity(capture(captured)) } returns true

        val module = newModule()
        val promise: Promise = mockk(relaxed = true)

        module.configureSecurity(JavaOnlyMap(), promise)

        assertEquals(
            ContentBlocking.CookieBehavior.ACCEPT_NON_TRACKERS,
            captured.captured.cookieBehavior,
        )
        assertEquals(4096L * 4096L, captured.captured.maxSnapshotPixels)
        assertFalse(captured.captured.allowDrm)
        assertFalse(captured.captured.allowAutoplay)
        assertFalse(captured.captured.enableConsoleOutput)
    }

    @Test
    fun `configureSecurity rejects when runtime already initialized`() {
        mockkObject(GeckoRuntimeManager)
        every { GeckoRuntimeManager.configureSecurity(any()) } returns false

        val module = newModule()
        val promise: Promise = mockk(relaxed = true)

        module.configureSecurity(JavaOnlyMap(), promise)

        verify { promise.reject("GECKO_RUNTIME_ALREADY_INITIALIZED", any<String>()) }
        verify(exactly = 0) { promise.resolve(any()) }
    }

    // ========== evaluateJavaScript — bridge connect-race retry ==========
    //
    // tryEvaluateJavaScript retries up to EVAL_BRIDGE_MAX_RETRIES (20 × 100ms)
    // for the trusted bridge port to appear, then rejects EVAL_BRIDGE_UNAVAILABLE.

    @Test
    fun `evaluateJavaScript rejects EVAL_BRIDGE_UNAVAILABLE after exhausting retries`() {
        mockkObject(GeckoSessionManager)
        every { GeckoSessionManager.getOrCreateSession(any()) } returns mockk(relaxed = true)
        mockkObject(GeckoRuntimeManager)
        // No controller at all -> every attempt fails to queue.
        every { GeckoRuntimeManager.getWebExtensionController() } returns null

        val module = newModule()
        val promise: Promise = mockk(relaxed = true)

        module.evaluateJavaScript("session-a", "1+1", promise)
        // Drain all 20 postDelayed retries (20 × 100ms); add a buffer so the
        // simulated looper crosses the final retry deadline.
        Shadows.shadowOf(android.os.Looper.getMainLooper())
            .idleFor(java.time.Duration.ofMillis(EVAL_RETRY_DRAIN_MS))

        verify { promise.reject("EVAL_BRIDGE_UNAVAILABLE", any<String>()) }
        verify(exactly = 0) { promise.resolve(any()) }
        // The retry loop must have polled the controller more than once.
        verify(atLeast = 2) { GeckoRuntimeManager.getWebExtensionController() }
    }

    @Test
    fun `evaluateJavaScript stops retrying once the bridge accepts the request`() {
        mockkObject(GeckoSessionManager)
        every { GeckoSessionManager.getOrCreateSession(any()) } returns mockk(relaxed = true)
        mockkObject(GeckoRuntimeManager)
        val controller: WebExtensionController = mockk(relaxed = true)
        // First poll: no port yet (false). Second poll: queued (true) -> the
        // bridge now owns the promise, so the module must NOT reject.
        every { controller.evaluateJavaScript(any(), any(), any()) } returnsMany listOf(false, true)
        every { GeckoRuntimeManager.getWebExtensionController() } returns controller

        val module = newModule()
        val promise: Promise = mockk(relaxed = true)

        module.evaluateJavaScript("session-a", "1+1", promise)
        Shadows.shadowOf(android.os.Looper.getMainLooper())
            .idleFor(java.time.Duration.ofMillis(EVAL_RETRY_DRAIN_MS))

        verify(exactly = 0) { promise.reject(any<String>(), any<String>()) }
        verify(exactly = 0) { promise.reject(any<String>(), any<String>(), any<Throwable>()) }
        // Exactly two queue attempts: the failed one and the accepted one.
        verify(exactly = 2) { controller.evaluateJavaScript("session-a", "1+1", promise) }
    }

    // ========== @ReactMethod navigation passthroughs ==========

    @Test
    fun `reload forwards to the session`() {
        mockkObject(GeckoSessionManager)
        val session = TestHelpers.createMockGeckoSession()
        every { GeckoSessionManager.getOrCreateSession("s") } returns session

        newModule().reload("s")

        verify { session.reload() }
    }

    @Test
    fun `goBack forwards to the session`() {
        mockkObject(GeckoSessionManager)
        val session = TestHelpers.createMockGeckoSession()
        every { GeckoSessionManager.getOrCreateSession("s") } returns session

        newModule().goBack("s")

        verify { session.goBack() }
    }

    @Test
    fun `goForward forwards to the session`() {
        mockkObject(GeckoSessionManager)
        val session = TestHelpers.createMockGeckoSession()
        every { GeckoSessionManager.getOrCreateSession("s") } returns session

        newModule().goForward("s")

        verify { session.goForward() }
    }

    @Test
    fun `stop forwards to the session`() {
        mockkObject(GeckoSessionManager)
        val session = TestHelpers.createMockGeckoSession()
        every { GeckoSessionManager.getOrCreateSession("s") } returns session

        newModule().stop("s")

        verify { session.stop() }
    }

    @Test
    fun `loadUrl loads a safe http url`() {
        mockkObject(GeckoSessionManager)
        val session = TestHelpers.createMockGeckoSession()
        every { GeckoSessionManager.getOrCreateSession("s") } returns session

        newModule().loadUrl("s", "https://example.com")

        verify { session.loadUri("https://example.com") }
    }

    @Test
    fun `loadUrl rejects a javascript url without touching the session`() {
        mockkObject(GeckoSessionManager)
        val session = TestHelpers.createMockGeckoSession()
        every { GeckoSessionManager.getOrCreateSession(any()) } returns session

        newModule().loadUrl("s", "javascript:alert(document.cookie)")

        verify(exactly = 0) { session.loadUri(any<String>()) }
        // A disallowed scheme must not even resolve a session.
        verify(exactly = 0) { GeckoSessionManager.getOrCreateSession(any()) }
    }

    @Test
    fun `setUserAgent with empty string resets to mobile mode`() {
        mockkObject(GeckoSessionManager)
        val session = TestHelpers.createMockGeckoSession()
        val settings = mockk<org.mozilla.geckoview.GeckoSessionSettings>(relaxed = true)
        every { session.settings } returns settings
        every { GeckoSessionManager.getOrCreateSession("s") } returns session

        newModule().setUserAgent("s", "")

        verify { settings.userAgentMode = org.mozilla.geckoview.GeckoSessionSettings.USER_AGENT_MODE_MOBILE }
        verify { settings.userAgentOverride = null }
    }

    @Test
    fun `setUserAgent with a value switches to desktop override`() {
        mockkObject(GeckoSessionManager)
        val session = TestHelpers.createMockGeckoSession()
        val settings = mockk<org.mozilla.geckoview.GeckoSessionSettings>(relaxed = true)
        every { session.settings } returns settings
        every { GeckoSessionManager.getOrCreateSession("s") } returns session

        newModule().setUserAgent("s", "CustomUA/1.0")

        verify { settings.userAgentMode = org.mozilla.geckoview.GeckoSessionSettings.USER_AGENT_MODE_DESKTOP }
        verify { settings.userAgentOverride = "CustomUA/1.0" }
    }

    // ========== resolvePermission ==========

    @Test
    fun `resolvePermission allow completes with VALUE_ALLOW`() {
        val module = newModule()
        val result: GeckoResult<Int> = mockk(relaxed = true)
        module.storePendingPermission("perm-1", result)

        module.resolvePermission("perm-1", allow = true)

        verify {
            result.complete(GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW)
        }
    }

    @Test
    fun `resolvePermission deny completes with VALUE_DENY`() {
        val module = newModule()
        val result: GeckoResult<Int> = mockk(relaxed = true)
        module.storePendingPermission("perm-1", result)

        module.resolvePermission("perm-1", allow = false)

        verify {
            result.complete(GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY)
        }
    }

    @Test
    fun `resolvePermission for an unknown id is a no-op`() {
        val module = newModule()
        val result: GeckoResult<Int> = mockk(relaxed = true)
        module.storePendingPermission("perm-1", result)

        module.resolvePermission("ghost", allow = true)

        verify(exactly = 0) { result.complete(any()) }
    }

    // ========== setEnhancedTrackingProtectionLevel / setCookieBannerMode ==========

    @Test
    fun `setEnhancedTrackingProtectionLevel resolves on a valid level`() {
        mockkObject(GeckoRuntimeManager)
        every { GeckoRuntimeManager.getRuntime() } returns mockk(relaxed = true)

        val promise: Promise = mockk(relaxed = true)
        newModule().setEnhancedTrackingProtectionLevel(2, promise)

        verify { promise.resolve(null) }
    }

    @Test
    fun `setEnhancedTrackingProtectionLevel rejects when runtime is uninitialized`() {
        mockkObject(GeckoRuntimeManager)
        every { GeckoRuntimeManager.getRuntime() } returns null

        val promise: Promise = mockk(relaxed = true)
        newModule().setEnhancedTrackingProtectionLevel(2, promise)

        verify { promise.reject("GECKO_RUNTIME", any<String>()) }
        verify(exactly = 0) { promise.resolve(any()) }
    }

    @Test
    fun `setEnhancedTrackingProtectionLevel rejects an out-of-range level as INVALID_ETP_LEVEL`() {
        mockkObject(GeckoRuntimeManager)
        every { GeckoRuntimeManager.getRuntime() } returns mockk(relaxed = true)

        val promise: Promise = mockk(relaxed = true)
        newModule().setEnhancedTrackingProtectionLevel(99, promise)

        verify { promise.reject("INVALID_ETP_LEVEL", any<String>()) }
    }

    @Test
    fun `setEnhancedTrackingProtectionLevel rejects ETP_SET_FAILED when the underlying call throws`() {
        mockkObject(GeckoRuntimeManager)
        val runtime = mockk<org.mozilla.geckoview.GeckoRuntime>(relaxed = true)
        val rtSettings = mockk<org.mozilla.geckoview.GeckoRuntimeSettings>(relaxed = true)
        val cb = mockk<org.mozilla.geckoview.ContentBlocking.Settings>(relaxed = true)
        every { runtime.settings } returns rtSettings
        every { rtSettings.contentBlocking } returns cb
        // A non-IllegalArgumentException must fall through to the generic catch.
        every { cb.setEnhancedTrackingProtectionLevel(any()) } throws RuntimeException("native boom")
        every { GeckoRuntimeManager.getRuntime() } returns runtime

        val promise: Promise = mockk(relaxed = true)
        newModule().setEnhancedTrackingProtectionLevel(2, promise)

        verify { promise.reject("ETP_SET_FAILED", any<String>()) }
        verify(exactly = 0) { promise.resolve(any()) }
    }

    @Test
    fun `setCookieBannerMode rejects COOKIE_BANNER_SET_FAILED when the underlying call throws`() {
        mockkObject(GeckoRuntimeManager)
        val runtime = mockk<org.mozilla.geckoview.GeckoRuntime>(relaxed = true)
        val rtSettings = mockk<org.mozilla.geckoview.GeckoRuntimeSettings>(relaxed = true)
        val cb = mockk<org.mozilla.geckoview.ContentBlocking.Settings>(relaxed = true)
        every { runtime.settings } returns rtSettings
        every { rtSettings.contentBlocking } returns cb
        // ContentBlockingController re-throws non-IAE failures (e.g. GeckoView < 108).
        every { cb.setCookieBannerMode(any()) } throws RuntimeException("unsupported on this build")
        every { GeckoRuntimeManager.getRuntime() } returns runtime

        val promise: Promise = mockk(relaxed = true)
        newModule().setCookieBannerMode(1, promise)

        verify { promise.reject("COOKIE_BANNER_SET_FAILED", any<String>()) }
        verify(exactly = 0) { promise.resolve(any()) }
    }

    @Test
    fun `setCookieBannerMode resolves on a valid mode`() {
        mockkObject(GeckoRuntimeManager)
        every { GeckoRuntimeManager.getRuntime() } returns mockk(relaxed = true)

        val promise: Promise = mockk(relaxed = true)
        newModule().setCookieBannerMode(1, promise)

        verify { promise.resolve(null) }
    }

    @Test
    fun `setCookieBannerMode rejects when runtime is uninitialized`() {
        mockkObject(GeckoRuntimeManager)
        every { GeckoRuntimeManager.getRuntime() } returns null

        val promise: Promise = mockk(relaxed = true)
        newModule().setCookieBannerMode(1, promise)

        verify { promise.reject("GECKO_RUNTIME", any<String>()) }
    }

    @Test
    fun `setCookieBannerMode rejects an out-of-range mode as INVALID_COOKIE_BANNER_MODE`() {
        mockkObject(GeckoRuntimeManager)
        every { GeckoRuntimeManager.getRuntime() } returns mockk(relaxed = true)

        val promise: Promise = mockk(relaxed = true)
        newModule().setCookieBannerMode(42, promise)

        verify { promise.reject("INVALID_COOKIE_BANNER_MODE", any<String>()) }
    }

    // ========== sendWebExtensionMessage ==========

    @Test
    fun `sendWebExtensionMessage resolves when the port accepts the message`() {
        mockkObject(GeckoRuntimeManager)
        val controller: WebExtensionController = mockk(relaxed = true)
        every { controller.sendMessage("s", "ext@x", "{}") } returns true
        every { GeckoRuntimeManager.getWebExtensionController() } returns controller

        val promise: Promise = mockk(relaxed = true)
        newModule().sendWebExtensionMessage("s", "ext@x", "{}", promise)

        verify { promise.resolve(null) }
    }

    @Test
    fun `sendWebExtensionMessage rejects when no port is connected`() {
        mockkObject(GeckoRuntimeManager)
        every { GeckoRuntimeManager.getWebExtensionController() } returns null

        val promise: Promise = mockk(relaxed = true)
        newModule().sendWebExtensionMessage("s", "ext@x", "{}", promise)

        verify { promise.reject("WEB_EXTENSION_PORT_UNAVAILABLE", any<String>()) }
        verify(exactly = 0) { promise.resolve(any()) }
    }

    // ========== installWebExtension ==========

    @Test
    fun `installWebExtension resolves on a successful install`() {
        mockkObject(GeckoRuntimeManager)
        val controller: WebExtensionController = mockk(relaxed = true)
        every { GeckoRuntimeManager.getRuntime(any()) } returns mockk(relaxed = true)
        every { GeckoRuntimeManager.getWebExtensionController() } returns controller
        every { controller.installExtension(any(), any()) } answers {
            secondArg<(Boolean, Throwable?) -> Unit>().invoke(true, null)
        }

        val promise: Promise = mockk(relaxed = true)
        newModule().installWebExtension("spatial_navigation", promise)

        verify { promise.resolve(null) }
    }

    @Test
    fun `installWebExtension maps IllegalArgumentException to INVALID_ASSET_PATH`() {
        mockkObject(GeckoRuntimeManager)
        val controller: WebExtensionController = mockk(relaxed = true)
        every { GeckoRuntimeManager.getRuntime(any()) } returns mockk(relaxed = true)
        every { GeckoRuntimeManager.getWebExtensionController() } returns controller
        every { controller.installExtension(any(), any()) } answers {
            secondArg<(Boolean, Throwable?) -> Unit>().invoke(false, IllegalArgumentException("bad path"))
        }

        val promise: Promise = mockk(relaxed = true)
        newModule().installWebExtension("../evil", promise)

        verify { promise.reject("INVALID_ASSET_PATH", any<String>()) }
    }

    @Test
    fun `installWebExtension maps a generic failure to WEB_EXTENSION_INSTALL`() {
        mockkObject(GeckoRuntimeManager)
        val controller: WebExtensionController = mockk(relaxed = true)
        every { GeckoRuntimeManager.getRuntime(any()) } returns mockk(relaxed = true)
        every { GeckoRuntimeManager.getWebExtensionController() } returns controller
        every { controller.installExtension(any(), any()) } answers {
            secondArg<(Boolean, Throwable?) -> Unit>().invoke(false, RuntimeException("boom"))
        }

        val promise: Promise = mockk(relaxed = true)
        newModule().installWebExtension("spatial_navigation", promise)

        verify { promise.reject("WEB_EXTENSION_INSTALL", any<String>()) }
    }

    @Test
    fun `installWebExtension rejects WEB_EXTENSION_INSTALL when runtime init throws`() {
        mockkObject(GeckoRuntimeManager)
        // getRuntime blowing up must be caught by the outer try/catch (L220-221)
        // and surfaced as a rejection rather than an uncaught exception.
        every { GeckoRuntimeManager.getRuntime(any()) } throws RuntimeException("runtime boom")

        val promise: Promise = mockk(relaxed = true)
        newModule().installWebExtension("spatial_navigation", promise)

        verify { promise.reject(eq("WEB_EXTENSION_INSTALL"), any<Throwable>()) }
        verify(exactly = 0) { promise.resolve(any()) }
    }

    @Test
    fun `installWebExtension rejects when the controller is unavailable`() {
        mockkObject(GeckoRuntimeManager)
        every { GeckoRuntimeManager.getRuntime(any()) } returns mockk(relaxed = true)
        every { GeckoRuntimeManager.getWebExtensionController() } returns null

        val promise: Promise = mockk(relaxed = true)
        newModule().installWebExtension("spatial_navigation", promise)

        verify { promise.reject("GECKO_RUNTIME", any<String>()) }
    }

    // ========== shutdown / closeSession ==========

    @Test
    fun `shutdown delegates to GeckoRuntimeManager`() {
        mockkObject(GeckoRuntimeManager)
        every { GeckoRuntimeManager.shutdown() } returns Unit

        newModule().shutdown()

        verify { GeckoRuntimeManager.shutdown() }
    }

    @Test
    fun `closeSession releases the session and cleans up its snapshots`() {
        mockkObject(GeckoSessionManager)
        every { GeckoSessionManager.releaseSession(any()) } returns Unit

        val module = newModule()
        val tmp = java.io.File.createTempFile("snap-close", ".png").also { it.deleteOnExit() }
        module.seedSnapshotForTest("s", tmp)

        module.closeSession("s")
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()

        verify { GeckoSessionManager.releaseSession("s") }
        assertTrue(module.snapshotsForSessionForTest("s").isEmpty())
        assertFalse("snapshot file must be deleted on close", tmp.exists())
    }

    // ========== sendEvent / sendViewEvent ==========

    @Test
    fun `sendEvent emits through the device event emitter`() {
        val ctx: ReactApplicationContext = mockk(relaxed = true)
        every { ctx.applicationContext } returns ctx
        val emitter =
            mockk<com.facebook.react.modules.core.DeviceEventManagerModule.RCTDeviceEventEmitter>(relaxed = true)
        every {
            ctx.getJSModule(com.facebook.react.modules.core.DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
        } returns emitter

        val module = GeckoViewModule(ctx)
        val params = JavaOnlyMap()
        module.sendEvent("onSomething", params)

        verify { emitter.emit("onSomething", params) }
    }

    @Test
    fun `sendViewEvent rewrites on-prefixed names to top and dispatches`() {
        mockkStatic(UIManagerHelper::class)
        val dispatcher = mockk<com.facebook.react.uimanager.events.EventDispatcher>(relaxed = true)
        every { UIManagerHelper.getEventDispatcherForReactTag(any(), 42) } returns dispatcher

        val module = newModule()
        module.sendViewEvent(42, "onGeckoPageStart", JavaOnlyMap())

        verify {
            dispatcher.dispatchEvent(withArg {
                assertEquals("topGeckoPageStart", it.eventName)
            })
        }
    }

    @Test
    fun `sendViewEvent drops the event when no dispatcher is available`() {
        mockkStatic(UIManagerHelper::class)
        every { UIManagerHelper.getEventDispatcherForReactTag(any(), any()) } returns null

        val module = newModule()
        // No dispatcher -> early return, no crash.
        module.sendViewEvent(7, "onGeckoPageStop", JavaOnlyMap())
    }

    @Test
    fun `sendViewEvent passes a non-on-prefixed name through unchanged`() {
        mockkStatic(UIManagerHelper::class)
        val dispatcher = mockk<com.facebook.react.uimanager.events.EventDispatcher>(relaxed = true)
        every { UIManagerHelper.getEventDispatcherForReactTag(any(), 9) } returns dispatcher

        val module = newModule()
        module.sendViewEvent(9, "customEvent", JavaOnlyMap())

        verify {
            dispatcher.dispatchEvent(withArg {
                assertEquals("customEvent", it.eventName)
            })
        }
    }

    // ========== captureSnapshot — post-capture size guard ==========

    @Test
    fun `captureSnapshot rejects when the captured bitmap exceeds the pixel cap`() {
        // View dimensions pass the pre-check, but the returned bitmap is larger
        // than maxSnapshotPixels -> SNAPSHOT_TOO_LARGE on the post-capture path.
        GeckoSecurityConfig.set(GeckoSecurityConfig.Options(maxSnapshotPixels = 16L))

        val view: GeckoView = mockk(relaxed = true)
        every { view.width } returns 2
        every { view.height } returns 2 // 4 px <= 16 cap, passes pre-check
        val bitmap = android.graphics.Bitmap.createBitmap(
            8, 8, android.graphics.Bitmap.Config.ARGB_8888
        ) // 64 px > 16 cap
        every { view.capturePixels() } returns GeckoResult.fromValue(bitmap)

        mockkObject(GeckoSessionManager)
        every { GeckoSessionManager.getOrCreateSession(any()) } returns mockk(relaxed = true)
        every { GeckoSessionManager.getView(any()) } returns view

        val module = newModule()
        val promise: Promise = mockk(relaxed = true)
        module.captureSnapshot("s", promise)
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()

        verify { promise.reject(eq("SNAPSHOT_TOO_LARGE"), any<String>()) }
        verify(exactly = 0) { promise.resolve(any()) }
        assertTrue("bitmap must still be recycled on the reject path", bitmap.isRecycled)
    }
}
