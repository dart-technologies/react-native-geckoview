package com.reactnative.geckoview

import com.facebook.react.BaseReactPackage
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.module.model.ReactModuleInfo
import com.facebook.react.module.model.ReactModuleInfoProvider
import com.facebook.react.uimanager.ViewManager
import com.facebook.soloader.SoLoader

class GeckoViewPackage : BaseReactPackage() {

    companion object {
        private const val TAG = "GeckoViewPackage"

        // Singleton module instance - shared across all lookups
        @Volatile
        private var moduleInstance: GeckoViewModule? = null

        fun getModuleInstance(): GeckoViewModule? = moduleInstance

        private val MODULE_INFOS: Map<String, ReactModuleInfo> = mapOf(
            "GeckoViewModule" to ReactModuleInfo(
                "GeckoViewModule",
                "GeckoViewModule",
                false, // canOverrideExistingModule
                true,  // needsEagerInit
                false, // hasConstants
                false  // isTurboModule
            )
        )

        // Loaded exactly once (via the companion `init {}`) — subsequent
        // lookups through `createViewManagers` no-op.
        init {
            try {
                SoLoader.loadLibrary("react_codegen_GeckoViewSpec")
                GeckoLog.d(TAG, "Loaded react_codegen_GeckoViewSpec")
            } catch (e: UnsatisfiedLinkError) {
                GeckoLog.w(TAG, "Fabric codegen lib not available", e)
            }
        }
    }

    override fun createViewManagers(reactContext: ReactApplicationContext): List<ViewManager<*, *>> {
        return listOf(GeckoViewManager())
    }

    override fun getReactModuleInfoProvider(): ReactModuleInfoProvider =
        ReactModuleInfoProvider {
            MODULE_INFOS
        }

    override fun getModule(name: String, reactContext: ReactApplicationContext): NativeModule? {
        GeckoLog.d(TAG, "getModule called")
        return when (name) {
            "GeckoViewModule" -> {
                if (moduleInstance == null) {
                    moduleInstance = GeckoViewModule(reactContext)
                }
                moduleInstance
            }
            else -> null
        }
    }
}
