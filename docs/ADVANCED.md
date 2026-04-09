# Advanced Usage Guide

This guide covers advanced features of `react-native-geckoview` including Web Extensions, custom prompt handling, and performance optimization.

## 1. Web Extensions

GeckoView supports standard WebExtensions (similar to Chrome/Firefox extensions). This allows you to inject scripts, modify content, and communicate between the web page and React Native.

### Installation

Extensions must be packaged as `.xpi` files or a directory containing `manifest.json`.

1.  **Place Extension Assets**: Add your extension files to `android/app/src/main/assets/extensions/`.
2.  **Install in React Native**:

```javascript
import { useRef, useEffect } from 'react';
import GeckoView from 'react-native-geckoview';

function ExtensionBrowser() {
  const geckoRef = useRef(null);

  useEffect(() => {
    // Install from Android assets
    geckoRef.current?.installWebExtension('resource://android/assets/extensions/my-extension/');
  }, []);

  return <GeckoView ref={geckoRef} source={{ uri: 'https://example.com' }} />;
}
```

### Messaging

Communication between the extension and React Native uses the `nativeMessaging` API.

**In Extension (`background.js`):**
```javascript
// Send message to React Native
browser.runtime.sendNativeMessage('browser', { type: 'greeting', content: 'Hello from Extension' });

// Receive message from React Native
browser.runtime.onNativeMessage.addListener((message) => {
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

// Send message to extension
geckoRef.current?.sendWebExtensionMessage({ type: 'update', data: 'New data' });
```

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

Handle `onGeckoAuth` to support Basic/Digest authentication.

```javascript
const handleAuth = (event) => {
  const { promptId, authOptions } = event.nativeEvent;
  // Show login modal...
  // On submit:
  geckoRef.current?.resolvePrompt(promptId, true, null, {
    username: 'user',
    password: 'pass'
  });
};
```

---

## 3. Performance Optimization

### Session Management

*   **Reuse Sessions**: Use `sessionKey` to persist state (cookies, history, cache) across component unmounts. This avoids expensive re-initialization.
*   **Limit Concurrent Sessions**: Each `GeckoSession` consumes significant memory. Destroy unused sessions using `GeckoViewModule.shutdown()` if necessary, though React Native's lifecycle usually handles this.

### Rendering

*   **Hardware Acceleration**: Ensure your Android manifest enables hardware acceleration.
*   **Layer Types**: In some cases, setting `renderToHardwareTextureAndroid` on the container View can help with compositing.

### Memory Leaks

*   **Event Listeners**: Ensure you are not creating closures that hold onto `GeckoView` refs unnecessarily.
*   **Large Snapshots**: Avoid frequent calls to `captureSnapshot()` with high resolutions, as this creates large Bitmaps in memory.

---

## 4. Fabric (New Architecture)

`react-native-geckoview` supports the New Architecture (Fabric) on React Native 0.81+.

### Verification

To verify Fabric is active:
1.  Enable `newArchEnabled=true` in `android/gradle.properties`.
2.  Run the app.
3.  Check Metro logs for "Fabric" tags.
4.  If the component renders successfully without falling back to the Paper renderer warning, Fabric is working.

### Troubleshooting

If you see "View config getter callback... must be a function":
*   Ensure the C++ library `libreact_codegen_geckoviewspec.so` is loading.
*   Check that `GeckoViewPackage` is added to your `MainApplication.kt` (though autolinking usually handles this).
