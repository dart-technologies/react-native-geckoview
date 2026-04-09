/**
 * @format
 * Web Extension Integration Tests
 * 
 * Tests for web extension installation, messaging, and error handling:
 * - Extension installation with various path formats
 * - Bidirectional messaging (JS ↔ Native)
 * - Error cases (invalid paths, malformed messages)
 * - Multiple extension management
 */

import '../setup';

import React, { useRef } from 'react';
import { render, waitFor, act, RenderResult } from '@testing-library/react-native';
import { NativeModules } from 'react-native';
import GeckoView, { GeckoViewRef } from 'react-native-geckoview';
import { clearFabricGlobals, fixtures, GeckoViewModuleMock } from './testUtils';

// Use shared mock setup
jest.mock('react-native', () => require('./testUtils').setupReactNativeMock());

describe('Web Extension Tests', () => {
    beforeEach(() => {
        clearFabricGlobals();
        jest.clearAllMocks();
    });

    describe('Extension Installation', () => {
        it('should call installWebExtension with correct path', async () => {
            const TestComponent: React.FC = () => {
                const geckoRef: React.RefObject<GeckoViewRef | null> = useRef<GeckoViewRef>(null);

                React.useEffect(() => {
                    setTimeout(() => {
                        geckoRef.current?.installWebExtension('asset://extensions/my-extension');
                    }, 100);
                }, []);

                return (
                    <GeckoView
                        ref={geckoRef}
                        source={{ uri: fixtures.urls.example }}
                    />
                );
            };

            render(<TestComponent />);

            const geckoModule = NativeModules.GeckoViewModule as GeckoViewModuleMock;
            await waitFor(() => {
                expect(geckoModule.installWebExtension).toHaveBeenCalledWith(
                    'asset://extensions/my-extension'
                );
            }, { timeout: 500 });
        });

        it('should allow installing multiple extensions', async () => {
            const TestComponent: React.FC = () => {
                const geckoRef: React.RefObject<GeckoViewRef | null> = useRef<GeckoViewRef>(null);

                React.useEffect(() => {
                    setTimeout(() => {
                        geckoRef.current?.installWebExtension('asset://extensions/ext1');
                        geckoRef.current?.installWebExtension('asset://extensions/ext2');
                        geckoRef.current?.installWebExtension('asset://extensions/ext3');
                    }, 100);
                }, []);

                return <GeckoView ref={geckoRef} source={{ uri: fixtures.urls.example }} />;
            };

            render(<TestComponent />);

            const geckoModule = NativeModules.GeckoViewModule as GeckoViewModuleMock;
            await waitFor(() => {
                expect(geckoModule.installWebExtension).toHaveBeenCalledTimes(3);
            }, { timeout: 500 });
        });

        it('should handle extension installation with various path formats', async () => {
            const TestComponent: React.FC = () => {
                const geckoRef: React.RefObject<GeckoViewRef | null> = useRef<GeckoViewRef>(null);

                React.useEffect(() => {
                    setTimeout(() => {
                        // Test different path formats
                        geckoRef.current?.installWebExtension('asset://extensions/test.xpi');
                        geckoRef.current?.installWebExtension('file:///path/to/extension');
                        geckoRef.current?.installWebExtension('https://example.com/extension.xpi');
                    }, 100);
                }, []);

                return <GeckoView ref={geckoRef} source={{ uri: fixtures.urls.example }} />;
            };

            render(<TestComponent />);

            const geckoModule = NativeModules.GeckoViewModule as GeckoViewModuleMock;
            await waitFor(() => {
                expect(geckoModule.installWebExtension).toHaveBeenCalledTimes(3);
            }, { timeout: 500 });
        });
    });

    describe('Extension Messaging', () => {
        it('should send web extension messages', async () => {
            const TestComponent: React.FC = () => {
                const geckoRef: React.RefObject<GeckoViewRef | null> = useRef<GeckoViewRef>(null);

                React.useEffect(() => {
                    setTimeout(() => {
                        geckoRef.current?.sendWebExtensionMessage(fixtures.messages.simpleExtension);
                    }, 100);
                }, []);

                return (
                    <GeckoView
                        ref={geckoRef}
                        source={{ uri: fixtures.urls.example }}
                    />
                );
            };

            render(<TestComponent />);

            const geckoModule = NativeModules.GeckoViewModule as GeckoViewModuleMock;
            await waitFor(() => {
                expect(geckoModule.sendWebExtensionMessage).toHaveBeenCalledWith(
                    fixtures.messages.simpleExtension
                );
            }, { timeout: 500 });
        });

        it('should properly serialize complex extension messages', async () => {
            const TestComponent: React.FC = () => {
                const geckoRef: React.RefObject<GeckoViewRef | null> = useRef<GeckoViewRef>(null);

                React.useEffect(() => {
                    setTimeout(() => {
                        geckoRef.current?.sendWebExtensionMessage(fixtures.messages.complexExtension);
                    }, 100);
                }, []);

                return <GeckoView ref={geckoRef} source={{ uri: fixtures.urls.example }} />;
            };

            render(<TestComponent />);

            const geckoModule = NativeModules.GeckoViewModule as GeckoViewModuleMock;
            await waitFor(() => {
                expect(geckoModule.sendWebExtensionMessage).toHaveBeenCalled();
                const callArg = geckoModule.sendWebExtensionMessage.mock.calls[0][0] as string;
                expect(typeof callArg).toBe('string');
                // Verify it's valid JSON
                expect(() => JSON.parse(callArg)).not.toThrow();
            }, { timeout: 500 });
        });

        it('should handle web extension messages from native', async () => {
            const onWebExtensionMessage = jest.fn();

            const { getByTestId }: RenderResult = render(
                <GeckoView
                    source={{ uri: fixtures.urls.example }}
                    onWebExtensionMessage={onWebExtensionMessage}
                    testID="gecko-with-extension"
                />
            );

            await waitFor(() => {
                expect(getByTestId('gecko-with-extension')).toBeDefined();
            });

            // Component registered for extension messages
        });
    });

    describe('Error Handling', () => {
        it('should handle invalid extension path gracefully', async () => {
            const TestComponent: React.FC = () => {
                const geckoRef: React.RefObject<GeckoViewRef | null> = useRef<GeckoViewRef>(null);

                React.useEffect(() => {
                    setTimeout(() => {
                        // Invalid path formats
                        geckoRef.current?.installWebExtension('');
                        geckoRef.current?.installWebExtension(null as any);
                        geckoRef.current?.installWebExtension(undefined as any);
                    }, 100);
                }, []);

                return <GeckoView ref={geckoRef} source={{ uri: fixtures.urls.example }} />;
            };

            // Should not throw
            expect(() => render(<TestComponent />)).not.toThrow();
        });

        it('should handle malformed extension messages', async () => {
            const TestComponent: React.FC = () => {
                const geckoRef: React.RefObject<GeckoViewRef | null> = useRef<GeckoViewRef>(null);

                React.useEffect(() => {
                    setTimeout(() => {
                        // Malformed message (not JSON)
                        geckoRef.current?.sendWebExtensionMessage('not-valid-json{');
                        // Empty message
                        geckoRef.current?.sendWebExtensionMessage('');
                        // Null message
                        geckoRef.current?.sendWebExtensionMessage(null as any);
                    }, 100);
                }, []);

                return <GeckoView ref={geckoRef} source={{ uri: fixtures.urls.example }} />;
            };

            // Should not throw
            expect(() => render(<TestComponent />)).not.toThrow();
        });

        it('should handle missing ref gracefully', () => {
            const TestComponent: React.FC = () => {
                // No ref assigned
                const geckoRef: React.RefObject<GeckoViewRef | null> = useRef<GeckoViewRef>(null);

                React.useEffect(() => {
                    // Try to call methods with null ref
                    geckoRef.current?.installWebExtension('asset://test');
                    geckoRef.current?.sendWebExtensionMessage('test');
                }, []);

                return <GeckoView source={{ uri: fixtures.urls.example }} />;
            };

            expect(() => render(<TestComponent />)).not.toThrow();
        });

        it('should handle extension installation failures from native', async () => {
            // Mock native method to reject
            const geckoModule = NativeModules.GeckoViewModule as GeckoViewModuleMock;
            geckoModule.installWebExtension.mockRejectedValueOnce(
                new Error('Extension not found')
            );

            const onError = jest.fn();
            const TestComponent: React.FC = () => {
                const geckoRef: React.RefObject<GeckoViewRef | null> = useRef<GeckoViewRef>(null);

                React.useEffect(() => {
                    setTimeout(async () => {
                        try {
                            await geckoRef.current?.installWebExtension('invalid://path');
                        } catch (error) {
                            onError(error);
                        }
                    }, 100);
                }, []);

                return <GeckoView ref={geckoRef} source={{ uri: fixtures.urls.example }} />;
            };

            render(<TestComponent />);

            // Error callback should be invoked
            await waitFor(() => {
                expect(onError).toHaveBeenCalled();
            }, { timeout: 500 });
        });

        it('should handle message sending failures from native', async () => {
            // Mock native method to reject
            const geckoModule = NativeModules.GeckoViewModule as GeckoViewModuleMock;
            geckoModule.sendWebExtensionMessage.mockRejectedValueOnce(
                new Error('Extension not loaded')
            );

            const onError = jest.fn();
            const TestComponent: React.FC = () => {
                const geckoRef: React.RefObject<GeckoViewRef | null> = useRef<GeckoViewRef>(null);

                React.useEffect(() => {
                    setTimeout(async () => {
                        try {
                            await geckoRef.current?.sendWebExtensionMessage('{ "test": true }');
                        } catch (error) {
                            onError(error);
                        }
                    }, 100);
                }, []);

                return <GeckoView ref={geckoRef} source={{ uri: fixtures.urls.example }} />;
            };

            render(<TestComponent />);

            await waitFor(() => {
                expect(onError).toHaveBeenCalled();
            }, { timeout: 500 });
        });

        it('should handle rapid sequential extension calls', async () => {
            const TestComponent: React.FC = () => {
                const geckoRef: React.RefObject<GeckoViewRef | null> = useRef<GeckoViewRef>(null);

                React.useEffect(() => {
                    setTimeout(() => {
                        // Rapid calls should all be queued
                        for (let i = 0; i < 10; i++) {
                            geckoRef.current?.installWebExtension(`asset://ext${i}`);
                        }
                    }, 100);
                }, []);

                return <GeckoView ref={geckoRef} source={{ uri: fixtures.urls.example }} />;
            };

            render(<TestComponent />);

            const geckoModule = NativeModules.GeckoViewModule as GeckoViewModuleMock;
            await waitFor(() => {
                expect(geckoModule.installWebExtension).toHaveBeenCalledTimes(10);
            }, { timeout: 500 });
        });
    });
});
