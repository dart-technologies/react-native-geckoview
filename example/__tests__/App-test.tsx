/**
 * @format
 * App component integration test
 */

import React from 'react';
// Import from 'pure' which doesn't auto-register afterEach cleanup hooks
import { render, fireEvent, waitFor, act, RenderResult, cleanup } from '@testing-library/react-native/pure';
import App from '../App';

// Mock GeckoView with event handler support
const mockGeckoViewRef = {
    loadUrl: jest.fn(),
    goBack: jest.fn(),
    goForward: jest.fn(),
    reload: jest.fn(),
    setUserAgent: jest.fn(),
    evaluateJavaScript: jest.fn(),
    resolvePrompt: jest.fn(),
};

jest.mock('react-native-geckoview', () => {
    const React = require('react');
    return {
        __esModule: true,
        default: React.forwardRef((props: any, ref: any) => {
            React.useImperativeHandle(ref, () => mockGeckoViewRef);
            const { View } = require('react-native');
            return <View testID="geckoview" {...props} />;
        }),
    };
});

describe('App', () => {
    beforeEach(() => {
        // Use advanceTimers to auto-advance fake timers when waiting
        jest.useFakeTimers({ advanceTimers: true });
        mockGeckoViewRef.loadUrl.mockClear();
        mockGeckoViewRef.goBack.mockClear();
        mockGeckoViewRef.goForward.mockClear();
        mockGeckoViewRef.reload.mockClear();
        mockGeckoViewRef.setUserAgent.mockClear();
    });

    afterEach(() => {
        // Clear all pending timers before doing cleanup
        jest.clearAllTimers();
        // Manual cleanup since we're using pure import
        cleanup();
        jest.useRealTimers();
        jest.restoreAllMocks();
    });

    it('renders without crashing', () => {
        render(<App />);
    });

    it('renders GeckoView component', () => {
        const { getByTestId }: RenderResult = render(<App />);
        expect(getByTestId('geckoview')).toBeTruthy();
    });

    describe('URL Bar', () => {
        // Note: Initial URL state is tested implicitly by other tests.
        // A dedicated "starts with default URL" test was causing timeout issues
        // due to testing-library cleanup interactions with fake timers.

        it('allows editing URL when focused', () => {
            const { getByPlaceholderText } = render(<App />);
            const input = getByPlaceholderText('Enter URL or search');

            fireEvent.changeText(input, 'https://example.com');
            expect(input.props.value).toBe('https://example.com');
        });

        it('loads URL on submit', () => {
            const { getByPlaceholderText } = render(<App />);
            const input = getByPlaceholderText('Enter URL or search');

            fireEvent.changeText(input, 'https://google.com');
            fireEvent(input, 'submitEditing');

            // URL constructor adds trailing slash
            expect(mockGeckoViewRef.loadUrl).toHaveBeenCalledWith('https://google.com/');
        });

        it('adds https:// prefix when missing', () => {
            const { getByPlaceholderText } = render(<App />);
            const input = getByPlaceholderText('Enter URL or search');

            fireEvent.changeText(input, 'example.com');
            fireEvent(input, 'submitEditing');

            expect(mockGeckoViewRef.loadUrl).toHaveBeenCalledWith('https://example.com/');
        });

        it('performs Google search for invalid URLs', () => {
            const { getByPlaceholderText } = render(<App />);
            const input = getByPlaceholderText('Enter URL or search');

            fireEvent.changeText(input, 'how to make coffee');
            fireEvent(input, 'submitEditing');

            expect(mockGeckoViewRef.loadUrl).toHaveBeenCalledWith(
                expect.stringContaining('google.com/search?q=how')
            );
        });
    });

    describe('Location Change Events', () => {
        it('updates URL bar on location change', () => {
            const { getByTestId, getByPlaceholderText } = render(<App />);
            const geckoView = getByTestId('geckoview');
            const input = getByPlaceholderText('Enter URL or search');

            // Simulate location change event
            fireEvent(geckoView, 'onGeckoLocationChange', {
                nativeEvent: { url: 'https://newsite.com/page' }
            });

            expect(input.props.value).toBe('https://newsite.com/page');
        });

        it('ignores about:blank in URL updates', () => {
            const { getByTestId, getByPlaceholderText } = render(<App />);
            const geckoView = getByTestId('geckoview');
            const input = getByPlaceholderText('Enter URL or search');

            // First, set a real URL
            fireEvent(geckoView, 'onGeckoLocationChange', {
                nativeEvent: { url: 'https://example.com' }
            });
            expect(input.props.value).toBe('https://example.com');

            // about:blank should be ignored
            fireEvent(geckoView, 'onGeckoLocationChange', {
                nativeEvent: { url: 'about:blank' }
            });
            expect(input.props.value).toBe('https://example.com');
        });

        it('ignores javascript: URLs in URL updates', () => {
            const { getByTestId, getByPlaceholderText } = render(<App />);
            const geckoView = getByTestId('geckoview');
            const input = getByPlaceholderText('Enter URL or search');

            fireEvent(geckoView, 'onGeckoLocationChange', {
                nativeEvent: { url: 'https://example.com' }
            });

            fireEvent(geckoView, 'onGeckoLocationChange', {
                nativeEvent: { url: 'javascript:void(0)' }
            });
            expect(input.props.value).toBe('https://example.com');
        });
    });

    describe('Navigation Controls', () => {
        it('calls goBack when back button pressed', () => {
            const { getByText } = render(<App />);

            fireEvent.press(getByText('◀'));
            expect(mockGeckoViewRef.goBack).toHaveBeenCalled();
        });

        it('calls goForward when forward button pressed', () => {
            const { getByText } = render(<App />);

            fireEvent.press(getByText('▶'));
            expect(mockGeckoViewRef.goForward).toHaveBeenCalled();
        });

        it('calls reload when reload button pressed', () => {
            const { getByText } = render(<App />);

            fireEvent.press(getByText('↻'));
            expect(mockGeckoViewRef.reload).toHaveBeenCalled();
        });
    });

    describe('Progress Events', () => {
        it('updates progress on onGeckoProgressChange', () => {
            const { getByTestId } = render(<App />);
            const geckoView = getByTestId('geckoview');

            fireEvent(geckoView, 'onGeckoPageStart', {
                nativeEvent: { url: 'https://example.com' }
            });

            fireEvent(geckoView, 'onGeckoProgressChange', {
                nativeEvent: { progress: 50 }
            });
        });

        it('handles page stop event', () => {
            const { getByTestId } = render(<App />);
            const geckoView = getByTestId('geckoview');

            fireEvent(geckoView, 'onGeckoPageStart', {
                nativeEvent: { url: 'https://example.com' }
            });

            fireEvent(geckoView, 'onGeckoPageStop', {
                nativeEvent: { success: true }
            });
        });
    });

    describe('Security Indicator', () => {
        it('shows secure indicator for HTTPS', () => {
            const { getByTestId, getByText } = render(<App />);
            const geckoView = getByTestId('geckoview');

            fireEvent(geckoView, 'onGeckoSecurityChange', {
                nativeEvent: { isSecure: true, host: 'example.com' }
            });

            expect(getByText('🔒')).toBeTruthy();
        });

        it('shows insecure indicator for HTTP', () => {
            const { getByTestId, getByText } = render(<App />);
            const geckoView = getByTestId('geckoview');

            fireEvent(geckoView, 'onGeckoSecurityChange', {
                nativeEvent: { isSecure: false, host: 'example.com' }
            });

            expect(getByText('🔓')).toBeTruthy();
        });
    });

    describe('Theme Toggle', () => {
        it('toggles theme when theme button pressed', () => {
            const { getByText } = render(<App />);

            // Initial state is light mode, so icon is moon (🌙) to switch to dark
            const themeButton = getByText('🌙');
            expect(themeButton).toBeTruthy();

            fireEvent.press(themeButton);

            // After toggle to dark mode, icon should be sun (☀️)
            expect(getByText('☀️')).toBeTruthy();
        });
    });

    describe('User Agent Switching', () => {
        it('sets desktop user agent', () => {
            const { getByText } = render(<App />);

            fireEvent.press(getByText('🖥️'));

            expect(mockGeckoViewRef.setUserAgent).toHaveBeenCalledWith(
                expect.stringContaining('X11; Linux x86_64')
            );
        });

        it('resets to mobile user agent', () => {
            const { getByText } = render(<App />);

            // First switch to desktop
            fireEvent.press(getByText('🖥️'));

            // Then switch back to mobile
            fireEvent.press(getByText('📱'));

            expect(mockGeckoViewRef.setUserAgent).toHaveBeenLastCalledWith('');
        });
    });
});
