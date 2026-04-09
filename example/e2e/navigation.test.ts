import { device, element, by, expect, waitFor } from 'detox';

/**
 * E2E Navigation Tests for GeckoView
 * 
 * Tests core browser navigation functionality:
 * - URL loading
 * - Reload
 * - URL bar updates
 */

describe('Navigation', () => {
    beforeAll(async () => {
        await device.launchApp({ newInstance: true });
    });

    beforeEach(async () => {
        await device.reloadReactNative();
    });

    it('should display the URL bar', async () => {
        await expect(element(by.id('url-input'))).toBeVisible();
    });

    it('should load a URL when submitted', async () => {
        // Clear and type a new URL
        await element(by.id('url-input')).clearText();
        await element(by.id('url-input')).typeText('https://example.com');
        await element(by.id('url-input')).tapReturnKey();

        // Wait for page to load
        await waitFor(element(by.id('geckoview')))
            .toBeVisible()
            .withTimeout(15000);

        // URL bar should update with the loaded URL
        await waitFor(element(by.id('url-input')))
            .toHaveText('https://example.com/')
            .withTimeout(10000);
    });

    it('should reload the page when reload button is pressed', async () => {
        // Navigate to a page
        await element(by.id('url-input')).clearText();
        await element(by.id('url-input')).typeText('https://example.com');
        await element(by.id('url-input')).tapReturnKey();
        await waitFor(element(by.id('url-input')))
            .toHaveText('https://example.com/')
            .withTimeout(15000);

        // Press reload button
        await element(by.id('reload-button')).tap();

        // Page should still show same URL after reload
        await waitFor(element(by.id('url-input')))
            .toHaveText('https://example.com/')
            .withTimeout(15000);
    });

    it('should add https:// prefix to URLs without protocol', async () => {
        await element(by.id('url-input')).clearText();
        await element(by.id('url-input')).typeText('example.com');
        await element(by.id('url-input')).tapReturnKey();

        // Should add https:// prefix
        await waitFor(element(by.id('url-input')))
            .toHaveText('https://example.com/')
            .withTimeout(15000);
    });

    it('should handle search queries', async () => {
        await element(by.id('url-input')).clearText();
        await element(by.id('url-input')).typeText('test search query');
        await element(by.id('url-input')).tapReturnKey();

        // Should redirect to a search engine (wait for geckoview to be visible)
        await waitFor(element(by.id('geckoview')))
            .toBeVisible()
            .withTimeout(15000);
    });
});
