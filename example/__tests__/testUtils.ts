/**
 * Shared test utilities for GeckoView tests
 * Provides common mocks, helpers, and fixtures
 */

import type { NativeModules as NativeModulesType } from 'react-native';

// Extend global type for test mocking
declare const global: {
    nativeFabricUIManager?: {
        getViewManagerConfig?: (name: string) => unknown;
    };
    __nativeComponentRegistry?: {
        get: (name: string) => unknown;
    };
    __fabricUIManagerBinding?: unknown;
    __getFabricUIManager?: () => unknown;
    __globalObjectProxy?: {
        nativeFabricUIManager?: {
            getViewManagerConfig?: (name: string) => unknown;
        };
    };
};

// Type for the GeckoViewModule mock
export interface GeckoViewModuleMock {
    reload: jest.Mock;
    goBack: jest.Mock;
    goForward: jest.Mock;
    stop: jest.Mock;
    shutdown: jest.Mock;
    installWebExtension: jest.Mock;
    sendWebExtensionMessage: jest.Mock;
    evaluateJavaScript: jest.Mock;
    resolvePrompt: jest.Mock;
    resolvePermission: jest.Mock;
    setEnhancedTrackingProtectionLevel: jest.Mock;
    setCookieBannerMode: jest.Mock;
    setCookieBannerGlobalRules: jest.Mock;
    captureSnapshot: jest.Mock;
    loadUrl: jest.Mock;
    setUserAgent: jest.Mock;
}

/**
 * Creates a complete GeckoViewModule mock with all methods
 * Use this for custom mock configurations
 */
export const createGeckoViewModuleMock = (): GeckoViewModuleMock => ({
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
    setCookieBannerGlobalRules: jest.fn(),
    captureSnapshot: jest.fn(),
    loadUrl: jest.fn(),
    setUserAgent: jest.fn(),
});

/**
 * Setup standard React Native mock for GeckoView tests.
 * Use this for one-line mock setup in test files:
 * 
 * @example
 * jest.mock('react-native', () => require('./testUtils').setupReactNativeMock());
 */
export const setupReactNativeMock = (): typeof import('react-native') => {
    // eslint-disable-next-line @typescript-eslint/no-var-requires
    const RN = jest.requireActual('react-native');
    RN.NativeModules.GeckoViewModule = createGeckoViewModuleMock();
    const mockRequireNativeComponent = jest.fn(() => 'NativeGeckoView');
    Object.defineProperty(RN, 'requireNativeComponent', {
        get: () => mockRequireNativeComponent,
        configurable: true,
    });
    return RN;
};

/**
 * Clears all Fabric-related globals
 * Use in beforeEach/afterEach to ensure clean test environment
 */
export const clearFabricGlobals = (): void => {
    delete global.__nativeComponentRegistry;
    delete global.nativeFabricUIManager;
    delete global.__fabricUIManagerBinding;
    delete global.__getFabricUIManager;
    delete global.__globalObjectProxy;
};

/**
 * Sets up a mock Fabric registry with a component
 * @param componentName - Name of the component to register
 * @param returnValue - Value to return when component is retrieved
 * @returns The mock get function
 */
export const createMockFabricRegistry = (
    componentName: string,
    returnValue: unknown = {}
): jest.Mock => {
    const get = jest.fn((name: string) => (name === componentName ? returnValue : null));
    global.__nativeComponentRegistry = { get };
    return get;
};

/**
 * Sets up a mock Fabric UI Manager
 * @param componentName - Name of the component to register
 * @param config - Config to return for the component
 * @returns The mock getViewManagerConfig function
 */
export const createMockFabricUIManager = (
    componentName: string,
    config: unknown = {}
): jest.Mock => {
    const getViewManagerConfig = jest.fn((name: string) =>
        name === componentName ? config : null
    );
    global.nativeFabricUIManager = { getViewManagerConfig };
    return getViewManagerConfig;
};

/**
 * Sets up a mock Hermes Fabric UI Manager (via __globalObjectProxy)
 * @param componentName - Name of the component to register
 * @param config - Config to return for the component
 * @returns The mock getViewManagerConfig function
 */
export const createMockHermesFabricUIManager = (
    componentName: string,
    config: unknown = {}
): jest.Mock => {
    const getViewManagerConfig = jest.fn((name: string) =>
        name === componentName ? config : null
    );
    global.__globalObjectProxy = {
        nativeFabricUIManager: { getViewManagerConfig },
    };
    return getViewManagerConfig;
};

/**
 * Common test fixtures
 */
export interface TestFixtures {
    urls: {
        example: string;
        mozilla: string;
        github: string;
    };
    sessionKeys: {
        default: string;
        persistent: string;
        temporary: string;
    };
    messages: {
        simpleExtension: string;
        complexExtension: string;
    };
}

export const fixtures: TestFixtures = {
    urls: {
        example: 'https://example.com',
        mozilla: 'https://mozilla.org',
        github: 'https://github.com',
    },
    sessionKeys: {
        default: 'test-session',
        persistent: 'persistent-session',
        temporary: 'temp-session',
    },
    messages: {
        simpleExtension: JSON.stringify({ action: 'doSomething', data: 'test' }),
        complexExtension: JSON.stringify({
            action: 'updateSettings',
            payload: {
                settings: {
                    theme: 'dark',
                    features: ['feature1', 'feature2'],
                },
            },
        }),
    },
};

/**
 * Waits for a condition with timeout
 * @param condition - Function that returns true when condition is met
 * @param timeout - Timeout in ms (default: 1000)
 * @param interval - Check interval in ms (default: 50)
 */
export const waitForCondition = async (
    condition: () => boolean,
    timeout: number = 1000,
    interval: number = 50
): Promise<void> => {
    const startTime = Date.now();
    while (!condition()) {
        if (Date.now() - startTime > timeout) {
            throw new Error('Timeout waiting for condition');
        }
        await new Promise<void>((resolve) => setTimeout(resolve, interval));
    }
};

/**
 * Suppresses console warnings during test execution
 * Useful for tests that intentionally trigger warnings
 * @param testFn - Test function to run with suppressed warnings
 */
export const suppressConsoleWarnings = async (
    testFn: () => Promise<void>
): Promise<void> => {
    const originalWarn = console.warn;
    const originalError = console.error;
    console.warn = jest.fn();
    console.error = jest.fn();
    try {
        await testFn();
    } finally {
        console.warn = originalWarn;
        console.error = originalError;
    }
};
