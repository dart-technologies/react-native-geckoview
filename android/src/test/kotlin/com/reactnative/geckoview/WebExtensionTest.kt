package com.reactnative.geckoview

import com.facebook.react.bridge.Promise
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.WebExtension
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for the production [WebExtensionController].
 *
 * These exercise the real class — its companion validators
 * ([WebExtensionController.isSafeAssetPath], [WebExtensionController.safeSerialize]),
 * the install single-completion contract, the eval single-settle guard via the
 * `internal *ForTest` hooks, and the incoming-message dispatch (`simulateClick`
 * trust gate, `focusExit`, `onWebExtensionMessage` field extraction) via
 * [WebExtensionController.handleMessageForTest].
 *
 * Note: full extension *installation* requires a live GeckoView runtime; that
 * path is covered by device integration tests. We never assert on the mocked
 * GeckoView SDK `org.mozilla.geckoview.WebExtensionController` here — that would
 * test the SDK, not this library.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class WebExtensionTest {

    private lateinit var runtime: GeckoRuntime

    @Before
    fun setUp() {
        TestHelpers.mockArguments()
        runtime = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        TestHelpers.unmockArguments()
        unmockkAll()
    }

    // ========== isSafeAssetPath (F6 analog — MED) ==========

    @Test
    fun `isSafeAssetPath accepts bundled extension name`() {
        assert(WebExtensionController.isSafeAssetPath("spatial_navigation"))
        assert(WebExtensionController.isSafeAssetPath("ext/sub/file.xpi"))
    }

    @Test
    fun `isSafeAssetPath rejects parent-dir traversal`() {
        assert(!WebExtensionController.isSafeAssetPath("../evil"))
        assert(!WebExtensionController.isSafeAssetPath("ext/../../../etc/passwd"))
    }

    @Test
    fun `isSafeAssetPath rejects URL-encoded traversal`() {
        assert(!WebExtensionController.isSafeAssetPath("%2e%2e/evil"))
        assert(!WebExtensionController.isSafeAssetPath("%2E%2E/evil"))
    }

    @Test
    fun `isSafeAssetPath rejects null byte`() {
        assert(!WebExtensionController.isSafeAssetPath("ext\u0000.xpi"))
    }

    @Test
    fun `isSafeAssetPath rejects backslash separator`() {
        assert(!WebExtensionController.isSafeAssetPath("ext\\win\\evil"))
    }

    @Test
    fun `isSafeAssetPath rejects absolute paths`() {
        assert(!WebExtensionController.isSafeAssetPath("/data/data/evil"))
    }

    @Test
    fun `isSafeAssetPath rejects empty path`() {
        assert(!WebExtensionController.isSafeAssetPath(""))
    }

    @Test
    fun `isSafeAssetPath rejects control characters`() {
        assert(!WebExtensionController.isSafeAssetPath("ext\u007f.xpi"))
    }

    // ========== safeSerialize (F4 analog — MED) ==========

    @Test
    fun `safeSerialize truncates oversized payload`() {
        val huge = "x".repeat(WebExtensionController.MAX_MESSAGE_BYTES + 100)
        val out = WebExtensionController.safeSerialize(huge)
        assert(out.endsWith("[truncated]")) { "Oversized output must carry truncation marker" }
        assert(out.length <= WebExtensionController.MAX_MESSAGE_BYTES + 20)
    }

    @Test
    fun `safeSerialize returns input unchanged when under cap`() {
        val small = """{"type":"hello"}"""
        val out = WebExtensionController.safeSerialize(small)
        assert(out == small)
    }

    @Test
    fun `safeSerialize handles deeply nested JSONObject`() {
        // Build a 500-deep nested object; toString() should succeed (no stack
        // overflow on org.json implementation) and the output length will be
        // truncated to the cap. Primary guard is the length cap; this test
        // documents that depth is tolerated, not a DoS vector alone.
        var inner = JSONObject()
        inner.put("leaf", "value")
        repeat(500) {
            val outer = JSONObject()
            outer.put("n", inner)
            inner = outer
        }
        val out = WebExtensionController.safeSerialize(inner)
        assert(out.isNotEmpty())
        assert(out.length <= WebExtensionController.MAX_MESSAGE_BYTES + 20)
    }

    // ========== Dual native-app-id registration ==========

    @Test
    fun `NATIVE_APP_IDS registers under both react-native and spatial relay ids`() {
        // The spatial-nav extension's relay is hard-pinned to "flutter_geckoview"
        // since its 3.0.1 hardening; the internal eval bridge uses
        // "react_native_geckoview". Both must be present so messages from either
        // surface reach the delegate.
        assert(WebExtensionController.NATIVE_APP_IDS.contains(WebExtensionController.NATIVE_APP_ID))
        assert(WebExtensionController.NATIVE_APP_IDS.contains(WebExtensionController.SPATIAL_NATIVE_APP_ID))
        assert(WebExtensionController.NATIVE_APP_ID == "react_native_geckoview")
        assert(WebExtensionController.SPATIAL_NATIVE_APP_ID == "flutter_geckoview")
        assert(WebExtensionController.NATIVE_APP_IDS.size == 2)
    }

    // ========== installExtension rejects unsafe paths ==========

    // installExtension reports unsafe paths exclusively through the callback;
    // it does NOT throw. A previous implementation invoked the callback AND
    // threw, which double-rejected the wrapping JS Promise.

    @Test
    fun `installExtension reports path traversal via callback without throwing`() {
        val controller = WebExtensionController(runtime)
        var calledWith: Pair<Boolean, Throwable?>? = null
        controller.installExtension("../../evil") { success, error ->
            calledWith = success to error
        }
        assert(calledWith != null) { "onInstalled callback must fire on unsafe path" }
        assert(calledWith!!.first == false)
        assert(calledWith!!.second is IllegalArgumentException)
    }

    @Test
    fun `installExtension reports null-byte path via callback without throwing`() {
        val controller = WebExtensionController(runtime)
        var calledWith: Pair<Boolean, Throwable?>? = null
        controller.installExtension("legit\u0000.xpi") { success, error ->
            calledWith = success to error
        }
        assert(calledWith?.first == false)
        assert(calledWith?.second is IllegalArgumentException)
    }

    @Test
    fun `installExtension reports absolute path via callback without throwing`() {
        val controller = WebExtensionController(runtime)
        var calledWith: Pair<Boolean, Throwable?>? = null
        controller.installExtension("/data/data/evil") { success, error ->
            calledWith = success to error
        }
        assert(calledWith?.first == false)
        assert(calledWith?.second is IllegalArgumentException)
    }

    @Test
    fun `installExtension does not throw when callback is null and path is unsafe`() {
        val controller = WebExtensionController(runtime)
        // No-arg form: no callback. Must not throw — the prior contract
        // leaked an uncaught IllegalArgumentException up to the caller.
        controller.installExtension("../evil")
    }

    @Test
    fun `installExtension invokes onInstalled exactly once on unsafe path`() {
        val controller = WebExtensionController(runtime)
        var callCount = 0
        controller.installExtension("../evil") { _, _ -> callCount++ }
        assert(callCount == 1) { "Callback must fire exactly once on unsafe path; got $callCount" }
    }

    @Test
    fun `sendMessage returns false when extension port is missing`() {
        val controller = WebExtensionController(runtime)
        val sent = controller.sendMessage("session-a", "missing@example.com", "{}")
        assert(!sent)
    }

    @Test
    fun `evaluateJavaScript returns false when trusted bridge port is missing`() {
        val controller = WebExtensionController(runtime)
        val promise: Promise = mockk(relaxed = true)
        val sent = controller.evaluateJavaScript("session-a", "document.title", promise)
        assert(!sent)
        verify(exactly = 0) { promise.resolve(any()) }
        verify(exactly = 0) { promise.reject(any<String>(), any<String>()) }
    }

    // ========== rejectPendingForKey single-completion guard ==========
    //
    // Pinned regression: an earlier review surfaced concern that a port
    // disconnect followed by a late eval result on the same requestId would
    // double-settle the JS Promise. The fix uses an atomic `pendingEval.remove`
    // — the second settle path finds the entry gone and short-circuits.

    @Test
    fun `rejectPendingForKey settles each pending eval exactly once`() {
        val controller = WebExtensionController(runtime)
        val promise: Promise = mockk(relaxed = true)
        controller.seedPendingEvalForTest(
            requestId = "r1",
            extensionId = "ext@geckoview.dev",
            sessionKey = "session-a",
            promise = promise,
        )
        controller.rejectPendingForKeyForTest(
            extensionId = "ext@geckoview.dev",
            sessionKey = "session-a",
            code = "EVAL_BRIDGE_UNAVAILABLE",
            message = "disconnected",
        )

        verify(exactly = 1) { promise.reject("EVAL_BRIDGE_UNAVAILABLE", "disconnected") }
        verify(exactly = 0) { promise.resolve(any()) }
    }

    @Test
    fun `late eval result after disconnect does not double-settle the promise`() {
        val controller = WebExtensionController(runtime)
        val promise: Promise = mockk(relaxed = true)
        controller.seedPendingEvalForTest(
            requestId = "r1",
            extensionId = "ext@geckoview.dev",
            sessionKey = "session-a",
            promise = promise,
        )

        // Disconnect first — rejects r1 and removes it from the map.
        controller.rejectPendingForKeyForTest(
            extensionId = "ext@geckoview.dev",
            sessionKey = "session-a",
            code = "EVAL_BRIDGE_UNAVAILABLE",
            message = "disconnected",
        )
        // Then a late result message arrives for the same requestId — the
        // entry is gone, so handleEvalResult must short-circuit without
        // attempting a second settle.
        controller.simulateEvalResultForTest("r1", result = "42")

        verify(exactly = 1) { promise.reject(any<String>(), any<String>()) }
        verify(exactly = 0) { promise.resolve(any()) }
    }

    @Test
    fun `rejectPendingForKey does not affect entries for a different key`() {
        val controller = WebExtensionController(runtime)
        val sessionAPromise: Promise = mockk(relaxed = true)
        val sessionBPromise: Promise = mockk(relaxed = true)
        controller.seedPendingEvalForTest(
            requestId = "r1",
            extensionId = "ext@geckoview.dev",
            sessionKey = "session-a",
            promise = sessionAPromise,
        )
        controller.seedPendingEvalForTest(
            requestId = "r2",
            extensionId = "ext@geckoview.dev",
            sessionKey = "session-b",
            promise = sessionBPromise,
        )

        controller.rejectPendingForKeyForTest(
            extensionId = "ext@geckoview.dev",
            sessionKey = "session-a",
            code = "EVAL_BRIDGE_UNAVAILABLE",
            message = "disconnected",
        )

        // Only session-a's pending eval should be rejected.
        verify(exactly = 1) { sessionAPromise.reject("EVAL_BRIDGE_UNAVAILABLE", "disconnected") }
        verify(exactly = 0) { sessionBPromise.reject(any<String>(), any<String>()) }

        // session-b's pending eval should still be settle-able by a result.
        controller.simulateEvalResultForTest("r2", result = "ok")
        verify(exactly = 1) { sessionBPromise.resolve("ok") }
    }

    // ========== isTrustedForInput — input-injection trust gate ==========

    @Test
    fun `isTrustedForInput trusts the internal bridge extension`() {
        val controller = WebExtensionController(runtime)
        assert(controller.isTrustedForInputForTest(WebExtensionController.INTERNAL_BRIDGE_EXTENSION_ID))
    }

    @Test
    fun `isTrustedForInput trusts allowlisted spatial-nav extension`() {
        val controller = WebExtensionController(runtime)
        // The allowlist is the single source of truth for trusted ids.
        for (id in GeckoRuntimeManager.allowedExtensionIds) {
            assert(controller.isTrustedForInputForTest(id)) { "Allowlisted id must be trusted: $id" }
        }
    }

    @Test
    fun `isTrustedForInput rejects an arbitrary extension id`() {
        val controller = WebExtensionController(runtime)
        assert(!controller.isTrustedForInputForTest("evil@attacker.example"))
        assert(!controller.isTrustedForInputForTest("unknown"))
    }

    // ========== simulateClick trust gate (incoming message dispatch) ==========
    //
    // simulateClick is an input-injection primitive. The handler must consult
    // the trust gate BEFORE resolving a target view. We observe this by mocking
    // GeckoSessionManager.getAllViews(): findBestTargetView() (and therefore
    // getAllViews) is only reached for a trusted extension.

    @Test
    fun `simulateClick from untrusted extension is ignored before view lookup`() {
        mockkObject(GeckoSessionManager)
        val controller = WebExtensionController(runtime)
        val message = JSONObject().apply {
            put("type", "simulateClick")
            put("x", 10.0)
            put("y", 20.0)
        }

        controller.handleMessageForTest("evil@attacker.example", message)

        // The trust gate short-circuits before any view resolution.
        verify(exactly = 0) { GeckoSessionManager.getAllViews() }
    }

    @Test
    fun `simulateClick from trusted bridge resolves a target view`() {
        mockkObject(GeckoSessionManager)
        every { GeckoSessionManager.getAllViews() } returns emptyList()
        val controller = WebExtensionController(runtime)
        val message = JSONObject().apply {
            put("type", "simulateClick")
            put("x", 10.0)
            put("y", 20.0)
        }

        controller.handleMessageForTest(WebExtensionController.INTERNAL_BRIDGE_EXTENSION_ID, message)

        // Trusted: the handler proceeds past the gate and resolves a view
        // (empty here, so it logs and returns — no crash).
        verify(atLeast = 1) { GeckoSessionManager.getAllViews() }
    }

    @Test
    fun `simulateClick with non-finite coordinates does not dispatch a touch`() {
        mockkObject(GeckoSessionManager)
        val view: GeckoView = mockk(relaxed = true)
        every { GeckoSessionManager.getAllViews() } returns listOf(view)
        val controller = WebExtensionController(runtime)
        val message = JSONObject().apply {
            put("type", "simulateClick")
            // No x/y -> optDouble returns NaN -> not finite.
        }

        controller.handleMessageForTest(WebExtensionController.INTERNAL_BRIDGE_EXTENSION_ID, message)

        verify(exactly = 0) { view.dispatchTouchEvent(any()) }
    }

    @Test
    fun `simulateClick prefers a focused view and posts the click`() {
        mockkObject(GeckoSessionManager)
        val unfocused: GeckoView = mockk(relaxed = true)
        val focused: GeckoView = mockk(relaxed = true)
        every { unfocused.hasFocus() } returns false
        every { focused.hasFocus() } returns true
        every { GeckoSessionManager.getAllViews() } returns listOf(unfocused, focused)

        val controller = WebExtensionController(runtime)
        controller.handleMessageForTest(
            WebExtensionController.INTERNAL_BRIDGE_EXTENSION_ID,
            JSONObject().apply { put("type", "simulateClick"); put("x", 5.0); put("y", 6.0) },
        )

        // findBestTargetView picks the focused view; the dispatch is queued on it.
        verify { focused.post(any()) }
    }

    @Test
    fun `simulateClick falls back to an attached view when none is focused`() {
        mockkObject(GeckoSessionManager)
        val detached: GeckoView = mockk(relaxed = true)
        val attached: GeckoView = mockk(relaxed = true)
        every { detached.hasFocus() } returns false
        every { attached.hasFocus() } returns false
        every { detached.isAttachedToWindow } returns false
        every { attached.isAttachedToWindow } returns true
        every { GeckoSessionManager.getAllViews() } returns listOf(detached, attached)

        val controller = WebExtensionController(runtime)
        controller.handleMessageForTest(
            WebExtensionController.INTERNAL_BRIDGE_EXTENSION_ID,
            JSONObject().apply { put("type", "simulateClick"); put("x", 5.0); put("y", 6.0) },
        )

        verify { attached.post(any()) }
    }

    // ========== focusExit (incoming message dispatch) ==========

    @Test
    fun `focusExit emits onFocusExit view event with direction`() {
        mockkObject(GeckoSessionManager)
        mockkObject(GeckoViewPackage.Companion)
        val module: GeckoViewModule = mockk(relaxed = true)
        val view: GeckoView = mockk(relaxed = true)
        every { view.id } returns 77
        every { GeckoViewPackage.getModuleInstance() } returns module
        every { GeckoSessionManager.getAllViews() } returns listOf(view)

        val controller = WebExtensionController(runtime)
        val message = JSONObject().apply {
            put("type", "focusExit")
            put("direction", "DOWN")
        }

        controller.handleMessageForTest(WebExtensionController.SPATIAL_NATIVE_APP_ID, message)

        verify(exactly = 1) { module.sendViewEvent(77, "onFocusExit", any()) }
    }

    @Test
    fun `focusExit with blank direction does not emit an event`() {
        mockkObject(GeckoSessionManager)
        mockkObject(GeckoViewPackage.Companion)
        val module: GeckoViewModule = mockk(relaxed = true)
        every { GeckoViewPackage.getModuleInstance() } returns module
        every { GeckoSessionManager.getAllViews() } returns emptyList()

        val controller = WebExtensionController(runtime)
        val message = JSONObject().apply {
            put("type", "focusExit")
            put("direction", "")
        }

        controller.handleMessageForTest(WebExtensionController.SPATIAL_NATIVE_APP_ID, message)

        verify(exactly = 0) { module.sendViewEvent(any(), "onFocusExit", any()) }
    }

    @Test
    fun `focusExit without a mounted view does not emit an event`() {
        mockkObject(GeckoSessionManager)
        mockkObject(GeckoViewPackage.Companion)
        val module: GeckoViewModule = mockk(relaxed = true)
        every { GeckoViewPackage.getModuleInstance() } returns module
        every { GeckoSessionManager.getAllViews() } returns emptyList()

        val controller = WebExtensionController(runtime)
        val message = JSONObject().apply {
            put("type", "focusExit")
            put("direction", "up")
        }

        controller.handleMessageForTest(WebExtensionController.SPATIAL_NATIVE_APP_ID, message)

        verify(exactly = 0) { module.sendViewEvent(any(), any(), any()) }
    }

    // ========== onWebExtensionMessage fan-out + modality extraction ==========

    @Test
    fun `generic message fans out onWebExtensionMessage to all views`() {
        mockkObject(GeckoSessionManager)
        mockkObject(GeckoViewPackage.Companion)
        val module: GeckoViewModule = mockk(relaxed = true)
        val viewA: GeckoView = mockk(relaxed = true)
        val viewB: GeckoView = mockk(relaxed = true)
        every { viewA.id } returns 1
        every { viewB.id } returns 2
        every { GeckoViewPackage.getModuleInstance() } returns module
        every { GeckoSessionManager.getAllViews() } returns listOf(viewA, viewB)

        val controller = WebExtensionController(runtime)
        val message = JSONObject().apply {
            put("type", "inputModalityChange")
            put("modality", "keyboard")
        }

        controller.handleMessageForTest("ext@geckoview.dev", message)

        verify(exactly = 1) { module.sendViewEvent(1, "onWebExtensionMessage", any()) }
        verify(exactly = 1) { module.sendViewEvent(2, "onWebExtensionMessage", any()) }
    }

    @Test
    fun `blank-type message is dropped without fan-out`() {
        mockkObject(GeckoSessionManager)
        mockkObject(GeckoViewPackage.Companion)
        val module: GeckoViewModule = mockk(relaxed = true)
        every { GeckoViewPackage.getModuleInstance() } returns module
        every { GeckoSessionManager.getAllViews() } returns emptyList()

        val controller = WebExtensionController(runtime)
        val message = JSONObject().apply { put("type", "") }

        controller.handleMessageForTest("ext@geckoview.dev", message)

        verify(exactly = 0) { module.sendViewEvent(any(), any(), any()) }
    }

    @Test
    fun `generic message is dropped when module is not ready`() {
        mockkObject(GeckoSessionManager)
        mockkObject(GeckoViewPackage.Companion)
        every { GeckoViewPackage.getModuleInstance() } returns null

        val controller = WebExtensionController(runtime)
        val message = JSONObject().apply { put("type", "someEvent") }

        // No module -> early return, no crash, no view query.
        controller.handleMessageForTest("ext@geckoview.dev", message)

        verify(exactly = 0) { GeckoSessionManager.getAllViews() }
    }

    @Test
    fun `generic message extracts direction inTrap and modality into the payload`() {
        mockkObject(GeckoSessionManager)
        mockkObject(GeckoViewPackage.Companion)
        val module: GeckoViewModule = mockk(relaxed = true)
        val view: GeckoView = mockk(relaxed = true)
        every { view.id } returns 11
        every { GeckoViewPackage.getModuleInstance() } returns module
        every { GeckoSessionManager.getAllViews() } returns listOf(view)

        val params = slot<com.facebook.react.bridge.WritableMap>()
        every { module.sendViewEvent(11, "onWebExtensionMessage", capture(params)) } returns Unit

        val controller = WebExtensionController(runtime)
        val message = JSONObject().apply {
            put("type", "inputModalityChange")
            put("direction", "up")
            put("inTrap", true)
            put("modality", "keyboard")
        }

        controller.handleMessageForTest("ext@geckoview.dev", message)

        // The forwarded payload wraps a `message` map carrying the extracted fields.
        val inner = params.captured.getMap("message")!!
        org.junit.Assert.assertEquals("inputModalityChange", inner.getString("type"))
        org.junit.Assert.assertEquals("up", inner.getString("direction"))
        org.junit.Assert.assertTrue(inner.getBoolean("inTrap"))
        org.junit.Assert.assertEquals("keyboard", inner.getString("modality"))
        org.junit.Assert.assertEquals("ext@geckoview.dev", inner.getString("extensionId"))
    }

    @Test
    fun `non-JSON message is forwarded with only extensionId and raw`() {
        mockkObject(GeckoSessionManager)
        mockkObject(GeckoViewPackage.Companion)
        val module: GeckoViewModule = mockk(relaxed = true)
        val view: GeckoView = mockk(relaxed = true)
        every { view.id } returns 22
        every { GeckoViewPackage.getModuleInstance() } returns module
        every { GeckoSessionManager.getAllViews() } returns listOf(view)

        val params = slot<com.facebook.react.bridge.WritableMap>()
        every { module.sendViewEvent(22, "onWebExtensionMessage", capture(params)) } returns Unit

        val controller = WebExtensionController(runtime)
        // A plain String (not a JSONObject) takes the `else` serialization arm.
        controller.handleMessageForTest("ext@geckoview.dev", "plain-text-message")

        val inner = params.captured.getMap("message")!!
        org.junit.Assert.assertEquals("ext@geckoview.dev", inner.getString("extensionId"))
        org.junit.Assert.assertEquals("plain-text-message", inner.getString("raw"))
    }

    @Test
    fun `handleIncomingMessage swallows exceptions thrown during fan-out`() {
        mockkObject(GeckoSessionManager)
        mockkObject(GeckoViewPackage.Companion)
        val module: GeckoViewModule = mockk(relaxed = true)
        val view: GeckoView = mockk(relaxed = true)
        every { view.id } returns 33
        every { GeckoViewPackage.getModuleInstance() } returns module
        every { GeckoSessionManager.getAllViews() } returns listOf(view)
        // sendViewEvent blowing up must be caught by the outer try/catch (L327-328)
        // rather than propagating out of the message handler.
        every { module.sendViewEvent(any(), any(), any()) } throws RuntimeException("dispatch boom")

        val controller = WebExtensionController(runtime)
        val message = JSONObject().apply { put("type", "someEvent") }

        // No throw escapes.
        controller.handleMessageForTest("ext@geckoview.dev", message)
    }

    // ========== EVAL_RESULT_TYPE routing through handleIncomingMessage ==========

    @Test
    fun `EVAL_RESULT_TYPE message routes to the pending eval and resolves`() {
        val controller = WebExtensionController(runtime)
        val promise: Promise = mockk(relaxed = true)
        controller.seedPendingEvalForTest(
            requestId = "rid-1",
            extensionId = WebExtensionController.INTERNAL_BRIDGE_EXTENSION_ID,
            sessionKey = "session-a",
            promise = promise,
        )

        // Arrives as a top-level message of type EVAL_RESULT_TYPE -> handleEvalResult.
        controller.handleMessageForTest(
            WebExtensionController.INTERNAL_BRIDGE_EXTENSION_ID,
            JSONObject().apply {
                put("type", WebExtensionController.EVAL_RESULT_TYPE)
                put("requestId", "rid-1")
                put("result", "hello")
            },
        )

        verify { promise.resolve("hello") }
    }

    @Test
    fun `EVAL_RESULT_TYPE message with an error field rejects the pending eval`() {
        val controller = WebExtensionController(runtime)
        val promise: Promise = mockk(relaxed = true)
        controller.seedPendingEvalForTest(
            requestId = "rid-err",
            extensionId = WebExtensionController.INTERNAL_BRIDGE_EXTENSION_ID,
            sessionKey = "session-a",
            promise = promise,
        )

        controller.handleMessageForTest(
            WebExtensionController.INTERNAL_BRIDGE_EXTENSION_ID,
            JSONObject().apply {
                put("type", WebExtensionController.EVAL_RESULT_TYPE)
                put("requestId", "rid-err")
                put("error", "ReferenceError: x is not defined")
            },
        )

        verify { promise.reject("EVAL_ERROR", "ReferenceError: x is not defined") }
        verify(exactly = 0) { promise.resolve(any()) }
    }

    @Test
    fun `EVAL_RESULT_TYPE message with no result resolves undefined`() {
        val controller = WebExtensionController(runtime)
        val promise: Promise = mockk(relaxed = true)
        controller.seedPendingEvalForTest(
            requestId = "rid-undef",
            extensionId = WebExtensionController.INTERNAL_BRIDGE_EXTENSION_ID,
            sessionKey = "session-a",
            promise = promise,
        )

        controller.simulateEvalResultForTest("rid-undef", result = null)

        verify { promise.resolve("undefined") }
    }

    @Test
    fun `eval result with a blank requestId is ignored`() {
        val controller = WebExtensionController(runtime)
        // No pending eval; a blank requestId must short-circuit before any lookup.
        controller.handleMessageForTest(
            WebExtensionController.INTERNAL_BRIDGE_EXTENSION_ID,
            JSONObject().apply {
                put("type", WebExtensionController.EVAL_RESULT_TYPE)
                put("requestId", "")
                put("result", "x")
            },
        )
        // No crash, nothing to assert beyond the no-throw contract.
    }

    @Test
    fun `eval result for an unknown requestId is ignored`() {
        val controller = WebExtensionController(runtime)
        controller.simulateEvalResultForTest("never-seen", result = "x")
        // No pending entry -> early return, no crash.
    }

    // ========== onConnect + port-backed messaging ==========

    private fun mockPort(extensionId: String, sessionKey: String?): Pair<WebExtension.Port, GeckoSession?> {
        val ext: WebExtension = mockk(relaxed = true)
        TestHelpers.setField(ext, "id", extensionId)
        val session: GeckoSession? = sessionKey?.let { mockk(relaxed = true) }
        val sender: WebExtension.MessageSender = mockk(relaxed = true)
        TestHelpers.setField(sender, "webExtension", ext)
        if (session != null) TestHelpers.setField(sender, "session", session)
        val port: WebExtension.Port = mockk(relaxed = true)
        TestHelpers.setField(port, "sender", sender)
        // The reverse lookup the controller uses to derive the PortKey sessionKey.
        if (session != null) every { GeckoSessionManager.getSessionKey(session) } returns sessionKey
        return port to session
    }

    @Test
    fun `sendMessage succeeds once a matching port is connected`() {
        mockkObject(GeckoSessionManager)
        val controller = WebExtensionController(runtime)
        val (port, _) = mockPort("ext@geckoview.dev", "session-a")

        controller.connectPortForTest(port)
        val sent = controller.sendMessage("session-a", "ext@geckoview.dev", "hello")

        assert(sent)
        verify { port.postMessage(any()) }
    }

    @Test
    fun `evaluateJavaScript queues to the connected bridge port and settles on result`() {
        mockkObject(GeckoSessionManager)
        val controller = WebExtensionController(runtime)
        val (port, _) = mockPort(WebExtensionController.INTERNAL_BRIDGE_EXTENSION_ID, "session-a")
        val posted = slot<JSONObject>()
        every { port.postMessage(capture(posted)) } returns Unit

        controller.connectPortForTest(port)
        val promise: Promise = mockk(relaxed = true)
        val queued = controller.evaluateJavaScript("session-a", "1+1", promise)

        assert(queued) { "evaluateJavaScript must queue when the bridge port is present" }
        // Pull the generated requestId from the posted eval request, then feed a
        // matching result back through the controller — the promise resolves.
        val requestId = posted.captured.getString("requestId")
        controller.simulateEvalResultForTest(requestId, "2")
        verify { promise.resolve("2") }
    }

    @Test
    fun `port delegate routes an eval result message to the pending promise`() {
        mockkObject(GeckoSessionManager)
        val controller = WebExtensionController(runtime)
        val (port, _) = mockPort(WebExtensionController.INTERNAL_BRIDGE_EXTENSION_ID, "session-a")
        val posted = slot<JSONObject>()
        every { port.postMessage(capture(posted)) } returns Unit
        val portDelegate = slot<WebExtension.PortDelegate>()
        every { port.setDelegate(capture(portDelegate)) } returns Unit

        controller.connectPortForTest(port)
        val promise: Promise = mockk(relaxed = true)
        controller.evaluateJavaScript("session-a", "document.title", promise)

        // Deliver the result via the registered PortDelegate (the onPortMessage
        // path) rather than the test hook.
        val requestId = posted.captured.getString("requestId")
        val result = JSONObject().apply {
            put("type", WebExtensionController.EVAL_RESULT_TYPE)
            put("requestId", requestId)
            put("result", "Example")
        }
        portDelegate.captured.onPortMessage(result, port)

        verify { promise.resolve("Example") }
    }

    @Test
    fun `port disconnect rejects pending evals for that port`() {
        mockkObject(GeckoSessionManager)
        val controller = WebExtensionController(runtime)
        val (port, _) = mockPort(WebExtensionController.INTERNAL_BRIDGE_EXTENSION_ID, "session-a")
        every { port.postMessage(any()) } returns Unit
        val portDelegate = slot<WebExtension.PortDelegate>()
        every { port.setDelegate(capture(portDelegate)) } returns Unit

        controller.connectPortForTest(port)
        val promise: Promise = mockk(relaxed = true)
        controller.evaluateJavaScript("session-a", "1+1", promise)

        portDelegate.captured.onDisconnect(port)

        verify { promise.reject("EVAL_BRIDGE_UNAVAILABLE", any<String>()) }
    }

    @Test
    fun `sendMessage returns false when postMessage throws`() {
        mockkObject(GeckoSessionManager)
        val controller = WebExtensionController(runtime)
        val (port, _) = mockPort("ext@geckoview.dev", "session-a")
        every { port.postMessage(any()) } throws RuntimeException("port closed")

        controller.connectPortForTest(port)
        val sent = controller.sendMessage("session-a", "ext@geckoview.dev", "hello")

        assert(!sent) { "a throwing postMessage must surface as a false send" }
    }

    @Test
    fun `evaluateJavaScript returns false and clears state when the bridge postMessage throws`() {
        mockkObject(GeckoSessionManager)
        val controller = WebExtensionController(runtime)
        val (port, _) = mockPort(WebExtensionController.INTERNAL_BRIDGE_EXTENSION_ID, "session-a")
        every { port.postMessage(any()) } throws RuntimeException("bridge gone")

        controller.connectPortForTest(port)
        val promise: Promise = mockk(relaxed = true)
        val queued = controller.evaluateJavaScript("session-a", "1+1", promise)

        // The throw path returns false; the pending entry+timeout were rolled back,
        // so the promise is neither resolved nor rejected here.
        assert(!queued) { "a throwing eval postMessage must return false" }
        verify(exactly = 0) { promise.resolve(any()) }
        verify(exactly = 0) { promise.reject(any<String>(), any<String>()) }
    }

    @Test
    fun `port delegate ignores a non-eval-result port message`() {
        mockkObject(GeckoSessionManager)
        val controller = WebExtensionController(runtime)
        val (port, _) = mockPort(WebExtensionController.INTERNAL_BRIDGE_EXTENSION_ID, "session-a")
        every { port.postMessage(any()) } returns Unit
        val portDelegate = slot<WebExtension.PortDelegate>()
        every { port.setDelegate(capture(portDelegate)) } returns Unit

        controller.connectPortForTest(port)

        // A port message whose type is not EVAL_RESULT_TYPE takes the `else`
        // logging arm (L227-228) without touching any pending eval.
        portDelegate.captured.onPortMessage(
            JSONObject().apply { put("type", "somethingElse") },
            port,
        )
        // Also exercise the non-JSONObject branch.
        portDelegate.captured.onPortMessage("not-json", port)
        // No crash; nothing to settle.
    }

    // ========== messageDelegate.onMessage — sender-id resolution ==========

    @Test
    fun `onMessage resolves the sender extension id and dispatches`() {
        mockkObject(GeckoSessionManager)
        mockkObject(GeckoViewPackage.Companion)
        val module: GeckoViewModule = mockk(relaxed = true)
        val view: GeckoView = mockk(relaxed = true)
        every { view.id } returns 55
        every { GeckoViewPackage.getModuleInstance() } returns module
        every { GeckoSessionManager.getAllViews() } returns listOf(view)

        // Build a MessageSender whose webExtension.id is the resolved sender id.
        val ext: WebExtension = mockk(relaxed = true)
        TestHelpers.setField(ext, "id", "sender-ext@geckoview.dev")
        val sender: WebExtension.MessageSender = mockk(relaxed = true)
        TestHelpers.setField(sender, "webExtension", ext)

        val params = slot<com.facebook.react.bridge.WritableMap>()
        every { module.sendViewEvent(55, "onWebExtensionMessage", capture(params)) } returns Unit

        val controller = WebExtensionController(runtime)
        val result = controller.dispatchOnMessageForTest(
            sender,
            JSONObject().apply { put("type", "someEvent") },
        )

        // onMessage always returns null (no synchronous response to the extension).
        org.junit.Assert.assertNull(result)
        org.junit.Assert.assertEquals(
            "sender-ext@geckoview.dev",
            params.captured.getMap("message")!!.getString("extensionId"),
        )
    }

    @Test
    fun `onMessage falls back to unknown when the sender has no extension`() {
        mockkObject(GeckoSessionManager)
        mockkObject(GeckoViewPackage.Companion)
        val module: GeckoViewModule = mockk(relaxed = true)
        val view: GeckoView = mockk(relaxed = true)
        every { view.id } returns 66
        every { GeckoViewPackage.getModuleInstance() } returns module
        every { GeckoSessionManager.getAllViews() } returns listOf(view)

        // Sender with no webExtension and no installed extension -> "unknown".
        val sender: WebExtension.MessageSender = mockk(relaxed = true)
        TestHelpers.setField(sender, "webExtension", null)

        val params = slot<com.facebook.react.bridge.WritableMap>()
        every { module.sendViewEvent(66, "onWebExtensionMessage", capture(params)) } returns Unit

        val controller = WebExtensionController(runtime)
        controller.dispatchOnMessageForTest(
            sender,
            JSONObject().apply { put("type", "someEvent") },
        )

        org.junit.Assert.assertEquals(
            "unknown",
            params.captured.getMap("message")!!.getString("extensionId"),
        )
    }

    // ========== installExtension success path (collaborator-backed) ==========

    @Test
    fun `installExtension on a safe generic path installs via installBuiltIn and reports success`() {
        val sdkController = mockk<org.mozilla.geckoview.WebExtensionController>(relaxed = true)
        every { runtime.webExtensionController } returns sdkController
        val ext: WebExtension = mockk(relaxed = true)
        every { sdkController.installBuiltIn(any()) } returns GeckoResult.fromValue(ext)

        val controller = WebExtensionController(runtime)
        var result: Pair<Boolean, Throwable?>? = null
        controller.installExtension("custom_ext") { ok, err -> result = ok to err }
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()

        assert(result?.first == true) { "safe install must report success" }
        // The message delegate must be registered under BOTH native-app ids.
        verify { ext.setMessageDelegate(any(), WebExtensionController.NATIVE_APP_ID) }
        verify { ext.setMessageDelegate(any(), WebExtensionController.SPATIAL_NATIVE_APP_ID) }
    }

    @Test
    fun `installExtension for spatial_navigation uses ensureBuiltIn with the pinned id`() {
        val sdkController = mockk<org.mozilla.geckoview.WebExtensionController>(relaxed = true)
        every { runtime.webExtensionController } returns sdkController
        val ext: WebExtension = mockk(relaxed = true)
        val idSlot = slot<String>()
        every { sdkController.ensureBuiltIn(any(), capture(idSlot)) } returns GeckoResult.fromValue(ext)

        val controller = WebExtensionController(runtime)
        var ok = false
        controller.installExtension("spatial_navigation") { success, _ -> ok = success }
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()

        assert(ok)
        assert(idSlot.captured == "spatial-navigation@geckoview.dev") {
            "spatial_navigation must install under its pinned extension id; got ${idSlot.captured}"
        }
        verify(exactly = 0) { sdkController.installBuiltIn(any()) }
    }

    @Test
    fun `installExtension reports failure when the install GeckoResult rejects`() {
        val sdkController = mockk<org.mozilla.geckoview.WebExtensionController>(relaxed = true)
        every { runtime.webExtensionController } returns sdkController
        every { sdkController.installBuiltIn(any()) } returns
            GeckoResult.fromException(RuntimeException("install failed"))

        val controller = WebExtensionController(runtime)
        var result: Pair<Boolean, Throwable?>? = null
        controller.installExtension("custom_ext") { ok, err -> result = ok to err }
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()

        assert(result?.first == false)
        assert(result?.second is RuntimeException)
    }

    @Test
    fun `installInternalBridge installs the trusted bridge under its forced id`() {
        val sdkController = mockk<org.mozilla.geckoview.WebExtensionController>(relaxed = true)
        every { runtime.webExtensionController } returns sdkController
        val ext: WebExtension = mockk(relaxed = true)
        val idSlot = slot<String>()
        val uriSlot = slot<String>()
        every { sdkController.ensureBuiltIn(capture(uriSlot), capture(idSlot)) } returns
            GeckoResult.fromValue(ext)

        val controller = WebExtensionController(runtime)
        var ok = false
        controller.installInternalBridge { success, _ -> ok = success }
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()

        assert(ok)
        assert(idSlot.captured == WebExtensionController.INTERNAL_BRIDGE_EXTENSION_ID)
        assert(uriSlot.captured.endsWith(WebExtensionController.INTERNAL_BRIDGE_ASSET_PATH))
    }
}
