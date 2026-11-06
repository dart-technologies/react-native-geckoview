package com.reactnative.geckoview

import com.facebook.react.bridge.WritableMap
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.geckoview.GeckoSession
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for [GeckoViewContentDelegate] — title changes, focus-exit hack,
 * context-menu forwarding.
 *
 * `GeckoSession.ContentDelegate.ContextElement` exposes `type`/`linkUri`/`srcUri`
 * as public-final fields (not getters), so MockK cannot stub them directly. We
 * set them via reflection on a relaxed mock — the same pattern used for
 * `WebRequestError.code` and `WebExtension.id` (see [TestHelpers.setField]).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class GeckoViewContentDelegateTest {

    private val module: GeckoViewModule = mockk(relaxed = true)
    private val session: GeckoSession = mockk(relaxed = true)
    private lateinit var contentDelegate: GeckoViewContentDelegate
    private val testViewId = 789

    @Before
    fun setUp() {
        TestHelpers.mockArguments()
        contentDelegate = GeckoViewContentDelegate(module, testViewId)
    }

    @After
    fun tearDown() {
        TestHelpers.unmockArguments()
    }

    // Title Change Events

    @Test
    fun `onTitleChange emits onTitleChange event with title`() {
        contentDelegate.onTitleChange(session, "Example Page Title")
        verify { module.sendViewEvent(testViewId, "onTitleChange", any()) }
    }

    @Test
    fun `onTitleChange handles null title`() {
        contentDelegate.onTitleChange(session, null)
        verify { module.sendViewEvent(testViewId, "onTitleChange", any()) }
    }

    @Test
    fun `onTitleChange handles empty title`() {
        contentDelegate.onTitleChange(session, "")
        verify { module.sendViewEvent(testViewId, "onTitleChange", any()) }
    }

    @Test
    fun `onTitleChange handles long title`() {
        contentDelegate.onTitleChange(session, "A".repeat(1000))
        verify { module.sendViewEvent(testViewId, "onTitleChange", any()) }
    }

    @Test
    fun `onTitleChange handles unicode titles`() {
        val titles = listOf(
            "日本語タイトル",
            "Titre en français",
            "Título en español",
            "🎉 Emoji Title 🚀",
            "العنوان بالعربية",
        )
        titles.forEach { contentDelegate.onTitleChange(session, it) }

        verify(exactly = titles.size) {
            module.sendViewEvent(testViewId, "onTitleChange", any())
        }
    }

    @Test
    fun `onTitleChange handles HTML entities in title`() {
        contentDelegate.onTitleChange(session, "Page &amp; Title &lt;test&gt;")
        verify { module.sendViewEvent(testViewId, "onTitleChange", any()) }
    }

    @Test
    fun `onTitleChange handles special characters`() {
        contentDelegate.onTitleChange(session, "Title with \"quotes\" and 'apostrophes'")
        verify { module.sendViewEvent(testViewId, "onTitleChange", any()) }
    }

    @Test
    fun `onTitleChange handles whitespace-only title`() {
        contentDelegate.onTitleChange(session, "   \t\n  ")
        verify { module.sendViewEvent(testViewId, "onTitleChange", any()) }
    }

    // Focus Exit Hack Detection

    @Test
    fun `onTitleChange detects focus exit up`() {
        contentDelegate.onTitleChange(session, "__FOCUS_EXIT__:UP")
        verify { module.sendViewEvent(testViewId, "onFocusExit", any()) }
        verify(exactly = 0) { module.sendViewEvent(testViewId, "onTitleChange", any()) }
    }

    @Test
    fun `onTitleChange detects focus exit down`() {
        contentDelegate.onTitleChange(session, "__FOCUS_EXIT__:DOWN")
        verify { module.sendViewEvent(testViewId, "onFocusExit", any()) }
        verify(exactly = 0) { module.sendViewEvent(testViewId, "onTitleChange", any()) }
    }

    @Test
    fun `onTitleChange detects focus exit left`() {
        contentDelegate.onTitleChange(session, "__FOCUS_EXIT__:LEFT")
        verify { module.sendViewEvent(testViewId, "onFocusExit", any()) }
        verify(exactly = 0) { module.sendViewEvent(testViewId, "onTitleChange", any()) }
    }

    @Test
    fun `onTitleChange detects focus exit right`() {
        contentDelegate.onTitleChange(session, "__FOCUS_EXIT__:RIGHT")
        verify { module.sendViewEvent(testViewId, "onFocusExit", any()) }
        verify(exactly = 0) { module.sendViewEvent(testViewId, "onTitleChange", any()) }
    }

    @Test
    fun `onTitleChange normalizes focus exit direction to lowercase`() {
        contentDelegate.onTitleChange(session, "__FOCUS_EXIT__:UP")
        verify { module.sendViewEvent(testViewId, "onFocusExit", any()) }
    }

    @Test
    fun `onTitleChange ignores similar but non-matching prefix`() {
        val titles = listOf(
            "_FOCUS_EXIT__:UP",
            "__FOCUS_EXIT_:UP",
            "FOCUS_EXIT:UP",
            "__FOCUS_EXIT:UP",
        )
        titles.forEach { contentDelegate.onTitleChange(session, it) }

        verify(exactly = titles.size) {
            module.sendViewEvent(testViewId, "onTitleChange", any())
        }
        verify(exactly = 0) { module.sendViewEvent(testViewId, "onFocusExit", any()) }
    }

    @Test
    fun `onTitleChange handles focus exit with lowercase direction`() {
        contentDelegate.onTitleChange(session, "__FOCUS_EXIT__:up")
        verify { module.sendViewEvent(testViewId, "onFocusExit", any()) }
    }

    // Integration Tests

    @Test
    fun `multiple title changes emit correct events`() {
        listOf("Page 1", "Page 2", "Page 3").forEach {
            contentDelegate.onTitleChange(session, it)
        }
        verify(exactly = 3) { module.sendViewEvent(testViewId, "onTitleChange", any()) }
    }

    @Test
    fun `focus exit followed by regular title change works correctly`() {
        contentDelegate.onTitleChange(session, "__FOCUS_EXIT__:UP")
        contentDelegate.onTitleChange(session, "Normal Title")

        verify(exactly = 1) { module.sendViewEvent(testViewId, "onFocusExit", any()) }
        verify(exactly = 1) { module.sendViewEvent(testViewId, "onTitleChange", any()) }
    }

    @Test
    fun `rapid title changes do not cause issues`() {
        repeat(100) { contentDelegate.onTitleChange(session, "Title $it") }
        verify(exactly = 100) { module.sendViewEvent(testViewId, "onTitleChange", any()) }
    }

    // Context Menu Events
    //
    // ContextElement.type/linkUri/srcUri are public-final fields; set them via
    // reflection on a relaxed mock so the `when(type)` mapping is exercised.

    private fun contextElement(
        type: Int,
        linkUri: String? = null,
        srcUri: String? = null,
    ): GeckoSession.ContentDelegate.ContextElement {
        val el = mockk<GeckoSession.ContentDelegate.ContextElement>(relaxed = true)
        TestHelpers.setField(el, "type", type)
        TestHelpers.setField(el, "linkUri", linkUri)
        TestHelpers.setField(el, "srcUri", srcUri)
        return el
    }

    @Test
    fun `onContextMenu maps TYPE_IMAGE to image and forwards coordinates and uris`() {
        val params = slot<WritableMap>()
        every { module.sendViewEvent(testViewId, "onContextMenu", capture(params)) } returns Unit

        val element = contextElement(
            GeckoSession.ContentDelegate.ContextElement.TYPE_IMAGE,
            linkUri = "https://example.com/page",
            srcUri = "https://example.com/cat.png",
        )
        contentDelegate.onContextMenu(session, 12, 34, element)

        verify { module.sendViewEvent(testViewId, "onContextMenu", any()) }
        assertEquals("image", params.captured.getString("type"))
        assertEquals(12, params.captured.getInt("x"))
        assertEquals(34, params.captured.getInt("y"))
        assertEquals("https://example.com/page", params.captured.getString("linkUri"))
        assertEquals("https://example.com/cat.png", params.captured.getString("srcUri"))
    }

    @Test
    fun `onContextMenu maps TYPE_VIDEO to video`() {
        val params = slot<WritableMap>()
        every { module.sendViewEvent(testViewId, "onContextMenu", capture(params)) } returns Unit

        contentDelegate.onContextMenu(
            session, 0, 0,
            contextElement(GeckoSession.ContentDelegate.ContextElement.TYPE_VIDEO, srcUri = "https://v.example/m.mp4"),
        )

        assertEquals("video", params.captured.getString("type"))
        assertEquals("https://v.example/m.mp4", params.captured.getString("srcUri"))
    }

    @Test
    fun `onContextMenu maps TYPE_AUDIO to audio`() {
        val params = slot<WritableMap>()
        every { module.sendViewEvent(testViewId, "onContextMenu", capture(params)) } returns Unit

        contentDelegate.onContextMenu(
            session, 0, 0,
            contextElement(GeckoSession.ContentDelegate.ContextElement.TYPE_AUDIO),
        )

        assertEquals("audio", params.captured.getString("type"))
    }

    @Test
    fun `onContextMenu maps TYPE_NONE to none`() {
        val params = slot<WritableMap>()
        every { module.sendViewEvent(testViewId, "onContextMenu", capture(params)) } returns Unit

        contentDelegate.onContextMenu(
            session, 0, 0,
            contextElement(GeckoSession.ContentDelegate.ContextElement.TYPE_NONE, linkUri = "https://link.example"),
        )

        assertEquals("none", params.captured.getString("type"))
        assertEquals("https://link.example", params.captured.getString("linkUri"))
    }

    @Test
    fun `onContextMenu maps an unrecognized type to none`() {
        val params = slot<WritableMap>()
        every { module.sendViewEvent(testViewId, "onContextMenu", capture(params)) } returns Unit

        contentDelegate.onContextMenu(session, 0, 0, contextElement(Int.MAX_VALUE))

        assertEquals("none", params.captured.getString("type"))
    }

    @Test
    fun `onContextMenu forwards null link and src uris`() {
        val params = slot<WritableMap>()
        every { module.sendViewEvent(testViewId, "onContextMenu", capture(params)) } returns Unit

        contentDelegate.onContextMenu(
            session, 5, 6,
            contextElement(GeckoSession.ContentDelegate.ContextElement.TYPE_IMAGE),
        )

        verify { module.sendViewEvent(testViewId, "onContextMenu", any()) }
        assertEquals("image", params.captured.getString("type"))
    }

    // Content process death (Codex final review F1)

    @Test
    fun `a crash gives up the focus the session's media delegate holds`() {
        // GeckoView has closed the session, and no pause or stop follows.
        val media: MediaDelegate = mockk(relaxed = true)
        every { session.mediaSessionDelegate } returns media

        contentDelegate.onCrash(session)

        verify(exactly = 1) { media.release() }
    }

    @Test
    fun `a kill gives up the focus the session's media delegate holds`() {
        val media: MediaDelegate = mockk(relaxed = true)
        every { session.mediaSessionDelegate } returns media

        contentDelegate.onKill(session)

        verify(exactly = 1) { media.release() }
    }
}
