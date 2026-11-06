package com.reactnative.geckoview

import com.facebook.react.module.annotations.ReactModule
import com.facebook.react.uimanager.SimpleViewManager
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.uimanager.ViewManagerDelegate
import com.facebook.react.uimanager.annotations.ReactProp
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.viewmanagers.GeckoViewManagerDelegate
import com.facebook.react.viewmanagers.GeckoViewManagerInterface
import org.mozilla.geckoview.GeckoView

@ReactModule(name = GeckoViewManagerImpl.NAME)
class GeckoViewManager :
    SimpleViewManager<GeckoView>(),
    GeckoViewManagerInterface<GeckoView> {

    private val impl = GeckoViewManagerImpl()
    private val delegate: ViewManagerDelegate<GeckoView> = GeckoViewManagerDelegate(this)

    override fun getDelegate(): ViewManagerDelegate<GeckoView> = delegate

    override fun getName() = impl.getName()

    override fun createViewInstance(context: ThemedReactContext): GeckoView =
        impl.createViewInstance(context)

    override fun onDropViewInstance(view: GeckoView) {
        impl.onDropViewInstance(view)
        super.onDropViewInstance(view)
    }

    override fun onAfterUpdateTransaction(view: GeckoView) {
        super.onAfterUpdateTransaction(view)
        impl.onAfterUpdateTransaction(view)
    }

    @ReactProp(name = "sessionKey")
    override fun setSessionKey(view: GeckoView, sessionKey: String?) =
        impl.setSessionKey(view, sessionKey)

    @ReactProp(name = "source")
    override fun setSource(view: GeckoView, source: ReadableMap?) =
        impl.setSource(view, source)

    @ReactProp(name = "handleAudioFocus", defaultBoolean = false)
    override fun setHandleAudioFocus(view: GeckoView, handleAudioFocus: Boolean) =
        impl.setHandleAudioFocus(view, handleAudioFocus)

    @ReactProp(name = "contentBlockingEnabled")
    override fun setContentBlockingEnabled(view: GeckoView, value: Boolean) =
        impl.setContentBlockingEnabled(view, value)

    @ReactProp(name = "cookieBannerMode")
    override fun setCookieBannerMode(view: GeckoView, value: String?) =
        impl.setCookieBannerMode(view, value)

    @ReactProp(name = "desktopMode", defaultBoolean = true)
    override fun setDesktopMode(view: GeckoView, value: Boolean) =
        impl.setDesktopMode(view, value)

    override fun getExportedCustomDirectEventTypeConstants(): Map<String, Any> = mapOf(
        "topGeckoPageStart" to mapOf("registrationName" to "onGeckoPageStart"),
        "topGeckoPageStop" to mapOf("registrationName" to "onGeckoPageStop"),
        "topGeckoProgressChange" to mapOf("registrationName" to "onGeckoProgressChange"),
        "topGeckoSecurityChange" to mapOf("registrationName" to "onGeckoSecurityChange"),
        "topGeckoLocationChange" to mapOf("registrationName" to "onGeckoLocationChange"),
        "topPageError" to mapOf("registrationName" to "onPageError"),
        "topMediaSessionAction" to mapOf("registrationName" to "onMediaSessionAction"),
        "topGeckoAlert" to mapOf("registrationName" to "onGeckoAlert"),
        "topGeckoConfirm" to mapOf("registrationName" to "onGeckoConfirm"),
        "topGeckoPrompt" to mapOf("registrationName" to "onGeckoPrompt"),
        "topGeckoPopup" to mapOf("registrationName" to "onGeckoPopup"),
        "topGeckoBeforeUnload" to mapOf("registrationName" to "onGeckoBeforeUnload"),
        "topGeckoAuth" to mapOf("registrationName" to "onGeckoAuth"),
        "topPermissionRequest" to mapOf("registrationName" to "onPermissionRequest"),
        "topWebExtensionMessage" to mapOf("registrationName" to "onWebExtensionMessage"),
        "topTitleChange" to mapOf("registrationName" to "onTitleChange"),
        "topContextMenu" to mapOf("registrationName" to "onContextMenu"),
        "topFocusExit" to mapOf("registrationName" to "onFocusExit"),
    )
}
