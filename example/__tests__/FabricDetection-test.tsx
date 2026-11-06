/**
 * Unit tests for the real `hasFabricRuntime()` implementation in
 * `src/fabricDetection.ts`.
 *
 * Everywhere else in the suite this function is consumed via a `jest.fn()`
 * mock (so the error-boundary UX can be steered), which left the actual
 * detection logic with ~0 direct coverage. These tests exercise each branch of
 * the `??` / `||` / `typeof` chain by passing explicit global refs, plus the
 * `getGlobalRef()` default-argument path.
 *
 * The implementation is reached through the library's `__TEST_ONLY__` export so
 * the test runs against the same module instance the rest of the package uses.
 */
import { __TEST_ONLY__ } from 'react-native-geckoview';

const { hasFabricRuntime } = __TEST_ONLY__;

describe('hasFabricRuntime', () => {
    it('returns false for an empty global ref (all detection points absent)', () => {
        expect(hasFabricRuntime({})).toBe(false);
    });

    it('detects the direct `nativeFabricUIManager` global', () => {
        expect(hasFabricRuntime({ nativeFabricUIManager: {} })).toBe(true);
    });

    it('detects the Hermes `__globalObjectProxy.nativeFabricUIManager` layout', () => {
        expect(
            hasFabricRuntime({ __globalObjectProxy: { nativeFabricUIManager: {} } })
        ).toBe(true);
    });

    it('prefers the proxy manager when both proxy and direct managers are present (?? left side)', () => {
        expect(
            hasFabricRuntime({
                __globalObjectProxy: { nativeFabricUIManager: {} },
                nativeFabricUIManager: {},
            })
        ).toBe(true);
    });

    it('falls back to the direct manager when the proxy manager is nullish (?? right side)', () => {
        // __globalObjectProxy present but its nativeFabricUIManager is undefined
        // -> the `??` must fall through to the direct global.
        expect(
            hasFabricRuntime({
                __globalObjectProxy: {},
                nativeFabricUIManager: {},
            })
        ).toBe(true);
    });

    it('returns false when the proxy exists but neither manager nor other hooks are set', () => {
        expect(hasFabricRuntime({ __globalObjectProxy: {} })).toBe(false);
    });

    it('detects `__fabricUIManagerBinding` when no manager is present (|| middle leg)', () => {
        expect(hasFabricRuntime({ __fabricUIManagerBinding: {} })).toBe(true);
    });

    it('detects a `__getFabricUIManager` function when nothing else is present (|| last leg)', () => {
        expect(hasFabricRuntime({ __getFabricUIManager: () => ({}) })).toBe(true);
    });

    it('ignores a non-function `__getFabricUIManager` (typeof guard false)', () => {
        expect(
            // a truthy-but-not-a-function value must NOT count as Fabric
            hasFabricRuntime({ __getFabricUIManager: 123 as unknown as () => unknown })
        ).toBe(false);
    });

    it('treats an explicit-null manager as absent (!= null guard)', () => {
        expect(
            hasFabricRuntime({ nativeFabricUIManager: null as unknown as undefined })
        ).toBe(false);
    });

    it('uses getGlobalRef() when called with no argument (default-arg branch)', () => {
        // In the Jest environment globalThis has no Fabric bindings, so the
        // default-ref path resolves to false. The call still exercises the
        // default-argument branch + getGlobalRef()'s globalThis lookup.
        const previous = (globalThis as { nativeFabricUIManager?: unknown }).nativeFabricUIManager;
        delete (globalThis as { nativeFabricUIManager?: unknown }).nativeFabricUIManager;
        try {
            expect(hasFabricRuntime()).toBe(false);
        } finally {
            if (previous !== undefined) {
                (globalThis as { nativeFabricUIManager?: unknown }).nativeFabricUIManager = previous;
            }
        }
    });

    it('reads Fabric off the real globalThis via the default ref', () => {
        const g = globalThis as { nativeFabricUIManager?: unknown };
        const previous = g.nativeFabricUIManager;
        g.nativeFabricUIManager = { stub: true };
        try {
            expect(hasFabricRuntime()).toBe(true);
        } finally {
            if (previous === undefined) {
                delete g.nativeFabricUIManager;
            } else {
                g.nativeFabricUIManager = previous;
            }
        }
    });
});
