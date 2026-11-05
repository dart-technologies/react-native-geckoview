# Troubleshooting & Debugging

Consolidated debugging and memory-profiling guide.

---

## JavaScript debugging (Chrome DevTools)

The repo ships an MCP config for AI-assisted debugging. Manual workflow:

1. **Launch Chrome with remote debugging.**
   ```bash
   # macOS
   /Applications/Google\ Chrome.app/Contents/MacOS/Google\ Chrome --remote-debugging-port=9222
   ```

2. **Enable the React Native debugger** from the in-app dev menu
   (`Cmd+M` on Mac emulator, shake on device) → "Open Debugger".

3. **Attach Chrome DevTools** via `chrome://inspect`; add `localhost:8081`
   as a discoverable target.

### MCP config (`.mcp-config.json`)

```json
{
  "mcpServers": {
    "chrome-devtools": {
      "command": "npx",
      "args": [
        "-y",
        "chrome-devtools-mcp@latest",
        "--browser-url=http://127.0.0.1:9222"
      ]
    }
  }
}
```

### Common issues

| Symptom | Fix |
|---|---|
| Port 9222 in use | `lsof -i :9222` → `kill -9 <PID>` |
| Debugger won't connect | `yarn start --reset-cache`, reload app (`R` twice in Metro) |
| No targets visible | Verify Chrome flag `--remote-debugging-port=9222`; check `localhost:9222/json` |

---

## Memory profiling

### Tools

- `adb shell dumpsys meminfo com.reactnative.geckoview.example`
- Android Studio → **Profiler** → Memory (for heap dumps / allocation tracking)

### Baseline → stress → compare

1. **Baseline** — launch the example, wait 1–2 minutes:
   ```bash
   adb shell dumpsys meminfo com.reactnative.geckoview.example | grep -A 10 "TOTAL"
   ```
2. **Stress** — repeat 10–20 times:
   - Navigate between URLs
   - Create/destroy sessions (`closeSession()` then fresh session key)
   - Toggle ETP levels and cookie modes
   - Install/uninstall web extensions
   - Mount/unmount `<GeckoView>` rapidly
3. **Compare** — re-run the `dumpsys meminfo` command and diff with the
   baseline. Continuous unbounded growth after stress stops indicates a
   leak.

### Continuous monitoring

```bash
watch -n 5 'adb shell dumpsys meminfo com.reactnative.geckoview.example | grep -A 10 TOTAL'
```

### Expected patterns

- **Normal:** memory ramps, then plateaus around the GeckoView steady-state
  (≈ 120–180 MB depending on page content).
- **Bad:** linear increase across stress cycles that doesn't release after
  calling `closeSession()` and letting GC run.

### Heap dump

```bash
adb shell am dumpheap com.reactnative.geckoview.example /data/local/tmp/heap.hprof
adb pull /data/local/tmp/heap.hprof
```

Open the `.hprof` in Android Studio → Profiler → **Analyze heap dump**.
Look for:
- `GeckoSession` instances held beyond expected lifetime.
- Delegate adapters (`GeckoViewNavigationDelegate`, `GeckoViewPromptDelegate`)
  whose referent view has been dropped. A `MediaDelegate` is expected to stay
  on an open session with a `sessionKey` after its view drops, until the
  session closes; a dropped view's delegate holds no view, Activity or React
  module. A view without a `sessionKey` closes its session when it drops, so
  that session should not outlive it.
- Unreleased `AudioFocusRequest` objects when `handleAudioFocus=true` is
  toggled aggressively.

---

## Native debugging (GeckoView + Kotlin)

1. Open `example/android/` in Android Studio.
2. Run the example in **Debug** mode (`yarn android` or Studio's run button).
3. Attach the debugger via **Run → Attach Debugger to Android Process**.
4. Kotlin source breakpoints work in `android/src/main/kotlin/...`.

GeckoView's internal logs are accessible via `adb logcat -s Gecko*`.

---

## Known test-suite quirks

- Tests that exercise `Arguments.makeNativeMap` require the Facebook
  `SoLoader` native init, which Robolectric can't bootstrap. The AuthPrompt
  event path is therefore `@Ignore`d in `GeckoViewPromptDelegateTest` and
  covered by device integration tests instead.
- `GeckoSession.ProgressDelegate.SecurityInformation`,
  `ContextElement`, `MediaSession.Metadata`, and `WebRequestError` are
  final classes that MockK can't instantiate under Robolectric. Behavior
  around them is tested structurally; full semantics via integration.

---

## External references

- [React Native Debugging](https://reactnative.dev/docs/debugging)
- [Chrome DevTools Protocol](https://chromedevtools.github.io/devtools-protocol/)
- [GeckoView Debugging docs](https://firefox-source-docs.mozilla.org/mobile/android/geckoview/consumer/gv-debug.html)
