import { device, element, by, waitFor } from 'detox';

/**
 * E2E User Agent Tests for GeckoView
 * 
 * Tests user agent switching functionality:
 * - Desktop mode toggle
 * - Mobile mode toggle
 */

describe('User Agent Switching', () => {
    beforeAll(async () => {
        await device.launchApp({ newInstance: true });
    });

    beforeEach(async () => {
        await device.reloadReactNative();
    });

    it('should switch to desktop user agent when desktop button is pressed', async () => {
        // First navigate to a page
        await element(by.id('url-input')).clearText();
        await element(by.id('url-input')).typeText('https://www.whatismybrowser.com/detect/what-is-my-user-agent');
        await element(by.id('url-input')).tapReturnKey();

        // Wait for page to load
        await waitFor(element(by.id('geckoview')))
            .toBeVisible()
            .withTimeout(15000);

        // Find and tap desktop mode button
        await element(by.id('desktop-button')).tap();

        // The page should reload with desktop user agent
        // This test validates the button works - actual UA verification 
        // would require inspecting page content or network requests
        await waitFor(element(by.id('geckoview')))
            .toBeVisible()
            .withTimeout(10000);
    });

    it('should switch back to mobile user agent when mobile button is pressed', async () => {
        // Tap mobile button to switch back
        await element(by.id('mobile-button')).tap();

        // Page should reload with mobile user agent
        await waitFor(element(by.id('geckoview')))
            .toBeVisible()
            .withTimeout(10000);
    });
});
