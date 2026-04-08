# react-native-geckoview [![npm](https://badge.fury.io/js/react-native-geckoview.svg)](https://www.npmjs.com/package/react-native-geckoview)

A React Native wrapper for Mozilla's [GeckoView](https://github.com/mozilla/geckoview) browser engine. Android only.

## Version Information

**GeckoView Version**: 143.0.20250908174027  
- [GeckoView JavaDoc](https://mozilla.github.io/geckoview/javadoc/mozilla-central/org/mozilla/geckoview/package-summary.html)
- [GeckoView Changelog](https://mozilla.github.io/geckoview/javadoc/mozilla-central/org/mozilla/geckoview/doc-files/CHANGELOG)
- [Mozilla Maven Repository](https://maven.mozilla.org/maven2/)

**Build Requirements**:
- Gradle: 8.9.1
- Android NDK: 27.0.12077973
- Android compileSdkVersion: 28+ (recommended: 34+)
- Android minSdkVersion: 16+ (GeckoView requires 21+)

## New Architecture (Fabric) Status

- ✅ Supported on React Native **0.81+**.  
  When `newArchEnabled=true`, the `com.facebook.react` Gradle plugin runs codegen, builds `libreact_codegen_geckoviewspec.so`, and registers the GeckoView ComponentDescriptor automatically.
- 🔁 If the consuming app keeps `newArchEnabled=false`, the library transparently falls back to the Paper renderer (no crashes or manual configuration required).

### Enabling Fabric

1. In your app’s `android/gradle.properties` set `newArchEnabled=true`.
2. Clean & rebuild (`./gradlew app:installDebug` or `yarn android`).
3. Verify the metro logs mention `Fabric` and GeckoView renders normally.  
   If Fabric fails to initialize, the JS component prints a warning and falls back to Paper.

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

- ✅ Latest GeckoView 143 with modern web platform support
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
| `sessionKey` | `string` | `undefined` | Unique session identifier for persistence across remounts |
| `handleAudioFocus` | `boolean` | `false` | Automatically manage Android audio focus for media playback |
| `contentBlockingEnabled` | `boolean` | `true` | Enable content blocking features |
| `cookieBannerMode` | `'reject'` \| `'accept'` \| `'none'` | `'none'` | Cookie banner handling mode |

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
| `onWebExtensionMessage` | `{ message }` | Message from web extension |
| `onTitleChange` | `title: string` | Page title changed |
| `onFaviconChange` | `uri: string` | Page favicon changed |
| `onContextMenu` | `items, x, y` | Context menu requested |
| `onFocusExit` | `direction: 'up'|'down'|'left'|'right'` | Focus exited view (TV/AAOS) |
| `onFullscreenRequest` | `enabled: boolean` | Fullscreen mode requested |
| `onPictureInPicture` | `enabled: boolean` | Picture-in-picture mode requested |

### Imperative Methods

Access methods via ref:

```javascript
const geckoRef = useRef(null);

// Navigation
geckoRef.current?.reload();
geckoRef.current?.goBack();
geckoRef.current?.goForward();
geckoRef.current?.stop();

// JavaScript Execution
const result = await geckoRef.current?.evaluateJavaScript('document.title');

// Prompt Resolution
geckoRef.current?.resolvePrompt(promptId, true, 'text input');

// Permission Handling
geckoRef.current?.resolvePermission(requestId, true);

// Privacy Controls
geckoRef.current?.setEnhancedTrackingProtectionLevel(2); // 0=NONE, 1=DEFAULT, 2=STRICT
geckoRef.current?.setCookieBannerMode(1); // 0=DISABLED, 1=REJECT, 2=REJECT_OR_ACCEPT

// Web Extensions
geckoRef.current?.installWebExtension('asset://extensions/my-extension');
geckoRef.current?.sendWebExtensionMessage(JSON.stringify({ action: 'doSomething' }));

// Lifecycle
geckoRef.current?.shutdown();
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

```javascript
function InteractiveBrowser() {
  const geckoRef = useRef(null);
  const [promptId, setPromptId] = useState(null);
  const [authPrompt, setAuthPrompt] = useState(null);

  const handlePopup = (event) => {
    const { promptId, targetUri } = event.nativeEvent;
    Alert.alert(
      'Popup Blocked',
      `Allow popup to ${targetUri}?`,
      [
        {
          text: 'Block',
          onPress: () => geckoRef.current?.resolvePrompt(promptId, false)
        },
        {
          text: 'Allow',
          onPress: () => geckoRef.current?.resolvePrompt(promptId, true)
        }
      ]
    );
  };

  const handleAuth = (event) => {
    const { promptId, message } = event.nativeEvent;
    setAuthPrompt({ promptId, message });
    // Show custom auth dialog
  };

  const submitAuth = (username, password) => {
    geckoRef.current?.resolvePrompt(
      authPrompt.promptId,
      true,
      null,
      { username, password }
    );
    setAuthPrompt(null);
  };

  return (
    <GeckoView
      ref={geckoRef}
      source={{ uri: 'https://example.com' }}
      onGeckoPopup={handlePopup}
      onGeckoAuth={handleAuth}
      style={{ flex: 1 }}
    />
  );
}
```

### Enhanced Tracking Protection

```javascript
function PrivacyBrowser() {
  const geckoRef = useRef(null);

  useEffect(() => {
    // Set strict tracking protection
    geckoRef.current?.setEnhancedTrackingProtectionLevel(2);
    
    // Auto-reject cookie banners
    geckoRef.current?.setCookieBannerMode(1);
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

## Testing

The library includes comprehensive test coverage (~78% overall):
- **249 JavaScript tests** (~85% coverage) - Component, session, Fabric detection
- **191 Kotlin unit tests** (~75% coverage) - All delegates and managers
- **13 E2E tests** - Real device flows via Detox

Run tests:
```bash
cd example && npm test              # JavaScript
cd example && npm run detox:test:android # E2E (requires emulator)
cd example/android && ./gradlew :react-native-geckoview:testDebugUnitTest  # Kotlin
```

See [docs/TESTING.md](docs/TESTING.md) for details.

## Advanced Usage

For Web Extensions, custom prompts, and performance tips, see [docs/ADVANCED.md](docs/ADVANCED.md).

## License

MIT License - Copyright (c) 2025 Dart Technologies, Inc. and all contributors

## Credits

Based on Mozilla's GeckoView. Ported from [flutter-geckoview](https://github.com/dart-technologies/flutter-geckoview) implementation.
