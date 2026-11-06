package com.reactnative.geckoview

import com.facebook.react.bridge.Arguments
import org.mozilla.geckoview.GeckoSession

class GeckoViewContentDelegate(
    private val module: GeckoViewModule,
    private val viewId: Int
) : GeckoSession.ContentDelegate {

    override fun onTitleChange(session: GeckoSession, title: String?) {
        val safeTitle = title ?: ""
        // Legacy spatial-navigation focus-exit fallback: older builds (and the
        // extension's no-bridge injected-script path) signal focus exit via a
        // document.title prefix. The primary path for the installed extension is
        // now a native `focusExit` message handled in WebExtensionController.
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

    override fun onCrash(session: GeckoSession) = MediaDelegate.releaseFor(session)

    override fun onKill(session: GeckoSession) = MediaDelegate.releaseFor(session)
}
