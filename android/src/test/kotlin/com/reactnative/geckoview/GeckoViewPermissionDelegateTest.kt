package com.reactnative.geckoview

import io.mockk.mockk
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.geckoview.GeckoSession
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for [GeckoViewPermissionDelegate].
 *
 * GeckoView's `ContentPermission` exposes `permission` and `uri` as public
 * final fields, which MockK cannot stub directly. Values are set via
 * [TestHelpers.setField] after creating the mock.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class GeckoViewPermissionDelegateTest {

    private val module: GeckoViewModule = mockk(relaxed = true)
    private val session: GeckoSession = mockk(relaxed = true)
    private lateinit var permissionDelegate: GeckoViewPermissionDelegate
    private val testViewId = 101

    @Before
    fun setUp() {
        TestHelpers.mockArguments()
        GeckoViewPermissionDelegate.autoGrantDrm = false
        GeckoViewPermissionDelegate.autoGrantAutoplay = false
        permissionDelegate = GeckoViewPermissionDelegate(module, testViewId)
    }

    @After
    fun tearDown() {
        TestHelpers.unmockArguments()
        GeckoViewPermissionDelegate.autoGrantDrm = false
        GeckoViewPermissionDelegate.autoGrantAutoplay = false
    }

    private fun mockPermission(
        permissionType: Int,
        uri: String = "https://example.com",
    ): GeckoSession.PermissionDelegate.ContentPermission {
        val perm = mockk<GeckoSession.PermissionDelegate.ContentPermission>(relaxed = true)
        TestHelpers.setField(perm, "permission", permissionType)
        TestHelpers.setField(perm, "uri", uri)
        return perm
    }

    @Test
    fun `permission delegate can be instantiated`() {
        assertNotNull(permissionDelegate)
    }

    @Test
    fun `onContentPermissionRequest prompts for media key system access by default`() {
        val perm = mockPermission(GeckoSession.PermissionDelegate.PERMISSION_MEDIA_KEY_SYSTEM_ACCESS)

        val result = permissionDelegate.onContentPermissionRequest(session, perm)

        assertNotNull(result)
        verify(exactly = 1) { module.sendViewEvent(testViewId, "onPermissionRequest", any()) }
        verify(exactly = 1) { module.storePendingPermission(any(), any()) }
    }

    @Test
    fun `onContentPermissionRequest prompts for autoplay audible by default`() {
        val perm = mockPermission(GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_AUDIBLE)

        val result = permissionDelegate.onContentPermissionRequest(session, perm)

        assertNotNull(result)
        verify(exactly = 1) { module.sendViewEvent(testViewId, "onPermissionRequest", any()) }
    }

    @Test
    fun `onContentPermissionRequest prompts for autoplay inaudible by default`() {
        val perm = mockPermission(GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_INAUDIBLE)

        val result = permissionDelegate.onContentPermissionRequest(session, perm)

        assertNotNull(result)
        verify(exactly = 1) { module.sendViewEvent(testViewId, "onPermissionRequest", any()) }
    }

    @Test
    fun `onContentPermissionRequest prompts for geolocation`() {
        val perm = mockPermission(GeckoSession.PermissionDelegate.PERMISSION_GEOLOCATION)

        val result = permissionDelegate.onContentPermissionRequest(session, perm)

        assertNotNull(result)
        verify { module.sendViewEvent(testViewId, "onPermissionRequest", any()) }
        verify { module.storePendingPermission(any(), any()) }
    }

    @Test
    fun `onContentPermissionRequest prompts for notifications`() {
        val perm = mockPermission(GeckoSession.PermissionDelegate.PERMISSION_DESKTOP_NOTIFICATION)

        val result = permissionDelegate.onContentPermissionRequest(session, perm)

        assertNotNull(result)
        verify { module.sendViewEvent(testViewId, "onPermissionRequest", any()) }
    }

    @Test
    fun `onContentPermissionRequest prompts for persistent storage`() {
        val perm = mockPermission(GeckoSession.PermissionDelegate.PERMISSION_PERSISTENT_STORAGE)

        val result = permissionDelegate.onContentPermissionRequest(session, perm)

        assertNotNull(result)
        verify { module.sendViewEvent(testViewId, "onPermissionRequest", any()) }
    }

    @Test
    fun `onContentPermissionRequest emits for unknown permission types`() {
        val perm = mockPermission(Int.MAX_VALUE)

        val result = permissionDelegate.onContentPermissionRequest(session, perm)

        assertNotNull(result)
        verify { module.sendViewEvent(testViewId, "onPermissionRequest", any()) }
    }

    @Test
    fun `each prompted request registers a distinct pending result`() {
        val perm1 = mockPermission(GeckoSession.PermissionDelegate.PERMISSION_GEOLOCATION, "https://a.com")
        val perm2 = mockPermission(GeckoSession.PermissionDelegate.PERMISSION_DESKTOP_NOTIFICATION, "https://b.com")

        val first = permissionDelegate.onContentPermissionRequest(session, perm1)
        val second = permissionDelegate.onContentPermissionRequest(session, perm2)

        assertNotNull(first)
        assertNotNull(second)
        verify(exactly = 2) { module.sendViewEvent(testViewId, "onPermissionRequest", any()) }
        verify(exactly = 2) { module.storePendingPermission(any(), any()) }
    }

    // ========== explicit DRM/autoplay opt-in ==========

    @Test
    fun `autoGrantDrm=true auto-grants DRM`() {
        GeckoViewPermissionDelegate.autoGrantDrm = true
        try {
            val perm = mockPermission(GeckoSession.PermissionDelegate.PERMISSION_MEDIA_KEY_SYSTEM_ACCESS)
            permissionDelegate.onContentPermissionRequest(session, perm)
            verify(exactly = 0) { module.sendViewEvent(any(), any(), any()) }
            verify(exactly = 0) { module.storePendingPermission(any(), any()) }
        } finally {
            GeckoViewPermissionDelegate.autoGrantDrm = false
        }
    }

    @Test
    fun `autoGrantAutoplay=true auto-grants autoplay`() {
        GeckoViewPermissionDelegate.autoGrantAutoplay = true
        try {
            val audible = mockPermission(GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_AUDIBLE)
            val inaudible = mockPermission(GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_INAUDIBLE)
            permissionDelegate.onContentPermissionRequest(session, audible)
            permissionDelegate.onContentPermissionRequest(session, inaudible)
            verify(exactly = 0) { module.sendViewEvent(any(), any(), any()) }
        } finally {
            GeckoViewPermissionDelegate.autoGrantAutoplay = false
        }
    }

    @Test
    fun `autoGrant flags default to false`() {
        assert(!GeckoViewPermissionDelegate.autoGrantDrm)
        assert(!GeckoViewPermissionDelegate.autoGrantAutoplay)
        val perm = mockPermission(GeckoSession.PermissionDelegate.PERMISSION_MEDIA_KEY_SYSTEM_ACCESS)
        permissionDelegate.onContentPermissionRequest(session, perm)
        verify(exactly = 1) { module.sendViewEvent(testViewId, "onPermissionRequest", any()) }
    }

    // ========== getPermissionString — full constant mapping ==========

    @Test
    fun `getPermissionString maps geolocation`() {
        assertEquals(
            "geolocation",
            permissionDelegate.getPermissionString(GeckoSession.PermissionDelegate.PERMISSION_GEOLOCATION),
        )
    }

    @Test
    fun `getPermissionString maps desktop notification`() {
        assertEquals(
            "notification",
            permissionDelegate.getPermissionString(GeckoSession.PermissionDelegate.PERMISSION_DESKTOP_NOTIFICATION),
        )
    }

    @Test
    fun `getPermissionString maps persistent storage`() {
        assertEquals(
            "persistent_storage",
            permissionDelegate.getPermissionString(GeckoSession.PermissionDelegate.PERMISSION_PERSISTENT_STORAGE),
        )
    }

    @Test
    fun `getPermissionString maps media key system access`() {
        assertEquals(
            "media_key_system_access",
            permissionDelegate.getPermissionString(GeckoSession.PermissionDelegate.PERMISSION_MEDIA_KEY_SYSTEM_ACCESS),
        )
    }

    @Test
    fun `getPermissionString maps autoplay audible`() {
        assertEquals(
            "autoplay_audible",
            permissionDelegate.getPermissionString(GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_AUDIBLE),
        )
    }

    @Test
    fun `getPermissionString maps autoplay inaudible`() {
        assertEquals(
            "autoplay_inaudible",
            permissionDelegate.getPermissionString(GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_INAUDIBLE),
        )
    }

    @Test
    fun `getPermissionString maps unknown constant to unknown`() {
        assertEquals("unknown", permissionDelegate.getPermissionString(Int.MAX_VALUE))
        assertEquals("unknown", permissionDelegate.getPermissionString(-999))
    }
}
