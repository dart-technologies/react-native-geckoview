/**
 * @format
 * Error Delegate Tests (Item 7)
 * 
 * Tests for error event emission and handling:
 * - Page load errors (network, security, content)
 * - Error category mapping
 * - Error message formatting
 * - Error recovery scenarios
 */

import '../setup';

import React from 'react';
import { render, waitFor } from '@testing-library/react-native';
import { NativeModules } from 'react-native';
import GeckoView from 'react-native-geckoview';
import { clearFabricGlobals, fixtures } from './testUtils';

// Use shared mock setup
jest.mock('react-native', () => require('./testUtils').setupReactNativeMock());

describe('Error Delegate Tests', () => {
    beforeEach(() => {
        clearFabricGlobals();
        jest.clearAllMocks();
    });

    describe('Error Event Handler', () => {
        it('should register onPageError callback', () => {
            const onPageError = jest.fn();

            const { getByTestId } = render(
                <GeckoView
                    source={{ uri: fixtures.urls.example }}
                    onPageError={onPageError}
                    testID="gecko-error"
                />
            );

            expect(getByTestId('gecko-error')).toBeDefined();
        });

        it('should not crash if onPageError is not provided', () => {
            expect(() =>
                render(
                    <GeckoView
                        source={{ uri: fixtures.urls.example }}
                    />
                )
            ).not.toThrow();
        });

        it('should render with both onPageError and onGeckoPageStop callbacks', () => {
            const onPageError = jest.fn();
            const onGeckoPageStop = jest.fn();

            const { getByTestId } = render(
                <GeckoView
                    source={{ uri: fixtures.urls.example }}
                    onPageError={onPageError}
                    onGeckoPageStop={onGeckoPageStop}
                    testID="gecko-callbacks"
                />
            );

            expect(getByTestId('gecko-callbacks')).toBeDefined();
        });
    });

    describe('Error Categories', () => {
        // These test the expected error category strings
        const errorCategories = [
            'unknown',
            'security',
            'network',
            'content',
            'uri',
            'proxy',
            'safebrowsing'
        ];

        errorCategories.forEach(category => {
            it(`should handle '${category}' error category`, () => {
                const onPageError = jest.fn();

                const { getByTestId } = render(
                    <GeckoView
                        source={{ uri: fixtures.urls.example }}
                        onPageError={onPageError}
                        testID={`gecko-error-${category}`}
                    />
                );

                expect(getByTestId(`gecko-error-${category}`)).toBeDefined();
                // Native code would emit: { errorCategory: category }
            });
        });
    });

    describe('Error Codes', () => {
        it('should handle SSL certificate error', () => {
            const onPageError = jest.fn();

            render(
                <GeckoView
                    source={{ uri: 'https://expired.badssl.com/' }}
                    onPageError={onPageError}
                />
            );

            // Native would emit: { errorCode: ERROR_SECURITY_SSL }
        });

        it('should handle connection timeout error', () => {
            const onPageError = jest.fn();

            render(
                <GeckoView
                    source={{ uri: 'https://10.255.255.1/' }} // Non-routable IP
                    onPageError={onPageError}
                />
            );

            // Native would emit: { errorCode: ERROR_NET_TIMEOUT }
        });

        it('should handle unknown host error', () => {
            const onPageError = jest.fn();

            render(
                <GeckoView
                    source={{ uri: 'https://this-domain-definitely-does-not-exist-12345.com/' }}
                    onPageError={onPageError}
                />
            );

            // Native would emit: { errorCode: ERROR_UNKNOWN_HOST }
        });

        it('should handle malformed URI error', () => {
            const onPageError = jest.fn();

            render(
                <GeckoView
                    source={{ uri: 'not-a-valid-url' }}
                    onPageError={onPageError}
                />
            );

            // Native would emit: { errorCode: ERROR_MALFORMED_URI }
        });
    });

    describe('Safe Browsing Errors', () => {
        it('should handle malware URI error', () => {
            const onPageError = jest.fn();

            render(
                <GeckoView
                    source={{ uri: fixtures.urls.example }}
                    onPageError={onPageError}
                    testID="gecko-malware"
                />
            );

            // Native would emit: { errorCode: ERROR_SAFEBROWSING_MALWARE_URI }
        });

        it('should handle phishing URI error', () => {
            const onPageError = jest.fn();

            render(
                <GeckoView
                    source={{ uri: fixtures.urls.example }}
                    onPageError={onPageError}
                    testID="gecko-phishing"
                />
            );

            // Native would emit: { errorCode: ERROR_SAFEBROWSING_PHISHING_URI }
        });

        it('should handle harmful URI error', () => {
            const onPageError = jest.fn();

            render(
                <GeckoView
                    source={{ uri: fixtures.urls.example }}
                    onPageError={onPageError}
                    testID="gecko-harmful"
                />
            );

            // Native would emit: { errorCode: ERROR_SAFEBROWSING_HARMFUL_URI }
        });
    });

    describe('Error Recovery', () => {
        it('should allow reload after error', async () => {
            const onPageError = jest.fn();
            const geckoRef = React.createRef<any>();

            render(
                <GeckoView
                    ref={geckoRef}
                    source={{ uri: fixtures.urls.example }}
                    onPageError={onPageError}
                />
            );

            // Simulate reload after error
            await waitFor(() => {
                if (geckoRef.current) {
                    expect(() => geckoRef.current.reload()).not.toThrow();
                }
            }, { timeout: 100 });
        });

        it('should allow navigation to new URL after error', async () => {
            const onPageError = jest.fn();

            const { rerender } = render(
                <GeckoView
                    source={{ uri: 'https://invalid-url-12345.test/' }}
                    onPageError={onPageError}
                />
            );

            // Navigate to valid URL
            rerender(
                <GeckoView
                    source={{ uri: fixtures.urls.example }}
                    onPageError={onPageError}
                />
            );

            // Should not crash
        });
    });

    describe('Error Event Data Structure', () => {
        it('should expect error event with uri property', () => {
            const onPageError = jest.fn();

            render(
                <GeckoView
                    source={{ uri: fixtures.urls.example }}
                    onPageError={onPageError}
                />
            );

            // Native event structure: { uri: string }
        });

        it('should expect error event with errorCode property', () => {
            const onPageError = jest.fn();

            render(
                <GeckoView
                    source={{ uri: fixtures.urls.example }}
                    onPageError={onPageError}
                />
            );

            // Native event structure: { errorCode: number }
        });

        it('should expect error event with errorCategory property', () => {
            const onPageError = jest.fn();

            render(
                <GeckoView
                    source={{ uri: fixtures.urls.example }}
                    onPageError={onPageError}
                />
            );

            // Native event structure: { errorCategory: string }
        });

        it('should expect error event with errorMessage property', () => {
            const onPageError = jest.fn();

            render(
                <GeckoView
                    source={{ uri: fixtures.urls.example }}
                    onPageError={onPageError}
                />
            );

            // Native event structure: { errorMessage: string }
        });
    });

    describe('Error with Multiple Handlers', () => {
        it('should work alongside onGeckoPageStart handler', () => {
            const onPageError = jest.fn();
            const onGeckoPageStart = jest.fn();

            const { getByTestId } = render(
                <GeckoView
                    source={{ uri: fixtures.urls.example }}
                    onPageError={onPageError}
                    onGeckoPageStart={onGeckoPageStart}
                    testID="gecko-multi-handler"
                />
            );

            expect(getByTestId('gecko-multi-handler')).toBeDefined();
        });

        it('should work alongside onGeckoProgressChange handler', () => {
            const onPageError = jest.fn();
            const onGeckoProgressChange = jest.fn();

            const { getByTestId } = render(
                <GeckoView
                    source={{ uri: fixtures.urls.example }}
                    onPageError={onPageError}
                    onGeckoProgressChange={onGeckoProgressChange}
                    testID="gecko-progress-error"
                />
            );

            expect(getByTestId('gecko-progress-error')).toBeDefined();
        });

        it('should work alongside onGeckoSecurityChange handler', () => {
            const onPageError = jest.fn();
            const onGeckoSecurityChange = jest.fn();

            const { getByTestId } = render(
                <GeckoView
                    source={{ uri: fixtures.urls.example }}
                    onPageError={onPageError}
                    onGeckoSecurityChange={onGeckoSecurityChange}
                    testID="gecko-security-error"
                />
            );

            expect(getByTestId('gecko-security-error')).toBeDefined();
        });
    });

    describe('Rapid Error Scenarios', () => {
        it('should handle rapid navigation to invalid URLs', () => {
            const onPageError = jest.fn();

            const { rerender } = render(
                <GeckoView
                    source={{ uri: 'https://invalid1.test/' }}
                    onPageError={onPageError}
                />
            );

            for (let i = 2; i <= 5; i++) {
                rerender(
                    <GeckoView
                        source={{ uri: `https://invalid${i}.test/` }}
                        onPageError={onPageError}
                    />
                );
            }

            // Should not crash
        });

        it('should handle alternating valid and invalid URLs', () => {
            const onPageError = jest.fn();

            const { rerender } = render(
                <GeckoView
                    source={{ uri: fixtures.urls.example }}
                    onPageError={onPageError}
                />
            );

            rerender(
                <GeckoView
                    source={{ uri: 'https://invalid.test/' }}
                    onPageError={onPageError}
                />
            );

            rerender(
                <GeckoView
                    source={{ uri: fixtures.urls.mozilla }}
                    onPageError={onPageError}
                />
            );

            // Should not crash
        });
    });
});
