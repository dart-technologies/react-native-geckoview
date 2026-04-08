declare const __DEV__: boolean;
declare const global: any;

/**
 * Fabric Detection Utilities
 * 
 * Detects whether React Native's Fabric renderer is available and whether
 * a specific native component is registered with it.
 * 
 * Compatible with React Native 0.74+ (legacy __nativeComponentRegistry)
 * and React Native 0.81+ (nativeFabricUIManager.getViewManagerConfig)
 */

export const getGlobalRef = (): any => {
    if (typeof global !== 'undefined') {
        return global;
    }
    if (typeof window !== 'undefined') {
        return window;
    }
    return {};
};

/**
 * Check if the Fabric runtime is available (not just enabled in config)
 */
export const hasFabricRuntime = (globalRef: any = getGlobalRef()): boolean => {
    // Check for Hermes-specific global proxy first
    const fabricManager = globalRef?.__globalObjectProxy?.nativeFabricUIManager
        ?? globalRef?.nativeFabricUIManager;

    return (
        fabricManager != null ||
        globalRef?.__fabricUIManagerBinding != null ||
        typeof globalRef?.__getFabricUIManager === 'function'
    );
};

/**
 * Get the component provider/config for a specific component
 * Supports both RN 0.81+ (getViewManagerConfig) and legacy (__nativeComponentRegistry)
 */
export const getFabricComponentProvider = (
    componentName: string,
    globalRef: any = getGlobalRef(),
) => {
    // RN 0.81+: Use nativeFabricUIManager.getViewManagerConfig()
    // This is the official API that the runtime uses
    const fabricManager = globalRef?.__globalObjectProxy?.nativeFabricUIManager
        ?? globalRef?.nativeFabricUIManager;

    if (fabricManager && typeof fabricManager.getViewManagerConfig === 'function') {
        try {
            const config = fabricManager.getViewManagerConfig(componentName);
            if (config != null) {
                return () => config; // Return as function for consistency
            }
        } catch (e) {
            // Component not registered, continue to fallback
        }
    }

    // Legacy RN 0.74-0.80: Use __nativeComponentRegistry
    const registry = globalRef?.__nativeComponentRegistry;
    if (registry && typeof registry.get === 'function') {
        return registry.get(componentName);
    }

    return undefined;
};

/**
 * Detect if Fabric is available AND a specific component is registered
 */
export const detectFabricAvailability = (
    componentName: string,
    globalRef: any = getGlobalRef(),
): boolean => {
    if (!hasFabricRuntime(globalRef)) {
        return false;
    }

    const componentProvider = getFabricComponentProvider(componentName, globalRef);
    return componentProvider != null && (typeof componentProvider === 'function' || typeof componentProvider === 'object');
};
