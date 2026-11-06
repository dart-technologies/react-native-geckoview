# react-native-geckoview [![npm](https://badge.fury.io/js/react-native-geckoview.svg)](https://www.npmjs.com/package/react-native-geckoview)

A React Native wrapper for Mozilla's [GeckoView](https://github.com/mozilla/geckoview) browser engine. Android only.

## Version Information

**GeckoView Version**: 150.0.20260415192539
- [GeckoView JavaDoc](https://mozilla.github.io/geckoview/javadoc/mozilla-central/org/mozilla/geckoview/package-summary.html)
- [GeckoView Changelog](https://mozilla.github.io/geckoview/javadoc/mozilla-central/org/mozilla/geckoview/doc-files/CHANGELOG)
- [Mozilla Maven Repository](https://maven.mozilla.org/maven2/)

**Build Requirements**:
- React Native: **0.85+** (Expo SDK 56-aligned)
- Gradle: 9.1.0
- Android Gradle Plugin: 8.12.0
- Kotlin: 2.2.20
- Android compileSdkVersion: 36
- Android minSdkVersion: 26 (GeckoView 150 requires API 26+)

## New Architecture (Fabric) Only

This library targets React Native 0.85+, which is **New-Architecture-only**.
The `com.facebook.react` Gradle plugin runs codegen unconditionally, builds
`libreact_codegen_geckoviewspec.so`, and registers the GeckoView
ComponentDescriptor automatically. No `newArchEnabled` flag needed.

Consumers on React Native 0.81 / 0.82 should pin
`react-native-geckoview@^0.1.0`, which retained Paper support.

## Installation

```bash
yarn add react-native-geckoview
```

Add Mozilla's Maven repository to your `android/build.gradle`:

```gradle
allprojects {
    repositories {
        // ... other repositories
        maven {
            url "https://maven.mozilla.org/maven2/"
        }
    }
}
```

## Features

- ✅ GeckoView 150 with modern web platform support
- ✅ Media playback with audio focus management
- ✅ Page load progress events and security monitoring
- ✅ Enhanced Tracking Protection (ETP)
- ✅ Cookie banner handling
- ✅ HTTP authentication prompts
- ✅ Popup and before-unload prompt handling
- ✅ JavaScript evaluation
- ✅ Session persistence and management
- ✅ Web extension support
- ✅ Navigation error handling
- ✅ Security-by-default: URL scheme allowlist, intent-URI sanitization,
  signed-extension-update gate, sandboxed content process in release

## Basic Usage

```javascript
import React, { useRef } from 'react';
import GeckoView from 'react-native-geckoview';

function MyBrowser() {
  const geckoRef = useRef(null);

  return (
    <GeckoView
      ref={geckoRef}
      source={{ uri: 'https://dart.art' }}
      style={{ flex: 1 }}
    />
  );
}
```

## API Reference

### Props

#### Basic Props

| Prop | Type | Default | Description |
|------|------|---------|-------------|
| `source` | `{ uri: string }` | required | URL to load |
| `sessionKey` | `string` | `undefined` | Pools the view's session under this key, so a remount with the same key gets it back. Without one, the view gets a session of its own, closed when the view unmounts |
| `handleAudioFocus` | `boolean` | `false` | Automatically manage Android audio focus for media playback |
| `contentBlockingEnabled` | `boolean` | `undefined` | Enable content blocking features (Gecko's built-in tracking protection) |
| `cookieBannerMode` | `'reject'` \| `'accept'` \| `'none'` | `'reject'` | Cookie banner handling mode |
| `desktopMode` | `boolean` | `true` | Use a desktop user-agent and viewport instead of mobile |

#### Progress Events

| Event | Payload | Description |
|-------|---------|-------------|
| `onGeckoPageStart` | `{ url: string }` | Fired when page load starts |
| `onGeckoPageStop` | `{ success: boolean }` | Fired when page load completes |
| `onGeckoProgressChange` | `{ progress: number }` | Loading progress (0-100) |
| `onGeckoSecurityChange` | `{ isSecure: boolean, host: string }` | SSL/security status change |

#### Error Events

| Event | Payload | Description |
|-------|---------|-------------|
| `onPageError` | `{ uri, errorCode, errorCategory, errorMessage }` | Navigation error occurred |
| `onContentProcessGone` | `{ reason: 'crash'\|'kill' }` | The page's content process crashed or was killed, and GeckoView closed the session; remount the view to recover ([docs/ADVANCED.md](docs/ADVANCED.md#content-process-crashes)) |

#### Media Events

| Event | Payload | Description |
|-------|---------|-------------|
| `onMediaSessionAction` | `{ action, title?, artist?, album? }` | Media playback state change |

Actions: `'play'`, `'pause'`, `'stop'`, `'activated'`, `'deactivated'`, `'metadata'`

#### Prompt Events

| Event | Payload | Description |
|-------|---------|-------------|
| `onGeckoAlert` | `{ promptId, message, defaultValue? }` | JavaScript alert() |
| `onGeckoConfirm` | `{ promptId, message, defaultValue? }` | JavaScript confirm() |
| `onGeckoPrompt` | `{ promptId, message, defaultValue? }` | JavaScript prompt() |
| `onGeckoPopup` | `{ promptId, targetUri?, type }` | Popup blocked |
| `onGeckoBeforeUnload` | `{ promptId, type }` | Before page unload |
| `onGeckoAuth` | `{ promptId, title?, message?, authOptions, type }` | HTTP authentication required |

#### Permission Events

| Event | Payload | Description |
|-------|---------|-------------|
| `onPermissionRequest` | `{ requestId, permission, uri }` | Permission requested (camera, microphone, etc.) |

#### Other Events

| Event | Payload | Description |
|-------|---------|-------------|
| `onWebExtensionMessage` | `{ message: { extensionId?, ... } }` | Message from web extension |
| `onTitleChange` | `{ title: string }` | Page title changed |
| `onContextMenu` | `{ x, y, linkUri?, srcUri?, type }` | Context menu requested |
| `onFocusExit` | `{ direction: 'up'\|'down'\|'left'\|'right' }` | Focus exited view (D-pad / TV / AAOS) |

### Imperative Methods

Access methods via ref:

```javascript
const geckoRef = useRef(null);

// Navigation
geckoRef.current?.reload();
geckoRef.current?.goBack();
geckoRef.current?.goForward();
geckoRef.current?.stop();

// Load a URL. Only http/https/about schemes are accepted; javascript:,
// data:, file:, content:, and blob: are silently dropped to prevent XSS
// via host-app forwarding of user-controlled URLs.
geckoRef.current?.loadUrl('https://example.com');

// JavaScript Execution. evaluateJavaScript uses the trusted internal
// WebExtension bridge. Code runs in the bridge content-script context
// with DOM access, and rejects with EVAL_BRIDGE_UNAVAILABLE until that
// bridge is connected for the session. The return type is generic:
// annotate it to avoid a cast at the call site.
const title = await geckoRef.current?.evaluateJavaScript<string>('document.title');

// Prompt Resolution. Signature:
//   resolvePrompt(promptId, confirm, text?, username?, password?)
geckoRef.current?.resolvePrompt(promptId, true, 'text input');     // text prompt
geckoRef.current?.resolvePrompt(promptId, true, undefined, user, pass); // HTTP auth

// Permission Handling
geckoRef.current?.resolvePermission(requestId, true);

// Privacy Controls. Both return Promise<void> and reject with
// INVALID_ETP_LEVEL / INVALID_COOKIE_BANNER_MODE on out-of-range input.
await geckoRef.current?.setEnhancedTrackingProtectionLevel(2); // 0=NONE, 1=DEFAULT, 2=STRICT
await geckoRef.current?.setCookieBannerMode(1);                // 0=DISABLED, 1=REJECT, 2=REJECT_OR_ACCEPT

// Web Extensions. Path is validated: ../, %2e%2e, null bytes, backslashes,
// and absolute paths are rejected. Update prompts that request new
// permissions or origins are denied automatically.
await geckoRef.current?.installWebExtension('my-extension');  // resolves relative to assets/
await geckoRef.current?.sendWebExtensionMessage(
  'my-extension@example.com',
  JSON.stringify({ action: 'doSomething' })
);

// Lifecycle
geckoRef.current?.closeSession(); // this view's session
geckoRef.current?.shutdown();     // the runtime and every session in the process
```

## Advanced Usage Examples

### Session Persistence

Persist browser sessions across component remounts:

```javascript
function PersistentBrowser() {
  const [url, setUrl] = useState('https://example.com');

  return (
    <GeckoView
      source={{ uri: url }}
      sessionKey="main-browser" // Preserves history and state
      style={{ flex: 1 }}
    />
  );
}
```

A session with a `sessionKey` stays open after its view unmounts, until
`closeSession()`, `shutdown()` or the AAOS playback gate closes it. Without a
`sessionKey`, each `GeckoView` gets a session of its own, which closes when the
view unmounts: the page and its media stop, and its saved snapshots are
deleted. StrictMode's double-invoked effects and a Fast Refresh that keeps
state don't close it.
Give a view a `sessionKey` to keep its page, or its audio, alive after it
unmounts. See [docs/ADVANCED.md](docs/ADVANCED.md#session-management).

### Media Playback with Audio Focus

```javascript
function MediaBrowser() {
  return (
    <GeckoView
      source={{ uri: 'https://youtube.com' }}
      handleAudioFocus={true} // Auto-manage audio focus
      onMediaSessionAction={(event) => {
        const { action, title, artist } = event.nativeEvent;
        console.log(`Media ${action}:`, { title, artist });
      }}
      style={{ flex: 1 }}
    />
  );
}
```

### Progress Monitoring

```javascript
function BrowserWithProgress() {
  const [loading, setLoading] = useState(false);
  const [progress, setProgress] = useState(0);

  return (
    <>
      {loading && <ProgressBar progress={progress} />}
      <GeckoView
        source={{ uri: 'https://example.com' }}
        onGeckoPageStart={() => {
          setLoading(true);
          setProgress(0);
        }}
        onGeckoProgressChange={(e) => {
          setProgress(e.nativeEvent.progress);
        }}
        onGeckoPageStop={() => {
          setLoading(false);
        }}
        style={{ flex: 1 }}
      />
    </>
  );
}
```

### Error Handling

```javascript
function RobustBrowser() {
  const [error, setError] = useState(null);

  return (
    <>
      {error && <ErrorBanner message={error} />}
      <GeckoView
        source={{ uri: 'https://example.com' }}
        onPageError={(event) => {
          const { errorMessage, errorCategory } = event.nativeEvent;
          setError(`${errorCategory}: ${errorMessage}`);
        }}
        style={{ flex: 1 }}
      />
    </>
  );
}
```

### Prompt Handling

The library never shows native dialogs for `alert()`/`confirm()`/`prompt()`,
popups, or HTTP auth — you render the UI and resolve via `resolvePrompt`:

```javascript
// Popup / confirm: resolvePrompt(promptId, confirm)
geckoRef.current?.resolvePrompt(promptId, true);

// HTTP auth: resolvePrompt(promptId, confirm, text?, username?, password?)
geckoRef.current?.resolvePrompt(promptId, true, undefined, username, password);
```

See [docs/ADVANCED.md](docs/ADVANCED.md#2-custom-prompt-handling) for full
alert/confirm/prompt and HTTP-auth handling patterns.

### Enhanced Tracking Protection

Both privacy setters return `Promise<void>` and reject on invalid input;
handle rejections so configuration errors don't fail silently.

```javascript
function PrivacyBrowser() {
  const geckoRef = useRef(null);

  useEffect(() => {
    (async () => {
      try {
        await geckoRef.current?.setEnhancedTrackingProtectionLevel(2); // strict
        await geckoRef.current?.setCookieBannerMode(1);                // reject
      } catch (e) {
        console.warn('Privacy config failed:', e);
      }
    })();
  }, []);

  return (
    <GeckoView
      ref={geckoRef}
      source={{ uri: 'https://example.com' }}
      style={{ flex: 1 }}
    />
  );
}
```

### JavaScript Evaluation

```javascript
function ScriptableBrowser() {
  const geckoRef = useRef(null);

  const getPageTitle = async () => {
    const title = await geckoRef.current?.evaluateJavaScript('document.title');
    console.log('Page title:', title);
  };

  const injectCSS = async () => {
    await geckoRef.current?.evaluateJavaScript(`
      const style = document.createElement('style');
      style.textContent = 'body { background: #000 !important; }';
      document.head.appendChild(style);
    `);
  };

  return (
    <GeckoView ref={geckoRef} source={{ uri: 'https://example.com' }} style={{ flex: 1 }} />
  );
}
```

### Multiple Instances

```javascript
function SplitBrowser() {
  return (
    <View style={{ flex: 1, flexDirection: 'row' }}>
      <GeckoView
        source={{ uri: 'https://mozilla.org' }}
        sessionKey="left-pane"
        style={{ flex: 1 }}
      />
      <GeckoView
        source={{ uri: 'https://example.com' }}
        sessionKey="right-pane"
        style={{ flex: 1 }}
      />
    </View>
  );
}
```

## TypeScript Support

Full TypeScript definitions are included. Import types:

```typescript
import GeckoView, { GeckoViewProps, GeckoViewRef } from 'react-native-geckoview';

const MyComponent: React.FC = () => {
  const geckoRef = useRef<GeckoViewRef>(null);
  
  const props: GeckoViewProps = {
    source: { uri: 'https://example.com' },
    handleAudioFocus: true,
    onGeckoPageStart: (event) => {
      console.log('Started loading:', event.nativeEvent.url);
    }
  };
  
  return <GeckoView ref={geckoRef} {...props} />;
};
```

## Security

See the [CHANGELOG `[Unreleased]` entry](CHANGELOG.md#unreleased) for the
security-audit baseline. Quick summary of what the library enforces:

| Surface | Enforcement |
|---|---|
| `loadUrl` / `setSource` / popup URIs | Scheme allowlist: `http`, `https`, `about` only |
| `onLoadRequest` (top-frame + subframe) | `javascript:`, `data:`, `file:`, `content:`, `blob:` denied |
| `intent://` handling | Selector/component cleared, grant-URI flags masked, `BROWSABLE` required, dangerous data schemes rejected |
| `installWebExtension` | Path validated against `../`, `%2e`, `\`, null bytes, absolute paths |
| Extension update prompts | Installed delegate denies new permissions/origins + unlisted IDs |
| Content-process sandbox | Enabled in all builds; sandbox-disable flags are never set |
| Autoplay / DRM | Blocked unless `configureGeckoViewSecurity(...)` opts in before first mount |
| Page console output | Off by default; optional in debug builds via `configureGeckoViewSecurity(...)` |

Security-sensitive runtime options are opt-in via `configureGeckoViewSecurity(options)`,
which must be called before the first `GeckoView` mount:

| `GeckoSecurityOptions` | Type | Default | Description |
|---|---|---|---|
| `allowDrm` | `boolean` | `false` | Allow Encrypted Media Extensions / DRM key-system access |
| `allowAutoplay` | `boolean` | `false` | Allow audible/inaudible autoplay without prompting |
| `enableConsoleOutput` | `boolean` | `false` | Forward page console output to logcat (debug builds only) |
| `cookieBehavior` | `number` | `4` (`ACCEPT_NON_TRACKERS`) | GeckoView `ContentBlocking.CookieBehavior` constant applied at runtime init |
| `maxSnapshotPixels` | `number` | `16777216` (4096×4096) | Max `captureSnapshot` size before it rejects with `SNAPSHOT_TOO_LARGE` |

Security-sensitive APIs are marked in the JSDoc of
[src/types.ts](src/types.ts) and [index.tsx](index.tsx).

## Testing

The library includes comprehensive test coverage across three layers:
- **JavaScript (Jest)** - Component, session, and security surface
- **Kotlin unit tests** - All delegates, managers, and security validators
- **E2E (Detox)** - Real device flows

See [docs/TESTING.md](docs/TESTING.md) for the current test counts and the
full file-by-file breakdown.

Run tests:
```bash
cd example && yarn test                 # JavaScript
cd example/android && ./gradlew :react-native-geckoview:testDebugUnitTest    # Kotlin (debug)
cd example/android && ./gradlew :react-native-geckoview:testReleaseUnitTest  # Kotlin (release — security guards)
cd example && yarn detox:test:android   # E2E (requires emulator)
```

The release-variant Kotlin pass enforces the runtime-config regression
guards (sandbox flags absent, signature verification enabled, autoplay
default blocked) — run both variants before shipping.

## Advanced Usage

For Web Extensions, custom prompts, and performance tips, see [docs/ADVANCED.md](docs/ADVANCED.md).

## License

MIT License - Copyright (c) 2025 Dart Technologies, Inc. and all contributors

## Credits

Based on Mozilla's GeckoView. Ported from [flutter-geckoview](https://github.com/dart-technologies/flutter-geckoview) implementation.
