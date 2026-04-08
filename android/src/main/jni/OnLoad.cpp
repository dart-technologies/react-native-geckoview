// OnLoad.cpp - JNI initialization for react-native-geckoview
// 
// NOTE: Component descriptor registration is handled automatically by
// React Native's autolinking system (via react-native.config.js).
// This file is reserved for any additional JNI initialization if needed.
//
// The autolinking system generates AutolinkingComponentsRegistry.cpp which
// calls GeckoViewSpec_registerComponentDescriptorsFromCodegen() automatically.

#include <fbjni/fbjni.h>

namespace facebook::react {

// JNI entry point - called when SoLoader loads libreact_codegen_geckoviewspec.so
// Currently empty - autolinking handles component registration.
// Add any additional library initialization here if needed.
extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *) {
  return facebook::jni::initialize(vm, [] {
    // Component registration handled by autolinking
    // Any additional initialization can go here
  });
}

} // namespace facebook::react
