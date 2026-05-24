/**
 * Tests for `GeckoViewErrorBoundary` (the fallback UI rendered when the native
 * codegen component fails to register).
 *
 * `GeckoView` wraps `<NativeGeckoView/>` in an error boundary. To trigger the
 * boundary we mock the codegen spec module (`src/GeckoViewNativeComponent`) so
 * `index.tsx`'s `require('./src/GeckoViewNativeComponent')` resolves to a React
 * component that throws on render. A module-level flag lets each test opt in to
 * throwing vs. rendering normally.
 *
 * Coverage targets in `index.tsx`:
 *  - the codegen try-branch `return mod.default` (line 21),
 *  - the error-boundary render branch (`!this.state.error` false leg),
 *  - the "Fabric runtime: yes/no" hint (both legs),
 *  - the `__DEV__ ? <details/> : null` branch (both legs),
 *  - the `error?.message || error` fallback.
 */
import React from 'react';

// Controls whether the mocked native component throws on render.
const throwState: { error: Error | null } = { error: null };

jest.mock('react-native-geckoview/src/GeckoViewNativeComponent', () => ({
    __esModule: true,
    default: function MockNativeGeckoView() {
        if (throwState.error) {
            throw throwState.error;
        }
        return null;
    },
}));

import { render } from '@testing-library/react-native';
import GeckoView from 'react-native-geckoview';
import { fixtures } from './testUtils';

type FabricGlobal = { nativeFabricUIManager?: unknown };

const setFabric = (present: boolean): (() => void) => {
    const g = globalThis as FabricGlobal;
    const previous = g.nativeFabricUIManager;
    if (present) {
        g.nativeFabricUIManager = { stub: true };
    } else {
        delete g.nativeFabricUIManager;
    }
    return () => {
        if (previous === undefined) {
            delete g.nativeFabricUIManager;
        } else {
            g.nativeFabricUIManager = previous;
        }
    };
};

const withDev = (value: boolean, fn: () => void): void => {
    const g = globalThis as { __DEV__?: boolean };
    const previous = g.__DEV__;
    g.__DEV__ = value;
    try {
        fn();
    } finally {
        g.__DEV__ = previous;
    }
};

describe('GeckoViewErrorBoundary', () => {
    let originalError: typeof console.error;

    beforeEach(() => {
        throwState.error = null;
        // React logs caught render errors via console.error; silence the noise.
        originalError = console.error;
        jest.spyOn(console, 'error').mockImplementation(() => {});
    });

    afterEach(() => {
        (console.error as jest.Mock).mockRestore?.();
        console.error = originalError;
    });

    it('renders children normally when nothing throws (happy path, codegen try-branch)', () => {
        const { queryByText } = render(<GeckoView source={{ uri: fixtures.urls.example }} />);
        expect(queryByText('GeckoView failed to render in this build.')).toBeNull();
    });

    it('renders the fallback UI when the native child throws on render', () => {
        throwState.error = new Error('codegen component not registered');
        const { getByText } = render(<GeckoView source={{ uri: fixtures.urls.example }} />);

        expect(getByText('GeckoView failed to render in this build.')).toBeTruthy();
        expect(getByText(/react-native-geckoview requires React Native 0\.83\+/)).toBeTruthy();
    });

    it('shows "Fabric runtime: yes" when a Fabric manager is present on the global', () => {
        const restore = setFabric(true);
        try {
            throwState.error = new Error('boom');
            const { getByText } = render(<GeckoView source={{ uri: fixtures.urls.example }} />);
            expect(getByText('Fabric runtime: yes')).toBeTruthy();
        } finally {
            restore();
        }
    });

    it('shows "Fabric runtime: no" when no Fabric manager is present', () => {
        const restore = setFabric(false);
        try {
            throwState.error = new Error('boom');
            const { getByText } = render(<GeckoView source={{ uri: fixtures.urls.example }} />);
            expect(getByText('Fabric runtime: no')).toBeTruthy();
        } finally {
            restore();
        }
    });

    it('renders the error detail text under __DEV__', () => {
        withDev(true, () => {
            throwState.error = new Error('detailed failure message');
            const { getByText } = render(<GeckoView source={{ uri: fixtures.urls.example }} />);
            expect(getByText('detailed failure message')).toBeTruthy();
        });
    });

    it('omits the error detail text when __DEV__ is false', () => {
        withDev(false, () => {
            throwState.error = new Error('hidden in production');
            const { queryByText, getByText } = render(
                <GeckoView source={{ uri: fixtures.urls.example }} />
            );
            expect(getByText('GeckoView failed to render in this build.')).toBeTruthy();
            expect(queryByText('hidden in production')).toBeNull();
        });
    });

    it('falls back to String(error) when the thrown error has an empty message', () => {
        withDev(true, () => {
            // Empty message -> `error?.message` falsy -> `|| error` renders String(error).
            throwState.error = new Error('');
            const { getByText } = render(<GeckoView source={{ uri: fixtures.urls.example }} />);
            // String(new Error('')) === 'Error'
            expect(getByText('Error')).toBeTruthy();
        });
    });
});
