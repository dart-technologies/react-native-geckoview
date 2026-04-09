/**
 * Enhanced GeckoView Example App
 * Modern browser interface mirroring the Flutter GeckoView design
 * Features: Theme toggle, progress bar, user agent switching, and per-domain theme hooks
 */

import React, { useRef, useState, useEffect, useCallback, useMemo } from 'react';
import {
    StyleSheet,
    Text,
    View,
    TextInput,
    TouchableOpacity,
    Pressable,
    Alert,
    Modal,
    NativeSyntheticEvent,
    TextInputSubmitEditingEventData,
    ViewStyle,
    TextStyle,
    StatusBar,
    Animated,
    Dimensions,
    Platform,
} from 'react-native';
import GeckoView, { GeckoViewRef } from 'react-native-geckoview';

// ============================================================================
// Types
// ============================================================================

interface PromptData {
    promptId: string;
    message: string;
    defaultValue: string;
}

interface AuthData {
    promptId: string;
    message: string;
}

interface MediaInfo {
    title: string;
    artist: string;
}

interface Theme {
    mode: 'light' | 'dark';
    colors: {
        background: string;
        surface: string;
        surfaceVariant: string;
        primary: string;
        primaryContainer: string;
        onPrimary: string;
        onSurface: string;
        onSurfaceVariant: string;
        border: string;
        shadow: string;
        error: string;
        success: string;
        accent: string;
        progressBackground: string;
        progressFill: string;
    };
}

// Native event types
interface PageStartNativeEvent { url: string; }
interface PageStopNativeEvent { success: boolean; }
interface ProgressChangeNativeEvent { progress: number; }
interface SecurityChangeNativeEvent { isSecure: boolean; host: string; }
interface PageErrorNativeEvent { uri: string; errorCode: number; errorCategory: string; errorMessage: string; }
interface MediaActionNativeEvent { action: 'play' | 'pause' | 'stop' | 'activated' | 'deactivated' | 'metadata'; title?: string; artist?: string; album?: string; }
interface PromptNativeEvent { promptId: string; message: string; defaultValue?: string; }
interface PopupNativeEvent { promptId: string; targetUri?: string; type: string; }
interface AuthNativeEvent { promptId: string; title?: string; message?: string; authOptions: unknown; type: string; }

// ============================================================================
// Theme Configuration
// ============================================================================

const lightTheme: Theme = {
    mode: 'light',
    colors: {
        background: '#F5F5F7',
        surface: '#FFFFFF',
        surfaceVariant: '#F0F0F2',
        primary: '#FF6B35',
        primaryContainer: '#FFE8DE',
        onPrimary: '#FFFFFF',
        onSurface: '#1D1D1F',
        onSurfaceVariant: '#6E6E73',
        border: 'rgba(0, 0, 0, 0.08)',
        shadow: 'rgba(0, 0, 0, 0.1)',
        error: '#FF3B30',
        success: '#34C759',
        accent: '#007AFF',
        progressBackground: 'rgba(0, 0, 0, 0.08)',
        progressFill: '#34C759',
    },
};

const darkTheme: Theme = {
    mode: 'dark',
    colors: {
        background: '#000000',
        surface: '#1C1C1E',
        surfaceVariant: '#2C2C2E',
        primary: '#FF8F5E',
        primaryContainer: '#4A2E1C',
        onPrimary: '#000000',
        onSurface: '#F5F5F7',
        onSurfaceVariant: '#A1A1A6',
        border: 'rgba(255, 255, 255, 0.1)',
        shadow: 'rgba(0, 0, 0, 0.5)',
        error: '#FF453A',
        success: '#30D158',
        accent: '#0A84FF',
        progressBackground: 'rgba(255, 255, 255, 0.1)',
        progressFill: '#30D158',
    },
};

// ============================================================================
// Per-Domain Theme Hooks (YouTube adaptive theming)
// ============================================================================

interface ThemeHookConfig {
    script: string;
    requiresReload?: boolean;
}

const themeHookConfigs: Map<RegExp, ThemeHookConfig> = new Map([
    [/(?:^|\.)youtube\.com$/, {
        requiresReload: true,
        script: `
(function(forceDark){
  try {
    const apply = (isDark) => {
      const persistPreference = () => {
        try {
          const expiry = new Date(Date.now() + 31536000000).toUTCString();
          const existing = document.cookie.split('; ').find((row) => row.startsWith('PREF='))?.substring(5) ?? '';
          const parts = existing.split('&').filter((part) => part && !part.startsWith('f6='));
          parts.push('f6=' + (isDark ? '400' : '0'));
          const cookieValue = parts.join('&');
          document.cookie = 'PREF=' + cookieValue + '; path=/; domain=.youtube.com; expires=' + expiry + '; Secure; SameSite=None';
        } catch (cookieErr) { console.error('GeckoView theme hook cookie error', cookieErr); }
        try {
          localStorage.setItem('yt-client-theme', isDark ? 'THEME_DARK' : 'THEME_LIGHT');
        } catch (storageErr) { console.error('GeckoView theme hook storage error', storageErr); }
      };
      const html = document.documentElement;
      const app = document.querySelector('ytd-app');
      const body = document.body;
      if (html) { html.toggleAttribute('dark', isDark); html.toggleAttribute('light', !isDark); }
      if (app) { app.toggleAttribute('dark', isDark); app.toggleAttribute('light', !isDark); }
      if (body) { body.classList.toggle('yt-dark-mode', isDark); body.classList.toggle('yt-light-mode', !isDark); }
      persistPreference();
    };
    apply(forceDark);
  } catch (err) { console.error('GeckoView theme hook failed', err); }
})(__IS_DARK__);
`,
    }],
]);

// ============================================================================
// Icon Components
// ============================================================================

interface IconButtonProps {
    icon: string;
    onPress: () => void;
    size?: number;
    theme: Theme;
    disabled?: boolean;
    active?: boolean;
    testID?: string;
}

const IconButton: React.FC<IconButtonProps> = ({
    icon,
    onPress,
    size = 22,
    theme,
    disabled = false,
    active = false,
    testID,
}) => (
    <Pressable
        testID={testID}
        onPress={onPress}
        disabled={disabled}
        style={({ pressed }) => [
            styles.iconButton,
            {
                backgroundColor: pressed
                    ? theme.colors.surfaceVariant
                    : active
                        ? theme.colors.primaryContainer
                        : 'transparent',
                opacity: disabled ? 0.4 : 1,
            },
        ]}
    >
        <Text style={[
            styles.iconText,
            {
                fontSize: size,
                color: active ? theme.colors.primary : theme.colors.onSurface,
            }
        ]}>
            {icon}
        </Text>
    </Pressable>
);

// ============================================================================
// Browser App Bar Component
// ============================================================================

interface BrowserAppBarProps {
    children: React.ReactNode;
    theme: Theme;
}

const BrowserAppBar: React.FC<BrowserAppBarProps> = ({ children, theme }) => (
    <View style={[
        styles.appBar,
        {
            backgroundColor: theme.colors.surface,
            shadowColor: theme.colors.shadow,
            borderBottomColor: theme.colors.border,
        },
    ]}>
        {children}
    </View>
);

// ============================================================================
// Progress Bar Component
// ============================================================================

interface ProgressBarProps {
    progress: number;
    theme: Theme;
    visible: boolean;
    testID?: string;
}

const ProgressBar: React.FC<ProgressBarProps> = ({ progress, theme, visible, testID }) => {
    if (!visible && progress === 0) return null;

    return (
        <View testID={testID} style={[styles.progressContainer, { backgroundColor: theme.colors.progressBackground }]}>
            <View
                style={[
                    styles.progressFill,
                    {
                        width: `${progress * 100}%`,
                        backgroundColor: theme.colors.progressFill,
                    },
                ]}
            />
        </View>
    );
};

// ============================================================================
// Main App Component
// ============================================================================

export default function App(): React.JSX.Element {
    const geckoRef = useRef<GeckoViewRef>(null);

    // Theme state
    const [themeMode, setThemeMode] = useState<'light' | 'dark'>('light');
    const theme = useMemo(() => themeMode === 'light' ? lightTheme : darkTheme, [themeMode]);

    // Navigation state
    const [inputUrl, setInputUrl] = useState<string>('https://dart.art');
    const [currentUri, setCurrentUri] = useState<URL | null>(null);
    const [isInputFocused, setIsInputFocused] = useState<boolean>(false);

    // Progress state
    const [loading, setLoading] = useState<boolean>(false);
    const [progress, setProgress] = useState<number>(0);
    const [isSecure, setIsSecure] = useState<boolean>(false);

    // Native event types
    interface LocationChangeNativeEvent { url: string; }

    // User Agent state
    const [userAgentMode, setUserAgentMode] = useState<'mobile' | 'desktop'>('mobile');

    // Media state
    const [mediaAction, setMediaAction] = useState<string | null>(null);
    const [mediaInfo, setMediaInfo] = useState<MediaInfo>({ title: '', artist: '' });

    // Prompt state
    const [promptData, setPromptData] = useState<PromptData | null>(null);
    const [authData, setAuthData] = useState<AuthData | null>(null);
    const [username, setUsername] = useState<string>('');
    const [password, setPassword] = useState<string>('');
    const [promptInput, setPromptInput] = useState<string>('');

    // Error state
    const [error, setError] = useState<string | null>(null);

    // Initial load effect
    useEffect(() => {
        // Load the default homepage after a short delay to ensure GeckoView is ready
        const timer = setTimeout(() => {
            geckoRef.current?.loadUrl('https://dart.art');
        }, 500);
        return () => clearTimeout(timer);
    }, []);

    // ========================================================================
    // Theme Hook Application
    // ========================================================================

    const applyPerDomainThemeHook = useCallback(async (triggeredByThemeChange = false) => {
        const controller = geckoRef.current;
        if (!controller || !currentUri) return;

        const host = currentUri.hostname?.toLowerCase();
        if (!host) return;

        for (const [pattern, config] of themeHookConfigs.entries()) {
            if (!pattern.test(host)) continue;

            const isDark = themeMode === 'dark';
            const script = config.script.replace(/__IS_DARK__/g, isDark ? 'true' : 'false');

            try {
                await controller.evaluateJavaScript(script);
                if (config.requiresReload && triggeredByThemeChange) {
                    controller.reload();
                }
            } catch (err) {
                console.error(`[ThemeHook] Failed for host=${host}:`, err);
                if (config.requiresReload && triggeredByThemeChange) {
                    controller.reload();
                }
            }
            break;
        }
    }, [currentUri, themeMode]);

    // ========================================================================
    // Navigation Handlers
    // ========================================================================

    const loadUrlOrSearch = useCallback((input: string) => {
        const trimmed = input.trim();
        try {
            const uri = new URL(trimmed.includes('://') ? trimmed : `https://${trimmed}`);
            if (uri.hostname) {
                setCurrentUri(uri);
                geckoRef.current?.loadUrl(uri.toString());
            }
        } catch {
            // Treat as search query
            const searchQuery = encodeURIComponent(trimmed);
            const fallback = new URL(`https://www.google.com/search?q=${searchQuery}`);
            setCurrentUri(fallback);
            geckoRef.current?.loadUrl(fallback.toString());
        }
        setError(null);
    }, []);

    const goBack = useCallback(() => geckoRef.current?.goBack(), []);
    const goForward = useCallback(() => geckoRef.current?.goForward(), []);
    const reload = useCallback(() => geckoRef.current?.reload(), []);

    const toggleTheme = useCallback(() => {
        const newMode = themeMode === 'light' ? 'dark' : 'light';
        setThemeMode(newMode);
        // Apply per-domain theme hooks after toggle
        setTimeout(() => applyPerDomainThemeHook(true), 100);
    }, [themeMode, applyPerDomainThemeHook]);

    const setDesktopMode = useCallback(() => {
        setUserAgentMode('desktop');
        geckoRef.current?.setUserAgent(
            'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36'
        );
        // Optional: Load a DRM demo
        geckoRef.current?.loadUrl('https://bitmovin.com/demos/drm');
    }, []);

    const setMobileMode = useCallback(() => {
        setUserAgentMode('mobile');
        geckoRef.current?.setUserAgent(''); // Reset to default mobile
    }, []);

    // ========================================================================
    // Event Handlers
    // ========================================================================

    const handlePageStart = useCallback((e: { nativeEvent: PageStartNativeEvent }) => {
        const url = e.nativeEvent.url;
        console.log('📄 Page loading:', url);
        // Don't update input for about:blank or internal pages
        if (url && !url.startsWith('about:') && !url.startsWith('javascript:')) {
            try {
                const uri = new URL(url);
                setCurrentUri(uri);
                // Only update input if not focused (user might be typing)
                if (!isInputFocused) {
                    setInputUrl(url);
                }
            } catch {
                // Invalid URL, ignore
            }
        }
        setLoading(true);
        setProgress(0);
        setError(null);
    }, [isInputFocused]);

    const handlePageStop = useCallback((e: { nativeEvent: PageStopNativeEvent }) => {
        console.log('✅ Page loaded:', e.nativeEvent.success);
        setLoading(false);
        setProgress(1);
        // Apply theme hooks after page load
        setTimeout(() => applyPerDomainThemeHook(), 500);
    }, [applyPerDomainThemeHook]);

    const handleProgressChange = useCallback((e: { nativeEvent: ProgressChangeNativeEvent }) => {
        setProgress(e.nativeEvent.progress / 100);
    }, []);

    const handleSecurityChange = useCallback((e: { nativeEvent: SecurityChangeNativeEvent }) => {
        setIsSecure(e.nativeEvent.isSecure);
    }, []);

    const handlePageError = useCallback((e: { nativeEvent: PageErrorNativeEvent }) => {
        const { errorMessage, errorCategory, uri } = e.nativeEvent;
        console.error('❌ Navigation error:', errorCategory, errorMessage);

        // Try to recover by treating as search
        if (uri) {
            loadUrlOrSearch(uri);
        } else {
            setError(`${errorCategory}: ${errorMessage}`);
        }
        setLoading(false);
    }, [loadUrlOrSearch]);

    const handleLocationChange = useCallback((e: { nativeEvent: LocationChangeNativeEvent }) => {
        const url = e.nativeEvent.url;
        console.log('📍 Location changed:', url);
        // Don't update input for about:blank or internal pages
        if (url && !url.startsWith('about:') && !url.startsWith('javascript:')) {
            try {
                const uri = new URL(url);
                setCurrentUri(uri);
                // Only update input if not focused (user might be typing)
                if (!isInputFocused) {
                    setInputUrl(url);
                }
            } catch {
                // Invalid URL, ignore
            }
        }
    }, [isInputFocused]);

    const handleMediaAction = useCallback((e: { nativeEvent: MediaActionNativeEvent }) => {
        const { action, title, artist } = e.nativeEvent;
        console.log('🎵 Media action:', action);
        setMediaAction(action);
        if (title || artist) {
            setMediaInfo({ title: title || '', artist: artist || '' });
        }
    }, []);

    // Prompt handlers
    const handleAlert = useCallback((e: { nativeEvent: PromptNativeEvent }) => {
        const { promptId, message } = e.nativeEvent;
        Alert.alert('Alert', message, [
            { text: 'OK', onPress: () => geckoRef.current?.resolvePrompt(promptId, true) }
        ]);
    }, []);

    const handleConfirm = useCallback((e: { nativeEvent: PromptNativeEvent }) => {
        const { promptId, message } = e.nativeEvent;
        Alert.alert('Confirm', message, [
            { text: 'Cancel', onPress: () => geckoRef.current?.resolvePrompt(promptId, false) },
            { text: 'OK', onPress: () => geckoRef.current?.resolvePrompt(promptId, true) }
        ]);
    }, []);

    const handlePrompt = useCallback((e: { nativeEvent: PromptNativeEvent }) => {
        const { promptId, message, defaultValue } = e.nativeEvent;
        setPromptData({ promptId, message, defaultValue: defaultValue || '' });
        setPromptInput(defaultValue || '');
    }, []);

    const handlePopup = useCallback((e: { nativeEvent: PopupNativeEvent }) => {
        const { promptId, targetUri } = e.nativeEvent;
        Alert.alert(
            'Popup Request',
            `A new window wants to open:\n${targetUri}`,
            [
                { text: 'Deny', onPress: () => geckoRef.current?.resolvePrompt(promptId, false) },
                { text: 'Allow', onPress: () => geckoRef.current?.resolvePrompt(promptId, true) }
            ]
        );
    }, []);

    const handleAuth = useCallback((e: { nativeEvent: AuthNativeEvent }) => {
        const { promptId, message } = e.nativeEvent;
        setAuthData({ promptId, message: message || 'Authentication required' });
    }, []);

    const submitPrompt = useCallback((text: string) => {
        if (promptData) {
            geckoRef.current?.resolvePrompt(promptData.promptId, true, text);
            setPromptData(null);
            setPromptInput('');
        }
    }, [promptData]);

    const submitAuth = useCallback(() => {
        if (authData) {
            geckoRef.current?.resolvePrompt(authData.promptId, true, undefined, username);
            setAuthData(null);
            setUsername('');
            setPassword('');
        }
    }, [authData, username]);

    // ========================================================================
    // Initial Load
    // ========================================================================

    useEffect(() => {
        // Load initial URL after component mounts
        const timer = setTimeout(() => {
            loadUrlOrSearch(inputUrl);
        }, 500);
        return () => clearTimeout(timer);
    }, []);

    // ========================================================================
    // Render
    // ========================================================================

    return (
        <View style={[styles.container, { backgroundColor: theme.colors.background }]}>
            <StatusBar
                barStyle={theme.mode === 'dark' ? 'light-content' : 'dark-content'}
                backgroundColor={theme.colors.surface}
            />

            {/* Browser App Bar */}
            <BrowserAppBar theme={theme}>
                <View style={styles.appBarContent}>
                    {/* Navigation Buttons */}
                    <IconButton testID="back-button" icon="◀" onPress={goBack} theme={theme} />
                    <IconButton testID="forward-button" icon="▶" onPress={goForward} theme={theme} />
                    <IconButton testID="reload-button" icon="↻" onPress={reload} theme={theme} />

                    {/* URL Input Field */}
                    <View style={[
                        styles.urlInputContainer,
                        { backgroundColor: theme.colors.surfaceVariant }
                    ]}>
                        <Text testID="security-indicator" style={[styles.securityIcon, { color: isSecure ? theme.colors.success : theme.colors.onSurfaceVariant }]}>
                            {isSecure ? '🔒' : '🔓'}
                        </Text>
                        <TextInput
                            testID="url-input"
                            style={[styles.urlInput, { color: theme.colors.onSurface }]}
                            value={inputUrl}
                            onChangeText={setInputUrl}
                            onSubmitEditing={() => loadUrlOrSearch(inputUrl)}
                            onFocus={() => setIsInputFocused(true)}
                            onBlur={() => setIsInputFocused(false)}
                            placeholder="Enter URL or search"
                            placeholderTextColor={theme.colors.onSurfaceVariant}
                            autoCapitalize="none"
                            autoCorrect={false}
                            keyboardType="url"
                            returnKeyType="go"
                            selectTextOnFocus={true}
                        />
                    </View>

                    {/* User Agent Mode Buttons */}
                    <IconButton
                        testID="desktop-button"
                        icon="🖥️"
                        onPress={setDesktopMode}
                        theme={theme}
                        active={userAgentMode === 'desktop'}
                    />
                    <IconButton
                        testID="mobile-button"
                        icon="📱"
                        onPress={setMobileMode}
                        theme={theme}
                        active={userAgentMode === 'mobile'}
                    />

                    {/* Theme Toggle */}
                    <IconButton
                        testID="theme-button"
                        icon={theme.mode === 'light' ? '🌙' : '☀️'}
                        onPress={toggleTheme}
                        theme={theme}
                    />
                </View>
            </BrowserAppBar>

            {/* Progress Bar */}
            <ProgressBar testID="progress-bar" progress={progress} theme={theme} visible={loading} />

            {/* Error Banner */}
            {error && (
                <View style={[styles.errorBanner, { backgroundColor: theme.colors.error }]}>
                    <Text style={styles.errorText}>❌ {error}</Text>
                    <TouchableOpacity onPress={() => setError(null)}>
                        <Text style={styles.errorDismiss}>✕</Text>
                    </TouchableOpacity>
                </View>
            )}

            {/* Media Info Bar */}
            {mediaAction && mediaAction !== 'deactivated' && (
                <View style={[styles.mediaBar, { backgroundColor: theme.colors.accent }]}>
                    <Text style={styles.mediaText}>
                        🎵 {mediaAction.toUpperCase()}
                        {mediaInfo.title ? ` - ${mediaInfo.title}` : ''}
                        {mediaInfo.artist ? ` (${mediaInfo.artist})` : ''}
                    </Text>
                </View>
            )}

            {/* GeckoView */}
            <GeckoView
                testID="geckoview"
                ref={geckoRef}
                sessionKey="main-session"
                handleAudioFocus={true}
                style={styles.geckoview}
                onGeckoPageStart={handlePageStart}
                onGeckoPageStop={handlePageStop}
                onGeckoProgressChange={handleProgressChange}
                onGeckoSecurityChange={handleSecurityChange}
                onGeckoLocationChange={handleLocationChange}
                onPageError={handlePageError}
                onMediaSessionAction={handleMediaAction}
                onGeckoAlert={handleAlert}
                onGeckoConfirm={handleConfirm}
                onGeckoPrompt={handlePrompt}
                onGeckoPopup={handlePopup}
                onGeckoAuth={handleAuth}
            />

            {/* Prompt Modal */}
            <Modal visible={!!promptData} transparent animationType="fade">
                <View style={styles.modalOverlay}>
                    <View style={[styles.modalContent, { backgroundColor: theme.colors.surface }]}>
                        <Text style={[styles.modalTitle, { color: theme.colors.onSurface }]}>
                            {promptData?.message}
                        </Text>
                        <TextInput
                            style={[
                                styles.modalInput,
                                {
                                    backgroundColor: theme.colors.surfaceVariant,
                                    color: theme.colors.onSurface,
                                    borderColor: theme.colors.border,
                                }
                            ]}
                            value={promptInput}
                            onChangeText={setPromptInput}
                            onSubmitEditing={() => submitPrompt(promptInput)}
                            placeholder="Enter text..."
                            placeholderTextColor={theme.colors.onSurfaceVariant}
                            autoFocus
                        />
                        <View style={styles.modalButtons}>
                            <TouchableOpacity
                                onPress={() => {
                                    geckoRef.current?.resolvePrompt(promptData?.promptId || '', false);
                                    setPromptData(null);
                                    setPromptInput('');
                                }}
                                style={[styles.modalButton, { backgroundColor: theme.colors.surfaceVariant }]}
                            >
                                <Text style={[styles.modalButtonText, { color: theme.colors.onSurface }]}>
                                    Cancel
                                </Text>
                            </TouchableOpacity>
                            <TouchableOpacity
                                onPress={() => submitPrompt(promptInput)}
                                style={[styles.modalButton, { backgroundColor: theme.colors.primary }]}
                            >
                                <Text style={[styles.modalButtonText, { color: theme.colors.onPrimary }]}>
                                    OK
                                </Text>
                            </TouchableOpacity>
                        </View>
                    </View>
                </View>
            </Modal>

            {/* Auth Modal */}
            <Modal visible={!!authData} transparent animationType="fade">
                <View style={styles.modalOverlay}>
                    <View style={[styles.modalContent, { backgroundColor: theme.colors.surface }]}>
                        <Text style={[styles.modalTitle, { color: theme.colors.onSurface }]}>
                            {authData?.message}
                        </Text>
                        <TextInput
                            style={[
                                styles.modalInput,
                                {
                                    backgroundColor: theme.colors.surfaceVariant,
                                    color: theme.colors.onSurface,
                                    borderColor: theme.colors.border,
                                }
                            ]}
                            value={username}
                            onChangeText={setUsername}
                            placeholder="Username"
                            placeholderTextColor={theme.colors.onSurfaceVariant}
                            autoCapitalize="none"
                            autoFocus
                        />
                        <TextInput
                            style={[
                                styles.modalInput,
                                {
                                    backgroundColor: theme.colors.surfaceVariant,
                                    color: theme.colors.onSurface,
                                    borderColor: theme.colors.border,
                                }
                            ]}
                            value={password}
                            onChangeText={setPassword}
                            placeholder="Password"
                            placeholderTextColor={theme.colors.onSurfaceVariant}
                            secureTextEntry
                        />
                        <View style={styles.modalButtons}>
                            <TouchableOpacity
                                onPress={() => {
                                    geckoRef.current?.resolvePrompt(authData?.promptId || '', false);
                                    setAuthData(null);
                                    setUsername('');
                                    setPassword('');
                                }}
                                style={[styles.modalButton, { backgroundColor: theme.colors.surfaceVariant }]}
                            >
                                <Text style={[styles.modalButtonText, { color: theme.colors.onSurface }]}>
                                    Cancel
                                </Text>
                            </TouchableOpacity>
                            <TouchableOpacity
                                onPress={submitAuth}
                                style={[styles.modalButton, { backgroundColor: theme.colors.primary }]}
                            >
                                <Text style={[styles.modalButtonText, { color: theme.colors.onPrimary }]}>
                                    Login
                                </Text>
                            </TouchableOpacity>
                        </View>
                    </View>
                </View>
            </Modal>
        </View>
    );
}

// ============================================================================
// Styles
// ============================================================================

const styles = StyleSheet.create({
    container: {
        flex: 1,
    },
    appBar: {
        paddingTop: Platform.OS === 'ios' ? 50 : 8,
        paddingBottom: 8,
        paddingHorizontal: 8,
        shadowOffset: { width: 0, height: 2 },
        shadowOpacity: 0.1,
        shadowRadius: 4,
        elevation: 4,
        borderBottomWidth: StyleSheet.hairlineWidth,
    },
    appBarContent: {
        flexDirection: 'row',
        alignItems: 'center',
        gap: 4,
    },
    iconButton: {
        width: 40,
        height: 40,
        borderRadius: 20,
        alignItems: 'center',
        justifyContent: 'center',
    },
    iconText: {
        fontWeight: '500',
    },
    urlInputContainer: {
        flex: 1,
        flexDirection: 'row',
        alignItems: 'center',
        height: 40,
        borderRadius: 20,
        paddingHorizontal: 12,
        marginHorizontal: 4,
    },
    securityIcon: {
        fontSize: 14,
        marginRight: 6,
    },
    urlInput: {
        flex: 1,
        fontSize: 14,
        paddingVertical: 0,
    },
    progressContainer: {
        height: 3,
        overflow: 'hidden',
    },
    progressFill: {
        height: '100%',
    },
    errorBanner: {
        flexDirection: 'row',
        alignItems: 'center',
        justifyContent: 'space-between',
        paddingHorizontal: 16,
        paddingVertical: 10,
    },
    errorText: {
        color: '#FFFFFF',
        fontSize: 13,
        flex: 1,
    },
    errorDismiss: {
        color: '#FFFFFF',
        fontSize: 18,
        paddingLeft: 12,
    },
    mediaBar: {
        paddingHorizontal: 16,
        paddingVertical: 10,
    },
    mediaText: {
        color: '#FFFFFF',
        fontSize: 13,
        textAlign: 'center',
    },
    geckoview: {
        flex: 1,
    },
    modalOverlay: {
        flex: 1,
        backgroundColor: 'rgba(0, 0, 0, 0.5)',
        justifyContent: 'center',
        alignItems: 'center',
    },
    modalContent: {
        borderRadius: 16,
        padding: 24,
        width: '85%',
        maxWidth: 400,
        shadowColor: '#000',
        shadowOffset: { width: 0, height: 8 },
        shadowOpacity: 0.25,
        shadowRadius: 16,
        elevation: 8,
    },
    modalTitle: {
        fontSize: 18,
        fontWeight: '600',
        marginBottom: 20,
        textAlign: 'center',
    },
    modalInput: {
        borderWidth: 1,
        borderRadius: 12,
        paddingHorizontal: 16,
        paddingVertical: 12,
        marginBottom: 12,
        fontSize: 15,
    },
    modalButtons: {
        flexDirection: 'row',
        justifyContent: 'flex-end',
        marginTop: 8,
        gap: 12,
    },
    modalButton: {
        paddingHorizontal: 24,
        paddingVertical: 12,
        borderRadius: 12,
    },
    modalButtonText: {
        fontWeight: '600',
        fontSize: 15,
    },
});
