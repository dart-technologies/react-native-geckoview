# Testing

This file is the single source of truth for test counts. Other docs link
here rather than restating numbers.

## Summary

| Platform | Tests | Coverage |
|----------|-------|----------|
| **Kotlin** | 422 | 87% line / 80% branch (JaCoCo, report-only) |
| **JavaScript** | 255 | 100% lines & branches (`src/**` + `index.tsx`) |
| **E2E** | 13 | Detox integration (not run in CI — see [UPGRADE.md](UPGRADE.md)) |

Counts are correct as of the last suite run; treat the running suites as the
source of truth. The remaining Kotlin gap is Android-framework code
(AudioManager, `AlertDialog` intent dialogs, `MotionEvent` synthesis,
`SoLoader`) that needs instrumentation rather than unit tests.

For exact, up-to-date counts, run the suites (`./gradlew testDebugUnitTest`
and `yarn test`) — the per-file tables below list focus areas, not pinned
totals.

Kotlin suite runs under both `testDebugUnitTest` and `testReleaseUnitTest`
— the release-variant pass enforces the runtime-config regression guards
(no runtime arguments, signature verification enabled, autoplay and EME
blocked by default through Gecko prefs). Both variants must pass before
shipping.

## Running Tests

```bash
# Kotlin (both variants)
cd example/android && ./gradlew :react-native-geckoview:testDebugUnitTest
cd example/android && ./gradlew :react-native-geckoview:testReleaseUnitTest

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

Mock defaults for `setEnhancedTrackingProtectionLevel` and
`setCookieBannerMode` return `Promise<void>` (via `mockResolvedValue`) —
these methods now return promises, so tests that use `.toBeInstanceOf(Promise)`
assertions work against the default mock.

### Standard Test File Pattern

```typescript
import '../setup';
import React from 'react';
import { NativeModules } from 'react-native';
import { fixtures, GeckoViewModuleMock } from './testUtils';

jest.mock('react-native', () => require('./testUtils').setupReactNativeMock());

import GeckoView, { GeckoViewRef } from 'react-native-geckoview';

describe('Feature Tests', () => {
    beforeEach(() => {
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
| `setupReactNativeMock()` | One-line React Native mock with all GeckoViewModule methods |
| `createGeckoViewModuleMock()` | Creates standalone mock object for custom configurations |
| `fixtures` | Standardized test data (URLs, session keys, messages) |
| `GeckoViewModuleMock` | TypeScript interface for typed mock access |
| `waitForCondition(fn, timeout)` | Poll-until-true helper for async state |
| `suppressConsoleWarnings(fn)` | Silence `console.warn/error` around a test body |

## Test Files

### Kotlin (`android/src/test/kotlin/`)

| File | Focus |
|---|---|
| `ContentBlockingControllerTest.kt` | ETP + cookie-banner mapping; fail-fast on invalid input; `setCookieBannerMode` propagates underlying GeckoView exceptions |
| `CookieBannerTest.kt` | Cookie banner mode strings |
| `GeckoLogTest.kt` | Pins the `GeckoLog` symbol + signature |
| `GeckoRuntimeManagerTest.kt` | Runtime lifecycle; `shutdown` lock order against `GeckoSessionManager`; **runtime-pref assertions (autoplay/EME blocked by default via `GeckoPreferenceController`, no signature-weakening prefs, no runtime arguments) + `configureSecurity → autoGrantDrm/Autoplay` propagation (security guard)** |
| `GeckoSecurityConfigTest.kt` | Pins `GeckoSecurityConfig.set()` propagation to the permission delegate |
| `GeckoSessionManagerTest.kt` | Session pooling + concurrency stress (ConcurrentModificationException regression); UI-thread-only session creation; `init` never reopens pooled sessions; a session GeckoView closed itself reopens on the next bind; every close path reports its sessions to the modules before closing them; a play delivered after a close takes no focus; a dropped view's session closes when the JS wrapper generated its key and stays pooled under a host's key or when the prop never arrived, and isn't closed again after the host's `closeSession` or the playback gate |
| `GeckoViewContentDelegateTest.kt` | Title / focus-exit events; a content-process crash or kill gives up the session's audio focus |
| `GeckoViewManagerImplTest.kt` | View manager prop plumbing; `handleAudioFocus` in either prop order (also through `ViewManager.updateProperties`) and toggled on a mounted view; a view dropped mid-playback leaves its pooled session's audio focus handled, a view that binds the session carries it on, and a session whose content process dies gives its focus up; teardown with no session or with a throwing `releaseSession`; a view whose key the JS wrapper generated closes its session when dropped (also through the codegen delegate, and when letting go of the view fails), and a host key's stays pooled |
| `GeckoViewManagerNewArchTest.kt` | Fabric view manager name; the exact exported direct events; the exported native props and their types, matched against the codegen spec |
| `GeckoViewModuleTest.kt` | **`isSafeLoadUrl` + `jsonEncodeString` adversarial matrix (security guard)**; per-session snapshot cleanup on `closeSession`, shutdown and the playback restriction, including for a session that closes with its dropped view, and saves refused once the gate closes; `captureSnapshot` branch coverage and UI-thread capture; ref methods never create sessions; typed `resolvePrompt` + prompt-timeout coverage for Alert/Button/Text; view events never coalesced |
| `GeckoViewNavigationDelegateTest.kt` | **`onLoadRequest` scheme gate + `sanitizeParsedIntent` + Play Store `appId` + host-spoof guards + `geo:`/maps launches need confirmation + `onNewSession` popup filter (security guard)** |
| `GeckoViewPermissionDelegateTest.kt` | Security-first DRM/autoplay prompts + explicit opt-in paths |
| `GeckoViewProgressDelegateTest.kt` | Progress events |
| `GeckoViewPromptDelegateTest.kt` | Alert/popup/auth; eval-looking alerts stay page alerts |
| `MediaDelegateTest.kt` | Audio focus, media session; `release()` on session close; detaching from and re-attaching to a view, with the media state told to the new view; focus-setting changes; a duck ending with focus coming back or with the request given up; focus changes on the main looper; media events delivered after the session closed are ignored |
| `WebExtensionBridgeAssetsTest.kt` | Pins that `manifest.json` + `bridge.js` ship under `assets/react_native_geckoview_bridge/` |
| `WebExtensionTest.kt` | Extension APIs + **`isSafeAssetPath` + `safeSerialize` + install rejection + missing-port guards (security guard)**; `installExtension` single-completion contract; port-scoped disconnect rejection + late-result short-circuit; in-flight evals reject `PLAYBACK_RESTRICTED` on a UX restriction |

### JavaScript (`example/__tests__/`)

| File | Focus |
|---|---|
| `testUtils.ts` | **Shared mock utilities and fixtures** |
| `App-test.tsx` | Full app integration |
| `AudioFocus-test.tsx` | Audio focus |
| `ErrorDelegate-test.tsx` | Error events |
| `GeckoView-test.tsx` | Component basics |
| `GeckoViewAdvanced-test.tsx` | Props, methods |
| `History-test.tsx` | Navigation history |
| `JavaScript-test.tsx` | JS execution API |
| `MemoryLeak-test.tsx` | Memory stress tests |
| `Popup-test.tsx` | Popup handling |
| `Security-test.tsx` | **Runtime security config + URL scheme forwarding + ETP/cookie-banner promise rejection + installWebExtension rejection + evaluateJavaScript forwarding** |
| `SessionManagement-test.tsx` | Session persistence; a view without a `sessionKey` has native close its session when the view drops, a host's key never does (even spread from props), and the wrapper never closes a session from JS, even when StrictMode reruns effects; the flag is fixed at mount, and an empty `sessionKey` counts as none |
| `Settings-test.tsx` | User agent, ETP |
| `Snapshot-test.tsx` | **Page capture: ref shape, sessionKey forwarding, error-code propagation (VIEW_NOT_FOUND, SNAPSHOT_TOO_LARGE, SNAPSHOT_ERROR), multi-instance closeSession isolation** |
| `WebExtension-test.tsx` | Extension APIs |

### E2E (`example/e2e/`)

Navigation, security indicators, theme toggle, user agent switching,
progress bar. See [docs/UPGRADE.md](UPGRADE.md#3-re-adding-e2e-tests-to-ci)
for the CI path.

## Security regression tests

The security-hardening audit introduced a set of adversarial-input tests
that MUST continue to pass. Files marked "security guard" above are pinned
— if they fail after a change, fix the code, don't weaken the test. The
runtime-config assertions in `GeckoRuntimeManagerTest` specifically run
under `testReleaseUnitTest` to catch a future commit re-adding a banned
sandbox flag or unsafe runtime pref.

## Coverage layers

The security-hardening fixes are exercised across three layers:

| Layer | Tooling | Scope |
|---|---|---|
| Unit + integration | Robolectric + MockK + the `*ForTest` `internal` hooks on `GeckoViewModule` and `WebExtensionController` | `@ReactMethod` end-to-end with mocked `GeckoView` / `GeckoResult` / `GeckoSession.PromptDelegate.*Prompt`. Drives every reject branch of `captureSnapshot`, every prompt-type branch of `schedulePromptTimeout`, single-completion of `installExtension` + port-scoped eval rejection, and the per-session snapshot cleanup that every session close (`closeSession`, `shutdown`, the UX restriction, a dropped view whose key the JS wrapper generated) reports to the module. |
| JS API contract | Jest with the shared `setupReactNativeMock` from `testUtils.ts` | Ref shape, sessionKey forwarding, native-side error-code propagation (`VIEW_NOT_FOUND`, `SNAPSHOT_TOO_LARGE`, `SNAPSHOT_ERROR`, `INVALID_ASSET_PATH`, `COOKIE_BANNER_SET_FAILED`), multi-instance isolation. |
| E2E | Detox | Navigation, security indicators, theme toggle, user agent switching, progress bar. The current example app does not surface `captureSnapshot`, `closeSession`, or `setCookieBannerMode` through testable UI affordances, so the snapshot/cookie-banner/prompt-timeout fixes are **not** covered at the Detox layer. Their behavior is fully exercised by the unit + JS layers above. |

## Known Limitations

- Final GeckoView classes (`WebExtension`, `ContentBlocking.Settings`,
  `GeckoSession.NavigationDelegate.LoadRequest`) cannot be mocked with
  MockK in Robolectric — `GeckoViewNavigationDelegateTest` calls the
  `decideNavigation` / `decideSubframeNavigation` / `sanitizeParsedIntent`
  internals directly rather than going through the public override.
- Detox e2e doesn't currently exercise `captureSnapshot`, `closeSession`,
  or `setCookieBannerMode` — see "Coverage layers" above. Adding e2e
  coverage requires the example app to expose these via tappable
  controls.
- The `bitmap.recycle()` branch of `captureSnapshot` for the
  SNAPSHOT_TOO_LARGE-after-capture path (line 288's bitmap-level check)
  cannot be hit in unit tests without instantiating a >16MP `Bitmap`.
  The view-level early reject (line 274) covers the same defensive
  posture and is exercised.
- JaCoCo coverage reports require additional configuration for Robolectric
  tests.
