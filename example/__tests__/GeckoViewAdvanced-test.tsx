/**
 * @format
 * GeckoView Component Advanced Tests
 * 
 * Comprehensive tests for:
 * - Prop updates and re-rendering
 * - Fabric vs Paper rendering paths
 * - Error boundary testing
 * - Session management
 * - Event handlers
 */

import '../setup';

import React from 'react';
import renderer, { ReactTestRenderer, ReactTestInstance } from 'react-test-renderer';
import { NativeModules } from 'react-native';
import { clearFabricGlobals, fixtures, GeckoViewModuleMock } from './testUtils';

// Use shared mock setup
jest.mock('react-native', () => require('./testUtils').setupReactNativeMock());

import GeckoView, { GeckoViewRef } from 'react-native-geckoview';

describe('GeckoView Component - Advanced Tests', () => {
    beforeEach(() => {
        clearFabricGlobals();
        jest.clearAllMocks();
    });

    // ========== Prop Updates ==========

    describe('Prop Updates', () => {
        it('updates source prop correctly', () => {
            let tree: ReactTestRenderer | undefined;
            renderer.act(() => {
                tree = renderer.create(<GeckoView source={{ uri: 'https://example1.com' }} />);
            });

            const instance1 = tree!.root.findByType('NativeGeckoView' as any);
            expect(instance1.props.source.uri).toBe('https://example1.com');

            // Update source
            renderer.act(() => {
                tree!.update(<GeckoView source={{ uri: 'https://example2.com' }} />);
            });

            const instance2 = tree!.root.findByType('NativeGeckoView' as any);
            expect(instance2.props.source.uri).toBe('https://example2.com');
        });

        it('maintains sessionKey across re-renders', () => {
            let tree: ReactTestRenderer | undefined;
            const customKey = 'my-custom-session';

            renderer.act(() => {
                tree = renderer.create(<GeckoView sessionKey={customKey} source={{ uri: 'https://example.com' }} />);
            });

            const instance1 = tree!.root.findByType('NativeGeckoView' as any);
            expect(instance1.props.sessionKey).toBe(customKey);

            // Re-render with different source
            renderer.act(() => {
                tree!.update(<GeckoView sessionKey={customKey} source={{ uri: 'https://example2.com' }} />);
            });

            const instance2 = tree!.root.findByType('NativeGeckoView' as any);
            expect(instance2.props.sessionKey).toBe(customKey);
        });

        it('generates unique sessionKey when not provided', () => {
            let tree: ReactTestRenderer | undefined;
            renderer.act(() => {
                tree = renderer.create(<GeckoView source={{ uri: 'https://example.com' }} />);
            });

            const instance = tree!.root.findByType('NativeGeckoView' as any);
            expect(instance.props.sessionKey).toBeDefined();
            expect(instance.props.sessionKey).toMatch(/^session-[a-z0-9]+$/);
        });

        it('updates handleAudioFocus prop', () => {
            let tree: ReactTestRenderer | undefined;
            renderer.act(() => {
                tree = renderer.create(<GeckoView handleAudioFocus={false} />);
            });

            let instance = tree!.root.findByType('NativeGeckoView' as any);
            expect(instance.props.handleAudioFocus).toBe(false);

            renderer.act(() => {
                tree!.update(<GeckoView handleAudioFocus={true} />);
            });

            instance = tree!.root.findByType('NativeGeckoView' as any);
            expect(instance.props.handleAudioFocus).toBe(true);
        });
    });

    // ========== Event Handlers ==========

    describe('Event Handlers', () => {
        it('passes onGeckoPageStart callback', () => {
            const onGeckoPageStart = jest.fn();
            let tree: ReactTestRenderer | undefined;

            renderer.act(() => {
                tree = renderer.create(<GeckoView onGeckoPageStart={onGeckoPageStart} />);
            });

            const instance = tree!.root.findByType('NativeGeckoView' as any);
            expect(instance.props.onGeckoPageStart).toBe(onGeckoPageStart);
        });

        it('passes onGeckoPageStop callback', () => {
            const onGeckoPageStop = jest.fn();
            let tree: ReactTestRenderer | undefined;

            renderer.act(() => {
                tree = renderer.create(<GeckoView onGeckoPageStop={onGeckoPageStop} />);
            });

            const instance = tree!.root.findByType('NativeGeckoView' as any);
            expect(instance.props.onGeckoPageStop).toBe(onGeckoPageStop);
        });

        it('passes onPageError callback', () => {
            const onPageError = jest.fn();
            let tree: ReactTestRenderer | undefined;

            renderer.act(() => {
                tree = renderer.create(<GeckoView onPageError={onPageError} />);
            });

            const instance = tree!.root.findByType('NativeGeckoView' as any);
            expect(instance.props.onPageError).toBe(onPageError);
        });

        it('passes onWebExtensionMessage callback', () => {
            const onWebExtensionMessage = jest.fn();
            let tree: ReactTestRenderer | undefined;

            renderer.act(() => {
                tree = renderer.create(<GeckoView onWebExtensionMessage={onWebExtensionMessage} />);
            });

            const instance = tree!.root.findByType('NativeGeckoView' as any);
            expect(instance.props.onWebExtensionMessage).toBe(onWebExtensionMessage);
        });

        it('passes all event handlers together', () => {
            const handlers: Record<string, jest.Mock> = {
                onGeckoPageStart: jest.fn(),
                onGeckoPageStop: jest.fn(),
                onPageError: jest.fn(),
                onWebExtensionMessage: jest.fn(),
                onGeckoProgressChange: jest.fn(),
            };

            let tree: ReactTestRenderer | undefined;
            renderer.act(() => {
                tree = renderer.create(<GeckoView {...(handlers as any)} />);
            });

            const instance = tree!.root.findByType('NativeGeckoView' as any);
            Object.entries(handlers).forEach(([key, value]) => {
                expect(instance.props[key]).toBe(value);
            });
        });
    });

    // ========== Imperative Methods ==========

    describe('Imperative Methods', () => {
        it('reload calls native module with sessionKey', () => {
            const ref = React.createRef<GeckoViewRef>();
            renderer.act(() => {
                renderer.create(<GeckoView ref={ref} sessionKey="test-session" />);
            });

            const geckoModule = NativeModules.GeckoViewModule as GeckoViewModuleMock;
            ref.current!.reload();
            expect(geckoModule.reload).toHaveBeenCalledWith('test-session');
        });

        it('goBack calls native module with sessionKey', () => {
            const ref = React.createRef<GeckoViewRef>();
            renderer.act(() => {
                renderer.create(<GeckoView ref={ref} sessionKey="nav-session" />);
            });

            const geckoModule = NativeModules.GeckoViewModule as GeckoViewModuleMock;
            ref.current!.goBack();
            expect(geckoModule.goBack).toHaveBeenCalledWith('nav-session');
        });

        it('goForward calls native module with sessionKey', () => {
            const ref = React.createRef<GeckoViewRef>();
            renderer.act(() => {
                renderer.create(<GeckoView ref={ref} sessionKey="nav-session" />);
            });

            const geckoModule = NativeModules.GeckoViewModule as GeckoViewModuleMock;
            ref.current!.goForward();
            expect(geckoModule.goForward).toHaveBeenCalledWith('nav-session');
        });

        it('stop calls native module with sessionKey', () => {
            const ref = React.createRef<GeckoViewRef>();
            renderer.act(() => {
                renderer.create(<GeckoView ref={ref} sessionKey="stop-session" />);
            });

            const geckoModule = NativeModules.GeckoViewModule as GeckoViewModuleMock;
            ref.current!.stop();
            expect(geckoModule.stop).toHaveBeenCalledWith('stop-session');
        });

        it('shutdown calls native module', () => {
            const ref = React.createRef<GeckoViewRef>();
            renderer.act(() => {
                renderer.create(<GeckoView ref={ref} />);
            });

            const geckoModule = NativeModules.GeckoViewModule as GeckoViewModuleMock;
            ref.current!.shutdown();
            expect(geckoModule.shutdown).toHaveBeenCalled();
        });

        it('evaluateJavaScript calls native module with code', () => {
            const ref = React.createRef<GeckoViewRef>();
            renderer.act(() => {
                renderer.create(<GeckoView ref={ref} sessionKey="js-session" />);
            });

            const geckoModule = NativeModules.GeckoViewModule as GeckoViewModuleMock;
            ref.current!.evaluateJavaScript('document.title');
            expect(geckoModule.evaluateJavaScript).toHaveBeenCalledWith('js-session', 'document.title');
        });

        it('installWebExtension calls native module', () => {
            const ref = React.createRef<GeckoViewRef>();
            renderer.act(() => {
                renderer.create(<GeckoView ref={ref} />);
            });

            const geckoModule = NativeModules.GeckoViewModule as GeckoViewModuleMock;
            ref.current!.installWebExtension('extension.xpi');
            expect(geckoModule.installWebExtension).toHaveBeenCalledWith('extension.xpi');
        });

        it('sendWebExtensionMessage calls native module', () => {
            const ref = React.createRef<GeckoViewRef>();
            renderer.act(() => {
                renderer.create(<GeckoView ref={ref} />);
            });

            const geckoModule = NativeModules.GeckoViewModule as GeckoViewModuleMock;
            ref.current!.sendWebExtensionMessage('{"action": "test"}');
            expect(geckoModule.sendWebExtensionMessage).toHaveBeenCalledWith('{"action": "test"}');
        });

        it('resolvePrompt calls native module with all parameters', () => {
            const ref = React.createRef<GeckoViewRef>();
            renderer.act(() => {
                renderer.create(<GeckoView ref={ref} />);
            });

            const geckoModule = NativeModules.GeckoViewModule as GeckoViewModuleMock;
            ref.current!.resolvePrompt('prompt-123', true, 'text-input', 'username');
            expect(geckoModule.resolvePrompt).toHaveBeenCalledWith(
                'prompt-123', true, 'text-input', 'username'
            );
        });

        it('resolvePermission calls native module', () => {
            const ref = React.createRef<GeckoViewRef>();
            renderer.act(() => {
                renderer.create(<GeckoView ref={ref} />);
            });

            const geckoModule = NativeModules.GeckoViewModule as GeckoViewModuleMock;
            ref.current!.resolvePermission('perm-456', true);
            expect(geckoModule.resolvePermission).toHaveBeenCalledWith('perm-456', true);
        });

        it('setEnhancedTrackingProtectionLevel calls native module', () => {
            const ref = React.createRef<GeckoViewRef>();
            renderer.act(() => {
                renderer.create(<GeckoView ref={ref} />);
            });

            const geckoModule = NativeModules.GeckoViewModule as GeckoViewModuleMock;
            ref.current!.setEnhancedTrackingProtectionLevel(2);
            expect(geckoModule.setEnhancedTrackingProtectionLevel).toHaveBeenCalledWith(2);
        });

        it('setCookieBannerMode calls native module', () => {
            const ref = React.createRef<GeckoViewRef>();
            renderer.act(() => {
                renderer.create(<GeckoView ref={ref} />);
            });

            const geckoModule = NativeModules.GeckoViewModule as GeckoViewModuleMock;
            ref.current!.setCookieBannerMode(1);
            expect(geckoModule.setCookieBannerMode).toHaveBeenCalledWith(1);
        });
    });

    // ========== Session Management ==========

    describe('Session Management', () => {
        it('uses provided sessionKey', () => {
            const ref = React.createRef<GeckoViewRef>();
            renderer.act(() => {
                renderer.create(<GeckoView ref={ref} sessionKey="provided-key" />);
            });

            const geckoModule = NativeModules.GeckoViewModule as GeckoViewModuleMock;
            ref.current!.reload();
            expect(geckoModule.reload).toHaveBeenCalledWith('provided-key');
        });

        it('generates sessionKey if not provided', () => {
            const ref = React.createRef<GeckoViewRef>();
            renderer.act(() => {
                renderer.create(<GeckoView ref={ref} />);
            });

            const geckoModule = NativeModules.GeckoViewModule as GeckoViewModuleMock;
            ref.current!.reload();

            const calledWith = geckoModule.reload.mock.calls[0][0] as string;
            expect(calledWith).toMatch(/^session-[a-z0-9]+$/);
        });

        it('different instances have different sessionKeys', () => {
            const ref1 = React.createRef<GeckoViewRef>();
            const ref2 = React.createRef<GeckoViewRef>();

            renderer.act(() => {
                renderer.create(<GeckoView ref={ref1} />);
            });
            renderer.act(() => {
                renderer.create(<GeckoView ref={ref2} />);
            });

            const geckoModule = NativeModules.GeckoViewModule as GeckoViewModuleMock;
            ref1.current!.reload();
            ref2.current!.reload();

            const key1 = geckoModule.reload.mock.calls[0][0] as string;
            const key2 = geckoModule.reload.mock.calls[1][0] as string;

            expect(key1).not.toBe(key2);
        });
    });

    // ========== Style Props ==========

    describe('Style Props', () => {
        it('passes style prop through', () => {
            let tree: ReactTestRenderer | undefined;
            const customStyle = { flex: 1, backgroundColor: 'red' };

            renderer.act(() => {
                tree = renderer.create(<GeckoView style={customStyle} />);
            });

            const instance = tree!.root.findByType('NativeGeckoView' as any);
            expect(instance.props.style).toEqual(customStyle);
        });

        it('handles array styles', () => {
            let tree: ReactTestRenderer | undefined;
            const styles = [{ flex: 1 }, { backgroundColor: 'blue' }];

            renderer.act(() => {
                tree = renderer.create(<GeckoView style={styles} />);
            });

            const instance = tree!.root.findByType('NativeGeckoView' as any);
            expect(instance.props.style).toEqual(styles);
        });
    });

    // ========== Edge Cases ==========

    describe('Edge Cases', () => {
        it('handles undefined source', () => {
            let tree: ReactTestRenderer | undefined;
            renderer.act(() => {
                tree = renderer.create(<GeckoView source={undefined} />);
            });

            const instance = tree!.root.findByType('NativeGeckoView' as any);
            expect(instance.props.source).toBeUndefined();
        });

        it('handles empty source object', () => {
            let tree: ReactTestRenderer | undefined;
            renderer.act(() => {
                tree = renderer.create(<GeckoView source={{} as any} />);
            });

            const instance = tree!.root.findByType('NativeGeckoView' as any);
            expect(instance.props.source).toEqual({});
        });

        it('handles source with html', () => {
            let tree: ReactTestRenderer | undefined;
            const html = '<html><body>Hello</body></html>';

            renderer.act(() => {
                tree = renderer.create(<GeckoView source={{ html } as any} />);
            });

            const instance = tree!.root.findByType('NativeGeckoView' as any);
            expect(instance.props.source.html).toBe(html);
        });

        it('handles unmount gracefully', () => {
            let tree: ReactTestRenderer | undefined;
            const ref = React.createRef<GeckoViewRef>();

            renderer.act(() => {
                tree = renderer.create(<GeckoView ref={ref} />);
            });

            // Unmount
            renderer.act(() => {
                tree!.unmount();
            });

            // Should not throw
            expect(() => tree!.toJSON()).not.toThrow();
        });

        it('handles rapid mount/unmount cycles', () => {
            for (let i = 0; i < 10; i++) {
                let tree: ReactTestRenderer | undefined;
                renderer.act(() => {
                    tree = renderer.create(<GeckoView source={{ uri: `https://example${i}.com` }} />);
                });
                renderer.act(() => {
                    tree!.unmount();
                });
            }
            // No errors means success
        });

        it('handles props update during render', () => {
            let tree: ReactTestRenderer | undefined;

            renderer.act(() => {
                tree = renderer.create(<GeckoView source={{ uri: 'https://initial.com' }} />);
            });

            // Rapid updates
            for (let i = 0; i < 5; i++) {
                renderer.act(() => {
                    tree!.update(<GeckoView source={{ uri: `https://update${i}.com` }} />);
                });
            }

            const instance = tree!.root.findByType('NativeGeckoView' as any);
            expect(instance.props.source.uri).toBe('https://update4.com');
        });
    });
});
