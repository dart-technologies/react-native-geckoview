# Advanced Usage Guide

This guide covers advanced features of `react-native-geckoview` including
Web Extensions, custom prompt handling, security configuration, and
performance optimization.

## 1. Web Extensions

GeckoView supports standard WebExtensions (similar to Chrome/Firefox
extensions). This allows you to inject scripts, modify content, and
communicate between the web page and React Native.

### Installation

Extensions must be packaged as `.xpi` files or a directory containing
`manifest.json`.

1.  **Place extension assets** in `android/app/src/main/assets/`.
2.  **Pass the relative asset path** to `installWebExtension`. The library
    resolves it against the APK's `assets/` via `resource://android/assets/<path>`.
3.  **Path validation applies**: `../`, URL-encoded traversal (`%2e`, `%2f`,
    `%5c`), null bytes, backslashes, and absolute paths are rejected — the
    promise rejects with `INVALID_ASSET_PATH`.

```javascript
import { useRef, useEffect } from 'react';
import GeckoView from 'react-native-geckoview';

function ExtensionBrowser() {
  const geckoRef = useRef(null);

  useEffect(() => {
    (async () => {
      try {
        // Relative to assets/ — the library prepends resource://android/assets/
        await geckoRef.current?.installWebExtension('extensions/my-extension');
      } catch (e) {
        console.warn('Extension install failed:', e); // INVALID_ASSET_PATH or native error
      }
    })();
  }, []);

  return <GeckoView ref={geckoRef} source={{ uri: 'https://example.com' }} />;
}
```

### Update-prompt gate

The runtime installs a `WebExtensionController.PromptDelegate` that:
- Denies installs of extension IDs not on the library's allowlist.
- Denies updates that request new permissions or origins (closes the
  "compromised-upstream-bump" attack).

If you need to add a new bundled extension, update `allowedExtensionIds`
in `GeckoRuntimeManager.kt` AND ship a signed update channel — the gate
does not accept arbitrary installed extensions by design.

### Messaging

Communication between the extension and React Native uses the `nativeMessaging` API.

**In Extension (`background.js` or content script):**
```javascript
const port = browser.runtime.connectNative('react_native_geckoview');

// Send message to React Native
browser.runtime.sendNativeMessage('react_native_geckoview', { type: 'greeting', content: 'Hello from Extension' });

// Receive message from React Native
port.onMessage.addListener((message) => {
  console.log('Received from RN:', message);
});
```

**In React Native:**
```javascript
<GeckoView
  ref={geckoRef}
  onWebExtensionMessage={(event) => {
    console.log('Extension says:', event.nativeEvent.message);
  }}
/>

// Send message to a connected extension port in this session
await geckoRef.current?.sendWebExtensionMessage(
  'my-extension@example.com',
  JSON.stringify({ type: 'update', data: 'New data' })
);
```

### Spatial Navigation (Android TV / D-pad)

For Android TV, Automotive (AAOS), and other D-pad-driven surfaces, the
`@dart-technologies/spatial-navigation-geckoview` extension provides
focus management, directional navigation, and D-pad-center activation
inside the rendered page. Use extension **3.1.0**.

**Bundling.** Place the extension's `manifest.json`, `spatial_navigation.js`,
and `background.js` under the host app's
`android/src/main/assets/spatial_navigation/`, then install it after mount:

```javascript
await geckoRef.current?.installWebExtension('spatial_navigation');
```

**Built-in bridge.** The extension relays to a native-messaging app id that
is pinned to `flutter_geckoview` and cannot be reconfigured from the host.
This library registers its message delegate under **both**
`react_native_geckoview` and `flutter_geckoview` (see
`WebExtensionController.NATIVE_APP_IDS`), so the extension's two host-facing
messages work out of the box with no glue code:

- `simulateClick` — D-pad center synthesizes a touch/click at the focused
  element's coordinates.
- `focusExit` — when focus leaves the page boundary, fires the
  `onFocusExit` prop (`{ direction: 'up' | 'down' | 'left' | 'right' }`) so
  the host can move focus to native UI.

```javascript
<GeckoView
  ref={geckoRef}
  source={{ uri: 'https://example.com' }}
  onFocusExit={(e) => moveNativeFocus(e.nativeEvent.direction)}
/>
```

**3.0.x → 3.1.0 migration.** In 3.1.0, `boundaryScrollBehavior` defaults to
`'scroll'` (it was `'exit'` in 3.0.x). If you relied on boundary `onFocusExit`
events to hand focus back to native UI, restore the old behavior by setting
the config before the extension's script runs (e.g. via an injected page
script):

```javascript
window.spatialNavConfig = { boundaryScrollBehavior: 'exit' };
```

**Retire any prior Expo config plugin.** The `simulateClick`/`focusExit`
bridge is now built into this library. If a host app previously patched the
library with an Expo config plugin that injected this behavior (or overwrote
`WebExtensionController.kt`), **retire that plugin** — leaving it enabled will
clobber the built-in bridge.

---

## 2. Custom Prompt Handling

By default, `react-native-geckoview` does not show native Android dialogs for `alert()`, `confirm()`, or `prompt()`. You must implement the UI in React Native.

### Implementation Pattern

Use a state-driven approach to show React Native Modals or Alerts.

```javascript
import { Alert } from 'react-native';

function Browser() {
  const geckoRef = useRef(null);

  const handleAlert = (event) => {
    const { promptId, message } = event.nativeEvent;
    Alert.alert('Page Alert', message, [
      { text: 'OK', onPress: () => geckoRef.current?.resolvePrompt(promptId, true) }
    ]);
  };

  const handleConfirm = (event) => {
    const { promptId, message } = event.nativeEvent;
    Alert.alert('Confirm', message, [
      { text: 'Cancel', onPress: () => geckoRef.current?.resolvePrompt(promptId, false) },
      { text: 'OK', onPress: () => geckoRef.current?.resolvePrompt(promptId, true) }
    ]);
  };

  return (
    <GeckoView
      ref={geckoRef}
      onGeckoAlert={handleAlert}
      onGeckoConfirm={handleConfirm}
      // ...
    />
  );
}
```

### HTTP Authentication

Handle `onGeckoAuth` to support Basic/Digest authentication. The username and
password are passed as the 4th and 5th positional arguments to `resolvePrompt`
— the signature is `resolvePrompt(promptId, confirm, text?, username?, password?)`.

```javascript
const handleAuth = (event) => {
  const { promptId, authOptions } = event.nativeEvent;
  // Show login modal...
  // On submit:
  geckoRef.current?.resolvePrompt(promptId, true, undefined, 'user', 'pass');
};
```

---

## 3. Security configuration

### URL allowlist on `loadUrl` / `setSource`

Only `http`, `https`, and `about` schemes reach the session. `javascript:`,
`data:`, `file:`, `content:`, and `blob:` are silently dropped. If your app
forwards any user-controlled URL (deep link, QR scan, paste handler) to
`loadUrl`, this protects you from trivial XSS — but you should NOT rely on
the drop being silent if UX requires notifying the user. Validate upstream.

### Navigation gate (`onLoadRequest`)

The library registers `onLoadRequest` and `onSubframeLoadRequest` handlers
that:
- Allow `http`, `https`, `about`.
- Deny `javascript:`, `data:`, `file:`, `content:`, `blob:` (all frames).
- Route `intent://` top-frame navigations to the sanitized dialog flow
  (subframes can never trigger `intent://` — attacker frames can't
  silently launch external apps).
- Deny top-frame `market:` and `geo:` navigations and ask before opening the
  Play Store or a maps app; Play Store and maps web URLs ask the same way.

No hook is currently exposed to override this from JS. If you need a
custom navigation policy, fork the library or file an issue.

### Intent-URI sanitization

When a page navigates to `intent://...`, the library parses via
`Intent.parseUri(URL, URI_INTENT_SCHEME)` and then:
- Clears `selector` and `component` (prevents hijacking to a specific receiver).
- Masks `FLAG_GRANT_{READ,WRITE,PERSISTABLE,PREFIX}_URI_PERMISSION` (prevents
  attacker-initiated URI grants).
- Removes `CATEGORY_DEFAULT`, adds `CATEGORY_BROWSABLE`.
- Rejects intents whose `data` scheme is anything other than `http` or `https`.

Custom-scheme intent payloads are denied by default. If your app needs one,
add a narrow allowlist in native code instead of forwarding arbitrary schemes.

### Runtime security options

Security-sensitive runtime features are off by default. Configure them before
the first `GeckoView` mount:

```typescript
import { configureGeckoViewSecurity } from 'react-native-geckoview';

await configureGeckoViewSecurity({
  allowDrm: false,
  allowAutoplay: false,
  enableConsoleOutput: false,
});
```

Set `allowDrm` only when the app needs Encrypted Media Extensions. Set
`allowAutoplay` only when drive-by playback is intended. `enableConsoleOutput`
only affects debug builds; release builds keep page console output out of
logcat.

When the runtime starts, the library sets these Gecko prefs through GeckoView's
`GeckoPreferenceController` (default branch, re-applied on every launch):

| Pref | Default | With opt-in |
|---|---|---|
| `media.autoplay.default` | `5` (block audible and inaudible) | `0` with `allowAutoplay` |
| `media.geckoview.autoplay.request` | `true` (blocked autoplay reaches `onPermissionRequest` as `autoplay_audible` / `autoplay_inaudible`) | `false` with `allowAutoplay` |
| `media.eme.enabled` | `false` | `true` with `allowDrm` |

Gecko has no command-line pref switch, so earlier builds that passed these as
`--setpref=` runtime arguments never applied them.

### Content-process sandbox

All builds run with GeckoView's default content-process sandbox. The library
passes no command-line arguments to Gecko, so no sandbox-disabling switch can
reach it.

Do NOT add sandbox-disable flags without understanding the trade-off: removing
the sandbox means any Gecko RCE gains full app-context code execution.

### Extension signature verification

Signature verification is ON by default at the runtime level. Bundled
extensions installed via `installWebExtension(<assetPath>)` use
`WebExtensionController.installBuiltIn`, which bypasses the signature check
for APK-packed `resource://` URIs by design. Do NOT re-enable
`xpinstall.signatures.required=false` — the library's current posture
assumes it stays ON.

---

## 4. Performance Optimization

### Session Management

*   **Reuse Sessions**: Use `sessionKey` to persist state (cookies, history, cache) across component unmounts. This avoids expensive re-initialization. A page keeps playing after its view unmounts. With `handleAudioFocus`, the library keeps handling the session's audio focus while no view shows it, until the session closes (`closeSession()`, `shutdown()` or the AAOS playback gate) or its content process dies: it holds focus while the page plays (taking it again if the page starts playing later) and pauses the page on a focus loss. A view that binds the same `sessionKey` carries that focus on without requesting it again, and gets `onMediaSessionAction` events `activated` and `play` for media already active or playing (not `metadata`, which isn't repeated). The carry-over is short when the remount also passes `source`: the view loads that URI into the pooled session, which stops the page's media, and the focus is given up as for any stop.
*   **Limit Concurrent Sessions**: Each `GeckoSession` consumes significant memory. Close a view's session with `ref.closeSession()`; `ref.shutdown()` tears down the runtime and every session in the process.
*   **Closed sessions stay closed**: sessions are created and opened only when a mounted view binds its `sessionKey`. After `closeSession()`, ref methods on that view never rebuild the session: `reload`, `goBack`, `goForward`, `stop`, `loadUrl` and `setUserAgent` do nothing, `evaluateJavaScript` rejects with `EVAL_BRIDGE_UNAVAILABLE` and `captureSnapshot` rejects with `VIEW_NOT_FOUND`. Remount the `GeckoView` (for example by changing its React `key`) to get a fresh session. A session GeckoView closed itself because its content process died is reopened when a view binds its `sessionKey` again, and loads that view's `source` afresh; see [Content-process crashes](#content-process-crashes).
*   **AAOS playback gate**: while the host has called `GeckoSessionManager.setPlaybackAllowed(false)`, every session is closed and the `sessionKey` and `source` props are dropped, not queued. `setPlaybackAllowed(true)` never reopens a session or resumes media, so remount the `GeckoView` once playback is allowed again.

### Content-process crashes

GeckoView closes a session itself when its content process crashes, or when the system kills it, often to reclaim memory. The view showing the session goes blank, and media that was playing stops; no `onMediaSessionAction` `pause` or `stop` is guaranteed to follow. The library gives up any audio focus it held for the session, then fires `onContentProcessGone` on that view, with `reason` `'crash'` or `'kill'`. A pooled session that no view shows fires nothing; the next view to bind its `sessionKey` reopens it.

The mounted view keeps the closed session, and `ref.reload()` doesn't bring the page back, because it goes to that closed session. Remount the view to recover, for example by changing its React `key`:

*   **Same `sessionKey`**: the pool reopens the session GeckoView closed (`GeckoSessionManager.ensureSessionOpen`), and the view loads its `source` afresh. The page's history and in-memory state are lost; cookies and the cache belong to the runtime, so they survive.
*   **Fresh `sessionKey`**: the view gets a new session. A view reads `sessionKey` only when it mounts, so this takes a remount too. A view mounted without a `sessionKey` gets a new generated key each time it mounts, so remounting it is always this case. Call `closeSession()` on the old view first, so the dead session doesn't stay pooled under the old key.

Don't call `closeSession()` and remount with the same `sessionKey` together. `closeSession()` reaches the UI thread by way of the native-modules thread, so it can close the reopened session after the new view has bound it: the view stays blank, and no event fires. The same goes for a `closeSession()` in an unmount cleanup, such as a `useEffect` cleanup, that runs during the remount.

A page that crashes its content process each time it loads fires the event again after every remount, so cap the deaths that come in a row, not those over the view's lifetime: an app that runs for days, as on a TV or in a car, sees the occasional low-memory kill.

```javascript
const MAX_IN_A_ROW = 3;
const IN_A_ROW_MS = 60_000; // a death later than this after the last starts a new count

function ResilientBrowser() {
  const [deaths, setDeaths] = useState({ mount: 0, inARow: 0, last: 0 });

  if (deaths.inARow > MAX_IN_A_ROW) {
    return <ErrorBanner message="This page keeps crashing." />;
  }

  return (
    <GeckoView
      key={deaths.mount} // a new key remounts the view
      sessionKey="main"
      source={{ uri: 'https://example.com' }}
      onContentProcessGone={(e) => {
        console.warn('Content process gone:', e.nativeEvent.reason); // 'crash' | 'kill'
        const now = Date.now();
        setDeaths((d) => ({
          mount: d.mount + 1, // the pool reopens "main" for the new view
          inARow: now - d.last < IN_A_ROW_MS ? d.inARow + 1 : 1,
          last: now,
        }));
      }}
      style={{ flex: 1 }}
    />
  );
}
```

### Rendering

*   **Hardware Acceleration**: Ensure your Android manifest enables hardware acceleration.
*   **Layer Types**: In some cases, setting `renderToHardwareTextureAndroid` on the container View can help with compositing.

### Memory Leaks

*   **Event Listeners**: Ensure you are not creating closures that hold onto `GeckoView` refs unnecessarily.
*   **Large Snapshots**: Avoid frequent calls to `captureSnapshot()` with high resolutions, as this creates large Bitmaps in memory. The capture runs on the UI thread and the PNG encode on a background thread; one save runs while one more waits, and a capture beyond that rejects with `SNAPSHOT_BUSY`. A save still in flight when its session closes rejects with `VIEW_NOT_FOUND`, or with `PLAYBACK_RESTRICTED` once the AAOS playback gate closes, and leaves no file behind. Closing a session, through `closeSession()`, `shutdown()` or the gate, also deletes the snapshots saved for it.

---

## 5. Fabric (New Architecture)

`react-native-geckoview` **requires** the New Architecture (Fabric) on
React Native 0.85+. Paper is not supported.

### Verification

1.  Run the app (`yarn android`).
2.  Metro logs should include `Running "example" with {"fabric":true}`.
3.  If the component renders a red "GeckoView failed to render" panel,
    the codegen library didn't load — rebuild with `./gradlew clean`
    and ensure `newArchEnabled=true` is set in the consumer app (the
    flag is ignored by the RN Gradle Plugin but some downstream tools
    still read it).

### Troubleshooting

If you see "View config getter callback... must be a function":
*   Ensure the C++ library `libreact_codegen_geckoviewspec.so` is loading.
*   Check that `GeckoViewPackage` is added to your `MainApplication.kt` (though autolinking usually handles this).
