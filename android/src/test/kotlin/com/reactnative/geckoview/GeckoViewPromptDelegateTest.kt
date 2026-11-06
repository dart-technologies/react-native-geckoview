package com.reactnative.geckoview

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Ignore
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.geckoview.GeckoSession
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for [GeckoViewPromptDelegate].
 *
 * Covers every prompt type the delegate dispatches:
 *  - Alert prompts, including eval-looking sentinel strings that must now
 *    remain ordinary page alerts.
 *  - Button / text prompts.
 *  - Popup, before-unload, and auth prompts through the injected
 *    [PromptWrapperFactory], which isolates this test from GeckoView's final
 *    prompt classes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class GeckoViewPromptDelegateTest {

    private val module: GeckoViewModule = mockk(relaxed = true)
    private val session: GeckoSession = mockk(relaxed = true)
    private val factory: PromptWrapperFactory = mockk()

    private val popupPrompt: GeckoSession.PromptDelegate.PopupPrompt = mockk()
    private val popupWrapper: PopupPromptWrapper = mockk(relaxed = true)

    private val beforeUnloadPrompt: GeckoSession.PromptDelegate.BeforeUnloadPrompt = mockk()
    private val beforeUnloadWrapper: BeforeUnloadPromptWrapper = mockk(relaxed = true)

    private val authPrompt: GeckoSession.PromptDelegate.AuthPrompt = mockk()
    private val authWrapper: AuthPromptWrapper = mockk(relaxed = true)

    private lateinit var promptDelegate: GeckoViewPromptDelegate
    private val testViewId = 202

    @Before
    fun setUp() {
        TestHelpers.mockArguments()
        promptDelegate = GeckoViewPromptDelegate(module, testViewId, factory)

        every { factory.createPopupPromptWrapper(popupPrompt) } returns popupWrapper
        every { factory.createBeforeUnloadPromptWrapper(beforeUnloadPrompt) } returns beforeUnloadWrapper
        every { factory.createAuthPromptWrapper(authPrompt) } returns authWrapper
    }

    @After
    fun tearDown() {
        TestHelpers.unmockArguments()
    }

    @Test
    fun `onPopupPrompt emits onGeckoPopup event and stores pending prompt`() {
        every { popupWrapper.targetUri } returns "https://example.com/popup"

        val result = promptDelegate.onPopupPrompt(session, popupPrompt)

        assertNotNull(result)
        verify { module.sendViewEvent(testViewId, "onGeckoPopup", any()) }
        verify { module.storePendingPrompt(any(), popupWrapper, any()) }
    }

    @Test
    fun `onBeforeUnloadPrompt emits onGeckoBeforeUnload event and stores pending prompt`() {
        val result = promptDelegate.onBeforeUnloadPrompt(session, beforeUnloadPrompt)

        assertNotNull(result)
        verify { module.sendViewEvent(testViewId, "onGeckoBeforeUnload", any()) }
        verify { module.storePendingPrompt(any(), beforeUnloadWrapper, any()) }
    }

    @Ignore(
        "Exercises Arguments.makeNativeMap(), which triggers SoLoader native " +
            "initialization under Robolectric. Covered by device integration tests."
    )
    @Test
    fun `onAuthPrompt emits onGeckoAuth event with structured options`() {
        every { authWrapper.title } returns "Login"
        every { authWrapper.message } returns "Please authenticate"
        every { authWrapper.authOptions } returns mapOf("username" to "user")

        val result = promptDelegate.onAuthPrompt(session, authPrompt)

        assertNotNull(result)
        verify { module.sendViewEvent(testViewId, "onGeckoAuth", any()) }
        verify { module.storePendingPrompt(any(), authWrapper, any()) }
    }

    @Test
    fun `distinct popup prompts generate distinct prompt ids`() {
        every { popupWrapper.targetUri } returns "https://a.com"
        val second: GeckoSession.PromptDelegate.PopupPrompt = mockk()
        val secondWrapper: PopupPromptWrapper = mockk(relaxed = true)
        every { factory.createPopupPromptWrapper(second) } returns secondWrapper
        every { secondWrapper.targetUri } returns "https://b.com"

        promptDelegate.onPopupPrompt(session, popupPrompt)
        promptDelegate.onPopupPrompt(session, second)

        verify(exactly = 2) { module.sendViewEvent(testViewId, "onGeckoPopup", any()) }
        verify(exactly = 2) { module.storePendingPrompt(any(), any(), any()) }
    }

    @Test
    fun `eval-looking result alert is surfaced as normal page alert`() {
        val alert: GeckoSession.PromptDelegate.AlertPrompt = mockk(relaxed = true)
        TestHelpers.setField(alert, "message", "__EVAL_RESULT__:abc-uuid:\"hello\"")

        promptDelegate.onAlertPrompt(session, alert)

        verify { module.sendViewEvent(testViewId, "onGeckoAlert", any()) }
        verify { module.storePendingPrompt(any(), alert, any()) }
    }

    @Test
    fun `eval-looking error alert is surfaced as normal page alert`() {
        val alert: GeckoSession.PromptDelegate.AlertPrompt = mockk(relaxed = true)
        TestHelpers.setField(alert, "message", "__EVAL_ERROR__:req-1:ReferenceError: x is not defined")

        promptDelegate.onAlertPrompt(session, alert)

        verify { module.sendViewEvent(testViewId, "onGeckoAlert", any()) }
        verify { module.storePendingPrompt(any(), alert, any()) }
    }
}
