# react-native-geckoview

React Native library integrating Mozilla's GeckoView (Firefox's rendering engine) for Android.

## Quick Reference

```bash
# JavaScript tests (216 tests)
cd example && yarn test

# Kotlin tests (230 tests, both variants)
cd example/android && ./gradlew :react-native-geckoview:testDebugUnitTest
cd example/android && ./gradlew :react-native-geckoview:testReleaseUnitTest

# Build example app
cd example && yarn android
```

## Project Structure

```
├── android/src/main/kotlin/     # Native module (Kotlin)
│   ├── GeckoViewManager.kt      # View manager
│   ├── GeckoViewModule.kt       # Native commands
│   ├── GeckoSessionManager.kt   # Session lifecycle
│   └── [delegates]              # Navigation, Progress, Permission, etc.
├── example/__tests__/           # Jest tests (17 files)
│   ├── testUtils.ts             # Shared mock utilities
│   └── *-test.tsx               # Test files
├── src/                         # TypeScript source
│   ├── fabricDetection.ts       # Fabric runtime detection (error UX)
│   ├── GeckoViewNativeComponent.ts # Codegen spec
│   ├── NativeEventPayloads.ts   # Shared event payload types
│   └── types.ts                 # Type definitions
├── docs/                        # Documentation
│   ├── TESTING.md               # Test infrastructure
│   └── TROUBLESHOOTING.md       # Debugging + memory profiling
└── index.tsx                    # Main entry point
```

## Key APIs

### JavaScript
```typescript
import GeckoView, { GeckoViewRef } from 'react-native-geckoview';

<GeckoView
  ref={geckoRef}
  source={{ uri: 'https://example.com' }}
  sessionKey="my-session"
  handleAudioFocus={true}
  onGeckoPageStart={(e) => {}}
  onGeckoPageStop={(e) => {}}
  onGeckoLocationChange={(e) => {}}
/>

// Imperative methods
geckoRef.current?.reload();
geckoRef.current?.goBack();
await geckoRef.current?.evaluateJavaScript('1+1');
await geckoRef.current?.setEnhancedTrackingProtectionLevel(2); // rejects on invalid level
```

### Native Module Methods
- `reload(sessionKey)`, `goBack(sessionKey)`, `goForward(sessionKey)`, `stop(sessionKey)`
- `loadUrl(sessionKey, url)`, `evaluateJavaScript(sessionKey, script)`
- `setEnhancedTrackingProtectionLevel(level)`, `setCookieBannerMode(mode)`
- `installWebExtension(path)`, `sendWebExtensionMessage(extensionId, json)`
- `resolvePrompt(promptId, confirmed, text, auth)`
- `captureSnapshot(sessionKey)`

## Test Utilities

All tests use shared utilities from `example/__tests__/testUtils.ts`:

```typescript
import { fixtures } from './testUtils';

jest.mock('react-native', () => require('./testUtils').setupReactNativeMock());

describe('Tests', () => {
    beforeEach(() => {
        jest.clearAllMocks();
    });

    it('uses fixtures', () => {
        render(<GeckoView source={{ uri: fixtures.urls.example }} />);
    });
});
```

`setEnhancedTrackingProtectionLevel` and `setCookieBannerMode` default
mocks return `Promise<void>` (via `.mockResolvedValue(undefined)`) — if
you override them, preserve the promise-returning contract or
`instanceof Promise` assertions in `Security-test.tsx` will fail.

## Test Coverage

| Platform | Tests | Est. Coverage |
|----------|-------|---------------|
| JavaScript | 216 | ~85% |
| Kotlin | 230 | ~80% |
| E2E | 13 | Integration |

Kotlin suite runs under both `testDebugUnitTest` and `testReleaseUnitTest`;
the release-variant assertion pass is where the runtime-config regression
guards live (sandbox flags, signature verification, autoplay default).

## Documentation

- `docs/TESTING.md` - Test utilities, patterns, file list
- `docs/TROUBLESHOOTING.md` - Debugging + memory profiling
- `docs/ADVANCED.md` - Advanced configuration

## Notes for AI Assistants

1. **Run tests after changes**: `yarn test` (JS) and `./gradlew testDebugUnitTest` + `testReleaseUnitTest` (Kotlin)
2. **Use shared mocks**: All test files use `setupReactNativeMock()` from testUtils.ts
3. **Use fixtures**: Prefer `fixtures.urls.example` over hardcoded URLs
4. **Android-only**: No iOS support (GeckoView is Android-specific)
5. **Check builds**: Test with `cd example && yarn android`
6. **Security-sensitive paths**: `loadUrl`, `setSource`, `evaluateJavaScript`,
   `installWebExtension`, and the `onLoadRequest` navigation gate enforce
   scheme/path allowlists. Any change that relaxes a validator, removes a
   runtime-arg guard, or reintroduces a banned pref should fail
   `GeckoRuntimeManagerTest` / `GeckoViewModuleTest` /
   `GeckoViewNavigationDelegateTest` — if a security test fails, fix the
   code, don't weaken the test.
