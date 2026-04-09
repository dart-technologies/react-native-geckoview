/**
 * @format
 * GeckoView Settings Tests
 * 
 * Tests for GeckoView configuration options:
 * - User agent switching
 * - Content blocking settings
 * - Cookie behavior
 * - Do Not Track (DNT) settings
 */

import '../setup';

import React from 'react';
import renderer from 'react-test-renderer';
import { NativeModules } from 'react-native';
import { clearFabricGlobals, fixtures } from './testUtils';

// Use shared mock setup
jest.mock('react-native', () => require('./testUtils').setupReactNativeMock());

import GeckoView, { GeckoViewRef } from 'react-native-geckoview';

describe('GeckoView Settings Tests', () => {
    beforeEach(() => {
        clearFabricGlobals();
        jest.clearAllMocks();
    });

    // ========== User Agent Settings ==========

    describe('User Agent', () => {
        it('exposes setUserAgent method via ref', () => {
            const ref = React.createRef<GeckoViewRef>();
            renderer.act(() => {
                renderer.create(
                    <GeckoView ref={ref} source={{ uri: fixtures.urls.example }} />
                );
            });

            expect(ref.current).not.toBeNull();
            expect(ref.current!.setUserAgent).toBeDefined();
            expect(typeof ref.current!.setUserAgent).toBe('function');
        });

        it('sets custom user agent string', () => {
            const ref = React.createRef<GeckoViewRef>();
            renderer.act(() => {
                renderer.create(
                    <GeckoView ref={ref} sessionKey="ua-session" source={{ uri: fixtures.urls.example }} />
                );
            });

            const customUA = 'Mozilla/5.0 (Custom Agent) Gecko/20100101 Firefox/120.0';
            ref.current!.setUserAgent(customUA);

            expect(NativeModules.GeckoViewModule.setUserAgent).toHaveBeenCalledWith(
                'ua-session',
                customUA
            );
        });

        it('sets desktop user agent', () => {
            const ref = React.createRef<GeckoViewRef>();
            renderer.act(() => {
                renderer.create(
                    <GeckoView ref={ref} sessionKey="desktop-session" source={{ uri: fixtures.urls.example }} />
                );
            });

            const desktopUA = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) Gecko/20100101 Firefox/120.0';
            ref.current!.setUserAgent(desktopUA);

            expect(NativeModules.GeckoViewModule.setUserAgent).toHaveBeenCalledWith(
                'desktop-session',
                desktopUA
            );
        });

        it('sets mobile user agent', () => {
            const ref = React.createRef<GeckoViewRef>();
            renderer.act(() => {
                renderer.create(
                    <GeckoView ref={ref} sessionKey="mobile-session" source={{ uri: fixtures.urls.example }} />
                );
            });

            const mobileUA = 'Mozilla/5.0 (Android 13; Mobile) Gecko/20100101 Firefox/120.0';
            ref.current!.setUserAgent(mobileUA);

            expect(NativeModules.GeckoViewModule.setUserAgent).toHaveBeenCalledWith(
                'mobile-session',
                mobileUA
            );
        });

        it('resets user agent with empty string', () => {
            const ref = React.createRef<GeckoViewRef>();
            renderer.act(() => {
                renderer.create(
                    <GeckoView ref={ref} sessionKey="reset-session" source={{ uri: fixtures.urls.example }} />
                );
            });

            ref.current!.setUserAgent('');

            expect(NativeModules.GeckoViewModule.setUserAgent).toHaveBeenCalledWith(
                'reset-session',
                ''
            );
        });
    });

    // ========== Enhanced Tracking Protection ==========

    describe('Enhanced Tracking Protection', () => {
        it('exposes setEnhancedTrackingProtectionLevel method', () => {
            const ref = React.createRef<GeckoViewRef>();
            renderer.act(() => {
                renderer.create(
                    <GeckoView ref={ref} source={{ uri: fixtures.urls.example }} />
                );
            });

            expect(ref.current!.setEnhancedTrackingProtectionLevel).toBeDefined();
            expect(typeof ref.current!.setEnhancedTrackingProtectionLevel).toBe('function');
        });

        it('sets ETP to NONE (0)', () => {
            const ref = React.createRef<GeckoViewRef>();
            renderer.act(() => {
                renderer.create(
                    <GeckoView ref={ref} source={{ uri: fixtures.urls.example }} />
                );
            });

            ref.current!.setEnhancedTrackingProtectionLevel(0);

            expect(NativeModules.GeckoViewModule.setEnhancedTrackingProtectionLevel).toHaveBeenCalledWith(0);
        });

        it('sets ETP to DEFAULT (1)', () => {
            const ref = React.createRef<GeckoViewRef>();
            renderer.act(() => {
                renderer.create(
                    <GeckoView ref={ref} source={{ uri: fixtures.urls.example }} />
                );
            });

            ref.current!.setEnhancedTrackingProtectionLevel(1);

            expect(NativeModules.GeckoViewModule.setEnhancedTrackingProtectionLevel).toHaveBeenCalledWith(1);
        });

        it('sets ETP to STRICT (2)', () => {
            const ref = React.createRef<GeckoViewRef>();
            renderer.act(() => {
                renderer.create(
                    <GeckoView ref={ref} source={{ uri: fixtures.urls.example }} />
                );
            });

            ref.current!.setEnhancedTrackingProtectionLevel(2);

            expect(NativeModules.GeckoViewModule.setEnhancedTrackingProtectionLevel).toHaveBeenCalledWith(2);
        });

        it('can change ETP level multiple times', () => {
            const ref = React.createRef<GeckoViewRef>();
            renderer.act(() => {
                renderer.create(
                    <GeckoView ref={ref} source={{ uri: fixtures.urls.example }} />
                );
            });

            ref.current!.setEnhancedTrackingProtectionLevel(0);
            ref.current!.setEnhancedTrackingProtectionLevel(1);
            ref.current!.setEnhancedTrackingProtectionLevel(2);
            ref.current!.setEnhancedTrackingProtectionLevel(1);

            expect(NativeModules.GeckoViewModule.setEnhancedTrackingProtectionLevel).toHaveBeenCalledTimes(4);
        });
    });

    // ========== Cookie Banner Mode ==========

    describe('Cookie Banner Mode', () => {
        it('exposes setCookieBannerMode method', () => {
            const ref = React.createRef<GeckoViewRef>();
            renderer.act(() => {
                renderer.create(
                    <GeckoView ref={ref} source={{ uri: fixtures.urls.example }} />
                );
            });

            expect(ref.current!.setCookieBannerMode).toBeDefined();
            expect(typeof ref.current!.setCookieBannerMode).toBe('function');
        });

        it('sets cookie banner mode to DISABLED (0)', () => {
            const ref = React.createRef<GeckoViewRef>();
            renderer.act(() => {
                renderer.create(
                    <GeckoView ref={ref} source={{ uri: fixtures.urls.example }} />
                );
            });

            ref.current!.setCookieBannerMode(0);

            expect(NativeModules.GeckoViewModule.setCookieBannerMode).toHaveBeenCalledWith(0);
        });

        it('sets cookie banner mode to REJECT (1)', () => {
            const ref = React.createRef<GeckoViewRef>();
            renderer.act(() => {
                renderer.create(
                    <GeckoView ref={ref} source={{ uri: fixtures.urls.example }} />
                );
            });

            ref.current!.setCookieBannerMode(1);

            expect(NativeModules.GeckoViewModule.setCookieBannerMode).toHaveBeenCalledWith(1);
        });

        it('sets cookie banner mode to REJECT_OR_ACCEPT (2)', () => {
            const ref = React.createRef<GeckoViewRef>();
            renderer.act(() => {
                renderer.create(
                    <GeckoView ref={ref} source={{ uri: fixtures.urls.example }} />
                );
            });

            ref.current!.setCookieBannerMode(2);

            expect(NativeModules.GeckoViewModule.setCookieBannerMode).toHaveBeenCalledWith(2);
        });
    });

    // ========== Content Blocking Prop ==========

    describe('Content Blocking Prop', () => {
        it('accepts contentBlockingEnabled prop', () => {
            renderer.act(() => {
                const tree = renderer.create(
                    <GeckoView
                        source={{ uri: fixtures.urls.example }}
                        contentBlockingEnabled={true}
                    />
                );
                expect(tree).toBeDefined();
            });
        });

        it('renders with contentBlockingEnabled=false', () => {
            renderer.act(() => {
                const tree = renderer.create(
                    <GeckoView
                        source={{ uri: fixtures.urls.example }}
                        contentBlockingEnabled={false}
                    />
                );
                expect(tree).toBeDefined();
            });
        });
    });

    // ========== Cookie Banner Prop ==========

    describe('Cookie Banner Prop', () => {
        it('accepts cookieBannerMode prop as "reject"', () => {
            renderer.act(() => {
                const tree = renderer.create(
                    <GeckoView
                        source={{ uri: fixtures.urls.example }}
                        cookieBannerMode="reject"
                    />
                );
                expect(tree).toBeDefined();
            });
        });

        it('accepts cookieBannerMode prop as "accept"', () => {
            renderer.act(() => {
                const tree = renderer.create(
                    <GeckoView
                        source={{ uri: fixtures.urls.example }}
                        cookieBannerMode="accept"
                    />
                );
                expect(tree).toBeDefined();
            });
        });

        it('accepts cookieBannerMode prop as "none"', () => {
            renderer.act(() => {
                const tree = renderer.create(
                    <GeckoView
                        source={{ uri: fixtures.urls.example }}
                        cookieBannerMode="none"
                    />
                );
                expect(tree).toBeDefined();
            });
        });
    });

    // ========== Load URL ==========

    describe('loadUrl Method', () => {
        it('exposes loadUrl method via ref', () => {
            const ref = React.createRef<GeckoViewRef>();
            renderer.act(() => {
                renderer.create(
                    <GeckoView ref={ref} source={{ uri: fixtures.urls.example }} />
                );
            });

            expect(ref.current!.loadUrl).toBeDefined();
            expect(typeof ref.current!.loadUrl).toBe('function');
        });

        it('loads new URL via loadUrl', () => {
            const ref = React.createRef<GeckoViewRef>();
            renderer.act(() => {
                renderer.create(
                    <GeckoView ref={ref} sessionKey="load-session" source={{ uri: fixtures.urls.example }} />
                );
            });

            ref.current!.loadUrl(fixtures.urls.mozilla);

            expect(NativeModules.GeckoViewModule.loadUrl).toHaveBeenCalledWith(
                'load-session',
                fixtures.urls.mozilla
            );
        });

        it('loads HTTPS URL', () => {
            const ref = React.createRef<GeckoViewRef>();
            renderer.act(() => {
                renderer.create(
                    <GeckoView ref={ref} sessionKey="https-session" source={{ uri: fixtures.urls.example }} />
                );
            });

            ref.current!.loadUrl('https://secure.example.com');

            expect(NativeModules.GeckoViewModule.loadUrl).toHaveBeenCalledWith(
                'https-session',
                'https://secure.example.com'
            );
        });

        it('loads file URL', () => {
            const ref = React.createRef<GeckoViewRef>();
            renderer.act(() => {
                renderer.create(
                    <GeckoView ref={ref} sessionKey="file-session" source={{ uri: fixtures.urls.example }} />
                );
            });

            ref.current!.loadUrl('file:///android_asset/index.html');

            expect(NativeModules.GeckoViewModule.loadUrl).toHaveBeenCalledWith(
                'file-session',
                'file:///android_asset/index.html'
            );
        });

        it('loads data URL', () => {
            const ref = React.createRef<GeckoViewRef>();
            renderer.act(() => {
                renderer.create(
                    <GeckoView ref={ref} sessionKey="data-session" source={{ uri: fixtures.urls.example }} />
                );
            });

            const dataUrl = 'data:text/html,<h1>Hello</h1>';
            ref.current!.loadUrl(dataUrl);

            expect(NativeModules.GeckoViewModule.loadUrl).toHaveBeenCalledWith(
                'data-session',
                dataUrl
            );
        });
    });

    // ========== Combined Settings ==========

    describe('Combined Settings', () => {
        it('can configure multiple settings at once', () => {
            const ref = React.createRef<GeckoViewRef>();
            renderer.act(() => {
                renderer.create(
                    <GeckoView ref={ref} sessionKey="combo-session" source={{ uri: fixtures.urls.example }} />
                );
            });

            // Configure all settings
            ref.current!.setEnhancedTrackingProtectionLevel(2);
            ref.current!.setCookieBannerMode(1);
            ref.current!.setUserAgent('Custom Agent');

            // All should have been called
            expect(NativeModules.GeckoViewModule.setEnhancedTrackingProtectionLevel).toHaveBeenCalledWith(2);
            expect(NativeModules.GeckoViewModule.setCookieBannerMode).toHaveBeenCalledWith(1);
            expect(NativeModules.GeckoViewModule.setUserAgent).toHaveBeenCalledWith('combo-session', 'Custom Agent');
        });

        it('maintains settings independence', () => {
            const ref = React.createRef<GeckoViewRef>();
            renderer.act(() => {
                renderer.create(
                    <GeckoView ref={ref} source={{ uri: fixtures.urls.example }} />
                );
            });

            // Change one setting
            ref.current!.setEnhancedTrackingProtectionLevel(0);

            // Other settings should not be affected
            expect(NativeModules.GeckoViewModule.setCookieBannerMode).not.toHaveBeenCalled();
            expect(NativeModules.GeckoViewModule.setUserAgent).not.toHaveBeenCalled();
        });
    });
});
