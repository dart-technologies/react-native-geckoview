/**
 * Runtime check for React Native's Fabric renderer.
 *
 * Since v0.2.0 the library requires RN 0.83+ (Fabric-only), so this is only
 * used by the error-boundary UX to render a helpful message if the native
 * codegen component failed to register.
 */

type GlobalRef = {
    nativeFabricUIManager?: unknown;
    __fabricUIManagerBinding?: unknown;
    __getFabricUIManager?: () => unknown;
    __globalObjectProxy?: {
        nativeFabricUIManager?: unknown;
    };
};

const getGlobalRef = (): GlobalRef => globalThis as GlobalRef;

/**
 * Returns `true` when the Fabric runtime is wired up on this JS context.
 * Handles both the direct global and the Hermes `__globalObjectProxy` layout.
 */
export const hasFabricRuntime = (globalRef: GlobalRef = getGlobalRef()): boolean => {
    const fabricManager =
        globalRef.__globalObjectProxy?.nativeFabricUIManager ?? globalRef.nativeFabricUIManager;

    return (
        fabricManager != null ||
        globalRef.__fabricUIManagerBinding != null ||
        typeof globalRef.__getFabricUIManager === 'function'
    );
};
