/**
 * @format
 * Fabric Detection Integration Tests
 * 
 * Tests for React Native Fabric runtime detection and component registration
 * across different RN versions (0.74+ legacy, 0.81+ modern API)
 */

import {
    detectFabricAvailability,
    hasFabricRuntime,
    getFabricComponentProvider,
} from '../../src/fabricDetection';
import {
    clearFabricGlobals,
    createMockFabricRegistry,
    createMockFabricUIManager,
    createMockHermesFabricUIManager,
} from './testUtils';

// Extend global for test mocking
declare const global: {
    nativeFabricUIManager?: {
        getViewManagerConfig?: (name: string) => unknown;
    } | null;
    __nativeComponentRegistry?: {
        get: (name: string) => unknown;
    };
    __fabricUIManagerBinding?: unknown | null;
    __getFabricUIManager?: (() => unknown) | null;
    __globalObjectProxy?: {
        nativeFabricUIManager?: {
            getViewManagerConfig?: (name: string) => unknown;
        };
    };
};

const COMPONENT_NAME = 'GeckoView';

describe('GeckoView Fabric Detection', () => {
    beforeEach(() => {
        clearFabricGlobals();
    });

    afterEach(() => {
        clearFabricGlobals();
    });

    describe('hasFabricRuntime', () => {
        it('returns true when nativeFabricUIManager exists', () => {
            global.nativeFabricUIManager = {};
            expect(hasFabricRuntime()).toBe(true);
        });

        it('returns true when __fabricUIManagerBinding exists', () => {
            global.__fabricUIManagerBinding = {};
            expect(hasFabricRuntime()).toBe(true);
        });

        it('returns true when __getFabricUIManager exists', () => {
            global.__getFabricUIManager = () => { };
            expect(hasFabricRuntime()).toBe(true);
        });

        it('returns true when Hermes proxy has nativeFabricUIManager', () => {
            global.__globalObjectProxy = {
                nativeFabricUIManager: {},
            };
            expect(hasFabricRuntime()).toBe(true);
        });

        it('returns false when no Fabric indicators exist', () => {
            expect(hasFabricRuntime()).toBe(false);
        });

        it('handles null/undefined gracefully', () => {
            global.nativeFabricUIManager = null;
            global.__fabricUIManagerBinding = undefined;
            expect(hasFabricRuntime()).toBe(false);
        });
    });

    describe('getFabricComponentProvider', () => {
        it('returns provider from modern API (RN 0.81+)', () => {
            const config = { name: COMPONENT_NAME, commands: {} };
            createMockFabricUIManager(COMPONENT_NAME, config);

            const provider = getFabricComponentProvider(COMPONENT_NAME);
            expect(provider).toBeDefined();
            expect(typeof provider).toBe('function');
            expect((provider as () => unknown)()).toEqual(config);
        });

        it('returns provider from legacy registry (RN 0.74-0.80)', () => {
            const mockProvider = jest.fn();
            createMockFabricRegistry(COMPONENT_NAME, mockProvider);

            const provider = getFabricComponentProvider(COMPONENT_NAME);
            expect(provider).toBe(mockProvider);
        });

        it('prefers modern API over legacy registry when both exist', () => {
            const modernConfig = { api: 'modern' };
            const legacyProvider = jest.fn();

            createMockFabricUIManager(COMPONENT_NAME, modernConfig);
            createMockFabricRegistry(COMPONENT_NAME, legacyProvider);

            const provider = getFabricComponentProvider(COMPONENT_NAME);
            expect((provider as () => unknown)()).toEqual(modernConfig);
            expect(legacyProvider).not.toHaveBeenCalled();
        });

        it('falls back to legacy if modern API returns null', () => {
            const legacyProvider = jest.fn();
            createMockFabricUIManager('OtherComponent', {});
            createMockFabricRegistry(COMPONENT_NAME, legacyProvider);

            const provider = getFabricComponentProvider(COMPONENT_NAME);
            expect(provider).toBe(legacyProvider);
        });

        it('returns undefined/null when component not found in any registry', () => {
            global.nativeFabricUIManager = { getViewManagerConfig: () => null };
            global.__nativeComponentRegistry = { get: () => null };

            const result = getFabricComponentProvider(COMPONENT_NAME);
            expect(result == null).toBe(true); // null or undefined is acceptable
        });
    });

    describe('detectFabricAvailability', () => {
        it('returns false when runtime is not available', () => {
            expect(detectFabricAvailability(COMPONENT_NAME)).toBe(false);
        });

        it('returns false when runtime exists but component is not registered', () => {
            createMockFabricUIManager('OtherComponent', {});
            expect(detectFabricAvailability(COMPONENT_NAME)).toBe(false);
        });

        it('returns true with modern API (RN 0.81+)', () => {
            createMockFabricUIManager(COMPONENT_NAME, {});
            expect(detectFabricAvailability(COMPONENT_NAME)).toBe(true);
        });

        it('returns true with Hermes proxy (RN 0.81+ on Hermes)', () => {
            createMockHermesFabricUIManager(COMPONENT_NAME, {});
            expect(detectFabricAvailability(COMPONENT_NAME)).toBe(true);
        });

        it('returns true with legacy registry (RN 0.74-0.80)', () => {
            global.nativeFabricUIManager = {}; // Runtime exists
            createMockFabricRegistry(COMPONENT_NAME, jest.fn());

            expect(detectFabricAvailability(COMPONENT_NAME)).toBe(true);
        });

        it('validates component provider is a function or object', () => {
            // Function provider
            global.nativeFabricUIManager = {};
            createMockFabricRegistry(COMPONENT_NAME, jest.fn());
            expect(detectFabricAvailability(COMPONENT_NAME)).toBe(true);

            // Object provider
            clearFabricGlobals();
            createMockFabricUIManager(COMPONENT_NAME, { commands: {} });
            expect(detectFabricAvailability(COMPONENT_NAME)).toBe(true);
        });

        it('handles exceptions from getViewManagerConfig gracefully', () => {
            global.nativeFabricUIManager = {
                getViewManagerConfig: () => {
                    throw new Error('Component not found');
                },
            };

            expect(() => detectFabricAvailability(COMPONENT_NAME)).not.toThrow();
            expect(detectFabricAvailability(COMPONENT_NAME)).toBe(false);
        });
    });

    describe('Edge Cases', () => {
        it('handles missing global object', () => {
            expect(() => hasFabricRuntime({})).not.toThrow();
            expect(hasFabricRuntime({})).toBe(false);
        });

        it('handles malformed Fabric objects', () => {
            global.nativeFabricUIManager = { notTheRightMethod: () => { } } as any;
            const result = getFabricComponentProvider(COMPONENT_NAME);
            expect(result == null).toBe(true); // null or undefined is acceptable
        });

        it('handles component names with special characters', () => {
            const specialName = 'My-Component_123';
            createMockFabricUIManager(specialName, {});
            expect(detectFabricAvailability(specialName)).toBe(true);
        });
    });
});
