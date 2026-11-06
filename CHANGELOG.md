# Changelog

All notable changes to `react-native-geckoview` are documented here.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)
and this project adheres to [Semantic Versioning](https://semver.org/).

## [Unreleased]

### SDK 58 / AAOS modernization

- Align the library and independently buildable example with React Native
  0.88, React 19.3, TypeScript 6, Gradle 9.4.1 and AGP 9.2.1.
- Pin GeckoView 159.0.20261022150525, Kotlin 2.4.20 and Media3 1.11.1 in
  `android/versions.json`. GeckoView requires Android compile SDK 37.2;
  the target remains API 36 and the minimum remains API 26.
- Use public React Native component registration and host dependency alignment.
- Close native sessions when AAOS playback is disallowed, keep a manual resume
  authorization latch, and remove released views from the weak session registry.
- Install the trusted evaluation bridge using a canonical folder URI and bind
  content messaging to each session, including sessions opened before installation.
- GeckoView 159 removed the cookie-banner handling API. `cookieBannerHandling`
  modes 1 and 2 now report an unsupported-operation error; mode 0 remains a
  no-op. Consumers must not assume automatic consent handling is available.
- Migrate tests to awaited Testing Library 14 operations and verify both native
  build variants alongside standalone library and example checks.

### Review fixes

- Confine session and runtime lifecycle to the UI thread, and close sessions
  before taking the runtime lock in `shutdown`, so the two managers always
  lock in the same order. Ref methods no longer rebuild a closed session:
  void methods do nothing, `evaluateJavaScript` rejects with
  `EVAL_BRIDGE_UNAVAILABLE` at once, and `captureSnapshot` rejects.
- Autoplay and EME defaults now take effect. The `--setpref=` runtime
  arguments were never a Gecko switch; the prefs are set through
  `GeckoPreferenceController`, so autoplay and DRM stay blocked unless the
  host opts in with `configureGeckoViewSecurity` before the first view mounts.
- Release audio focus when a playing session is closed. A view dropped
  mid-playback no longer gives up the focus its pooled session is still
  playing under: the session's media delegate stays, without events, holding
  focus while the page plays and pausing it on a focus loss, until the session
  closes or its content process dies. A view that binds the session reuses
  that delegate, so focus carries over without a new request, and it hears
  that the page's media is active and playing.
- A session GeckoView closed because its content process died reopens when a
  view binds its key again, instead of leaving that view blank.
- `handleAudioFocus` now takes effect whatever order React Native applies the
  view's props in (release builds tend to apply `sessionKey` first), and when
  it changes on a mounted view.
- A page the library muted for a duck is unmuted when it gives up that focus
  request: on a pause or stop, a new request, or `handleAudioFocus` turning
  off. The page used to stay silent, since no `AUDIOFOCUS_GAIN` reaches a
  request that was given up.
- A media play or activation that GeckoView delivers after its session closed
  is ignored. A play sent just before `closeSession`, shutdown or the AAOS
  playback gate used to request audio focus again, which nothing gave back,
  and a late play or activation still reached JS.
- View events are never coalesced, so two in a row for one view (such as the
  `activated` and `play` a view gets when it binds a playing session) both
  reach JS. React Native 0.88 already delivers them uncoalesced, since they
  carry no surface id, so nothing changes there; this keeps it so if that
  changes.
- Capture snapshots on the UI thread.
- Shutdown and the AAOS playback restriction now drop the snapshots of every
  session they close, as `closeSession` does: a save in flight rejects instead
  of writing and resolving a screenshot for a closed session, and saved
  snapshots are deleted. A save rejects with `PLAYBACK_RESTRICTED` from the
  moment the playback gate closes, as evaluations do, and with
  `VIEW_NOT_FOUND` when its session closes otherwise.
- Resolve alert, confirm and text prompts with GeckoView 159's typed API, so
  `resolvePrompt` completes them instead of leaving the page blocked.
- Ask before a page opens the maps app (`geo:` and maps links), as `intent:`
  and `market:` already did.
- Reject evaluations in flight with `PLAYBACK_RESTRICTED` when the playback
  gate closes, and scope a bridge disconnect to its own port.
- CI: install the Android SDK command-line tools on ubuntu-26.04 runners,
  give jobs a read-only token, and audit the library's shipped dependencies.

### Security

Security audit and hardening pass. 24 findings across 5 severity tiers
(2 CRITICAL, 6 HIGH, 9 MEDIUM, 6 LOW + 3 defensible) all remediated with
regression tests.

**CRITICAL — Runtime hardening** ([GeckoRuntimeManager.kt](android/src/main/kotlin/com/reactnative/geckoview/GeckoRuntimeManager.kt)):
- Sandbox-disabling flags (`--no-sandbox`, `--disable-gpu-sandbox`) are now
  removed entirely. Debug and release builds run with GeckoView's default
  content-process sandbox.
- Extension signature verification re-enabled: removed
  `xpinstall.signatures.required=false` and
  `extensions.install.requireSecureOrigin=false`. Any unsigned-extension
  install via web APIs is now rejected. `installBuiltIn` is used for the
  bundled `spatial_navigation` extension and bypasses the check by design.
- `WebExtensionController.PromptDelegate` now installed at runtime init
  (previously unset). Denies installs of unlisted extension IDs; denies
  updates requesting new permissions or origins (closes the
  "compromised-upstream-bump" attack class). Uses a version-tolerant
  `java.lang.reflect.Proxy` shim to handle the signature change in
  GeckoView 128→150.
- `consoleOutput(true)` is now off by default and available only when
  `configureGeckoViewSecurity({ enableConsoleOutput: true })` is called
  before runtime init in a debug build.
- Autoplay defaults to BLOCKED (`media.autoplay.default=5`); the
  `media.autoplay.enabled=true` override was removed.
- EME/DRM defaults to disabled (`media.eme.enabled=false`) and must be
  explicitly enabled via `configureGeckoViewSecurity({ allowDrm: true })`.

**HIGH — Navigation gate** ([GeckoViewNavigationDelegate.kt](android/src/main/kotlin/com/reactnative/geckoview/GeckoViewNavigationDelegate.kt)):
- Added `onLoadRequest` / `onSubframeLoadRequest` overrides that deny
  `javascript:`, `data:`, `file:`, `content:`, and `blob:` schemes (top-frame
  and subframe). `intent:` is intercepted and routed to the dialog flow with
  sanitization; the original navigation is always denied so Gecko never
  renders the `intent://` URL itself.
- `Intent.parseUri(url, URI_INTENT_SCHEME)` output is sanitized before
  launch: `selector` and `component` cleared,
  `FLAG_GRANT_{READ,WRITE,PERSISTABLE,PREFIX}_URI_PERMISSION` masked off,
  `CATEGORY_DEFAULT` removed, `CATEGORY_BROWSABLE` required. Intent data
  schemes are restricted to `http` and `https`; custom schemes are denied
  unless a host app adds a narrow native allowlist.
- `handlePlayStore` validates the extracted `appId` against
  `^[a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z][a-zA-Z0-9_]*)+$` before building the
  `market://` URI. URL-encoded traversal, empty IDs, and IDs with slashes
  are rejected.
- `onNewSession` pre-filters popup URIs: popups with scheme ∉ `{http,
  https, about}` no longer fire `onGeckoPopup`, preventing host apps from
  accidentally feeding `javascript:` URIs back into `loadUrl`.

**HIGH — Module-surface validation** ([GeckoViewModule.kt](android/src/main/kotlin/com/reactnative/geckoview/GeckoViewModule.kt)):
- **Breaking.** `loadUrl(sessionKey, url)` now rejects URLs whose scheme is
  not in `{http, https, about}` (case-insensitive). Host apps that
  previously forwarded user-controlled URLs (deep links, QR scans, paste
  handlers) are protected from trivial XSS via `javascript:`.
- **Breaking.** `setSource({uri})` applies the same scheme allowlist.
- **Breaking.** `evaluateJavaScript(sessionKey, code)` now uses the trusted
  internal WebExtension bridge instead of the page-observable alert result
  channel. It rejects with `EVAL_BRIDGE_UNAVAILABLE` until the bridge content
  script connects for the session.
- `captureSnapshot` now writes PNGs to `context.noBackupFilesDir` instead
  of `cacheDir`, caps bitmap size at 16 MP, calls `File.deleteOnExit()`,
  evicts snapshots older than 5 minutes on each capture, and cleans up all
  on-disk snapshots when `closeSession` is called. Snapshots no longer
  sync to cloud backup, and private-mode page content doesn't persist.
- **Breaking.** `setEnhancedTrackingProtectionLevel(level)` and
  `setCookieBannerMode(mode)` now return `Promise<void>` and reject with
  `INVALID_ETP_LEVEL` / `INVALID_COOKIE_BANNER_MODE` on out-of-range input.
  Silent fallback to DEFAULT / DISABLED is removed — a caller passing `99`
  expecting "strictest" would have previously gotten DEFAULT, weaker than
  intended.

**HIGH — WebExtension message hardening** ([WebExtensionController.kt](android/src/main/kotlin/com/reactnative/geckoview/WebExtensionController.kt)):
- `installExtension(assetPath)` now validates the path against
  `^[a-zA-Z0-9_\-./]+$` and rejects `..`, URL-encoded traversal (`%2e`,
  `%2f`, `%5c`), null bytes, backslashes, and absolute paths. Throws
  `IllegalArgumentException` on unsafe input, which the JS bridge surfaces
  as `INVALID_ASSET_PATH`.
- Raw extension messages are capped at 1 MB during serialization; oversized
  payloads are truncated with a `…[truncated]` marker to prevent UI-thread
  OOM from a misbehaving extension.
- WebExtension messages with blank `type` are dropped before dispatch,
  closing an empty-string bypass pattern on JS consumers that whitelist
  events by truthiness.
- `sendWebExtensionMessage` now routes by extension ID and session instead
  of a single last-connected port. It rejects with
  `WEB_EXTENSION_PORT_UNAVAILABLE` when the requested extension/session port
  is not connected, and emitted message payloads include `extensionId`.

**HIGH — Supply chain** ([android/build.gradle](android/build.gradle)):
- `react-android` dependency pinned to `0.83.+` (was `+`). The
  `//noinspection GradleDynamicVersion` comment was removed so Gradle's own
  supply-chain warning is restored. Consumers can override via
  `rootProject.ext.reactNativeVersion`.

**MEDIUM — Concurrency** ([GeckoSessionManager.kt](android/src/main/kotlin/com/reactnative/geckoview/GeckoSessionManager.kt)):
- `sessions` map migrated from `mutableMapOf` to `ConcurrentHashMap`;
  `init()` is now `@Synchronized`. Eliminates the
  `ConcurrentModificationException` race where `init()` iterated while
  `getOrCreateSession` mutated concurrently.

**LOW — Permission defaults** ([GeckoViewPermissionDelegate.kt](android/src/main/kotlin/com/reactnative/geckoview/GeckoViewPermissionDelegate.kt)):
- DRM (`PERMISSION_MEDIA_KEY_SYSTEM_ACCESS`) and autoplay
  (`PERMISSION_AUTOPLAY_AUDIBLE`/`INAUDIBLE`) now route through the standard
  `onPermissionRequest` event by default. Host apps can opt in before first
  mount with `configureGeckoViewSecurity({ allowDrm, allowAutoplay })`.

**Additional hardening (MEDIUM/LOW)**:
- Release-build log gating: URL and payload logging in navigation delegate,
  view manager, view module, package, and extension controller now flows
  through `GeckoLog`. Release builds no longer emit URLs, extension messages,
  promptIds, or view IDs to logcat.

### Added — Test coverage
- New Kotlin coverage for the security hardening: new `GeckoViewModuleTest`
  (`isSafeLoadUrl` + `jsonEncodeString`), extended
  `GeckoViewNavigationDelegateTest` (`onLoadRequest`, `sanitizeParsedIntent`,
  Play Store `appId`, `onNewSession`), extended `GeckoRuntimeManagerTest`
  (banned-pref assertions for release variant), extended
  `ContentBlockingControllerTest` (fail-fast assertions),
  `GeckoSessionManagerTest` (concurrent init/release stress),
  `GeckoViewPermissionDelegateTest` (DRM opt-out path), and
  `WebExtensionTest` (`isSafeAssetPath` adversarial matrix +
  `safeSerialize` truncation + install rejection).
- New JS coverage: `Security-test.tsx` covering `loadUrl` scheme
  forwarding, `setEnhancedTrackingProtectionLevel` / `setCookieBannerMode`
  Promise returns + rejection propagation, `installWebExtension`
  asset-path rejection, runtime security config, and `evaluateJavaScript`
  payload forwarding.
- Release-variant test invocation added to verification: all Kotlin
  security assertions run under both `testDebugUnitTest` and
  `testReleaseUnitTest`. See [docs/TESTING.md](docs/TESTING.md) for the
  current per-file breakdown.

### Changed
- **Breaking.** `GeckoViewRef.setEnhancedTrackingProtectionLevel` and
  `setCookieBannerMode` now return `Promise<void>` instead of `void`. TS
  types updated in [src/types.ts](src/types.ts). Callers that relied on
  silent-default behavior on invalid input must now handle the rejection.
- `android/build.gradle` opts into `buildFeatures.buildConfig = true`
  (required by AGP 8+ for `BuildConfig.DEBUG` access in Kotlin).
- Autoplay default flipped from ALLOWED (0) to BLOCKED (5). Pages now need
  a gesture or explicit permission grant before media plays.
- Added `configureGeckoViewSecurity(options)` for pre-runtime opt-ins to DRM,
  autoplay, and debug console output.
- **Breaking.** `GeckoViewRef.sendWebExtensionMessage` now takes
  `(extensionId, message)` and returns `Promise<void>`.

## [0.2.0] - 2026-04-22

### Streamlining
- **Kotlin tests unified on MockK.** Dropped `mockito-core`, `mockito-inline`,
  and `mockito-kotlin` dependencies. Shared `TestHelpers.setField()`
  reflection helper replaces per-file copies in Permission and Prompt
  delegate tests.
- **Deleted dead Fabric-era tests.** `FabricRegistry-test.ts` (201 LOC)
  and most of `FabricHardening-test.tsx` (113 LOC) verified Paper
  fallback paths that no longer exist. Kept only the runtime detection
  used by the error-boundary UI.
- **Merged overlapping JS test files.** Removed `ContentBlocking-test.tsx`
  (covered by `Settings-test.tsx`) and `CookieBanner-test.tsx` (overlap
  with Settings + new Kotlin-level coverage). Net test count: 249 → 203
  (still covers every prop and ref method).
- **Centralized Jest setup.** 16 copies of
  `jest.mock('react-native', …)` replaced with a single `setupFiles`
  entry pointing to `example/setup.ts`.
- **Dropped three redundant Fabric mock factories** from `testUtils.ts`
  (`createMockFabricRegistry`, `createMockFabricUIManager`,
  `createMockHermesFabricUIManager`) and the now-unused
  `clearFabricGlobals` helper.
- **Silenced library dev logs in test environments** via a shared
  `devLog()` gate on `NODE_ENV !== 'test'`.
- **Simplified `fabricDetection.ts`** from 86 LOC to 39 LOC — kept only
  `hasFabricRuntime()` for the error-boundary UX; deleted the Paper
  detection / Hermes proxy / component-provider lookup.
- **Consolidated docs.** Merged `DEBUGGING.md`, `MEMORY_PROFILING.md`,
  and `FABRIC.md` into a single `docs/TROUBLESHOOTING.md`. Fabric
  essentials moved to `README.md`.
- **Deleted dead repo scaffolding.** Removed `example/android/app/BUCK`
  (Facebook's unused Buck build file), the 15-line GeckoView version
  comment block from `android/build.gradle`, the commented-out
  `installArchives` task, and stale `src/main/java` source-set entries.
- **Dropped dead library dependencies:** `@babel/runtime` (TSC emit
  doesn't use Babel helpers), explicit `kotlin-stdlib` pin (pulled
  transitively by the Kotlin plugin), `mockito-inline` (merged into
  mockito-core 5+ and now removed entirely), JaCoCo plugin and the
  unused `jacocoTestReport` task, and the meaningless
  `versionCode 28` / `versionName "1.0"` on the library artifact.
- **Introduced a shared `tsconfig.base.json`** that `tsconfig.build.json`
  and `example/tsconfig.json` extend; the two configs previously
  duplicated ~80% of their compiler options.
- **Set `engines.node: ">=22"`** on the library and added `.nvmrc`.
- **Shared ESLint config** at the repo root, extended by the example.
- **Dependabot** (`.github/dependabot.yml`) — weekly npm PRs (grouped
  by area), monthly Gradle + GitHub Actions PRs.
- **CI overhaul:** composite `setup` action removes four copies of the
  Node/Java install steps; Android SDK + Gradle caches wired up;
  library build artifact uploaded per PR.

### Changed — Expo SDK 55 / RN 0.83 alignment
- **Breaking:** peer dependencies bumped to React `^19.2.0` and React Native
  `>=0.83.0`. Consumers on older RN must stay on `0.1.x`.
- **Breaking:** Paper (Legacy Architecture) support removed. RN 0.83 is
  New-Architecture-only and the RN Gradle Plugin no longer honors the
  `newArchEnabled` flag. Deleted `android/src/paper/` and the
  `isNewArchitectureEnabled()` gate; codegen always runs.
- GeckoView bumped **143.0.20250908174027 → 150.0.20260415192539**
  (seven Firefox major versions; no API breakage on the surfaces this
  library uses — full audit in
  [docs/UPGRADE.md](docs/UPGRADE.md)).
- `GeckoRuntime` now calls `warmUp()` explicitly after creation — v150
  stopped pre-starting the child process eagerly, so the first session
  would otherwise block on cold start.
- `GeckoGenericEvent` constructor switched to the explicit
  `Event(surfaceId, viewTag)` form (RN 0.83 deprecated the single-arg
  variant).
- `GeckoViewPackage` now extends `BaseReactPackage` (formerly
  `TurboReactPackage`, deprecated in RN 0.82+).
- `ReactModuleInfo` now uses the 6-parameter constructor (dropped
  `isCxxModule`).
- `GeckoViewManager.getExportedCustomDirectEventTypeConstants` returns
  a Kotlin `mapOf(...)` instead of `MapBuilder.builder()` (deprecated).
- Android tooling: AGP **8.9.1 → 8.12.0**, Kotlin **2.2.10 → 2.2.20**,
  `compileSdk`/`targetSdk` **35 → 36**, `buildToolsVersion` **35 → 36**.
- Test tooling bumped: Mockito **5.11.0 → 5.14.2**, mockito-kotlin
  **5.2.1 → 5.4.0**, MockK **1.13.8 → 1.13.13**, Robolectric
  **4.11.1 → 4.14.1**, JaCoCo **0.8.10 → 0.8.12**.
- Example app: RN **0.81.5 → 0.83.6**, React **19.1.0 → 19.2.0**,
  TypeScript **^5.0.0 → ~5.9.2**, `@babel/core`
  **^7.8.3 → ^7.26.0**, `@testing-library/react-native`
  **^12.8.1 → ^13.3.0**, dropped the legacy
  `metro-react-native-babel-preset`, switched the
  `react-native-geckoview` dependency from `file:../` to `link:../` to
  avoid yarn copying Android build artifacts.

### Fixed
- `WebExtensionController` used `flutter_geckoview` as its native app id,
  silently breaking message routing for extensions that filter by
  `react_native_geckoview` ([WebExtensionController.kt](android/src/main/kotlin/com/reactnative/geckoview/WebExtensionController.kt)).
- Navigation delegate lambdas re-read `context.currentActivity` after the
  `?.` gate, risking `NullPointerException` during activity transitions.
  Activities are now captured before the lambda runs
  ([GeckoViewNavigationDelegate.kt](android/src/main/kotlin/com/reactnative/geckoview/GeckoViewNavigationDelegate.kt)).
- `pendingPrompts` / `pendingPermissions` in `GeckoViewModule` are now
  `Collections.synchronizedMap` and guarded by a 60-second timeout cleanup
  so unresolved prompts no longer leak for the life of the process.
- `GeckoRuntimeManager.shutdown()` now closes pooled sessions via
  `GeckoSessionManager.closeAllSessions()` before tearing down the runtime.
- `WebExtensionController` tracks connected ports by extension ID and
  session, preventing cross-extension message routing.
- Content-blocking toggle is now wired through to
  `ContentBlockingController.setEnhancedTrackingProtectionLevel` instead
  of being a TODO.

### Added
- `onDropViewInstance` on both Paper and New Architecture view managers:
  releases the session reference, clears delegates, and purges tracking
  maps. Previously cleanup relied entirely on `WeakHashMap` GC.
- TypeScript build pipeline: `tsc` emits CommonJS, ES modules, and
  declaration files to `lib/`. `package.json` now exposes `main`,
  `module`, `types`, `react-native`, and `exports` correctly.
- CI workflow now runs JS tests + typecheck + build, Kotlin unit tests,
  the Android example build, and a dependency audit — in addition to
  the existing `assembleRelease` job.
- Shared event payload types in
  [src/NativeEventPayloads.ts](src/NativeEventPayloads.ts) imported by
  `types.ts` (the codegen spec `GeckoViewNativeComponent.ts` re-declares the
  same shapes inline, kept in sync by hand).
- JSDoc on all public props, ref methods, and type aliases.
- Additional Kotlin tests for `GeckoViewPermissionDelegate` (auto-grant
  and user-prompt paths) and `GeckoViewPromptDelegate` (popup,
  before-unload, auth, and `evaluateJavaScript` alert fast-path).
- `CONTRIBUTING.md` and this `CHANGELOG.md`.

### Changed
- `GeckoViewProps` / `GeckoViewRef` typing tightened: `any` replaced with
  `unknown` or concrete shapes; `NativeSyntheticEvent<T>` wrappers used
  for event callbacks. Removed the `props as any` cast in the component
  renderer.
- Jest coverage threshold raised from 60% branches / 70% statements to
  75% / 80% to match the actual suite.
- Example app no longer double-dispatches the initial URL load on mount.

### Deprecated
- None.

### Removed
- None.

## [0.1.0] - 2026-04-10

### Added
- Initial Kotlin rewrite with Fabric codegen support.
- Session pooling keyed by `sessionKey`.
- Bridgeless-compatible event dispatch via
  `UIManagerHelper.getEventDispatcherForReactTag`.
- `installWebExtension`, `sendWebExtensionMessage`, `evaluateJavaScript`,
  `captureSnapshot`.
- Documentation suite under `docs/`: `TESTING.md`, `FABRIC.md`,
  `DEBUGGING.md`, `MEMORY_PROFILING.md`, `ADVANCED.md`, `UPGRADE.md`.

[Unreleased]: https://github.com/dart-technologies/react-native-geckoview/compare/v0.2.0...HEAD
[0.2.0]: https://github.com/dart-technologies/react-native-geckoview/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/dart-technologies/react-native-geckoview/releases/tag/v0.1.0
