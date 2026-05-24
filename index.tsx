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
const NativeGeckoView: HostComponent<GeckoViewProps> = (() => {
    try {
        // eslint-disable-next-line @typescript-eslint/no-var-requires
        const mod = require('./src/GeckoViewNativeComponent');
        return mod.default as HostComponent<GeckoViewProps>;
    } catch {
        // Fallback: codegen unavailable (e.g. under Jest). Tests stub
        // `requireNativeComponent` in setup.
        // eslint-disable-next-line @typescript-eslint/no-var-requires
        const { requireNativeComponent } = require('react-native');
        return requireNativeComponent('GeckoView') as HostComponent<GeckoViewProps>;
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

const isTestEnv = typeof process !== 'undefined' && process.env?.NODE_ENV === 'test';
const devLog = (...args: unknown[]): void => {
    if (__DEV__ && !isTestEnv) {
        // eslint-disable-next-line no-console
        console.log(...args);
    }
};

interface ViewConfigRegistry {
    get?: (name: string) => unknown;
}

// Ensure direct event types (e.g. `topTitleChange`) are registered before native events fire.
// In practice this avoids "Unsupported top level event type ..." errors in Bridgeless mode.
function preloadViewConfig(): void {
    if (isTestEnv) return;
    try {
        // Internal React Native API: only used as a defensive warm-up.
        const registry: ViewConfigRegistry | undefined = require('react-native/Libraries/Renderer/shims/ReactNativeViewConfigRegistry');
        if (typeof registry?.get === 'function') {
            registry.get('GeckoView');
        }
    } catch (e) {
        devLog('[GeckoView] Failed to preload view config', e);
    }
}

preloadViewConfig();

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
    const [sessionKey] = useState(
        () => props.sessionKey || `session-${Math.random().toString(36).substring(2, 11)}`
    );

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

    // Sessions are pooled in GeckoSessionManager (keyed by sessionKey) and deliberately
    // outlive GeckoView mounts. Auto-closing on unmount would discard session state on
    // every re-render; consumers should call ref.closeSession() explicitly.

    return (
        <GeckoViewErrorBoundary style={props.style}>
            <NativeGeckoView {...props} sessionKey={sessionKey} />
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
