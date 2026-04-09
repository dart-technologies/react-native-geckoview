/**
 * Popup Tests for GeckoView
 * Tests the window.open / popup handling via onGeckoPopup event
 */

import '../setup';

import React from 'react';
import renderer, { ReactTestInstance } from 'react-test-renderer';
import { clearFabricGlobals, fixtures } from './testUtils';

// Use shared mock setup - reduces 30 lines of duplication
jest.mock('react-native', () => require('./testUtils').setupReactNativeMock());

import GeckoView from 'react-native-geckoview';

describe('GeckoView Popup', () => {
    beforeEach(() => {
        clearFabricGlobals();
        jest.clearAllMocks();
    });

    it('accepts onGeckoPopup prop', () => {
        const onGeckoPopup = jest.fn();
        let tree: renderer.ReactTestRenderer | undefined;

        renderer.act(() => {
            tree = renderer.create(
                <GeckoView
                    source={{ uri: fixtures.urls.example }}
                    onGeckoPopup={onGeckoPopup}
                />
            );
        });

        // Find the native component
        const nativeComponent = tree!.root.findByType('NativeGeckoView' as any) as ReactTestInstance;
        expect(nativeComponent.props.onGeckoPopup).toBe(onGeckoPopup);
    });

    it('passes popup event handler to native component', () => {
        const handlePopup = jest.fn((event) => {
            // Handle popup by potentially opening a new tab
            console.log('Popup requested:', event.nativeEvent?.targetUri);
        });

        let tree: renderer.ReactTestRenderer | undefined;
        renderer.act(() => {
            tree = renderer.create(
                <GeckoView
                    source={{ uri: fixtures.urls.example }}
                    onGeckoPopup={handlePopup}
                />
            );
        });

        const nativeComponent = tree!.root.findByType('NativeGeckoView' as any) as ReactTestInstance;
        expect(nativeComponent.props.onGeckoPopup).toBeDefined();
    });
});
