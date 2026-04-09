/**
 * @format
 * History / Navigation Tests
 * 
 * Tests for history navigation functionality:
 * - Back/Forward navigation
 * - Reload functionality
 * - Stop loading
 * - URL loading via ref methods
 */

import '../setup';

import React from 'react';
import renderer from 'react-test-renderer';
import { NativeModules } from 'react-native';
import { clearFabricGlobals, fixtures } from './testUtils';

// Use shared mock setup
jest.mock('react-native', () => require('./testUtils').setupReactNativeMock());

import GeckoView, { GeckoViewRef } from 'react-native-geckoview';

describe('History Navigation Tests', () => {
    beforeEach(() => {
        clearFabricGlobals();
        jest.clearAllMocks();
    });

    // ========== Back Navigation ==========

    describe('goBack', () => {
        it('exposes goBack method via ref', () => {
            const ref = React.createRef<GeckoViewRef>();
            renderer.act(() => {
                renderer.create(
                    <GeckoView ref={ref} source={{ uri: fixtures.urls.example }} />
                );
            });

            expect(ref.current).not.toBeNull();
            expect(ref.current!.goBack).toBeDefined();
            expect(typeof ref.current!.goBack).toBe('function');
        });

        it('calls native goBack with session key', () => {
            const ref = React.createRef<GeckoViewRef>();
            renderer.act(() => {
                renderer.create(
                    <GeckoView ref={ref} sessionKey="back-session" source={{ uri: fixtures.urls.example }} />
                );
            });

            ref.current!.goBack();

            expect(NativeModules.GeckoViewModule.goBack).toHaveBeenCalledWith('back-session');
        });

        it('can call goBack multiple times', () => {
            const ref = React.createRef<GeckoViewRef>();
            renderer.act(() => {
                renderer.create(
                    <GeckoView ref={ref} sessionKey="multi-back" source={{ uri: fixtures.urls.example }} />
                );
            });

            ref.current!.goBack();
            ref.current!.goBack();
            ref.current!.goBack();

            expect(NativeModules.GeckoViewModule.goBack).toHaveBeenCalledTimes(3);
        });
    });

    // ========== Forward Navigation ==========

    describe('goForward', () => {
        it('exposes goForward method via ref', () => {
            const ref = React.createRef<GeckoViewRef>();
            renderer.act(() => {
                renderer.create(
                    <GeckoView ref={ref} source={{ uri: fixtures.urls.example }} />
                );
            });

            expect(ref.current!.goForward).toBeDefined();
            expect(typeof ref.current!.goForward).toBe('function');
        });

        it('calls native goForward with session key', () => {
            const ref = React.createRef<GeckoViewRef>();
            renderer.act(() => {
                renderer.create(
                    <GeckoView ref={ref} sessionKey="forward-session" source={{ uri: fixtures.urls.example }} />
                );
            });

            ref.current!.goForward();

            expect(NativeModules.GeckoViewModule.goForward).toHaveBeenCalledWith('forward-session');
        });

        it('can call goForward multiple times', () => {
            const ref = React.createRef<GeckoViewRef>();
            renderer.act(() => {
                renderer.create(
                    <GeckoView ref={ref} sessionKey="multi-forward" source={{ uri: fixtures.urls.example }} />
                );
            });

            ref.current!.goForward();
            ref.current!.goForward();

            expect(NativeModules.GeckoViewModule.goForward).toHaveBeenCalledTimes(2);
        });
    });

    // ========== Reload ==========

    describe('reload', () => {
        it('exposes reload method via ref', () => {
            const ref = React.createRef<GeckoViewRef>();
            renderer.act(() => {
                renderer.create(
                    <GeckoView ref={ref} source={{ uri: fixtures.urls.example }} />
                );
            });

            expect(ref.current!.reload).toBeDefined();
            expect(typeof ref.current!.reload).toBe('function');
        });

        it('calls native reload with session key', () => {
            const ref = React.createRef<GeckoViewRef>();
            renderer.act(() => {
                renderer.create(
                    <GeckoView ref={ref} sessionKey="reload-session" source={{ uri: fixtures.urls.example }} />
                );
            });

            ref.current!.reload();

            expect(NativeModules.GeckoViewModule.reload).toHaveBeenCalledWith('reload-session');
        });

        it('can reload multiple times', () => {
            const ref = React.createRef<GeckoViewRef>();
            renderer.act(() => {
                renderer.create(
                    <GeckoView ref={ref} sessionKey="multi-reload" source={{ uri: fixtures.urls.example }} />
                );
            });

            ref.current!.reload();
            ref.current!.reload();

            expect(NativeModules.GeckoViewModule.reload).toHaveBeenCalledTimes(2);
        });
    });

    // ========== Stop Loading ==========

    describe('stop', () => {
        it('exposes stop method via ref', () => {
            const ref = React.createRef<GeckoViewRef>();
            renderer.act(() => {
                renderer.create(
                    <GeckoView ref={ref} source={{ uri: fixtures.urls.example }} />
                );
            });

            expect(ref.current!.stop).toBeDefined();
            expect(typeof ref.current!.stop).toBe('function');
        });

        it('calls native stop with session key', () => {
            const ref = React.createRef<GeckoViewRef>();
            renderer.act(() => {
                renderer.create(
                    <GeckoView ref={ref} sessionKey="stop-session" source={{ uri: fixtures.urls.example }} />
                );
            });

            ref.current!.stop();

            expect(NativeModules.GeckoViewModule.stop).toHaveBeenCalledWith('stop-session');
        });
    });

    // ========== Shutdown ==========

    describe('shutdown', () => {
        it('exposes shutdown method via ref', () => {
            const ref = React.createRef<GeckoViewRef>();
            renderer.act(() => {
                renderer.create(
                    <GeckoView ref={ref} source={{ uri: fixtures.urls.example }} />
                );
            });

            expect(ref.current!.shutdown).toBeDefined();
            expect(typeof ref.current!.shutdown).toBe('function');
        });

        it('calls native shutdown', () => {
            const ref = React.createRef<GeckoViewRef>();
            renderer.act(() => {
                renderer.create(
                    <GeckoView ref={ref} source={{ uri: fixtures.urls.example }} />
                );
            });

            ref.current!.shutdown();

            expect(NativeModules.GeckoViewModule.shutdown).toHaveBeenCalled();
        });
    });

    // ========== Load URL ==========

    describe('loadUrl', () => {
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

        it('calls native loadUrl with session key and URL', () => {
            const ref = React.createRef<GeckoViewRef>();
            renderer.act(() => {
                renderer.create(
                    <GeckoView ref={ref} sessionKey="url-session" source={{ uri: fixtures.urls.example }} />
                );
            });

            ref.current!.loadUrl(fixtures.urls.mozilla);

            expect(NativeModules.GeckoViewModule.loadUrl).toHaveBeenCalledWith(
                'url-session',
                fixtures.urls.mozilla
            );
        });

        it('loads different URL types', () => {
            const ref = React.createRef<GeckoViewRef>();
            renderer.act(() => {
                renderer.create(
                    <GeckoView ref={ref} sessionKey="types-session" source={{ uri: fixtures.urls.example }} />
                );
            });

            // HTTPS
            ref.current!.loadUrl('https://example.com');
            expect(NativeModules.GeckoViewModule.loadUrl).toHaveBeenLastCalledWith(
                'types-session',
                'https://example.com'
            );

            // HTTP
            ref.current!.loadUrl('http://example.com');
            expect(NativeModules.GeckoViewModule.loadUrl).toHaveBeenLastCalledWith(
                'types-session',
                'http://example.com'
            );

            // File
            ref.current!.loadUrl('file:///path/to/file.html');
            expect(NativeModules.GeckoViewModule.loadUrl).toHaveBeenLastCalledWith(
                'types-session',
                'file:///path/to/file.html'
            );
        });
    });

    // ========== Navigation Sequences ==========

    describe('Navigation Sequences', () => {
        it('handles back then forward sequence', () => {
            const ref = React.createRef<GeckoViewRef>();
            renderer.act(() => {
                renderer.create(
                    <GeckoView ref={ref} sessionKey="seq-session" source={{ uri: fixtures.urls.example }} />
                );
            });

            ref.current!.goBack();
            ref.current!.goForward();

            expect(NativeModules.GeckoViewModule.goBack).toHaveBeenCalledWith('seq-session');
            expect(NativeModules.GeckoViewModule.goForward).toHaveBeenCalledWith('seq-session');
        });

        it('handles load URL then reload sequence', () => {
            const ref = React.createRef<GeckoViewRef>();
            renderer.act(() => {
                renderer.create(
                    <GeckoView ref={ref} sessionKey="load-reload" source={{ uri: fixtures.urls.example }} />
                );
            });

            ref.current!.loadUrl(fixtures.urls.mozilla);
            ref.current!.reload();

            expect(NativeModules.GeckoViewModule.loadUrl).toHaveBeenCalled();
            expect(NativeModules.GeckoViewModule.reload).toHaveBeenCalled();
        });

        it('handles stop then reload sequence', () => {
            const ref = React.createRef<GeckoViewRef>();
            renderer.act(() => {
                renderer.create(
                    <GeckoView ref={ref} sessionKey="stop-reload" source={{ uri: fixtures.urls.example }} />
                );
            });

            ref.current!.stop();
            ref.current!.reload();

            expect(NativeModules.GeckoViewModule.stop).toHaveBeenCalled();
            expect(NativeModules.GeckoViewModule.reload).toHaveBeenCalled();
        });

        it('handles rapid navigation calls', () => {
            const ref = React.createRef<GeckoViewRef>();
            renderer.act(() => {
                renderer.create(
                    <GeckoView ref={ref} sessionKey="rapid-session" source={{ uri: fixtures.urls.example }} />
                );
            });

            // Simulate rapid user interaction
            ref.current!.goBack();
            ref.current!.goBack();
            ref.current!.goForward();
            ref.current!.reload();
            ref.current!.goForward();

            expect(NativeModules.GeckoViewModule.goBack).toHaveBeenCalledTimes(2);
            expect(NativeModules.GeckoViewModule.goForward).toHaveBeenCalledTimes(2);
            expect(NativeModules.GeckoViewModule.reload).toHaveBeenCalledTimes(1);
        });
    });

    // ========== Multiple Instances ==========

    describe('Multiple Instances', () => {
        it('handles navigation on different sessions independently', () => {
            const ref1 = React.createRef<GeckoViewRef>();
            const ref2 = React.createRef<GeckoViewRef>();

            renderer.act(() => {
                renderer.create(
                    <GeckoView ref={ref1} sessionKey="session-1" source={{ uri: fixtures.urls.example }} />
                );
                renderer.create(
                    <GeckoView ref={ref2} sessionKey="session-2" source={{ uri: fixtures.urls.mozilla }} />
                );
            });

            ref1.current!.goBack();
            ref2.current!.goForward();

            expect(NativeModules.GeckoViewModule.goBack).toHaveBeenCalledWith('session-1');
            expect(NativeModules.GeckoViewModule.goForward).toHaveBeenCalledWith('session-2');
        });

        it('reloads specific session only', () => {
            const ref1 = React.createRef<GeckoViewRef>();
            const ref2 = React.createRef<GeckoViewRef>();

            renderer.act(() => {
                renderer.create(
                    <GeckoView ref={ref1} sessionKey="reload-1" source={{ uri: fixtures.urls.example }} />
                );
                renderer.create(
                    <GeckoView ref={ref2} sessionKey="reload-2" source={{ uri: fixtures.urls.mozilla }} />
                );
            });

            ref1.current!.reload();

            expect(NativeModules.GeckoViewModule.reload).toHaveBeenCalledWith('reload-1');
            expect(NativeModules.GeckoViewModule.reload).not.toHaveBeenCalledWith('reload-2');
        });
    });
});
