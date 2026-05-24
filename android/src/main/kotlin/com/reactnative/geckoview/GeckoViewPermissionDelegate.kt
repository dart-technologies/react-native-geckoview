package com.reactnative.geckoview

import com.facebook.react.bridge.Arguments
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import java.util.UUID

/**
 * Permission delegate that surfaces most permission requests to host-app JS
 * (`onPermissionRequest`). DRM key-system access and autoplay are prompted by
 * default; host apps can explicitly opt in before runtime init via
 * `configureGeckoViewSecurity(...)`.
 *
 * Rationale: DRM is a fingerprinting vector and autoplay has privacy,
 * battery, and bandwidth costs. Security-first defaults require an explicit
 * host-app choice before these requests are granted without a JS prompt.
 */
class GeckoViewPermissionDelegate(
    private val module: GeckoViewModule,
    private val viewId: Int
) : GeckoSession.PermissionDelegate {

    override fun onContentPermissionRequest(
        session: GeckoSession,
        perm: GeckoSession.PermissionDelegate.ContentPermission
    ): GeckoResult<Int>? {
        val isDrm = perm.permission == GeckoSession.PermissionDelegate.PERMISSION_MEDIA_KEY_SYSTEM_ACCESS
        val isAutoplay =
            perm.permission == GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_AUDIBLE ||
                perm.permission == GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_INAUDIBLE

        if ((isDrm && autoGrantDrm) || (isAutoplay && autoGrantAutoplay)) {
            return GeckoResult.fromValue(GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW)
        }

        return handlePermissionRequest(perm.permission, perm.uri)
    }

    private fun handlePermissionRequest(permission: Int, uri: String): GeckoResult<Int> {
        val result = GeckoResult<Int>()
        val requestId = UUID.randomUUID().toString()

        module.storePendingPermission(requestId, result)

        val params = Arguments.createMap().apply {
            putString("requestId", requestId)
            putString("permission", getPermissionString(permission))
            putString("uri", uri)
        }
        module.sendViewEvent(viewId, "onPermissionRequest", params)

        return result
    }

    internal fun getPermissionString(permission: Int): String {
        return when (permission) {
            GeckoSession.PermissionDelegate.PERMISSION_GEOLOCATION -> "geolocation"
            GeckoSession.PermissionDelegate.PERMISSION_DESKTOP_NOTIFICATION -> "notification"
            GeckoSession.PermissionDelegate.PERMISSION_PERSISTENT_STORAGE -> "persistent_storage"
            GeckoSession.PermissionDelegate.PERMISSION_MEDIA_KEY_SYSTEM_ACCESS -> "media_key_system_access"
            GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_AUDIBLE -> "autoplay_audible"
            GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_INAUDIBLE -> "autoplay_inaudible"
            else -> "unknown"
        }
    }

    companion object {
        /**
         * Explicit opt-ins set from [GeckoSecurityConfig]. Defaults are false.
         */
        @Volatile
        @JvmStatic
        var autoGrantDrm: Boolean = false

        @Volatile
        @JvmStatic
        var autoGrantAutoplay: Boolean = false
    }
}
