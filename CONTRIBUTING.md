# Contributing to react-native-geckoview

Thanks for your interest in contributing. This library embeds Mozilla's
GeckoView (Firefox rendering engine) into React Native on Android.

## Quick start

```bash
# 1. Install dependencies (library + example app)
yarn install
cd example && yarn install

# 2. Run the test suites locally (counts: see docs/TESTING.md)
yarn test                                                                    # JS (Jest)
cd example/android && ./gradlew :react-native-geckoview:testDebugUnitTest    # Kotlin debug
cd example/android && ./gradlew :react-native-geckoview:testReleaseUnitTest  # Kotlin release: enforces security guards

# 3. Build the TypeScript output (CommonJS + ESM + .d.ts)
yarn build

# 4. Typecheck the library source
yarn typecheck

# 5. Launch the example app on a connected device / emulator
cd example && yarn android
```

The library is **Android-only** — GeckoView has no iOS support.

## Layout

```
├── android/src/main/kotlin/com/reactnative/geckoview/   # native module
│   ├── GeckoViewManagerImpl.kt     # shared manager logic
│   ├── GeckoViewModule.kt          # @ReactMethod native commands
│   ├── GeckoSessionManager.kt      # session pool (keyed by sessionKey)
│   ├── GeckoRuntimeManager.kt      # singleton Gecko runtime + extension prompt delegate
│   ├── WebExtensionController.kt   # bundled-extension install + messaging
│   ├── ContentBlockingController.kt# ETP / cookie-banner setters
│   └── *Delegate.kt                # GeckoView callback adapters
├── android/src/newarch/kotlin/     # Fabric ViewManager glue
├── android/src/test/kotlin/        # Kotlin unit tests (MockK + Robolectric)
├── src/                            # TypeScript source
│   ├── types.ts                    # user-facing prop / ref types
│   ├── NativeEventPayloads.ts      # shared event payload shapes
│   ├── GeckoViewNativeComponent.ts # codegen spec
│   └── fabricDetection.ts          # Fabric runtime detection (error-boundary UX)
├── index.tsx                       # entry point
├── example/__tests__/              # Jest test suite
└── docs/                           # additional docs
```

## Making changes

1. **File an issue first** for new features or behavior changes so the
   approach can be discussed before code review.
2. **Branch from `main`**. Name branches `fix/<topic>`, `feat/<topic>`,
   or `chore/<topic>`.
3. **Tests are required** for new behavior. Mirror the existing style:
   - JS: `example/__tests__/<Area>-test.tsx` using the shared
     `setupReactNativeMock()` and `fixtures` from `testUtils.ts`.
   - Kotlin: `android/src/test/kotlin/.../<Class>Test.kt` using Robolectric
     + MockK (the repo standardized on MockK in 2026-Q2 — do not add
     `org.mockito.*` imports).
   - **Security-sensitive changes** (URL/path validators, runtime-config
     flags, delegate install, permission defaults): add an adversarial
     regression test in the matching `*Test.kt` file. See the security-
     guard sections in [docs/TESTING.md](docs/TESTING.md).
4. **Run the full suite locally** before opening a PR. CI runs the same
   commands on Ubuntu with Node 22 and JDK 21 (Zulu).
5. **Keep the public API stable** unless the PR is explicitly a breaking
   change. Prop names, ref methods, and native event names are the
   contract.
6. **Document breaking changes** in [CHANGELOG.md](CHANGELOG.md) under a
   `### Breaking` heading.

## Style

- **Kotlin**: idiomatic, favor `val`/data classes/scope functions; stick
  with existing indentation (4 spaces).
- **TypeScript**: `strict: true`; no `any` in public types (use `unknown`
  or a concrete shape in [src/NativeEventPayloads.ts](src/NativeEventPayloads.ts)).
- **JSDoc** on every new ref method, prop, and event type.
- **Commit messages**: conventional-commit-ish imperative subject
  (`Fix …`, `Add …`, `Refactor …`). No trailing `.`.

## Testing patterns

See [docs/TESTING.md](docs/TESTING.md) for the full test infrastructure
guide. Key points:

- Every test file calls `jest.clearAllMocks()` in `beforeEach`.
- Prefer `fixtures.urls.example` over hardcoded URLs.
- For Kotlin tests, final GeckoView classes (`ContentPermission`,
  `AlertPrompt.message`, `LoadRequest`, etc.) have public final fields
  that MockK cannot stub directly — set them via `TestHelpers.setField`,
  or call the internal non-delegate helper (e.g. `decideNavigation`)
  instead of the public override.
- Security-sensitive runtime-config assertions must run under the release
  variant too: `./gradlew :react-native-geckoview:testReleaseUnitTest`.

## Threading model

GeckoView operations must run on the UI thread. Anything called from a
`@ReactMethod` runs on the native-modules queue; wrap with
`UiThreadUtil.runOnUiThread { … }` before touching a `GeckoSession`
method that asserts on the UI thread (e.g. `close()`).

## Releasing

Releases are cut from `main`. Workflow:

1. Bump `version` in [package.json](package.json).
2. Update [CHANGELOG.md](CHANGELOG.md) with the new heading and
   release notes.
3. Run `yarn build` and verify `lib/` contents.
4. `git tag vX.Y.Z && git push --tags`.
5. `npm publish` (CI's `prepublishOnly` hook runs `yarn build`).

## Code of conduct

Be respectful. This is a small open-source project; treat contributors
and reporters the way you'd want to be treated. Harassment or
personal attacks are not tolerated and may result in a ban.
