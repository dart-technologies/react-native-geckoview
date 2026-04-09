/**
 * @format
 * GeckoView Component Tests
 * 
 * Tests for the main GeckoView React component,
 * including props, imperative methods, and ref handling
 */

import '../setup';

import React from 'react';
import renderer, { ReactTestRenderer, ReactTestInstance } from 'react-test-renderer';
import { NativeModules } from 'react-native';
import { clearFabricGlobals, fixtures, GeckoViewModuleMock } from './testUtils';

// Use shared mock setup
jest.mock('react-native', () => require('./testUtils').setupReactNativeMock());

import GeckoView, { GeckoViewRef } from 'react-native-geckoview';

describe('GeckoView Component', () => {
    beforeEach(() => {
        clearFabricGlobals();
        jest.clearAllMocks();
    });

    it('renders correctly', () => {
        let tree: ReactTestRenderer | undefined;
        renderer.act(() => {
            tree = renderer.create(<GeckoView source={{ uri: fixtures.urls.example }} />);
        });
        expect(tree).toBeDefined();
    });

    it('passes handleAudioFocus prop to native component', () => {
        let tree: ReactTestRenderer | undefined;
        renderer.act(() => {
            tree = renderer.create(<GeckoView handleAudioFocus={true} />);
        });
        const instance: ReactTestInstance = tree!.root;
        const nativeComponent = instance.findByType('NativeGeckoView' as any);
        expect(nativeComponent.props.handleAudioFocus).toBe(true);
    });

    it('exposes imperative methods via ref', () => {
        const ref = React.createRef<GeckoViewRef>();
        renderer.act(() => {
            renderer.create(<GeckoView ref={ref} />);
        });

        expect(ref.current).not.toBeNull();
        expect(ref.current!.reload).toBeDefined();
        expect(ref.current!.resolvePrompt).toBeDefined();
        expect(ref.current!.setEnhancedTrackingProtectionLevel).toBeDefined();
    });

    it('calls native module methods', () => {
        const ref = React.createRef<GeckoViewRef>();
        renderer.act(() => {
            renderer.create(<GeckoView ref={ref} sessionKey={fixtures.sessionKeys.default} />);
        });

        const geckoModule = NativeModules.GeckoViewModule as GeckoViewModuleMock;

        ref.current!.reload();
        expect(geckoModule.reload).toHaveBeenCalledWith(fixtures.sessionKeys.default);

        ref.current!.resolvePrompt('prompt-id', true, 'text', 'user');
        expect(geckoModule.resolvePrompt).toHaveBeenCalledWith('prompt-id', true, 'text', 'user');

        ref.current!.setEnhancedTrackingProtectionLevel(1);
        expect(geckoModule.setEnhancedTrackingProtectionLevel).toHaveBeenCalledWith(1);

        ref.current!.setCookieBannerMode(2);
        expect(geckoModule.setCookieBannerMode).toHaveBeenCalledWith(2);
    });
});
