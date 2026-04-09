# Testing

## Summary

| Platform | Tests | Lines | Est. Coverage |
|----------|-------|-------|---------------|
| **Kotlin** | 191 | 1,380 | ~75% |
| **JavaScript** | 249 | 364 | ~85% |
| **E2E** | 13 | — | Integration |
| **Total** | **453** | 1,744 | **~78%** |

## Running Tests

```bash
# Kotlin
cd example/android && ./gradlew :react-native-geckoview:testDebugUnitTest

# JavaScript
cd example && yarn test

# E2E (requires emulator)
cd example && yarn detox:build:android && yarn detox:test:android
```

## Test Utilities

All JavaScript tests use shared utilities from `example/__tests__/testUtils.ts`:

### One-Line Mock Setup

```typescript
// Before: 30+ lines of duplicated mock code per file
// After: 1 line
jest.mock('react-native', () => require('./testUtils').setupReactNativeMock());
```

### Standard Test File Pattern

```typescript
import '../setup';
import React from 'react';
import { NativeModules } from 'react-native';
import { clearFabricGlobals, fixtures, GeckoViewModuleMock } from './testUtils';

jest.mock('react-native', () => require('./testUtils').setupReactNativeMock());

import GeckoView, { GeckoViewRef } from 'react-native-geckoview';

describe('Feature Tests', () => {
    beforeEach(() => {
        clearFabricGlobals();
        jest.clearAllMocks();
    });

    it('should work correctly', () => {
        // Use fixtures.urls.example instead of hardcoded URLs
        // Use fixtures.sessionKeys.default instead of "test-session"
    });
});
```

### Available Utilities

| Export | Purpose |
|--------|---------|
| `setupReactNativeMock()` | One-line React Native mock with all 16 GeckoViewModule methods |
| `createGeckoViewModuleMock()` | Creates standalone mock object for custom configurations |
| `clearFabricGlobals()` | Clears Fabric globals between tests |
| `fixtures` | Standardized test data (URLs, session keys, messages) |
| `GeckoViewModuleMock` | TypeScript interface for typed mock access |

## Coverage Details

### Kotlin (191 tests, 1,380 LOC)
| File | LOC | Tests | Coverage |
|------|-----|-------|----------|
| `GeckoViewModule.kt` | 239 | 15+ | High |
| `MediaDelegate.kt` | 187 | 25 | High |
| `GeckoViewNavigationDelegate.kt` | 153 | 16 | High |
| `GeckoViewPromptDelegate.kt` | 146 | 3 | Medium |
| `GeckoViewManagerImpl.kt` | 117 | 6 | High | 
| `GeckoSessionManager.kt` | 48 | 9 | High |
| `GeckoViewContentDelegate.kt` | 48 | 17 | High |
| `GeckoViewProgressDelegate.kt` | 42 | 13 | High |
| `GeckoViewPermissionDelegate.kt` | 52 | 1 | Low |
| `WebExtensionController.kt` | 61 | 33 | High |

### JavaScript (249 tests, 364 LOC)
| File | LOC | Coverage |
|------|-----|----------|
| `index.tsx` | 127 | ~90% (all methods exercised) |
| `fabricDetection.ts` | 85 | ~80% (FabricHardening tests) |
| `GeckoViewNativeComponent.ts` | 86 | Type definitions |
| `types.ts` | 66 | Type definitions |

## Test Files

### Kotlin (`android/src/test/kotlin/`)
- `ContentBlockingControllerTest.kt` - ETP, cookies
- `CookieBannerTest.kt` - Cookie banner modes
- `GeckoSessionManagerTest.kt` - Session lifecycle
- `GeckoRuntimeManagerTest.kt` - Singleton runtime
- `GeckoViewNavigationDelegateTest.kt` - Navigation, popups
- `GeckoViewProgressDelegateTest.kt` - Page load
- `GeckoViewContentDelegateTest.kt` - Title, focus exit
- `MediaDelegateTest.kt` - Audio focus, media
- `WebExtensionTest.kt` - Extension APIs

### JavaScript (`example/__tests__/`)
- `testUtils.ts` - **Shared mock utilities and fixtures**
- `App-test.tsx` - Full app integration
- `GeckoView-test.tsx` - Component basics
- `GeckoViewAdvanced-test.tsx` - Props, methods
- `SessionManagement-test.tsx` - Session persistence
- `JavaScript-test.tsx` - JS execution API
- `Settings-test.tsx` - User agent, ETP
- `History-test.tsx` - Navigation history
- `AudioFocus-test.tsx` - Audio focus 
- `ErrorDelegate-test.tsx` - Error events
- `CookieBanner-test.tsx` - Cookie banners
- `ContentBlocking-test.tsx` - Content blocking rules
- `Popup-test.tsx` - Popup handling
- `Snapshot-test.tsx` - Page capture
- `FabricHardening-test.tsx` - Fabric detection
- `FabricRegistry-test.ts` - Fabric registry logic
- `MemoryLeak-test.tsx` - Memory stress tests
- `WebExtension-test.tsx` - Extension APIs

### E2E (`example/e2e/`)
Navigation, security indicators, theme toggle, user agent switching, progress bar.

## Known Limitations

- Final GeckoView classes (`WebExtension`, `ContentBlocking.Settings`) cannot be mocked with MockK in Robolectric - covered by E2E tests instead.
- JaCoCo coverage reports require additional configuration for Robolectric tests.

