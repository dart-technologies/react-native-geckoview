/**
 * Shared native event payload types for GeckoView events.
 *
 * These plain TypeScript interfaces describe the shape of every `onGecko*` /
 * `onPage*` event payload and are imported by `types.ts`, which wraps each in
 * `{ nativeEvent: T }` for the user-facing React prop types.
 *
 * NOTE: the codegen spec `GeckoViewNativeComponent.ts` does NOT import these —
 * RN codegen requires inline object-literal types in `DirectEventHandler<...>`,
 * so it re-declares each payload shape inline. The two definitions must be kept
 * in sync by hand: any change here must be mirrored in the matching
 * `DirectEventHandler` literal in `GeckoViewNativeComponent.ts`.
 *
 * Keep payloads free of `any`. Use `unknown` or structured types instead.
 */

export interface PageStartPayload {
    url: string;
}

export interface PageStopPayload {
    success: boolean;
}

export interface ProgressChangePayload {
    progress: number;
}

export interface SecurityChangePayload {
    isSecure: boolean;
    host: string;
}

export interface LocationChangePayload {
    url: string;
}

export interface PageErrorPayload {
    uri: string;
    errorCode: number;
    errorCategory: string;
    errorMessage: string;
}

export interface MediaSessionActionPayload {
    action: 'play' | 'pause' | 'stop' | 'activated' | 'deactivated' | 'metadata';
    title?: string;
    artist?: string;
    album?: string;
}

export interface AlertPromptPayload {
    promptId: string;
    message: string;
    defaultValue?: string;
}

export interface ConfirmPromptPayload {
    promptId: string;
    message: string;
    defaultValue?: string;
}

export interface TextPromptPayload {
    promptId: string;
    message: string;
    defaultValue?: string;
}

export interface PopupPromptPayload {
    promptId: string;
    targetUri?: string;
    type: string;
}

export interface BeforeUnloadPromptPayload {
    promptId: string;
    type: string;
}

export interface AuthOptions {
    level?: number;
    flags?: number;
    uri?: string;
    username?: string;
    [key: string]: unknown;
}

export interface AuthPromptPayload {
    promptId: string;
    title?: string;
    message?: string;
    authOptions: AuthOptions;
    type: string;
}

export interface PermissionRequestPayload {
    requestId: string;
    permission: string;
    uri: string;
}

export interface WebExtensionMessagePayload {
    message: WebExtensionMessage;
}

export interface WebExtensionMessage {
    extensionId?: string;
    type?: string;
    direction?: string;
    inTrap?: boolean;
    raw?: string;
    [key: string]: unknown;
}

export interface TitleChangePayload {
    title: string;
}

export interface ContextMenuPayload {
    x: number;
    y: number;
    linkUri?: string;
    srcUri?: string;
    type: string;
}

export interface FocusExitPayload {
    direction: 'up' | 'down' | 'left' | 'right';
}
