package com.reactnative.geckoview

import android.content.Context
import android.util.Log
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings

object GeckoRuntimeManager {
    private var runtime: GeckoRuntime? = null
    private var webExtensionController: WebExtensionController? = null

    private fun applyDefaultContentBlockingSettings(runtime: GeckoRuntime) {
        try {
            val contentBlocking = runtime.settings.contentBlocking

            contentBlocking.setCookieBehavior(org.mozilla.geckoview.ContentBlocking.CookieBehavior.ACCEPT_NON_TRACKERS)
            contentBlocking.setCookieBehaviorPrivateMode(
                org.mozilla.geckoview.ContentBlocking.CookieBehavior.ACCEPT_NON_TRACKERS
            )
            Log.d(TAG, "Cookie behavior set to ACCEPT_NON_TRACKERS")
        } catch (e: Throwable) {
            Log.w(TAG, "Failed to set cookie behavior ACCEPT_NON_TRACKERS", e)
        }
    }

    @Synchronized
    fun getRuntime(context: Context): GeckoRuntime {
        if (runtime == null) {
            val settings = GeckoRuntimeSettings.Builder()
                .arguments(arrayOf(
                    "--disable-features=VizDisplayCompositor",
                    "--disable-gpu-sandbox",
                    "--no-sandbox",
                    "--setpref=media.autoplay.default=0",
                    "--setpref=media.autoplay.enabled=true",
                    "--setpref=media.geckoview.autoplay.request=false",
                    "--setpref=extensions.install.requireSecureOrigin=false",
                    "--setpref=xpinstall.signatures.required=false",
                    "--setpref=media.eme.enabled=true"
                ))
                .consoleOutput(true)
                .build()

            runtime = GeckoRuntime.create(context.applicationContext, settings)
            applyDefaultContentBlockingSettings(runtime!!)
            webExtensionController = WebExtensionController(runtime!!)
        }
        return runtime!!
    }

    fun getRuntime(): GeckoRuntime? = runtime

    fun getWebExtensionController(): WebExtensionController? = webExtensionController

    fun shutdown() {
        runtime?.shutdown()
        runtime = null
        webExtensionController = null
    }

    private const val TAG = "GeckoRuntimeManager"
}
