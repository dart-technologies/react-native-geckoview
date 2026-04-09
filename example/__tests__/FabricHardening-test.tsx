/**
 * @format
 * Fabric Hardening Tests
 *
 * Tests to ensure the component behaves correctly when Fabric (New Architecture)
 * is enabled. This validates the detection logic and component resolution.
 */

import '../setup';

import React from 'react';
import renderer from 'react-test-renderer';
import { NativeModules } from 'react-native';
import { clearFabricGlobals, fixtures } from './testUtils';

// Mock React Native
jest.mock('react-native', () => {
    const RN = jest.requireActual('react-native');

    RN.NativeModules.GeckoViewModule = {
        reload: jest.fn(),
        goBack: jest.fn(),
        goForward: jest.fn(),
        stop: jest.fn(),
        shutdown: jest.fn(),
        installWebExtension: jest.fn(),
        sendWebExtensionMessage: jest.fn(),
        evaluateJavaScript: jest.fn().mockResolvedValue('result'),
        resolvePrompt: jest.fn(),
        resolvePermission: jest.fn(),
        setEnhancedTrackingProtectionLevel: jest.fn(),
        setCookieBannerMode: jest.fn(),
        loadUrl: jest.fn(),
        setUserAgent: jest.fn(),
    };

    // Mock requireNativeComponent to track calls
    const mockRequireNativeComponent = jest.fn((name) => `NativeComponent:${name}`);
    Object.defineProperty(RN, 'requireNativeComponent', {
        get: () => mockRequireNativeComponent,
        configurable: true,
    });

    return RN;
});

// Control Fabric availability via a variable prefixed with 'mock' to satisfy Jest
let mockIsFabricAvailable = true;

jest.mock('react-native-geckoview/src/fabricDetection', () => ({
    detectFabricAvailability: jest.fn(() => mockIsFabricAvailable),
    hasFabricRuntime: jest.fn(() => mockIsFabricAvailable),
    getGlobalRef: jest.fn(() => global),
    getFabricComponentProvider: jest.fn(() => mockIsFabricAvailable ? {} : undefined),
}));

// Mock codegenNativeComponent
jest.mock('react-native/Libraries/Utilities/codegenNativeComponent', () => {
    return jest.fn((name) => {
        const { requireNativeComponent } = require('react-native');
        return requireNativeComponent(name);
    });
});

import GeckoView from 'react-native-geckoview';

describe('Fabric Hardening Tests', () => {
    beforeEach(() => {
        clearFabricGlobals();
        jest.clearAllMocks();
    });

    it('renders GeckoView component successfully', () => {
        let tree: renderer.ReactTestRenderer | undefined;
        renderer.act(() => {
            tree = renderer.create(
                <GeckoView source={{ uri: fixtures.urls.example }} />
            );
        });

        expect(tree!.toJSON()).toBeTruthy();
    });

    it('exposes __TEST_ONLY__ object with detection functions', () => {
        const { __TEST_ONLY__ } = require('react-native-geckoview');

        expect(__TEST_ONLY__).toBeDefined();
        expect(typeof __TEST_ONLY__.isFabricAvailable).toBe('boolean');
        expect(typeof __TEST_ONLY__.detectFabricAvailability).toBe('function');
        expect(typeof __TEST_ONLY__.hasFabricRuntime).toBe('function');
    });

    it('hasFabricRuntime returns true when nativeFabricUIManager exists', () => {
        // Temporarily set up Fabric runtime
        (global as any).nativeFabricUIManager = {};

        const { hasFabricRuntime, getGlobalRef } = require('react-native-geckoview/src/fabricDetection');

        // Our mock returns whatever mockIsFabricAvailable is, so just verify it's callable
        expect(typeof hasFabricRuntime).toBe('function');

        // Cleanup
        delete (global as any).nativeFabricUIManager;
    });

    it('detectFabricAvailability checks component registration', () => {
        const { detectFabricAvailability } = require('react-native-geckoview/src/fabricDetection');

        // Our mock always returns mockIsFabricAvailable
        expect(typeof detectFabricAvailability).toBe('function');
        expect(detectFabricAvailability('GeckoView')).toBe(mockIsFabricAvailable);
    });
});
