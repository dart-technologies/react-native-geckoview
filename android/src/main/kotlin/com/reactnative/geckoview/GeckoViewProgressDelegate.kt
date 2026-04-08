package com.reactnative.geckoview

import com.facebook.react.bridge.Arguments
import org.mozilla.geckoview.GeckoSession

class GeckoViewProgressDelegate(
    private val module: GeckoViewModule,
    private val viewId: Int
) : GeckoSession.ProgressDelegate {

    override fun onPageStart(session: GeckoSession, url: String) {
        val params = Arguments.createMap().apply {
            putString("url", url)
        }
        module.sendViewEvent(viewId, "onGeckoPageStart", params)
    }

    override fun onPageStop(session: GeckoSession, success: Boolean) {
        val params = Arguments.createMap().apply {
            putBoolean("success", success)
        }
        module.sendViewEvent(viewId, "onGeckoPageStop", params)
    }

    override fun onProgressChange(session: GeckoSession, progress: Int) {
        val params = Arguments.createMap().apply {
            putInt("progress", progress)
        }
        module.sendViewEvent(viewId, "onGeckoProgressChange", params)
    }

    override fun onSecurityChange(
        session: GeckoSession,
        securityInfo: GeckoSession.ProgressDelegate.SecurityInformation
    ) {
        val params = Arguments.createMap().apply {
            putBoolean("isSecure", securityInfo.isSecure)
            putString("host", securityInfo.host)
        }
        module.sendViewEvent(viewId, "onGeckoSecurityChange", params)
    }
}
