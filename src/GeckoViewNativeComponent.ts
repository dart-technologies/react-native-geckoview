import { codegenNativeComponent } from 'react-native';
import type { CodegenTypes, HostComponent, ViewProps } from 'react-native';

interface NativeProps extends ViewProps {
    sessionKey?: string;
    source?: { uri: string };
    handleAudioFocus?: boolean;
    contentBlockingEnabled?: boolean;
    cookieBannerMode?: CodegenTypes.WithDefault<'reject' | 'accept' | 'none', 'reject'>;

    // Event handlers matching types.ts
    onGeckoPageStart?: CodegenTypes.DirectEventHandler<{
        url: string;
    }>;
    onGeckoPageStop?: CodegenTypes.DirectEventHandler<{
        success: boolean;
    }>;
    onGeckoProgressChange?: CodegenTypes.DirectEventHandler<{
        progress: CodegenTypes.Double;
    }>;
    onGeckoSecurityChange?: CodegenTypes.DirectEventHandler<{
        isSecure: boolean;
        host: string;
    }>;
    onGeckoLocationChange?: CodegenTypes.DirectEventHandler<{
        url: string;
    }>;
    onPageError?: CodegenTypes.DirectEventHandler<{
        uri: string;
        errorCode: CodegenTypes.Int32;
        errorCategory: string;
        errorMessage: string;
    }>;
    onMediaSessionAction?: CodegenTypes.DirectEventHandler<{
        action: string;
        title?: string;
        artist?: string;
        album?: string;
    }>;
    onGeckoAlert?: CodegenTypes.DirectEventHandler<{
        promptId: string;
        message: string;
        defaultValue?: string;
    }>;
    onGeckoConfirm?: CodegenTypes.DirectEventHandler<{
        promptId: string;
        message: string;
        defaultValue?: string;
    }>;
    onGeckoPrompt?: CodegenTypes.DirectEventHandler<{
        promptId: string;
        message: string;
        defaultValue?: string;
    }>;
    onGeckoPopup?: CodegenTypes.DirectEventHandler<{
        promptId: string;
        targetUri?: string;
        type: string;
    }>;
    onGeckoBeforeUnload?: CodegenTypes.DirectEventHandler<{
        promptId: string;
        type: string;
    }>;
    onGeckoAuth?: CodegenTypes.DirectEventHandler<{
        promptId: string;
        title?: string;
        message?: string;
        authOptions: Readonly<{}>;
        type: string;
    }>;
    onPermissionRequest?: CodegenTypes.DirectEventHandler<{
        requestId: string;
        permission: string;
        uri: string;
    }>;
    onWebExtensionMessage?: CodegenTypes.DirectEventHandler<{
        message: Readonly<{}>;
    }>;

    onTitleChange?: CodegenTypes.DirectEventHandler<{
        title: string;
    }>;
    onContextMenu?: CodegenTypes.DirectEventHandler<{
        x: CodegenTypes.Int32;
        y: CodegenTypes.Int32;
        linkUri?: string;
        srcUri?: string;
        type: string;
    }>;
    onFocusExit?: CodegenTypes.DirectEventHandler<{
        direction: 'up' | 'down' | 'left' | 'right';
    }>;
}

export default codegenNativeComponent<NativeProps>(
    'GeckoView'
) as HostComponent<NativeProps>;
