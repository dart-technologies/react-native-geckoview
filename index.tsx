declare const __DEV__: boolean;
declare const global: any;

import React, { forwardRef, useImperativeHandle, useEffect, useState } from 'react';
import {
    requireNativeComponent,
    NativeModules,
    Platform,
    ViewProps,
    View,
    Text,
    StyleSheet,
} from 'react-native';
import { GeckoViewProps, GeckoViewRef } from './src/types';
import { detectFabricAvailability, hasFabricRuntime } from './src/fabricDetection';

const LINKING_ERROR =
    `The package 'react-native-geckoview' doesn't seem to be linked. Make sure: \n\n` +
    Platform.select({ ios: "- You have run 'pod install'\n", default: '' }) +
    '- You rebuilt the app after installing the package\n' +
    '- You are not using Expo Go\n';

// Prefer the static view-config (codegen) component when available.
// In Bridgeless mode, `requireNativeComponent` can't reliably fetch view configs via UIManager.
const NativeGeckoView = (() => {
    try {
        const component = require('./src/GeckoViewNativeComponent').default;
        if (component) {
            if (__DEV__) {
                console.log('[GeckoView] Loading codegen component');
            }
            return component;
        }
    } catch (e) {
        if (__DEV__) {
            console.log('[GeckoView] Codegen component unavailable, falling back to requireNativeComponent');
        }
    }

    if (__DEV__) {
        console.log('[GeckoView] Loading legacy component via requireNativeComponent');
    }
    return requireNativeComponent<GeckoViewProps>('GeckoView');
})();

// Ensure direct event types (e.g. `topTitleChange`) are registered before native events fire.
// In practice this avoids "Unsupported top level event type ..." errors in Fabric/Bridgeless.
function preloadViewConfig(): void {
    if (process.env.NODE_ENV === 'test') return;
    try {
        // Internal API: used defensively only.
        // eslint-disable-next-line @typescript-eslint/no-var-requires
        const registry = require('react-native/Libraries/Renderer/shims/ReactNativeViewConfigRegistry');
        if (registry?.get) {
            registry.get('GeckoView');
        }
    } catch (e) {
        if (__DEV__) {
            console.log('[GeckoView] Failed to preload view config', e);
        }
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
        if (__DEV__) {
            console.log('[GeckoView] Render error', error);
        }
    }

    render() {
        if (!this.state.error) {
            return this.props.children;
        }

        const hasFabric = hasFabricRuntime();
        const fabricConfigAvailable = detectFabricAvailability('GeckoView');

        return (
            <View style={[styles.unsupported, this.props.style]}>
                <Text style={styles.unsupportedText}>
                    GeckoView failed to render in this build.
                </Text>
                <Text style={styles.unsupportedHint}>
                    {hasFabric
                        ? `Fabric runtime: yes (view config: ${fabricConfigAvailable ? 'found' : 'missing'})`
                        : 'Fabric runtime: no'}
                </Text>
                {__DEV__ ? (
                    <Text style={styles.unsupportedDetails} numberOfLines={8}>
                        {String(this.state.error?.message || this.state.error)}
                    </Text>
                ) : null}
                <Text style={styles.unsupportedHint}>
                    If you enabled `newArchEnabled=true`, rebuild the native app and ensure codegen completed for `react-native-geckoview`.
                </Text>
            </View>
        );
    }
}

const GeckoViewModule = NativeModules.GeckoViewModule
    ? NativeModules.GeckoViewModule
    : new Proxy(
        {},
        {
            get() {
                throw new Error(LINKING_ERROR);
            },
        }
    );

const GeckoView = forwardRef<GeckoViewRef, GeckoViewProps>((props, ref) => {
    const [sessionKey] = useState(() => props.sessionKey || `session-${Math.random().toString(36).substr(2, 9)}`);

    useImperativeHandle(ref, () => ({
        reload: () => GeckoViewModule.reload(sessionKey),
        goBack: () => GeckoViewModule.goBack(sessionKey),
        goForward: () => GeckoViewModule.goForward(sessionKey),
        stop: () => GeckoViewModule.stop(sessionKey),
        closeSession: () => GeckoViewModule.closeSession(sessionKey),
        shutdown: () => GeckoViewModule.shutdown(),
        installWebExtension: (assetPath: string) => GeckoViewModule.installWebExtension(assetPath),
        sendWebExtensionMessage: (message: string) => GeckoViewModule.sendWebExtensionMessage(message),
        captureSnapshot: (options?: any) => GeckoViewModule.captureSnapshot(sessionKey),
        evaluateJavaScript: (code: string) => GeckoViewModule.evaluateJavaScript(sessionKey, code),
        resolvePrompt: (promptId: string, confirm: boolean, text?: string, username?: string) =>
            GeckoViewModule.resolvePrompt(promptId, confirm, text, username),
        resolvePermission: (requestId: string, allow: boolean) =>
            GeckoViewModule.resolvePermission(requestId, allow),
        setEnhancedTrackingProtectionLevel: (level: 0 | 1 | 2) =>
            GeckoViewModule.setEnhancedTrackingProtectionLevel(level),
        setCookieBannerMode: (mode: 0 | 1 | 2) =>
            GeckoViewModule.setCookieBannerMode(mode),
        loadUrl: (url: string) => GeckoViewModule.loadUrl(sessionKey, url),
        setUserAgent: (userAgent: string) => GeckoViewModule.setUserAgent(sessionKey, userAgent),
    }));

    useEffect(() => {
        return () => {
            // Intentionally do NOT close the session on unmount. Sessions are pooled in
            // GeckoSessionManager (keyed by sessionKey) and deliberately outlive GeckoView
            // mounts so consumers can remount to resume state during navigation transitions.
            // Auto-closing here would blow away session state on every re-render and force
            // getOrCreateSession to silently spin up a blank replacement on remount.
            // Call ref.closeSession() explicitly when the session is truly done.
        };
    }, [sessionKey]);

    return (
        <GeckoViewErrorBoundary style={props.style}>
            <NativeGeckoView {...(props as any)} sessionKey={sessionKey} />
        </GeckoViewErrorBoundary>
    );
});

export default GeckoView;

// Re-export types for consumers
export type { GeckoViewProps, GeckoViewRef } from './src/types';

export const __TEST_ONLY__ = {
    detectFabricAvailability,
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
