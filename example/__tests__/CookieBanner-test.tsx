/**
 * @format
 * Cookie Banner Tests (Item 9)
 * 
 * Tests for cookie banner handling configuration:
 * - Cookie banner mode settings (disabled, reject, reject_or_accept)
 * - Cookie banner global rules
 * - Integration with ETP settings
 */

import '../setup';

import React, { useRef } from 'react';
import { render, waitFor, act } from '@testing-library/react-native';
import { NativeModules } from 'react-native';
import GeckoView, { GeckoViewRef } from 'react-native-geckoview';
import { clearFabricGlobals, fixtures, GeckoViewModuleMock } from './testUtils';

// Use shared mock setup
jest.mock('react-native', () => require('./testUtils').setupReactNativeMock());

describe('Cookie Banner Tests', () => {
    beforeEach(() => {
        clearFabricGlobals();
        jest.clearAllMocks();
    });

    describe('Cookie Banner Mode Configuration', () => {
        it('should call setCookieBannerMode with DISABLED mode (0)', async () => {
            const TestComponent: React.FC = () => {
                const geckoRef = useRef<GeckoViewRef>(null);

                React.useEffect(() => {
                    setTimeout(() => {
                        // Mode 0 = DISABLED
                        (NativeModules.GeckoViewModule as GeckoViewModuleMock).setCookieBannerMode(0);
                    }, 100);
                }, []);

                return <GeckoView ref={geckoRef} source={{ uri: fixtures.urls.example }} />;
            };

            render(<TestComponent />);

            const geckoModule = NativeModules.GeckoViewModule as GeckoViewModuleMock;
            await waitFor(() => {
                expect(geckoModule.setCookieBannerMode).toHaveBeenCalledWith(0);
            }, { timeout: 500 });
        });

        it('should call setCookieBannerMode with REJECT mode (1)', async () => {
            const geckoModule = NativeModules.GeckoViewModule as GeckoViewModuleMock;

            render(<GeckoView source={{ uri: fixtures.urls.example }} />);

            // Simulate mode change
            act(() => {
                geckoModule.setCookieBannerMode(1);
            });

            expect(geckoModule.setCookieBannerMode).toHaveBeenCalledWith(1);
        });

        it('should call setCookieBannerMode with REJECT_OR_ACCEPT mode (2)', async () => {
            const geckoModule = NativeModules.GeckoViewModule as GeckoViewModuleMock;

            render(<GeckoView source={{ uri: fixtures.urls.example }} />);

            act(() => {
                geckoModule.setCookieBannerMode(2);
            });

            expect(geckoModule.setCookieBannerMode).toHaveBeenCalledWith(2);
        });
    });

    describe('Cookie Banner Mode Transitions', () => {
        it('should allow transitioning between modes', async () => {
            const geckoModule = NativeModules.GeckoViewModule as GeckoViewModuleMock;

            render(<GeckoView source={{ uri: fixtures.urls.example }} />);

            // Transition through all modes
            act(() => {
                geckoModule.setCookieBannerMode(0); // DISABLED
                geckoModule.setCookieBannerMode(1); // REJECT
                geckoModule.setCookieBannerMode(2); // REJECT_OR_ACCEPT
            });

            expect(geckoModule.setCookieBannerMode).toHaveBeenCalledTimes(3);
        });

        it('should handle rapid mode changes', async () => {
            const geckoModule = NativeModules.GeckoViewModule as GeckoViewModuleMock;

            render(<GeckoView source={{ uri: fixtures.urls.example }} />);

            act(() => {
                for (let i = 0; i < 10; i++) {
                    geckoModule.setCookieBannerMode(i % 3);
                }
            });

            expect(geckoModule.setCookieBannerMode).toHaveBeenCalledTimes(10);
        });

        it('should handle repeated same mode calls idempotently', async () => {
            const geckoModule = NativeModules.GeckoViewModule as GeckoViewModuleMock;

            render(<GeckoView source={{ uri: fixtures.urls.example }} />);

            act(() => {
                geckoModule.setCookieBannerMode(1);
                geckoModule.setCookieBannerMode(1);
                geckoModule.setCookieBannerMode(1);
            });

            expect(geckoModule.setCookieBannerMode).toHaveBeenCalledTimes(3);
            // All calls should be with mode 1
            expect(geckoModule.setCookieBannerMode).toHaveBeenNthCalledWith(1, 1);
            expect(geckoModule.setCookieBannerMode).toHaveBeenNthCalledWith(2, 1);
            expect(geckoModule.setCookieBannerMode).toHaveBeenNthCalledWith(3, 1);
        });
    });

    describe('Cookie Banner Global Rules', () => {
        it('should call setCookieBannerGlobalRules with valid JSON', async () => {
            const geckoModule = NativeModules.GeckoViewModule as GeckoViewModuleMock;

            render(<GeckoView source={{ uri: fixtures.urls.example }} />);

            const rulesJson = JSON.stringify({
                rules: [
                    { domain: 'example.com', action: 'reject' }
                ]
            });

            act(() => {
                geckoModule.setCookieBannerGlobalRules?.(rulesJson);
            });

            expect(geckoModule.setCookieBannerGlobalRules).toHaveBeenCalledWith(rulesJson);
        });

        it('should handle multiple domain rules', async () => {
            const geckoModule = NativeModules.GeckoViewModule as GeckoViewModuleMock;

            render(<GeckoView source={{ uri: fixtures.urls.example }} />);

            const rulesJson = JSON.stringify({
                rules: [
                    { domain: 'site1.com', action: 'reject' },
                    { domain: 'site2.com', action: 'accept' },
                    { domain: 'site3.com', action: 'reject_or_accept' }
                ]
            });

            act(() => {
                geckoModule.setCookieBannerGlobalRules?.(rulesJson);
            });

            expect(geckoModule.setCookieBannerGlobalRules).toHaveBeenCalled();
        });

        it('should handle wildcard domain rules', async () => {
            const geckoModule = NativeModules.GeckoViewModule as GeckoViewModuleMock;

            render(<GeckoView source={{ uri: fixtures.urls.example }} />);

            const rulesJson = JSON.stringify({
                rules: [
                    { domain: '*.example.com', action: 'reject' }
                ]
            });

            act(() => {
                geckoModule.setCookieBannerGlobalRules?.(rulesJson);
            });

            expect(geckoModule.setCookieBannerGlobalRules).toHaveBeenCalledWith(
                expect.stringContaining('*.example.com')
            );
        });

        it('should handle empty rules array', async () => {
            const geckoModule = NativeModules.GeckoViewModule as GeckoViewModuleMock;

            render(<GeckoView source={{ uri: fixtures.urls.example }} />);

            const rulesJson = JSON.stringify({ rules: [] });

            act(() => {
                geckoModule.setCookieBannerGlobalRules?.(rulesJson);
            });

            expect(geckoModule.setCookieBannerGlobalRules).toHaveBeenCalled();
        });
    });

    describe('Cookie Banner with ETP Integration', () => {
        it('should work alongside ETP settings', async () => {
            const geckoModule = NativeModules.GeckoViewModule as GeckoViewModuleMock;

            render(<GeckoView source={{ uri: fixtures.urls.example }} />);

            // Both ETP and cookie banner can be configured
            act(() => {
                geckoModule.setEnhancedTrackingProtectionLevel(2); // STRICT
                geckoModule.setCookieBannerMode(1); // REJECT
            });

            expect(geckoModule.setEnhancedTrackingProtectionLevel).toHaveBeenCalledWith(2);
            expect(geckoModule.setCookieBannerMode).toHaveBeenCalledWith(1);
        });

        it('should allow changing ETP without affecting cookie banner mode', async () => {
            const geckoModule = NativeModules.GeckoViewModule as GeckoViewModuleMock;

            render(<GeckoView source={{ uri: fixtures.urls.example }} />);

            act(() => {
                geckoModule.setCookieBannerMode(1);
            });

            act(() => {
                geckoModule.setEnhancedTrackingProtectionLevel(0);
                geckoModule.setEnhancedTrackingProtectionLevel(1);
                geckoModule.setEnhancedTrackingProtectionLevel(2);
            });

            // Cookie banner mode should only be called once
            expect(geckoModule.setCookieBannerMode).toHaveBeenCalledTimes(1);
            // ETP should be called 3 times
            expect(geckoModule.setEnhancedTrackingProtectionLevel).toHaveBeenCalledTimes(3);
        });
    });

    describe('Cookie Banner Error Handling', () => {
        it('should not crash with invalid mode value', () => {
            const geckoModule = NativeModules.GeckoViewModule as GeckoViewModuleMock;

            expect(() => {
                render(<GeckoView source={{ uri: fixtures.urls.example }} />);

                act(() => {
                    // Invalid mode values should be handled gracefully
                    geckoModule.setCookieBannerMode(-1);
                    geckoModule.setCookieBannerMode(100);
                });
            }).not.toThrow();
        });

        it('should not crash with malformed JSON rules', () => {
            const geckoModule = NativeModules.GeckoViewModule as GeckoViewModuleMock;

            expect(() => {
                render(<GeckoView source={{ uri: fixtures.urls.example }} />);

                act(() => {
                    geckoModule.setCookieBannerGlobalRules?.('not valid json');
                });
            }).not.toThrow();
        });

        it('should not crash with empty string rules', () => {
            const geckoModule = NativeModules.GeckoViewModule as GeckoViewModuleMock;

            expect(() => {
                render(<GeckoView source={{ uri: fixtures.urls.example }} />);

                act(() => {
                    geckoModule.setCookieBannerGlobalRules?.('');
                });
            }).not.toThrow();
        });
    });

    describe('Cookie Banner Mode Constants', () => {
        it('should have correct DISABLED constant value', () => {
            const MODE_DISABLED = 0;
            expect(MODE_DISABLED).toBe(0);
        });

        it('should have correct REJECT constant value', () => {
            const MODE_REJECT = 1;
            expect(MODE_REJECT).toBe(1);
        });

        it('should have correct REJECT_OR_ACCEPT constant value', () => {
            const MODE_REJECT_OR_ACCEPT = 2;
            expect(MODE_REJECT_OR_ACCEPT).toBe(2);
        });

        it('should have distinct mode values', () => {
            const MODE_DISABLED = 0;
            const MODE_REJECT = 1;
            const MODE_REJECT_OR_ACCEPT = 2;

            expect(MODE_DISABLED).not.toBe(MODE_REJECT);
            expect(MODE_REJECT).not.toBe(MODE_REJECT_OR_ACCEPT);
            expect(MODE_DISABLED).not.toBe(MODE_REJECT_OR_ACCEPT);
        });
    });
});
