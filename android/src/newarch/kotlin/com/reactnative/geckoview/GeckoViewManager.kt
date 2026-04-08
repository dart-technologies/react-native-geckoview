package com.reactnative.geckoview

import com.facebook.react.common.MapBuilder
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

    override fun getExportedCustomDirectEventTypeConstants(): Map<String, Any>? {
        return MapBuilder.builder<String, Any>()
            .put("topGeckoPageStart", MapBuilder.of("registrationName", "onGeckoPageStart"))
            .put("topGeckoPageStop", MapBuilder.of("registrationName", "onGeckoPageStop"))
            .put("topGeckoProgressChange", MapBuilder.of("registrationName", "onGeckoProgressChange"))
            .put("topGeckoSecurityChange", MapBuilder.of("registrationName", "onGeckoSecurityChange"))
            .put("topGeckoLocationChange", MapBuilder.of("registrationName", "onGeckoLocationChange"))
            .put("topPageError", MapBuilder.of("registrationName", "onPageError"))
            .put("topMediaSessionAction", MapBuilder.of("registrationName", "onMediaSessionAction"))
            .put("topGeckoAlert", MapBuilder.of("registrationName", "onGeckoAlert"))
            .put("topGeckoConfirm", MapBuilder.of("registrationName", "onGeckoConfirm"))
            .put("topGeckoPrompt", MapBuilder.of("registrationName", "onGeckoPrompt"))
            .put("topGeckoPopup", MapBuilder.of("registrationName", "onGeckoPopup"))
            .put("topGeckoBeforeUnload", MapBuilder.of("registrationName", "onGeckoBeforeUnload"))
            .put("topGeckoAuth", MapBuilder.of("registrationName", "onGeckoAuth"))
            .put("topPermissionRequest", MapBuilder.of("registrationName", "onPermissionRequest"))
            .put("topWebExtensionMessage", MapBuilder.of("registrationName", "onWebExtensionMessage"))
            .put("topTitleChange", MapBuilder.of("registrationName", "onTitleChange"))
            .put("topContextMenu", MapBuilder.of("registrationName", "onContextMenu"))
            .put("topFocusExit", MapBuilder.of("registrationName", "onFocusExit"))
            .build()
    }
}
