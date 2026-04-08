package com.reactnative.geckoview

import com.facebook.react.bridge.Arguments
import org.mozilla.geckoview.GeckoSession

class GeckoViewContentDelegate(
    private val module: GeckoViewModule,
    private val viewId: Int
) : GeckoSession.ContentDelegate {

    override fun onTitleChange(session: GeckoSession, title: String?) {
        val safeTitle = title ?: ""
        // Spatial Navigation Hack: Check for focus exit prefix
        if (safeTitle.startsWith("__FOCUS_EXIT__:")) {
            val direction = safeTitle.substringAfter("__FOCUS_EXIT__:")
            val params = Arguments.createMap().apply {
                putString("direction", direction.lowercase())
            }
            module.sendViewEvent(viewId, "onFocusExit", params)
        } else {
            val params = Arguments.createMap().apply {
                putString("title", safeTitle)
            }
            module.sendViewEvent(viewId, "onTitleChange", params)
        }
    }

    override fun onContextMenu(
        session: GeckoSession,
        screenX: Int,
        screenY: Int,
        element: GeckoSession.ContentDelegate.ContextElement
    ) {
        val params = Arguments.createMap().apply {
            putInt("x", screenX)
            putInt("y", screenY)
            putString("linkUri", element.linkUri)
            putString("srcUri", element.srcUri)
            putString("type", when (element.type) {
                GeckoSession.ContentDelegate.ContextElement.TYPE_IMAGE -> "image"
                GeckoSession.ContentDelegate.ContextElement.TYPE_VIDEO -> "video"
                GeckoSession.ContentDelegate.ContextElement.TYPE_AUDIO -> "audio"
                else -> "none"
            })
        }
        module.sendViewEvent(viewId, "onContextMenu", params)
    }
}
