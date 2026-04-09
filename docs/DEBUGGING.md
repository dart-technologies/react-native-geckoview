# Debugging Guide

## React Native JavaScript Debugging with Chrome DevTools

This project includes support for debugging React Native JavaScript using Chrome DevTools Protocol via MCP (Model Context Protocol).

### Prerequisites

- Node.js and npm installed
- Chrome or Chromium browser
- React Native development environment set up

### Setup

#### 1. Start Chrome with Remote Debugging Enabled

Launch Chrome/Chromium with remote debugging on port 9222:

```bash
# macOS
/Applications/Google\ Chrome.app/Contents/MacOS/Google\ Chrome --remote-debugging-port=9222

# Alternative: Use Chromium
/Applications/Chromium.app/Contents/MacOS/Chromium --remote-debugging-port=9222
```

#### 2. Enable Remote JS Debugging in React Native

In your running React Native app:

1. Open the Developer Menu:
   - **Android Emulator**: Press `Cmd+M` (Mac) or `Ctrl+M` (Windows/Linux)
   - **Physical Device**: Shake the device

2. Select **"Debug"** or **"Open Debugger"**

This will open a debugger window at `http://localhost:8081/debugger-ui/`

#### 3. Connect Chrome DevTools

Once the debugger is open:

1. In Chrome, navigate to `chrome://inspect`
2. Click **"Configure..."** next to "Discover network targets"
3. Add `localhost:8081` if not already present
4. Your React Native app should appear under "Remote Target"
5. Click **"inspect"** to open DevTools

### Using Chrome DevTools MCP

The `.mcp-config.json` file configures the Chrome DevTools MCP server for AI-assisted debugging:

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

This allows AI assistants to interact with Chrome DevTools for debugging your React Native JavaScript.

### Debugging GeckoView Native Code

For debugging the native GeckoView Android code, see the Android Studio debugging documentation.

### Troubleshooting

#### Port 9222 Already in Use

```bash
# Find process using port 9222
lsof -i :9222

# Kill the process if needed
kill -9 <PID>
```

#### React Native Debugger Not Connecting

1. Ensure Metro bundler is running: `yarn start`
2. Reload the app: Press `R` twice in the Metro terminal
3. Clear Metro cache: `yarn start --reset-cache`

#### No Remote Targets Visible

1. Verify Chrome is running with `--remote-debugging-port=9222`
2. Check if `localhost:9222/json` returns JSON (should show open tabs/targets)
3. Restart both Chrome and the React Native app

### Additional Resources

- [React Native Debugging](https://reactnative.dev/docs/debugging)
- [Chrome DevTools Protocol](https://chromedevtools.github.io/devtools-protocol/)
- [Chrome DevTools MCP](https://github.com/ChromeDevTools/chrome-devtools-mcp)
