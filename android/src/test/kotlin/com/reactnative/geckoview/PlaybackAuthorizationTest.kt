package com.reactnative.geckoview

import org.junit.Assert.*
import org.junit.Test

class PlaybackAuthorizationTest {
    @Test fun `unknown startup is blocked and initial parked display can autoplay`() {
        val policy = PlaybackAuthorization()
        assertFalse(policy.allowed)
        assertFalse(policy.authorize())
        policy.update(true, false, true)
        assertTrue(policy.allowed)
    }
    @Test fun `drive then park stays blocked with no JavaScript execution`() {
        val policy = PlaybackAuthorization()
        policy.update(true, false, true)
        policy.update(true, true, true)
        assertFalse(policy.authorize())
        policy.update(true, false, true)
        assertFalse(policy.allowed)
        assertTrue(policy.resumeRequired)
        assertTrue(policy.authorize())
        assertTrue(policy.allowed)
    }
    @Test fun `service loss and reconnect require explicit authorization`() {
        val policy = PlaybackAuthorization()
        policy.update(true, false, true)
        policy.update(false, false, true)
        assertFalse(policy.allowed)
        assertFalse(policy.authorize())
        policy.update(true, false, true)
        assertFalse(policy.allowed)
        assertTrue(policy.authorize())
    }
    @Test fun `background and foreground never resume automatically`() {
        val policy = PlaybackAuthorization()
        policy.update(true, false, true)
        policy.background()
        assertFalse(policy.authorize())
        policy.update(true, false, true)
        assertFalse(policy.allowed)
        assertTrue(policy.authorize())
    }
    @Test fun `unrestricted passenger display remains allowed`() {
        val policy = PlaybackAuthorization()
        // Vehicle motion alone is not an input: only this display's UXR is.
        policy.update(true, false, true)
        assertTrue(policy.allowed)
    }
}
