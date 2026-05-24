# Upgrade Roadmap

Forward-looking work for react-native-geckoview. Completed migrations are
recorded in [CHANGELOG.md](../CHANGELOG.md); this file tracks what's left.

---

## Already shipped

The following are done and live on `main` — see
[CHANGELOG.md](../CHANGELOG.md#unreleased) for the details and exact version
bumps:

- Kotlin deprecation fixes (event dispatch, `BaseReactPackage`, 6-param
  `ReactModuleInfo`, `MapBuilder` → `mapOf`).
- Example app + tooling aligned to Expo SDK 56 / RN 0.85.3 (AGP 8.12,
  Kotlin 2.2.20, compileSdk 36).
- GeckoView bumped to v150; Paper / Legacy Architecture removed
  (New-Architecture-only; old consumers pin `0.1.x`).
- Security audit + hardening (24 findings remediated with regression
  tests; posture summary in the CHANGELOG security table).

Deliberately **not** pursued: migrating the example app to the Expo managed
workflow — the existing Detox rig and custom native-module wiring would be
disruptive to port, so it stays bare RN.

---

## 1. Re-adding E2E Tests to CI — NOT STARTED

### Current State

CI runs unit tests (JS + Kotlin) and `./gradlew assembleRelease`. E2E
tests exist at `example/e2e/` with Detox config at
`example/.detoxrc.js` but only work locally against the
`Automotive_1408p_landscape_with_Google_Play` AVD.

### Why E2E Tests Fail in CI

All 5 test suites (`navigation`, `progress`, `security`, `theme`,
`userAgent`) load external websites:

- `https://example.com` — navigation tests
- `http://neverssl.com` — security tests (HTTP vs HTTPS)
- `https://www.whatismybrowser.com` — user-agent tests

CI emulators have unreliable / no internet access. Page loads time out.

### Path to CI-Compatible E2E Tests

**Option A: Local HTML content (recommended)**

Bundle test HTML in `example/android/app/src/main/assets/test/` and load
them via `file:///android_asset/`:

```
example/android/app/src/main/assets/test/
├── index.html          # Basic page for navigation tests
├── secure.html         # HTTPS-simulated page for security tests
├── progress.html       # Slow-loading page (JS delay) for progress tests
└── useragent.html      # Page that echoes User-Agent via JS
```

Tests change from:

```ts
await element(by.id('url-input')).typeText('https://example.com');
```

to:

```ts
await element(by.id('url-input')).typeText('file:///android_asset/test/index.html');
```

No network needed. Deterministic. Fast.

**Option B: Local HTTP server**

Start a lightweight HTTP server in the CI workflow before running tests:

```yaml
- name: Start test server
  run: |
    cd example/e2e/fixtures
    python3 -m http.server 8080 &
```

Tests load `http://10.0.2.2:8080/` (Android emulator host loopback).

**Option C: Detox network mocking** — most complex.

### Recommended Sequence

1. Create local HTML test fixtures (Option A).
2. Rewrite the 5 test suites to use local assets.
3. Add `theme.test.ts` to CI first (no network needed).
4. Add remaining tests incrementally.
5. Add an emulator step to the existing `.github/workflows/ci.yml`:

```yaml
- name: Run E2E Tests
  uses: reactivecircus/android-emulator-runner@v2
  with:
    api-level: 31
    target: google_apis
    arch: x86_64
    script: cd example && yarn detox:test:android:release --headless
```

### CI Emulator Notes

- **Ubuntu + KVM** (`ubuntu-latest` with KVM enabled) is the only
  reliable Android emulator environment on GitHub Actions.
- macOS ARM runners cannot run x86_64 emulators.
- API 31 + x86_64 + `google_apis` is the most stable combination.
- GeckoView cold start takes 5–10s on emulator — budget timeouts
  accordingly. v150 stopped eagerly pre-starting the child process; the
  library now calls `GeckoRuntime.warmUp()` on creation, but tests
  should still allow ~10s.
- `.detoxrc.js` already handles CI vs local AVD names via
  `process.env.CI`.

---

## 2. Security Audit Follow-ups

The security audit itself is complete — the full posture table and remediated
findings live in [CHANGELOG.md `[Unreleased]`](../CHANGELOG.md#unreleased).
Two validation tasks remain outstanding:

- **On-device bridge validation.** Verify the internal bridge connects on a
  physical device before depending on `evaluateJavaScript` in production
  flows.
- **On-device media validation.** If opting in to DRM/autoplay, smoke test
  representative media sites and confirm the app's consent model is still
  correct.

---

## 3. Future Considerations

- **GeckoView `warmUp()` change in v150** — already handled via
  reflection in `GeckoRuntimeManager`. If v152+ promotes `warmUp` to a
  stable public method, remove the reflection guard.
- **JS engine: Hermes v1** — Expo SDK 56 ships Hermes v1 as the
  default (precompiled in the EAS XCFramework). The example app uses the
  default Hermes; revisit if startup perf becomes a concern.
- **Edge-to-edge on Android 16+** — SDK 56 makes edge-to-edge
  mandatory. The example app doesn't use `expo-system-ui`; if added,
  remove any `edgeToEdgeEnabled` config (no longer honored).
- **`@react-native-community/cli`** — pinned at `^20.1.3` to match RN
  0.85. Later majors may require minor config shifts.
