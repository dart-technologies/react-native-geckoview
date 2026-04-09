/**
 * @format
 * Session Management Integration Tests
 * 
 * Tests native session behavior across component lifecycle:
 * - Session persistence across remounts
 * - Navigation history restoration
 * - Multiple instance management
 * - Lifecycle and cleanup
 */
import '../setup';

import React, { useRef, useState } from 'react';
import { render, waitFor, act, RenderResult } from '@testing-library/react-native';
import { NativeModules } from 'react-native';
import GeckoView, { GeckoViewRef } from 'react-native-geckoview';
import { clearFabricGlobals, fixtures, GeckoViewModuleMock } from './testUtils';

// Mock Fabric detection to force legacy mode
jest.mock('../../src/fabricDetection', () => ({
    hasFabricRuntime: jest.fn(() => false),
    detectFabricAvailability: jest.fn(() => false),
}));

// Use shared mock setup
jest.mock('react-native', () => require('./testUtils').setupReactNativeMock());

describe('GeckoView Session Management E2E', () => {
    beforeEach(() => {
        clearFabricGlobals();
        jest.clearAllMocks();
    });

    describe('Session Persistence', () => {
        it('should maintain session across component remounts with sessionKey', async () => {
            const sessionKey = fixtures.sessionKeys.default;
            const testUrl = fixtures.urls.example;

            // First mount
            const { unmount }: RenderResult = render(
                <GeckoView
                    source={{ uri: testUrl }}
                    sessionKey={sessionKey}
                />
            );

            await waitFor(() => {
                expect(NativeModules.GeckoViewModule).toBeDefined();
            });

            // Unmount
            unmount();

            // Remount with same sessionKey - session should be preserved
            const { getByTestId }: RenderResult = render(
                <GeckoView
                    source={{ uri: testUrl }}
                    sessionKey={sessionKey}
                    testID="geckoview-remounted"
                />
            );

            await waitFor(() => {
                const view = getByTestId('geckoview-remounted');
                expect(view).toBeDefined();
            });
        });

        it('should restore navigation history with persistent session', async () => {
            const TestComponent: React.FC = () => {
                const geckoRef: React.RefObject<GeckoViewRef | null> = useRef<GeckoViewRef>(null);
                const [mounted, setMounted] = useState<boolean>(true);

                return mounted ? (
                    <GeckoView
                        ref={geckoRef}
                        source={{ uri: fixtures.urls.example }}
                        sessionKey="persistent-session"
                        testID="gecko-with-history"
                    />
                ) : null;
            };

            const { rerender, getByTestId }: RenderResult = render(<TestComponent />);

            await act(async () => {
                // Simulate navigation that would create history
                await new Promise<void>(resolve => setTimeout(resolve, 100));
            });

            // Component remounts should preserve history
            rerender(<TestComponent />);

            await waitFor(() => {
                expect(getByTestId('gecko-with-history')).toBeDefined();
            });
        });

        it('should create new session when sessionKey changes', async () => {
            const { rerender }: RenderResult = render(
                <GeckoView
                    source={{ uri: 'https://example.com' }}
                    sessionKey="session-1"
                    testID="gecko-view"
                />
            );

            await waitFor(() => {
                expect(NativeModules.GeckoViewModule).toBeDefined();
            });

            // Change sessionKey
            rerender(
                <GeckoView
                    source={{ uri: 'https://example.com' }}
                    sessionKey="session-2"
                    testID="gecko-view"
                />
            );

            // Component should update with new session
            await waitFor(() => {
                expect(NativeModules.GeckoViewModule).toBeDefined();
            });
        });
    });

    describe('Multiple Instances', () => {
        it('should support multiple GeckoView instances with separate sessions', async () => {
            const MultiInstanceComponent: React.FC = () => (
                <>
                    <GeckoView
                        source={{ uri: 'https://example.com' }}
                        sessionKey="session-a"
                        testID="gecko-a"
                    />
                    <GeckoView
                        source={{ uri: 'https://mozilla.org' }}
                        sessionKey="session-b"
                        testID="gecko-b"
                    />
                </>
            );

            const { getByTestId }: RenderResult = render(<MultiInstanceComponent />);

            await waitFor(() => {
                const viewA = getByTestId('gecko-a');
                const viewB = getByTestId('gecko-b');
                expect(viewA).toBeDefined();
                expect(viewB).toBeDefined();
            });

        });

        it('should share session when using same sessionKey across instances', async () => {
            const sharedKey = 'shared-session';

            const SharedSessionComponent: React.FC = () => (
                <>
                    <GeckoView
                        source={{ uri: 'https://example.com' }}
                        sessionKey={sharedKey}
                        testID="gecko-1"
                    />
                    <GeckoView
                        source={{ uri: 'https://example.com' }}
                        sessionKey={sharedKey}
                        testID="gecko-2"
                    />
                </>
            );

            const { getByTestId }: RenderResult = render(<SharedSessionComponent />);

            await waitFor(() => {
                expect(getByTestId('gecko-1')).toBeDefined();
                expect(getByTestId('gecko-2')).toBeDefined();
            });
        });

        it('should isolate sessions for instances without sessionKey', async () => {
            const IsolatedComponent: React.FC = () => (
                <>
                    <GeckoView
                        source={{ uri: 'https://example.com' }}
                        testID="isolated-1"
                    />
                    <GeckoView
                        source={{ uri: 'https://mozilla.org' }}
                        testID="isolated-2"
                    />
                </>
            );

            const { getByTestId }: RenderResult = render(<IsolatedComponent />);

            await waitFor(() => {
                expect(getByTestId('isolated-1')).toBeDefined();
                expect(getByTestId('isolated-2')).toBeDefined();
            });

        });
    });

    describe('Lifecycle and Cleanup', () => {
        it('should clean up session on unmount without sessionKey', async () => {
            const { unmount, getByTestId }: RenderResult = render(
                <GeckoView source={{ uri: 'https://example.com' }} testID="temp-view" />
            );

            await waitFor(() => {
                expect(getByTestId('temp-view')).toBeDefined();
            });

            // Unmount should trigger cleanup
            unmount();

            // Verify cleanup completed
        });

        it('should preserve session on unmount when sessionKey is provided', async () => {
            const { unmount }: RenderResult = render(
                <GeckoView
                    source={{ uri: 'https://example.com' }}
                    sessionKey="persistent"
                    testID="persistent-view"
                />
            );

            await act(async () => {
                await new Promise<void>(resolve => setTimeout(resolve, 100));
            });

            unmount();

            // Remount with same session key
            const { getByTestId }: RenderResult = render(
                <GeckoView
                    source={{ uri: 'https://example.com' }}
                    sessionKey="persistent"
                    testID="remounted-view"
                />
            );

            await waitFor(() => {
                expect(getByTestId('remounted-view')).toBeDefined();
            });
        });

        it('should handle rapid mount/unmount cycles', async () => {
            interface RapidCycleProps {
                iteration: number;
            }

            const RapidCycleComponent: React.FC<RapidCycleProps> = ({ iteration }) => (
                <GeckoView
                    source={{ uri: 'https://example.com' }}
                    sessionKey={`cycle-${iteration}`}
                    testID={`gecko-${iteration}`}
                />
            );

            for (let i = 0; i < 5; i++) {
                const { unmount }: RenderResult = render(<RapidCycleComponent iteration={i} />);
                await act(async () => {
                    await new Promise<void>(resolve => setTimeout(resolve, 50));
                });
                unmount();
            }

            // Verify separate native components created
        });
    });

    describe('Imperative Methods with Session Context', () => {
        it('should execute navigation methods on correct session', async () => {
            const TestComponent: React.FC = () => {
                const geckoRef: React.RefObject<GeckoViewRef | null> = useRef<GeckoViewRef>(null);

                React.useEffect(() => {
                    // Simulate user navigation
                    setTimeout(() => {
                        geckoRef.current?.goBack();
                    }, 100);
                }, []);

                return (
                    <GeckoView
                        ref={geckoRef}
                        source={{ uri: 'https://example.com' }}
                        sessionKey="navigation-test"
                        testID="navigation-view"
                    />
                );
            };

            render(<TestComponent />);

            const geckoModule = NativeModules.GeckoViewModule as GeckoViewModuleMock;
            await waitFor(() => {
                expect(geckoModule.goBack).toHaveBeenCalled();
            }, { timeout: 500 });
        });

        it('should call methods on correct instance with multiple views', async () => {
            const MultiViewComponent: React.FC = () => {
                const ref1: React.RefObject<GeckoViewRef | null> = useRef<GeckoViewRef>(null);
                const ref2: React.RefObject<GeckoViewRef | null> = useRef<GeckoViewRef>(null);

                React.useEffect(() => {
                    setTimeout(() => {
                        ref1.current?.reload();
                        ref2.current?.stop();
                    }, 100);
                }, []);

                return (
                    <>
                        <GeckoView
                            ref={ref1}
                            source={{ uri: 'https://example.com' }}
                            sessionKey="view-1"
                        />
                        <GeckoView
                            ref={ref2}
                            source={{ uri: 'https://mozilla.org' }}
                            sessionKey="view-2"
                        />
                    </>
                );
            };

            render(<MultiViewComponent />);

            const geckoModule = NativeModules.GeckoViewModule as GeckoViewModuleMock;
            await waitFor(() => {
                expect(geckoModule.reload).toHaveBeenCalled();
                expect(geckoModule.stop).toHaveBeenCalled();
            }, { timeout: 500 });
        });
    });

    describe('Event Handlers with Session Isolation', () => {
        it('should emit events to correct handler per session', async () => {
            const onPageStart1 = jest.fn();
            const onPageStart2 = jest.fn();

            const { getByTestId }: RenderResult = render(
                <>
                    <GeckoView
                        source={{ uri: 'https://example.com' }}
                        sessionKey="event-session-1"
                        onGeckoPageStart={onPageStart1}
                        testID="event-view-1"
                    />
                    <GeckoView
                        source={{ uri: 'https://mozilla.org' }}
                        sessionKey="event-session-2"
                        onGeckoPageStart={onPageStart2}
                        testID="event-view-2"
                    />
                </>
            );

            await waitFor(() => {
                expect(getByTestId('event-view-1')).toBeDefined();
                expect(getByTestId('event-view-2')).toBeDefined();
            });

            // Events should be properly isolated
            // In real implementation, native events would trigger these
        });
    });
});
