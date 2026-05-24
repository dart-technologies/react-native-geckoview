/**
 * Security regression tests for the JS-facing surface of react-native-geckoview.
 *
 * The native module enforces the allowlist / throws on invalid input; these
 * tests verify the TypeScript binding forwards the original arguments and
 * surfaces rejections back to the caller. End-to-end validation (that
 * `javascript:` URLs are actually dropped before reaching Gecko) lives in
 * `GeckoViewModuleTest.kt` / `GeckoViewNavigationDelegateTest.kt`.
 */
import React, { createRef } from 'react';
import { act, render } from '@testing-library/react-native';
import { NativeModules } from 'react-native';
import GeckoView, { configureGeckoViewSecurity } from 'react-native-geckoview';
import type { GeckoViewRef } from 'react-native-geckoview';
import { fixtures } from './testUtils';

const geckoModule = () =>
    NativeModules.GeckoViewModule as {
        loadUrl: jest.Mock;
        setEnhancedTrackingProtectionLevel: jest.Mock;
        setCookieBannerMode: jest.Mock;
        installWebExtension: jest.Mock;
        evaluateJavaScript: jest.Mock;
        configureSecurity: jest.Mock;
    };

const mountGecko = (): React.RefObject<GeckoViewRef | null> => {
    const ref = createRef<GeckoViewRef>();
    render(<GeckoView ref={ref} source={{ uri: fixtures.urls.example }} />);
    return ref;
};

describe('Security hardening (JS surface)', () => {
    beforeEach(() => {
        jest.clearAllMocks();
    });

    describe('configureGeckoViewSecurity', () => {
        it('forwards security options to the native module', async () => {
            geckoModule().configureSecurity.mockResolvedValueOnce(undefined);
            await expect(
                configureGeckoViewSecurity({
                    allowDrm: true,
                    allowAutoplay: false,
                    enableConsoleOutput: true,
                })
            ).resolves.toBeUndefined();
            expect(geckoModule().configureSecurity).toHaveBeenCalledWith({
                allowDrm: true,
                allowAutoplay: false,
                enableConsoleOutput: true,
            });
        });

        it('surfaces native late-configuration rejection', async () => {
            geckoModule().configureSecurity.mockRejectedValueOnce(
                new Error('GECKO_RUNTIME_ALREADY_INITIALIZED')
            );
            await expect(configureGeckoViewSecurity({ allowDrm: true })).rejects.toThrow(
                'GECKO_RUNTIME_ALREADY_INITIALIZED'
            );
        });
    });

    describe('loadUrl scheme forwarding', () => {
        it('forwards http URLs verbatim to the native module', () => {
            const ref = mountGecko();
            ref.current!.loadUrl('http://example.com/page');
            expect(geckoModule().loadUrl).toHaveBeenCalledWith(
                expect.any(String),
                'http://example.com/page'
            );
        });

        it('forwards https URLs verbatim to the native module', () => {
            const ref = mountGecko();
            ref.current!.loadUrl('https://example.com/page');
            expect(geckoModule().loadUrl).toHaveBeenCalledWith(
                expect.any(String),
                'https://example.com/page'
            );
        });

        it('forwards about:blank verbatim to the native module', () => {
            const ref = mountGecko();
            ref.current!.loadUrl('about:blank');
            expect(geckoModule().loadUrl).toHaveBeenCalledWith(expect.any(String), 'about:blank');
        });

        it('still forwards javascript: URLs to native (native rejects them)', () => {
            // The TS binding is intentionally permissive — the contract is that
            // the NATIVE module enforces the allowlist. This test documents that
            // the JS side does NOT pre-filter, so a future refactor that moves
            // enforcement to JS must keep the native filter in place too.
            const ref = mountGecko();
            ref.current!.loadUrl('javascript:alert(1)');
            expect(geckoModule().loadUrl).toHaveBeenCalledWith(
                expect.any(String),
                'javascript:alert(1)'
            );
        });
    });

    describe('setEnhancedTrackingProtectionLevel promise', () => {
        it('returns a Promise', () => {
            const ref = mountGecko();
            const result = ref.current!.setEnhancedTrackingProtectionLevel(2);
            expect(result).toBeInstanceOf(Promise);
        });

        it('forwards the level argument unchanged', async () => {
            geckoModule().setEnhancedTrackingProtectionLevel.mockResolvedValueOnce(undefined);
            const ref = mountGecko();
            await act(async () => {
                await ref.current!.setEnhancedTrackingProtectionLevel(1);
            });
            expect(geckoModule().setEnhancedTrackingProtectionLevel).toHaveBeenCalledWith(1);
        });

        it('surfaces native rejection to the caller', async () => {
            geckoModule().setEnhancedTrackingProtectionLevel.mockRejectedValueOnce(
                new Error('INVALID_ETP_LEVEL')
            );
            const ref = mountGecko();
            await expect(ref.current!.setEnhancedTrackingProtectionLevel(2)).rejects.toThrow(
                'INVALID_ETP_LEVEL'
            );
        });
    });

    describe('setCookieBannerMode promise', () => {
        it('returns a Promise', () => {
            const ref = mountGecko();
            const result = ref.current!.setCookieBannerMode(1);
            expect(result).toBeInstanceOf(Promise);
        });

        it('surfaces native INVALID_COOKIE_BANNER_MODE rejection to the caller', async () => {
            geckoModule().setCookieBannerMode.mockRejectedValueOnce(
                new Error('INVALID_COOKIE_BANNER_MODE')
            );
            const ref = mountGecko();
            await expect(ref.current!.setCookieBannerMode(0)).rejects.toThrow(
                'INVALID_COOKIE_BANNER_MODE'
            );
        });

        // Fix 3 contract: previously, ContentBlockingController swallowed
        // exceptions from `setCookieBannerMode` and the React promise resolved
        // even when the underlying call failed. Now the inner exception is
        // re-thrown and the JSModule wrapper rejects with COOKIE_BANNER_SET_FAILED.
        it('surfaces COOKIE_BANNER_SET_FAILED when the native call propagates a runtime error', async () => {
            geckoModule().setCookieBannerMode.mockRejectedValueOnce(
                Object.assign(
                    new Error('setCookieBannerMode requires GeckoView 108+'),
                    { code: 'COOKIE_BANNER_SET_FAILED' }
                )
            );
            const ref = mountGecko();
            await expect(ref.current!.setCookieBannerMode(2)).rejects.toThrow(
                /requires GeckoView 108\+/
            );
        });

        it('forwards the mode argument unchanged for each valid value', async () => {
            geckoModule().setCookieBannerMode.mockResolvedValue(undefined);
            const ref = mountGecko();
            for (const mode of [0, 1, 2] as const) {
                await act(async () => {
                    await ref.current!.setCookieBannerMode(mode);
                });
                expect(geckoModule().setCookieBannerMode).toHaveBeenCalledWith(mode);
            }
            expect(geckoModule().setCookieBannerMode).toHaveBeenCalledTimes(3);
        });
    });

    describe('installWebExtension surfaces asset-path rejection', () => {
        it('propagates INVALID_ASSET_PATH error to the caller', async () => {
            geckoModule().installWebExtension.mockRejectedValueOnce(
                Object.assign(new Error('Unsafe extension asset path'), {
                    code: 'INVALID_ASSET_PATH',
                })
            );
            const ref = mountGecko();
            await expect(ref.current!.installWebExtension('../../evil')).rejects.toThrow(
                'Unsafe extension asset path'
            );
        });

        it('resolves for a valid bundled extension name', async () => {
            geckoModule().installWebExtension.mockResolvedValueOnce(undefined);
            const ref = mountGecko();
            await expect(ref.current!.installWebExtension('spatial_navigation')).resolves.toBeUndefined();
            expect(geckoModule().installWebExtension).toHaveBeenCalledWith('spatial_navigation');
        });
    });

    describe('evaluateJavaScript contract', () => {
        it('forwards the raw code string to the trusted bridge', async () => {
            geckoModule().evaluateJavaScript.mockResolvedValueOnce('"ok"');
            const ref = mountGecko();
            const payload = '"); stealCookies(); eval("';
            await act(async () => {
                await ref.current!.evaluateJavaScript(payload);
            });
            expect(geckoModule().evaluateJavaScript).toHaveBeenCalledWith(
                expect.any(String),
                payload
            );
        });

        it('propagates EVAL_ERROR rejection from native', async () => {
            geckoModule().evaluateJavaScript.mockRejectedValueOnce(new Error('syntax error'));
            const ref = mountGecko();
            await expect(ref.current!.evaluateJavaScript('not valid JS {{{')).rejects.toThrow(
                'syntax error'
            );
        });
    });
});
