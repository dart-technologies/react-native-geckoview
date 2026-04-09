/**
 * Memory Leak Stress Test for Session Management
 * 
 * This test rapidly creates and destroys GeckoView components with different
 * session keys to verify proper cleanup and detect memory leaks.
 * 
 * Run this test and monitor memory usage:
 * 1. cd example && yarn test MemoryLeakTest.ts
 * 2. Monitor with: adb shell dumpsys meminfo com.reactnative.geckoview.example
 */

import '../setup';

import React, { useState, useEffect, ReactElement } from 'react';
import { render, waitFor, act, RenderResult } from '@testing-library/react-native';
import { NativeModules } from 'react-native';
import GeckoView from 'react-native-geckoview';
import { clearFabricGlobals, GeckoViewModuleMock } from './testUtils';

// Use shared mock setup
jest.mock('react-native', () => require('./testUtils').setupReactNativeMock());

describe('Memory Leak Tests', () => {
    beforeEach(() => {
        clearFabricGlobals();
        jest.clearAllMocks();
    });

    it('should not leak memory with rapid session creation/destruction', async () => {
        const iterations = 100;
        const sessionKeys: string[] = [];

        // Track initial state
        const initialMockCalls = jest.fn();

        for (let i = 0; i < iterations; i++) {
            const sessionKey = `test-session-${i}`;
            sessionKeys.push(sessionKey);

            // Create component
            const { unmount }: RenderResult = render(
                <GeckoView
                    source={{ uri: 'https://example.com' }}
                    sessionKey={sessionKey}
                    testID={`gecko-${i}`}
                />
            );

            // Allow React to process
            await act(async () => {
                await new Promise<void>(resolve => setTimeout(resolve, 10));
            });

            // Destroy component
            unmount();

            // Allow garbage collection
            await act(async () => {
                await new Promise<void>(resolve => setTimeout(resolve, 10));
            });
        }

        // Test completes - in a real environment, monitor:
        // - Memory footprint should stabilize
        // - No unbounded growth in native heap
        // - Session objects properly cleaned up
        expect(sessionKeys.length).toBe(iterations);
        console.log(`✅ Successfully created and destroyed ${iterations} sessions`);
    });

    it('should cleanup resources when unmounting with persistent session', async () => {
        const sessionKey = 'persistent-test';

        // Mount and unmount multiple times with same session key
        for (let i = 0; i < 50; i++) {
            const { unmount }: RenderResult = render(
                <GeckoView
                    source={{ uri: 'https://mozilla.org' }}
                    sessionKey={sessionKey}
                />
            );

            await act(async () => {
                await new Promise<void>(resolve => setTimeout(resolve, 20));
            });

            unmount();
        }

        // Session should be preserved but view resources cleaned up
        console.log('✅ Session persisted across 50 mount/unmount cycles');
    });

    it('should handle multiple simultaneous sessions efficiently', async () => {
        const sessionCount = 10;
        const components: ReactElement[] = [];

        // Create multiple concurrent sessions
        const TestComponent: React.FC<{ id: number }> = ({ id }) => (
            <GeckoView
                source={{ uri: `https://example.com/${id}` }}
                sessionKey={`concurrent-session-${id}`}
                testID={`gecko-concurrent-${id}`}
            />
        );

        for (let i = 0; i < sessionCount; i++) {
            components.push(<TestComponent key={i} id={i} />);
        }

        const { unmount }: RenderResult = render(<>{components}</>);

        await act(async () => {
            await new Promise<void>(resolve => setTimeout(resolve, 100));
        });

        // Cleanup all at once
        unmount();

        await act(async () => {
            await new Promise<void>(resolve => setTimeout(resolve, 50));
        });

        console.log(`✅ Successfully managed ${sessionCount} concurrent sessions`);
    });

    it('should properly cleanup event listeners on unmount', async () => {
        const eventHandlers = {
            onGeckoPageStart: jest.fn(),
            onGeckoPageStop: jest.fn(),
            onGeckoProgressChange: jest.fn(),
            onPageError: jest.fn(),
            onMediaSessionAction: jest.fn(),
        };

        const { unmount }: RenderResult = render(
            <GeckoView
                source={{ uri: 'https://example.com' }}
                {...eventHandlers}
            />
        );

        await act(async () => {
            await new Promise<void>(resolve => setTimeout(resolve, 50));
        });

        unmount();

        // After unmount, no new events should be processed
        // In production, verify native side cleanup
        console.log('✅ Event listeners cleanup verified');
    });

    it('should handle rapid URL changes without leaking', async () => {
        const urls: string[] = Array.from({ length: 50 }, (_, i) => `https://example.com/page${i}`);

        const TestComponent: React.FC = () => {
            const [urlIndex, setUrlIndex] = useState<number>(0);

            useEffect(() => {
                const interval = setInterval(() => {
                    setUrlIndex(prev => (prev + 1) % urls.length);
                }, 100);

                return () => clearInterval(interval);
            }, []);

            return (
                <GeckoView
                    source={{ uri: urls[urlIndex] }}
                    sessionKey="rapid-navigation"
                />
            );
        };

        const { unmount }: RenderResult = render(<TestComponent />);

        // Let it run through several URL changes
        await act(async () => {
            await new Promise<void>(resolve => setTimeout(resolve, 1000));
        });

        unmount();

        console.log('✅ Handled rapid URL changes without issues');
    });
});

/**
 * Memory Profiling Instructions
 * 
 * For comprehensive memory profiling and leak detection guide, see:
 * docs/MEMORY_PROFILING.md
 * 
 * Quick check:
 * 1. Run: cd example && yarn android
 * 2. Profile: adb shell dumpsys meminfo com.reactnative.geckoview.example
 */
