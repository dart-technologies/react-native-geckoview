/**
 * Covers the `index.tsx` code paths gated on `isTestEnv` (NODE_ENV === 'test'),
 * which are skipped during the normal Jest run:
 *
 *  - `preloadViewConfig()` body (the ReactNativeViewConfigRegistry warm-up and
 *    its try/catch), guarded by `if (isTestEnv) return;`
 *  - `devLog()` actually logging, guarded by `if (__DEV__ && !isTestEnv)`
 *
 * To reach them the module is re-required inside `jest.isolateModules` with
 * `process.env.NODE_ENV` flipped away from 'test' so `isTestEnv` evaluates
 * false. Both the success path (registry resolves) and the failure path
 * (registry require throws -> devLog) are exercised.
 */
describe('index.tsx non-test-env startup paths', () => {
    const ORIGINAL_NODE_ENV = process.env.NODE_ENV;
    let logSpy: jest.SpyInstance;

    beforeEach(() => {
        logSpy = jest.spyOn(console, 'log').mockImplementation(() => {});
    });

    afterEach(() => {
        process.env.NODE_ENV = ORIGINAL_NODE_ENV;
        logSpy.mockRestore();
        jest.dontMock('react-native/Libraries/Renderer/shims/ReactNativeViewConfigRegistry');
    });

    it('runs preloadViewConfig against a resolvable view-config registry', () => {
        process.env.NODE_ENV = 'production';

        const getMock = jest.fn();
        // Provide a registry whose `get` is a function so the inner
        // `if (typeof registry?.get === 'function')` true-leg runs.
        jest.doMock(
            'react-native/Libraries/Renderer/shims/ReactNativeViewConfigRegistry',
            () => ({ get: getMock }),
            { virtual: true }
        );

        jest.isolateModules(() => {
            // eslint-disable-next-line @typescript-eslint/no-var-requires
            const mod = require('react-native-geckoview');
            expect(mod.default).toBeDefined();
        });

        // preloadViewConfig should have warmed up the 'GeckoView' config.
        expect(getMock).toHaveBeenCalledWith('GeckoView');
    });

    it('tolerates a view-config registry without a get() function', () => {
        process.env.NODE_ENV = 'production';

        // Registry resolves but exposes no `get` function -> the inner
        // `if (typeof registry?.get === 'function')` false-leg runs and the
        // module load must still succeed.
        jest.doMock(
            'react-native/Libraries/Renderer/shims/ReactNativeViewConfigRegistry',
            () => ({}),
            { virtual: true }
        );

        jest.isolateModules(() => {
            // eslint-disable-next-line @typescript-eslint/no-var-requires
            const mod = require('react-native-geckoview');
            expect(mod.default).toBeDefined();
        });
    });

    it('logs via devLog when the view-config registry require throws', () => {
        process.env.NODE_ENV = 'production';

        // Force the registry require to throw so the catch -> devLog path runs.
        jest.doMock(
            'react-native/Libraries/Renderer/shims/ReactNativeViewConfigRegistry',
            () => {
                throw new Error('no renderer in this environment');
            },
            { virtual: true }
        );

        jest.isolateModules(() => {
            // eslint-disable-next-line @typescript-eslint/no-var-requires
            const mod = require('react-native-geckoview');
            expect(mod.default).toBeDefined();
        });

        // devLog (active because __DEV__ is true and NODE_ENV !== 'test') should
        // have emitted the failure diagnostic.
        expect(logSpy).toHaveBeenCalledWith(
            '[GeckoView] Failed to preload view config',
            expect.any(Error)
        );
    });
});
