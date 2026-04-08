# Android Native Module

Kotlin implementation of GeckoView wrapper for React Native.

## Structure

```
src/main/kotlin/com/reactnative/geckoview/
├── GeckoViewPackage.kt          # Package registration
├── GeckoViewManager.kt          # View manager (Paper/Fabric)
├── GeckoViewManagerImpl.kt      # View manager implementation
├── GeckoViewModule.kt           # Native module commands
├── GeckoSessionManager.kt       # Session lifecycle
├── GeckoRuntimeManager.kt       # Singleton runtime
├── MediaDelegate.kt             # Audio focus & media
├── GeckoViewNavigationDelegate.kt
├── GeckoViewProgressDelegate.kt
├── GeckoViewPermissionDelegate.kt
├── GeckoViewPromptDelegate.kt
├── GeckoViewContentDelegate.kt
├── ContentBlockingController.kt
└── WebExtensionController.kt
```

## Building

### Debug Build
```bash
cd example/android
./gradlew :react-native-geckoview:assembleDebug
```

### Run Tests (191 tests)
```bash
./gradlew :react-native-geckoview:testDebugUnitTest
```

### Coverage Report
```bash
./gradlew :react-native-geckoview:jacocoTestReport
```

## Fabric Support

When `newArchEnabled=true`:
- CMake builds `libreact_codegen_GeckoViewSpec.so`
- Autolinking registers `GeckoViewComponentDescriptor`
- See `src/main/jni/` for native build files

## Dependencies

- GeckoView 143.0.20250908174027
- Kotlin 1.9+
- React Native 0.81+
