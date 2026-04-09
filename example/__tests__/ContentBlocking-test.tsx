/**
 * Content Blocking Tests for GeckoView
 * Tests Enhanced Tracking Protection and Cookie Banner handling
 */

import '../setup';

import React from 'react';
import { render } from '@testing-library/react-native';
import { NativeModules } from 'react-native';
import { clearFabricGlobals } from './testUtils';

// Use shared mock setup
jest.mock('react-native', () => require('./testUtils').setupReactNativeMock());

import GeckoView, { GeckoViewRef } from 'react-native-geckoview';

describe('Content Blocking', () => {
    let geckoRef: React.RefObject<GeckoViewRef | null>;

    beforeEach(() => {
        clearFabricGlobals();
        geckoRef = React.createRef<GeckoViewRef>();
        jest.clearAllMocks();
    });

    it('sets Enhanced Tracking Protection level', () => {
        render(<GeckoView ref={geckoRef} />);

        // Test NONE (0)
        geckoRef.current?.setEnhancedTrackingProtectionLevel(0);
        expect(NativeModules.GeckoViewModule.setEnhancedTrackingProtectionLevel).toHaveBeenCalledWith(0);

        // Test DEFAULT (1)
        geckoRef.current?.setEnhancedTrackingProtectionLevel(1);
        expect(NativeModules.GeckoViewModule.setEnhancedTrackingProtectionLevel).toHaveBeenCalledWith(1);

        // Test STRICT (2)
        geckoRef.current?.setEnhancedTrackingProtectionLevel(2);
        expect(NativeModules.GeckoViewModule.setEnhancedTrackingProtectionLevel).toHaveBeenCalledWith(2);
    });

    it('sets Cookie Banner mode', () => {
        render(<GeckoView ref={geckoRef} />);

        // Test DISABLED (0)
        geckoRef.current?.setCookieBannerMode(0);
        expect(NativeModules.GeckoViewModule.setCookieBannerMode).toHaveBeenCalledWith(0);

        // Test REJECT (1)
        geckoRef.current?.setCookieBannerMode(1);
        expect(NativeModules.GeckoViewModule.setCookieBannerMode).toHaveBeenCalledWith(1);

        // Test REJECT_OR_ACCEPT (2)
        geckoRef.current?.setCookieBannerMode(2);
        expect(NativeModules.GeckoViewModule.setCookieBannerMode).toHaveBeenCalledWith(2);
    });
});
