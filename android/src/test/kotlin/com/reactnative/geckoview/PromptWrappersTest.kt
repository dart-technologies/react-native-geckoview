package com.reactnative.geckoview

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoSession
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for the prompt-wrapper indirection in [PromptWrappers].
 *
 * The `Real*` wrappers translate the library's boolean intent into GeckoView's
 * `AllowOrDeny` enum and forward to the (final) GeckoView prompt classes;
 * [DefaultPromptWrapperFactory] is the production factory that produces them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PromptWrappersTest {

    private val response: GeckoSession.PromptDelegate.PromptResponse = mockk(relaxed = true)

    // ========== RealPopupPromptWrapper ==========

    @Test
    fun `RealPopupPromptWrapper respond(true) confirms with ALLOW`() {
        val prompt: GeckoSession.PromptDelegate.PopupPrompt = mockk(relaxed = true)
        every { prompt.confirm(any()) } returns response

        val out = RealPopupPromptWrapper(prompt).respond(true)

        verify { prompt.confirm(AllowOrDeny.ALLOW) }
        assertSame(response, out)
    }

    @Test
    fun `RealPopupPromptWrapper respond(false) confirms with DENY`() {
        val prompt: GeckoSession.PromptDelegate.PopupPrompt = mockk(relaxed = true)
        every { prompt.confirm(any()) } returns response

        RealPopupPromptWrapper(prompt).respond(false)

        verify { prompt.confirm(AllowOrDeny.DENY) }
    }

    @Test
    fun `RealPopupPromptWrapper exposes targetUri`() {
        val prompt: GeckoSession.PromptDelegate.PopupPrompt = mockk(relaxed = true)
        TestHelpers.setField(prompt, "targetUri", "https://popup.example")

        assertEquals("https://popup.example", RealPopupPromptWrapper(prompt).targetUri)
    }

    // ========== RealBeforeUnloadPromptWrapper ==========

    @Test
    fun `RealBeforeUnloadPromptWrapper confirm(true) allows navigation`() {
        val prompt: GeckoSession.PromptDelegate.BeforeUnloadPrompt = mockk(relaxed = true)
        every { prompt.confirm(any()) } returns response

        val out = RealBeforeUnloadPromptWrapper(prompt).confirm(true)

        verify { prompt.confirm(AllowOrDeny.ALLOW) }
        assertSame(response, out)
    }

    @Test
    fun `RealBeforeUnloadPromptWrapper confirm(false) denies navigation`() {
        val prompt: GeckoSession.PromptDelegate.BeforeUnloadPrompt = mockk(relaxed = true)
        every { prompt.confirm(any()) } returns response

        RealBeforeUnloadPromptWrapper(prompt).confirm(false)

        verify { prompt.confirm(AllowOrDeny.DENY) }
    }

    // ========== RealAuthPromptWrapper ==========

    @Test
    fun `RealAuthPromptWrapper confirm forwards username and password`() {
        val prompt: GeckoSession.PromptDelegate.AuthPrompt = mockk(relaxed = true)
        every { prompt.confirm(any<String>(), any<String>()) } returns response

        val out = RealAuthPromptWrapper(prompt).confirm("alice", "hunter2")

        verify { prompt.confirm("alice", "hunter2") }
        assertSame(response, out)
    }

    @Test
    fun `RealAuthPromptWrapper dismiss forwards to prompt`() {
        val prompt: GeckoSession.PromptDelegate.AuthPrompt = mockk(relaxed = true)
        every { prompt.dismiss() } returns response

        val out = RealAuthPromptWrapper(prompt).dismiss()

        verify { prompt.dismiss() }
        assertSame(response, out)
    }

    @Test
    fun `RealAuthPromptWrapper exposes title and message`() {
        val prompt: GeckoSession.PromptDelegate.AuthPrompt = mockk(relaxed = true)
        TestHelpers.setField(prompt, "title", "Sign in")
        TestHelpers.setField(prompt, "message", "Enter credentials")

        val wrapper = RealAuthPromptWrapper(prompt)
        assertEquals("Sign in", wrapper.title)
        assertEquals("Enter credentials", wrapper.message)
    }

    @Test
    fun `RealAuthPromptWrapper maps authOptions fields`() {
        val prompt: GeckoSession.PromptDelegate.AuthPrompt = mockk(relaxed = true)
        val options: GeckoSession.PromptDelegate.AuthPrompt.AuthOptions = mockk(relaxed = true)
        TestHelpers.setField(options, "uri", "https://auth.example")
        TestHelpers.setField(options, "level", 1)
        TestHelpers.setField(options, "username", "bob")
        TestHelpers.setField(options, "flags", 7)
        TestHelpers.setField(prompt, "authOptions", options)

        val map = RealAuthPromptWrapper(prompt).authOptions
        assertEquals("https://auth.example", map["uri"])
        assertEquals(1, map["level"])
        assertEquals("bob", map["username"])
        assertEquals(7, map["flags"])
    }

    // ========== DefaultPromptWrapperFactory ==========

    @Test
    fun `factory produces a RealPopupPromptWrapper`() {
        val prompt: GeckoSession.PromptDelegate.PopupPrompt = mockk(relaxed = true)
        val wrapper = DefaultPromptWrapperFactory().createPopupPromptWrapper(prompt)
        assertTrue(wrapper is RealPopupPromptWrapper)
    }

    @Test
    fun `factory produces a RealBeforeUnloadPromptWrapper`() {
        val prompt: GeckoSession.PromptDelegate.BeforeUnloadPrompt = mockk(relaxed = true)
        val wrapper = DefaultPromptWrapperFactory().createBeforeUnloadPromptWrapper(prompt)
        assertTrue(wrapper is RealBeforeUnloadPromptWrapper)
    }

    @Test
    fun `factory produces a RealAuthPromptWrapper`() {
        val prompt: GeckoSession.PromptDelegate.AuthPrompt = mockk(relaxed = true)
        val wrapper = DefaultPromptWrapperFactory().createAuthPromptWrapper(prompt)
        assertTrue(wrapper is RealAuthPromptWrapper)
    }
}
