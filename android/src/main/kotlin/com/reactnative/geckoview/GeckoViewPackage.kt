package com.reactnative.geckoview

import android.util.Log
import com.facebook.react.TurboReactPackage
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.module.model.ReactModuleInfo
import com.facebook.react.module.model.ReactModuleInfoProvider
import com.facebook.react.uimanager.ViewManager
import com.facebook.soloader.SoLoader

class GeckoViewPackage : TurboReactPackage() {
    
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
                true,  // needsEagerInit - CHANGED to true for early initialization
                false, // hasConstants
                false, // isCxxModule
                false  // isTurboModule
            )
        )

        private fun ensureCodegenLoaded() {
            try {
                SoLoader.loadLibrary("react_codegen_GeckoViewSpec")
                Log.d(TAG, "Loaded react_codegen_GeckoViewSpec")
            } catch (e: UnsatisfiedLinkError) {
                Log.w(TAG, "Fabric codegen lib not available (Paper build or codegen not generated)", e)
            }
        }

        init {
            ensureCodegenLoaded()
        }
    }
    
    override fun createNativeModules(reactContext: ReactApplicationContext): List<NativeModule> {
        Log.d(TAG, "createNativeModules called")
        val module = GeckoViewModule(reactContext)
        moduleInstance = module
        return listOf(module)
    }

    override fun createViewManagers(reactContext: ReactApplicationContext): List<ViewManager<*, *>> {
        ensureCodegenLoaded()
        return listOf(GeckoViewManager())
    }

    override fun getReactModuleInfoProvider(): ReactModuleInfoProvider =
        ReactModuleInfoProvider {
            MODULE_INFOS
        }

    override fun getModule(name: String, reactContext: ReactApplicationContext): NativeModule? {
        Log.d(TAG, "getModule called for: $name")
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
