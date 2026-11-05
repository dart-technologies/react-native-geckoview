import type { NativeSyntheticEvent, ViewProps } from 'react-native';
import type {
    AlertPromptPayload,
    AuthPromptPayload,
    BeforeUnloadPromptPayload,
    ConfirmPromptPayload,
    ContextMenuPayload,
    FocusExitPayload,
    LocationChangePayload,
    MediaSessionActionPayload,
    PageErrorPayload,
    PageStartPayload,
    PageStopPayload,
    PermissionRequestPayload,
    PopupPromptPayload,
    ProgressChangePayload,
    SecurityChangePayload,
    TextPromptPayload,
    TitleChangePayload,
    WebExtensionMessagePayload,
} from './NativeEventPayloads';

export type {
    AlertPromptPayload,
    AuthPromptPayload,
    BeforeUnloadPromptPayload,
    ConfirmPromptPayload,
    ContextMenuPayload,
    FocusExitPayload,
    LocationChangePayload,
    MediaSessionActionPayload,
    PageErrorPayload,
    PageStartPayload,
    PageStopPayload,
    PermissionRequestPayload,
    PopupPromptPayload,
    ProgressChangePayload,
    SecurityChangePayload,
    TextPromptPayload,
    TitleChangePayload,
    WebExtensionMessagePayload,
} from './NativeEventPayloads';

/** Callback receiving a React Native `NativeSyntheticEvent<T>` wrapper. */
export type GeckoEventHandler<T> = (event: NativeSyntheticEvent<T>) => void;

/**
 * Enhanced Tracking Protection level.
 *
 * - `0` — Disabled.
 * - `1` — Standard (default in Firefox).
 * - `2` — Strict.
 */
export type EnhancedTrackingProtectionLevel = 0 | 1 | 2;

/**
 * Cookie banner handling mode.
 *
 * - `0` — Disabled (banners are not auto-handled).
 * - `1` — Reject banners.
 * - `2` — Reject banners; accept if the site doesn't expose a reject option.
 *
 * Note: Mozilla's `COOKIE_BANNER_MODE_REJECT_OR_ACCEPT` (value 2) is the
 * closest available to "accept" — there is no pure-accept mode.
 */
export type CookieBannerMode = 0 | 1 | 2;

/** Security-sensitive Gecko runtime options. Must be configured before first mount. */
export interface GeckoSecurityOptions {
    /** Allow Encrypted Media Extensions / DRM key-system access. Defaults to false. */
    allowDrm?: boolean;
    /** Allow audible/inaudible autoplay without prompting. Defaults to false. */
    allowAutoplay?: boolean;
    /** Forward page console output to logcat in debug builds. Defaults to false. */
    enableConsoleOutput?: boolean;
    /**
     * GeckoView `ContentBlocking.CookieBehavior` constant applied at runtime
     * init. Defaults to `ACCEPT_NON_TRACKERS` (4).
     */
    cookieBehavior?: number;
    /**
     * Max captured snapshot size in pixels before `captureSnapshot` rejects with
     * `SNAPSHOT_TOO_LARGE`. Defaults to 16,777,216 (4096×4096). Raise for 8K
     * panels or lower on memory-constrained devices.
     */
    maxSnapshotPixels?: number;
}

/** Props accepted by the {@link GeckoView} component. */
export interface GeckoViewProps extends ViewProps {
    /** Initial URL to load. Changes are reloaded via `loadUrl`. */
    source?: { uri: string };
    /**
     * Stable identifier to pool a `GeckoSession` across remounts. Views sharing
     * a `sessionKey` reuse the same underlying session (history, cookies), which
     * stays open after they unmount, until `closeSession()`. Without one, the
     * view gets a session of its own, closed when the view unmounts.
     */
    sessionKey?: string;
    /** When true, requests system audio focus while media plays. */
    handleAudioFocus?: boolean;
    /** Enables Gecko's built-in content blocking (tracking protection). */
    contentBlockingEnabled?: boolean;
    /**
     * Cookie banner auto-handling mode. GeckoView 159 removed the cookie-banner
     * API, so `'reject'` and `'accept'` are logged as unsupported and change
     * nothing; omitted or `'none'` leaves banners to the page.
     */
    cookieBannerMode?: 'reject' | 'accept' | 'none';
    /**
     * Use a desktop user-agent and viewport instead of mobile. Defaults to
     * `true`. Applied to the session and updatable at runtime.
     */
    desktopMode?: boolean;

    /** Fired when a page calls `window.alert()`. Resolve via `resolvePrompt`. */
    onGeckoAlert?: GeckoEventHandler<AlertPromptPayload>;
    /** Fired when a page calls `window.confirm()`. Resolve via `resolvePrompt`. */
    onGeckoConfirm?: GeckoEventHandler<ConfirmPromptPayload>;
    /** Fired when a page calls `window.prompt()`. Resolve via `resolvePrompt`. */
    onGeckoPrompt?: GeckoEventHandler<TextPromptPayload>;

    /** Fired when a page attempts to open a popup window. */
    onGeckoPopup?: GeckoEventHandler<PopupPromptPayload>;
    /** Fired on `beforeunload` navigation confirmation. */
    onGeckoBeforeUnload?: GeckoEventHandler<BeforeUnloadPromptPayload>;
    /** Fired on HTTP basic/digest auth challenges. */
    onGeckoAuth?: GeckoEventHandler<AuthPromptPayload>;

    /** Fired when a page load begins. */
    onGeckoPageStart?: GeckoEventHandler<PageStartPayload>;
    /** Fired when a page load completes (success or failure). */
    onGeckoPageStop?: GeckoEventHandler<PageStopPayload>;
    /** Fired as page load progresses (0–100). */
    onGeckoProgressChange?: GeckoEventHandler<ProgressChangePayload>;
    /** Fired when the page security state changes (HTTPS/host). */
    onGeckoSecurityChange?: GeckoEventHandler<SecurityChangePayload>;
    /** Fired when the document location (URL) changes. */
    onGeckoLocationChange?: GeckoEventHandler<LocationChangePayload>;

    /** Fired when a page load fails. */
    onPageError?: GeckoEventHandler<PageErrorPayload>;

    /** Fired on media session state/metadata changes. */
    onMediaSessionAction?: GeckoEventHandler<MediaSessionActionPayload>;

    /** Fired when the page requests a permission. Resolve via `resolvePermission`. */
    onPermissionRequest?: GeckoEventHandler<PermissionRequestPayload>;

    /** Fired when an installed WebExtension sends a message to the host app. */
    onWebExtensionMessage?: GeckoEventHandler<WebExtensionMessagePayload>;

    /** Fired when the document title changes. */
    onTitleChange?: GeckoEventHandler<TitleChangePayload>;

    /** Fired on context-menu (long press) requests. */
    onContextMenu?: GeckoEventHandler<ContextMenuPayload>;

    /** Fired when D-pad / keyboard focus exits the view boundary. */
    onFocusExit?: GeckoEventHandler<FocusExitPayload>;
}

/** Result returned from `evaluateJavaScript`. A JSON-parsed value or a string. */
export type EvaluateJavaScriptResult = unknown;

/** Imperative methods exposed via `ref` on a `GeckoView`. */
export interface GeckoViewRef {
    /** Reload the current page. */
    reload: () => void;
    /** Navigate back in this session's history (no-op at root). */
    goBack: () => void;
    /** Navigate forward in this session's history (no-op at head). */
    goForward: () => void;
    /** Stop the currently loading page. */
    stop: () => void;
    /** Close and dispose this view's session. */
    closeSession: () => void;
    /**
     * Shut down the shared Gecko runtime. **Global blast radius:** this closes
     * *every* session and tears down the runtime for the whole process — in a
     * multi-view screen it affects all other GeckoViews, not just this one.
     * Prefer `closeSession` for per-view teardown.
     */
    shutdown: () => void;
    /** Install a built-in WebExtension by asset path. */
    installWebExtension: (assetPath: string) => Promise<void>;
    /** Send a string message to a connected WebExtension port in this session. */
    sendWebExtensionMessage: (extensionId: string, message: string) => Promise<void>;
    /** Capture a PNG snapshot of the current page; resolves to a file path. */
    captureSnapshot: () => Promise<string>;
    /**
     * Evaluate JavaScript against the current document through the trusted
     * internal WebExtension bridge.
     *
     * **Security:** the code runs in the bridge content-script context, with
     * DOM access but **without page-script globals** (a function/variable
     * defined by the page's own scripts is not visible here). Never evaluate
     * untrusted or user-provided strings. Rejects with:
     * - `EVAL_BRIDGE_UNAVAILABLE` if the session is closed, or the bridge
     *   content script has not connected within the retry window;
     * - `PLAYBACK_RESTRICTED` while the host's playback gate is closed (AAOS UX
     *   restrictions), including evaluations in flight when it closes;
     * - `EVAL_TIMEOUT` if the page does not answer in time;
     * - `EVAL_ERROR` if the script throws.
     *
     * @typeParam T  Expected return type; annotate to avoid casting at the call
     *   site (the value is still produced by the native bridge, not validated).
     */
    evaluateJavaScript: <T = EvaluateJavaScriptResult>(code: string) => Promise<T>;
    /**
     * Resolve a pending prompt event.
     *
     * @param text  For `onGeckoPrompt` (text input), the entered value.
     * @param username  For `onGeckoAuth` (HTTP basic/digest), the username.
     * @param password  For `onGeckoAuth`, the password.
     */
    resolvePrompt: (
        promptId: string,
        confirm: boolean,
        text?: string,
        username?: string,
        password?: string,
    ) => void;
    /** Resolve a pending `onPermissionRequest` event. */
    resolvePermission: (requestId: string, allow: boolean) => void;
    /**
     * Set Enhanced Tracking Protection level for the shared runtime.
     *
     * Rejects with `INVALID_ETP_LEVEL` if `level` is not `0`, `1`, or `2`.
     */
    setEnhancedTrackingProtectionLevel: (level: EnhancedTrackingProtectionLevel) => Promise<void>;
    /**
     * Set cookie banner auto-handling mode for the shared runtime.
     *
     * Rejects with `INVALID_COOKIE_BANNER_MODE` if `mode` is not `0`, `1`, or `2`.
     */
    setCookieBannerMode: (mode: CookieBannerMode) => Promise<void>;
    /**
     * Load a new URL in this view's session.
     *
     * **Security:** only `http`, `https`, and `about` schemes are accepted.
     * `javascript:`, `data:`, `file:`, `content:`, and `blob:` URLs are
     * silently dropped to prevent XSS via host-app forwarding of
     * user-controlled URLs (deep link, QR scan, pasted URL). Use
     * `evaluateJavaScript` for trusted script execution.
     */
    loadUrl: (url: string) => void;
    /** Override the user-agent string. Empty string resets to default mobile UA. */
    setUserAgent: (userAgent: string) => void;
}
