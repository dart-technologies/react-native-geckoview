/**
 * Shared test utilities for GeckoView tests
 * Provides the native module mock, fixtures, and small helpers.
 *
 * The `setupReactNativeMock()` factory is wired up globally via
 * `example/setup.ts` + Jest `setupFiles`, so individual test files only need
 * to `import GeckoView from 'react-native-geckoview'` and go. See
 * `docs/TESTING.md` for the pattern.
 */

// Type for the GeckoViewModule mock
export interface GeckoViewModuleMock {
    reload: jest.Mock;
    configureSecurity: jest.Mock;
    goBack: jest.Mock;
    goForward: jest.Mock;
    stop: jest.Mock;
    closeSession: jest.Mock;
    shutdown: jest.Mock;
    installWebExtension: jest.Mock;
    sendWebExtensionMessage: jest.Mock;
    evaluateJavaScript: jest.Mock;
    resolvePrompt: jest.Mock;
    resolvePermission: jest.Mock;
    setEnhancedTrackingProtectionLevel: jest.Mock;
    setCookieBannerMode: jest.Mock;
    captureSnapshot: jest.Mock;
    loadUrl: jest.Mock;
    setUserAgent: jest.Mock;
}

/**
 * Creates a complete GeckoViewModule mock with all methods stubbed.
 * Promise-returning methods resolve with placeholder values so callers can
 * assert `toBeInstanceOf(Promise)` and chain `.then(...)` in tests.
 */
export const createGeckoViewModuleMock = (): GeckoViewModuleMock => ({
    reload: jest.fn(),
    configureSecurity: jest.fn().mockResolvedValue(undefined),
    goBack: jest.fn(),
    goForward: jest.fn(),
    stop: jest.fn(),
    closeSession: jest.fn(),
    shutdown: jest.fn(),
    installWebExtension: jest.fn().mockResolvedValue(undefined),
    sendWebExtensionMessage: jest.fn().mockResolvedValue(undefined),
    evaluateJavaScript: jest.fn().mockResolvedValue('result'),
    resolvePrompt: jest.fn(),
    resolvePermission: jest.fn(),
    setEnhancedTrackingProtectionLevel: jest.fn().mockResolvedValue(undefined),
    setCookieBannerMode: jest.fn().mockResolvedValue(undefined),
    captureSnapshot: jest.fn().mockResolvedValue('/tmp/snapshot.png'),
    loadUrl: jest.fn(),
    setUserAgent: jest.fn(),
});

/**
 * Setup the shared React Native mock. Invoked once by `example/setup.ts`
 * via Jest's `setupFiles`. Test files should not call this directly.
 */
export const setupReactNativeMock = (): typeof import('react-native') => {
    // eslint-disable-next-line @typescript-eslint/no-var-requires
    const RN = jest.requireActual('react-native');
    RN.NativeModules.GeckoViewModule = createGeckoViewModuleMock();
    const stubComponent = jest.fn(() => 'NativeGeckoView');
    Object.defineProperty(RN, 'requireNativeComponent', {
        get: () => stubComponent,
        configurable: true,
    });
    return RN;
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
 * Waits for a condition with timeout.
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
 * Useful for tests that intentionally trigger warnings.
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
