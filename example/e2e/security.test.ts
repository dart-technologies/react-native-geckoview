import { device, element, by, expect, waitFor } from 'detox';

/**
 * E2E Security Indicator Tests for GeckoView
 * 
 * Tests security indicator functionality:
 * - HTTPS secure indicator
 * - HTTP insecure indicator
 */

describe('Security Indicators', () => {
    beforeAll(async () => {
        await device.launchApp({ newInstance: true });
    });

    beforeEach(async () => {
        await device.reloadReactNative();
    });

    it('should show secure indicator for HTTPS sites', async () => {
        // Navigate to an HTTPS site
        await element(by.id('url-input')).clearText();
        await element(by.id('url-input')).typeText('https://example.com');
        await element(by.id('url-input')).tapReturnKey();

        // Wait for page to load
        await waitFor(element(by.id('geckoview')))
            .toBeVisible()
            .withTimeout(15000);

        // Check for secure indicator visibility
        await expect(element(by.id('security-indicator'))).toBeVisible();
    });

    it('should show indicator for HTTP sites', async () => {
        // Navigate to an HTTP site
        await element(by.id('url-input')).clearText();
        await element(by.id('url-input')).typeText('http://neverssl.com');
        await element(by.id('url-input')).tapReturnKey();

        // Wait for page to load
        await waitFor(element(by.id('geckoview')))
            .toBeVisible()
            .withTimeout(15000);

        // Check for indicator visibility
        await expect(element(by.id('security-indicator'))).toBeVisible();
    });
});
