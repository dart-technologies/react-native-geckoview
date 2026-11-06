declare const __DEV__: boolean;

import React, { forwardRef, useImperativeHandle, useState } from 'react';
import {
    NativeModules,
    Platform,
    View,
    Text,
    StyleSheet,
    type HostComponent,
    type ViewProps,
} from 'react-native';
// The codegen spec module calls `codegenNativeComponent('GeckoView')` at
// import time, which reaches into React Native's native bridge. Wrap the
// import so the library still loads under Jest (where there is no bridge)
// and test files can exercise the component via the mocked NativeModules.
const NativeGeckoView: HostComponent<NativeGeckoViewProps> = (() => {
    try {
        // eslint-disable-next-line @typescript-eslint/no-var-requires
        const mod = require('./src/GeckoViewNativeComponent');
        return mod.default as HostComponent<NativeGeckoViewProps>;
    } catch {
        // Fallback: codegen unavailable (e.g. under Jest). Tests stub
        // `requireNativeComponent` in setup.
        // eslint-disable-next-line @typescript-eslint/no-var-requires
        const { requireNativeComponent } = require('react-native');
        return requireNativeComponent('GeckoView') as HostComponent<NativeGeckoViewProps>;
    }
})();
import type {
    CookieBannerMode,
    EnhancedTrackingProtectionLevel,
    EvaluateJavaScriptResult,
    GeckoViewProps,
    GeckoViewRef,
    GeckoSecurityOptions,
} from './src/types';
import { hasFabricRuntime } from './src/fabricDetection';

/** The public props, plus the ones only this wrapper passes to the native view. */
type NativeGeckoViewProps = GeckoViewProps & {
    /** See `closeSessionOnDrop` in src/GeckoViewNativeComponent.ts. */
    closeSessionOnDrop?: boolean;
};

/**
 * Shape of the native `GeckoViewModule` turbo/legacy module. Mirrors the
 * Kotlin `@ReactMethod` signatures in `GeckoViewModule.kt`.
 */
interface NativeGeckoViewModule {
    configureSecurity(options: GeckoSecurityOptions): Promise<void>;
    reload(sessionKey: string): void;
    goBack(sessionKey: string): void;
    goForward(sessionKey: string): void;
    stop(sessionKey: string): void;
    closeSession(sessionKey: string): void;
    shutdown(): void;
    loadUrl(sessionKey: string, url: string): void;
    setUserAgent(sessionKey: string, userAgent: string): void;
    resolvePrompt(promptId: string, confirm: boolean, text?: string, username?: string, password?: string): void;
    resolvePermission(requestId: string, allow: boolean): void;
    installWebExtension(assetPath: string): Promise<void>;
    sendWebExtensionMessage(sessionKey: string, extensionId: string, message: string): Promise<void>;
    setEnhancedTrackingProtectionLevel(level: EnhancedTrackingProtectionLevel): Promise<void>;
    setCookieBannerMode(mode: CookieBannerMode): Promise<void>;
    captureSnapshot(sessionKey: string): Promise<string>;
    evaluateJavaScript(sessionKey: string, code: string): Promise<EvaluateJavaScriptResult>;
}

const LINKING_ERROR =
    `The package 'react-native-geckoview' doesn't seem to be linked. Make sure: \n\n` +
    Platform.select({
        ios: "- react-native-geckoview is Android-only; GeckoView has no iOS support.\n",
        default: '',
    }) +
    '- You rebuilt the app after installing the package\n' +
    '- You are not using Expo Go\n';

const isTestEnv = (globalThis as { process?: { env?: { NODE_ENV?: string } } }).process?.env?.NODE_ENV === 'test';
const devLog = (...args: unknown[]): void => {
    if (__DEV__ && !isTestEnv) {
        // eslint-disable-next-line no-console
        console.log(...args);
    }
};

class GeckoViewErrorBoundary extends React.Component<
    { children: React.ReactNode; style?: ViewProps['style'] },
    { error: Error | null }
> {
    state: { error: Error | null } = { error: null };

    static getDerivedStateFromError(error: Error) {
        return { error };
    }

    componentDidCatch(error: Error) {
        devLog('[GeckoView] Render error', error);
    }

    render() {
        if (!this.state.error) {
            return this.props.children;
        }

        const hasFabric = hasFabricRuntime();

        return (
            <View style={[styles.unsupported, this.props.style]}>
                <Text style={styles.unsupportedText}>
                    GeckoView failed to render in this build.
                </Text>
                <Text style={styles.unsupportedHint}>
                    Fabric runtime: {hasFabric ? 'yes' : 'no'}
                </Text>
                {__DEV__ ? (
                    <Text style={styles.unsupportedDetails} numberOfLines={8}>
                        {String(this.state.error?.message || this.state.error)}
                    </Text>
                ) : null}
                <Text style={styles.unsupportedHint}>
                    react-native-geckoview requires React Native 0.83+ with the New
                    Architecture enabled. Rebuild the native app and ensure codegen
                    completed for `react-native-geckoview`.
                </Text>
            </View>
        );
    }
}

const GeckoViewModule: NativeGeckoViewModule = NativeModules.GeckoViewModule
    ? (NativeModules.GeckoViewModule as NativeGeckoViewModule)
    : (new Proxy(
        {},
        {
            get() {
                throw new Error(LINKING_ERROR);
            },
        }
    ) as NativeGeckoViewModule);

export function configureGeckoViewSecurity(options: GeckoSecurityOptions): Promise<void> {
    return GeckoViewModule.configureSecurity(options);
}

// A default session key only has to be unique among this app's native sessions,
// which can outlive a JS reload: a load-time stamp plus a counter is enough. The
// key is not a secret; any code in the app can address a session by its key.
const DEFAULT_SESSION_KEY_PREFIX = `session-${Date.now().toString(36)}`;
let defaultSessionKeyCount = 0;

/**
 * React component that embeds a Mozilla GeckoView (Firefox rendering engine)
 * inside a React Native tree.
 *
 * **Android-only.** Use an imperative `ref` to control navigation, resolve
 * prompts/permissions, and evaluate JavaScript. See {@link GeckoViewRef}.
 *
 * @example
 * ```tsx
 * const ref = useRef<GeckoViewRef>(null);
 * <GeckoView
 *   ref={ref}
 *   source={{ uri: 'https://example.com' }}
 *   sessionKey="main"
 *   onGeckoPageStop={(e) => console.log('loaded', e.nativeEvent.success)}
 * />
 * ```
 */
const GeckoView = forwardRef<GeckoViewRef, GeckoViewProps>((props, ref) => {
    const [{ sessionKey, keyGenerated }] = useState(() => ({
        sessionKey: props.sessionKey || `${DEFAULT_SESSION_KEY_PREFIX}-${++defaultSessionKeyCount}`,
        keyGenerated: !props.sessionKey,
    }));

    useImperativeHandle(
        ref,
        () => ({
            reload: () => GeckoViewModule.reload(sessionKey),
            goBack: () => GeckoViewModule.goBack(sessionKey),
            goForward: () => GeckoViewModule.goForward(sessionKey),
            stop: () => GeckoViewModule.stop(sessionKey),
            closeSession: () => GeckoViewModule.closeSession(sessionKey),
            shutdown: () => GeckoViewModule.shutdown(),
            installWebExtension: (assetPath) => GeckoViewModule.installWebExtension(assetPath),
            sendWebExtensionMessage: (extensionId, message) =>
                GeckoViewModule.sendWebExtensionMessage(sessionKey, extensionId, message),
            captureSnapshot: () => GeckoViewModule.captureSnapshot(sessionKey),
            evaluateJavaScript: <T = EvaluateJavaScriptResult,>(code: string) =>
                GeckoViewModule.evaluateJavaScript(sessionKey, code) as Promise<T>,
            resolvePrompt: (promptId, confirm, text, username, password) =>
                GeckoViewModule.resolvePrompt(promptId, confirm, text, username, password),
            resolvePermission: (requestId, allow) =>
                GeckoViewModule.resolvePermission(requestId, allow),
            setEnhancedTrackingProtectionLevel: (level) =>
                GeckoViewModule.setEnhancedTrackingProtectionLevel(level),
            setCookieBannerMode: (mode) => GeckoViewModule.setCookieBannerMode(mode),
            loadUrl: (url) => GeckoViewModule.loadUrl(sessionKey, url),
            setUserAgent: (userAgent) => GeckoViewModule.setUserAgent(sessionKey, userAgent),
        }),
        [sessionKey]
    );

    // Sessions are pooled natively by sessionKey. A host's key outlives this view: a
    // remount with the same key gets its session back, until ref.closeSession(). A
    // generated key is never bound again once its view is gone, so the native view
    // closes that session when React Native drops it (closeSessionOnDrop). Not from an
    // effect cleanup here: StrictMode's double invocation, Fast Refresh and a hidden
    // <Activity> run cleanups too, and would close the session under a native view
    // that is still mounted.

    return (
        <GeckoViewErrorBoundary style={props.style}>
            <NativeGeckoView
                {...props}
                sessionKey={sessionKey}
                closeSessionOnDrop={keyGenerated}
            />
        </GeckoViewErrorBoundary>
    );
});

GeckoView.displayName = 'GeckoView';

export default GeckoView;

// Re-export types for consumers
export type {
    CookieBannerMode,
    EnhancedTrackingProtectionLevel,
    EvaluateJavaScriptResult,
    GeckoEventHandler,
    GeckoSecurityOptions,
    GeckoViewProps,
    GeckoViewRef,
} from './src/types';
export type * from './src/NativeEventPayloads';

export const __TEST_ONLY__ = {
    hasFabricRuntime,
};

const styles = StyleSheet.create({
    unsupported: {
        alignItems: 'center',
        justifyContent: 'center',
        backgroundColor: '#260000',
        padding: 12,
    },
    unsupportedText: {
        color: '#ffb3b3',
        textAlign: 'center',
        fontWeight: '600',
    },
    unsupportedHint: {
        color: '#ffcccc',
        textAlign: 'center',
        marginTop: 8,
        fontSize: 12,
    },
    unsupportedDetails: {
        color: '#ffd6d6',
        textAlign: 'center',
        marginTop: 8,
        fontSize: 12,
    },
});
