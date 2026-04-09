# Fabric Support

React Native GeckoView supports the New Architecture (Fabric) on React Native 0.81+.

## How It Works

1. **Autolinking** - `react-native.config.js` declares `GeckoViewComponentDescriptor`
2. **Codegen** - `package.json` codegenConfig generates C++ component interfaces
3. **CMake Build** - Compiles `libreact_codegen_GeckoViewSpec.so` with 16KB page-size for Android 15+
4. **Registration** - Autolinking generates registration code in `autolinking.cpp`
5. **Runtime** - `ReactNativeApplicationEntryPoint.loadReactNative()` loads all Fabric libraries
6. **JS Detection** - `fabricDetection.ts` detects Fabric runtime and loads appropriate component

## Enabling Fabric

```properties
# android/gradle.properties
newArchEnabled=true
```

Then rebuild: `./gradlew app:installDebug`

## Fallback Behavior

If `newArchEnabled=false` or Fabric fails to initialize:
- Library automatically falls back to Paper renderer
- No crashes or manual configuration required
- JS component logs a warning and continues

## Verification

Check metro logs for:
```
[GeckoView] hasFabricRuntime: true
Running "example" with {"fabric":true}
```

## Key Files

| File | Purpose |
|------|---------|
| `react-native.config.js` | Autolinking configuration |
| `src/fabricDetection.ts` | Runtime Fabric detection |
| `android/src/main/jni/CMakeLists.txt` | Native build configuration |
| `android/src/main/jni/OnLoad.cpp` | JNI entry point |

## Requirements

- React Native 0.76+ (for prefab targets)
- Consumer app must set `newArchEnabled=true`
- Gradle 8.x with React Native Gradle Plugin
