/**
 * Snapshot tests for the JS-facing surface of `captureSnapshot`.
 *
 * Native-side branch coverage (VIEW_NOT_FOUND, SNAPSHOT_TOO_LARGE,
 * SNAPSHOT_ERROR, the bitmap-recycle path, per-session tracking) lives in
 * [GeckoViewModuleTest.kt]. These tests pin down the JS contract: the ref
 * surface forwards the session key, returns a Promise, propagates each error
 * code unchanged, and resolves with the file path on success.
 */

import '../setup';

import React, { createRef } from 'react';
import { act, render } from '@testing-library/react-native';
import { NativeModules } from 'react-native';
import GeckoView, { GeckoViewRef } from 'react-native-geckoview';
import { fixtures } from './testUtils';

const geckoModule = () =>
    NativeModules.GeckoViewModule as {
        captureSnapshot: jest.Mock;
        closeSession: jest.Mock;
    };

const mountGecko = (
    sessionKey: string = fixtures.sessionKeys.default
): React.RefObject<GeckoViewRef | null> => {
    const ref = createRef<GeckoViewRef>();
    render(
        <GeckoView
            ref={ref}
            sessionKey={sessionKey}
            source={{ uri: fixtures.urls.example }}
        />
    );
    return ref;
};

describe('GeckoView captureSnapshot — JS surface', () => {
    beforeEach(() => {
        jest.clearAllMocks();
    });

    it('exposes captureSnapshot as a function on the ref', () => {
        const ref = mountGecko();
        expect(ref.current).not.toBeNull();
        expect(typeof ref.current!.captureSnapshot).toBe('function');
    });

    it('returns a Promise', () => {
        geckoModule().captureSnapshot.mockResolvedValueOnce('/tmp/snapshot.png');
        const ref = mountGecko();
        const result = ref.current!.captureSnapshot();
        expect(result).toBeInstanceOf(Promise);
    });

    it('forwards the sessionKey to the native module', async () => {
        geckoModule().captureSnapshot.mockResolvedValueOnce('/tmp/snapshot.png');
        const ref = mountGecko('explicit-session');
        await act(async () => {
            await ref.current!.captureSnapshot();
        });
        expect(geckoModule().captureSnapshot).toHaveBeenCalledWith('explicit-session');
        expect(geckoModule().captureSnapshot).toHaveBeenCalledTimes(1);
    });

    it('resolves with the absolute file path on success', async () => {
        geckoModule().captureSnapshot.mockResolvedValueOnce(
            '/data/data/com.example/files/snapshot.png'
        );
        const ref = mountGecko();
        await expect(ref.current!.captureSnapshot()).resolves.toBe(
            '/data/data/com.example/files/snapshot.png'
        );
    });

    it('propagates VIEW_NOT_FOUND rejection', async () => {
        geckoModule().captureSnapshot.mockRejectedValueOnce(
            Object.assign(new Error('No GeckoView attached to session: missing'), {
                code: 'VIEW_NOT_FOUND',
            })
        );
        const ref = mountGecko('missing');
        await expect(ref.current!.captureSnapshot()).rejects.toThrow(
            /No GeckoView attached/
        );
    });

    it('propagates SNAPSHOT_TOO_LARGE rejection (view-level)', async () => {
        geckoModule().captureSnapshot.mockRejectedValueOnce(
            Object.assign(new Error('View exceeds 16777216 pixels (5000x5000)'), {
                code: 'SNAPSHOT_TOO_LARGE',
            })
        );
        const ref = mountGecko();
        await expect(ref.current!.captureSnapshot()).rejects.toThrow(/exceeds.*pixels/);
    });

    it('propagates SNAPSHOT_ERROR rejection on null bitmap', async () => {
        geckoModule().captureSnapshot.mockRejectedValueOnce(
            Object.assign(new Error('Failed to capture snapshot: Bitmap is null'), {
                code: 'SNAPSHOT_ERROR',
            })
        );
        const ref = mountGecko();
        await expect(ref.current!.captureSnapshot()).rejects.toThrow(/Bitmap is null/);
    });

    it('propagates SNAPSHOT_ERROR rejection from the capturePixels error path (Fix 7)', async () => {
        geckoModule().captureSnapshot.mockRejectedValueOnce(
            Object.assign(new Error('capturePixels failed: GPU process crashed'), {
                code: 'SNAPSHOT_ERROR',
            })
        );
        const ref = mountGecko();
        await expect(ref.current!.captureSnapshot()).rejects.toThrow(/capturePixels failed/);
    });

    it('captures back-to-back snapshots without losing the session key', async () => {
        geckoModule()
            .captureSnapshot.mockResolvedValueOnce('/tmp/snapshot-1.png')
            .mockResolvedValueOnce('/tmp/snapshot-2.png');
        const ref = mountGecko('series-session');
        await act(async () => {
            await ref.current!.captureSnapshot();
            await ref.current!.captureSnapshot();
        });
        expect(geckoModule().captureSnapshot).toHaveBeenCalledTimes(2);
        expect(geckoModule().captureSnapshot).toHaveBeenNthCalledWith(1, 'series-session');
        expect(geckoModule().captureSnapshot).toHaveBeenNthCalledWith(2, 'series-session');
    });
});

describe('GeckoView closeSession — JS surface (Fix 2 contract)', () => {
    beforeEach(() => {
        jest.clearAllMocks();
    });

    it('forwards the active sessionKey to the native module', () => {
        const ref = mountGecko('to-close');
        ref.current!.closeSession();
        expect(geckoModule().closeSession).toHaveBeenCalledWith('to-close');
        expect(geckoModule().closeSession).toHaveBeenCalledTimes(1);
    });

    it('isolates closeSession calls across two GeckoView instances with distinct keys', () => {
        const ref1 = createRef<GeckoViewRef>();
        const ref2 = createRef<GeckoViewRef>();
        render(
            <>
                <GeckoView ref={ref1} sessionKey="alpha" source={{ uri: fixtures.urls.example }} />
                <GeckoView ref={ref2} sessionKey="beta" source={{ uri: fixtures.urls.mozilla }} />
            </>
        );

        ref1.current!.closeSession();

        expect(geckoModule().closeSession).toHaveBeenCalledWith('alpha');
        expect(geckoModule().closeSession).not.toHaveBeenCalledWith('beta');

        ref2.current!.closeSession();
        expect(geckoModule().closeSession).toHaveBeenLastCalledWith('beta');
    });

    it('closing one session does not affect the captureSnapshot call for another', async () => {
        geckoModule().captureSnapshot.mockResolvedValue('/tmp/x.png');
        const ref1 = createRef<GeckoViewRef>();
        const ref2 = createRef<GeckoViewRef>();
        render(
            <>
                <GeckoView ref={ref1} sessionKey="alpha" source={{ uri: fixtures.urls.example }} />
                <GeckoView ref={ref2} sessionKey="beta" source={{ uri: fixtures.urls.example }} />
            </>
        );

        ref1.current!.closeSession();
        await act(async () => {
            await ref2.current!.captureSnapshot();
        });

        // The native side scopes snapshot cleanup to the closed session key
        // (covered in Kotlin tests). On the JS side we pin the contract that
        // captureSnapshot continues to forward the surviving session key.
        expect(geckoModule().captureSnapshot).toHaveBeenCalledWith('beta');
    });
});
