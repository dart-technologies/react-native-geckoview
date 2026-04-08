package com.reactnative.geckoview

import com.facebook.react.bridge.Arguments
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import java.util.UUID

class GeckoViewPermissionDelegate(
    private val module: GeckoViewModule,
    private val viewId: Int
) : GeckoSession.PermissionDelegate {

    override fun onContentPermissionRequest(
        session: GeckoSession,
        perm: GeckoSession.PermissionDelegate.ContentPermission
    ): GeckoResult<Int>? {
        // Auto-grant DRM and Autoplay
        if (perm.permission == GeckoSession.PermissionDelegate.PERMISSION_MEDIA_KEY_SYSTEM_ACCESS ||
            perm.permission == GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_AUDIBLE ||
            perm.permission == GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_INAUDIBLE) {
            return GeckoResult.fromValue(GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW)
        }

        // Request user approval for others
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

    private fun getPermissionString(permission: Int): String {
        return when (permission) {
            GeckoSession.PermissionDelegate.PERMISSION_GEOLOCATION -> "geolocation"
            GeckoSession.PermissionDelegate.PERMISSION_DESKTOP_NOTIFICATION -> "notification"
            GeckoSession.PermissionDelegate.PERMISSION_PERSISTENT_STORAGE -> "persistent_storage"
            else -> "unknown"
        }
    }
}
