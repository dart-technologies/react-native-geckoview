/**
 * Snapshot Tests for GeckoView
 * Tests the captureSnapshot functionality
 */

import '../setup';

import React from 'react';
import renderer from 'react-test-renderer';
import { clearFabricGlobals, fixtures } from './testUtils';

// Use shared mock setup - reduces 30 lines of duplication
jest.mock('react-native', () => require('./testUtils').setupReactNativeMock());

import GeckoView, { GeckoViewRef } from 'react-native-geckoview';

describe('GeckoView Snapshot', () => {
    beforeEach(() => {
        clearFabricGlobals();
        jest.clearAllMocks();
    });

    it('exposes captureSnapshot method via ref', () => {
        const ref = React.createRef<GeckoViewRef>();
        renderer.act(() => {
            renderer.create(<GeckoView ref={ref} source={{ uri: fixtures.urls.example }} />);
        });

        expect(ref.current).not.toBeNull();
        expect(ref.current!.captureSnapshot).toBeDefined();
        expect(typeof ref.current!.captureSnapshot).toBe('function');
    });

    it('captureSnapshot returns a promise', async () => {
        const ref = React.createRef<GeckoViewRef>();
        renderer.act(() => {
            renderer.create(<GeckoView ref={ref} sessionKey={fixtures.sessionKeys.default} source={{ uri: fixtures.urls.example }} />);
        });

        // captureSnapshot should return a promise
        const result = ref.current!.captureSnapshot();
        expect(result).toBeInstanceOf(Promise);
    });
});
