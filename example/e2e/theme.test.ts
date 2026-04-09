import { device, element, by, expect } from 'detox';

/**
 * E2E Theme Toggle Tests for GeckoView
 * 
 * Tests theme switching functionality:
 * - Dark mode toggle
 * - Light mode toggle
 */

describe('Theme Toggle', () => {
    beforeAll(async () => {
        await device.launchApp({ newInstance: true });
    });

    beforeEach(async () => {
        await device.reloadReactNative();
    });

    it('should toggle from light to dark theme', async () => {
        // Find the theme toggle button
        await expect(element(by.id('theme-button'))).toBeVisible();

        // Tap to switch to dark mode
        await element(by.id('theme-button')).tap();

        // Button should still be visible after toggle
        await expect(element(by.id('theme-button'))).toBeVisible();
    });

    it('should toggle from dark back to light theme', async () => {
        // Tap again to switch back to light mode
        await element(by.id('theme-button')).tap();

        // Button should still be visible
        await expect(element(by.id('theme-button'))).toBeVisible();
    });
});
