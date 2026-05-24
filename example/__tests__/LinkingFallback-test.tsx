/**
 * Tests for the module-level native-module binding in `index.tsx`:
 *
 *   const GeckoViewModule = NativeModules.GeckoViewModule
 *       ? NativeModules.GeckoViewModule
 *       : new Proxy({}, { get() { throw new Error(LINKING_ERROR); } });
 *
 * Covers both legs of that ternary:
 *  - module present  -> `configureGeckoViewSecurity` forwards to the native mock,
 *  - module absent   -> any access throws the "not linked" error.
 *
 * The library is re-required inside `jest.isolateModules` so the binding is
 * recomputed against the mutated `NativeModules` for each case.
 */
import { NativeModules } from 'react-native';

describe('GeckoViewModule linking fallback', () => {
    const ORIGINAL = NativeModules.GeckoViewModule;

    afterEach(() => {
        NativeModules.GeckoViewModule = ORIGINAL;
    });

    it('binds directly to the native module when it is registered', () => {
        jest.isolateModules(() => {
            // eslint-disable-next-line @typescript-eslint/no-var-requires
            const mod = require('react-native-geckoview');
            expect(typeof mod.configureGeckoViewSecurity).toBe('function');
            // Reaches NativeModules.GeckoViewModule.configureSecurity (the mock),
            // which resolves -> must not throw the linking error.
            expect(() => mod.configureGeckoViewSecurity({})).not.toThrow();
        });
    });

    it('builds a Proxy that throws the linking error when the native module is absent', () => {
        delete (NativeModules as { GeckoViewModule?: unknown }).GeckoViewModule;

        jest.isolateModules(() => {
            // eslint-disable-next-line @typescript-eslint/no-var-requires
            const mod = require('react-native-geckoview');
            // Accessing any method on the Proxy throws the linking error.
            expect(() => mod.configureGeckoViewSecurity({})).toThrow(/doesn't seem to be linked/);
        });
    });
});
