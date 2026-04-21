import { ViewProps } from 'react-native';

export interface GeckoViewProps extends ViewProps {
    source?: {
        uri: string;
    };
    sessionKey?: string;
    handleAudioFocus?: boolean;
    contentBlockingEnabled?: boolean;
    cookieBannerMode?: 'reject' | 'accept' | 'none';

    // Basic prompts
    onGeckoAlert?: (event: { nativeEvent: { promptId: string; message: string; defaultValue?: string } }) => void;
    onGeckoConfirm?: (event: { nativeEvent: { promptId: string; message: string; defaultValue?: string } }) => void;
    onGeckoPrompt?: (event: { nativeEvent: { promptId: string; message: string; defaultValue?: string } }) => void;

    // Enhanced prompts
    onGeckoPopup?: (event: { nativeEvent: { promptId: string; targetUri?: string; type: string } }) => void;
    onGeckoBeforeUnload?: (event: { nativeEvent: { promptId: string; type: string } }) => void;
    onGeckoAuth?: (event: { nativeEvent: { promptId: string; title?: string; message?: string; authOptions: any; type: string } }) => void;

    // Progress events
    onGeckoPageStart?: (event: { nativeEvent: { url: string } }) => void;
    onGeckoPageStop?: (event: { nativeEvent: { success: boolean } }) => void;
    onGeckoProgressChange?: (event: { nativeEvent: { progress: number } }) => void;
    onGeckoSecurityChange?: (event: { nativeEvent: { isSecure: boolean; host: string } }) => void;
    onGeckoLocationChange?: (event: { nativeEvent: { url: string } }) => void;

    // Error events
    onPageError?: (event: { nativeEvent: { uri: string; errorCode: number; errorCategory: string; errorMessage: string } }) => void;

    // Media events
    onMediaSessionAction?: (event: { nativeEvent: { action: 'play' | 'pause' | 'stop' | 'activated' | 'deactivated' | 'metadata'; title?: string; artist?: string; album?: string } }) => void;

    // Permission events
    onPermissionRequest?: (event: { nativeEvent: { requestId: string; permission: string; uri: string } }) => void;

    // Web extension events
    onWebExtensionMessage?: (event: { nativeEvent: { message: any } }) => void;

    // Legacy/other events
    onTitleChange?: (event: { nativeEvent: { title: string } }) => void;
    onContextMenu?: (event: { nativeEvent: { x: number; y: number; linkUri?: string; srcUri?: string; type: string } }) => void;
    onFocusExit?: (event: { nativeEvent: { direction: 'up' | 'down' | 'left' | 'right' } }) => void;
}

export interface GeckoViewRef {
    reload: () => void;
    goBack: () => void;
    goForward: () => void;
    stop: () => void;
    closeSession: () => void;
    shutdown: () => void;
    installWebExtension: (assetPath: string) => Promise<void>;
    sendWebExtensionMessage: (message: string) => void;
    captureSnapshot: (options?: any) => Promise<string>;
    evaluateJavaScript: (code: string) => Promise<any>;
    resolvePrompt: (promptId: string, confirm: boolean, text?: string, username?: string) => void;
    resolvePermission: (requestId: string, allow: boolean) => void;
    setEnhancedTrackingProtectionLevel: (level: 0 | 1 | 2) => void;
    setCookieBannerMode: (mode: 0 | 1 | 2) => void;
    loadUrl: (url: string) => void;
    setUserAgent: (userAgent: string) => void;
}
