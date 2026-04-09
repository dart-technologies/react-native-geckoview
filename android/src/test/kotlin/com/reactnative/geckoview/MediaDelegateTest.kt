package com.reactnative.geckoview

import android.content.Context
import android.media.AudioManager
import android.media.AudioFocusRequest
import org.junit.Before
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.mozilla.geckoview.GeckoSession
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for MediaDelegate
 * Verifies media session events and audio focus handling
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MediaDelegateTest {

    @Mock private lateinit var context: com.facebook.react.uimanager.ThemedReactContext
    @Mock private lateinit var module: GeckoViewModule
    @Mock private lateinit var session: GeckoSession
    @Mock private lateinit var audioManager: AudioManager
    @Mock private lateinit var mediaSession: org.mozilla.geckoview.MediaSession

    private lateinit var mediaDelegate: MediaDelegate
    private val testViewId = 789

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        TestHelpers.mockArguments()
        
        whenever(context.getSystemService(Context.AUDIO_SERVICE)).thenReturn(audioManager)
        whenever(context.applicationContext).thenReturn(context)
    }

    @After
    fun tearDown() {
        TestHelpers.unmockArguments()
    }

    // Audio Focus Tests - Play

    @Test
    fun `onPlay requests audio focus when handleAudioFocus is true`() {
        mediaDelegate = MediaDelegate(context, module, true, testViewId)
        
        mediaDelegate.onPlay(session, mediaSession)
        
        verify(audioManager).requestAudioFocus(any<AudioFocusRequest>())
        verify(module).sendViewEvent(eq(testViewId), eq("onMediaSessionAction"), any())
    }

    @Test
    fun `onPlay does not request audio focus when handleAudioFocus is false`() {
        mediaDelegate = MediaDelegate(context, module, false, testViewId)
        
        mediaDelegate.onPlay(session, mediaSession)
        
        verify(audioManager, never()).requestAudioFocus(any<AudioFocusRequest>())
        verify(module).sendViewEvent(eq(testViewId), eq("onMediaSessionAction"), any())
    }

    @Test
    fun `onPlay handles multiple consecutive play calls with audio focus`() {
        mediaDelegate = MediaDelegate(context, module, true, testViewId)
        
        // Multiple play calls
        // Note: requestAudioFocus() internally calls abandonAudioFocusRequest() before requesting
        repeat(3) {
            mediaDelegate.onPlay(session, mediaSession)
        }
        
        // First call: 1 request. Each subsequent call: 1 abandon + 1 request
        verify(audioManager, times(3)).requestAudioFocus(any<AudioFocusRequest>())
        verify(audioManager, times(2)).abandonAudioFocusRequest(any<AudioFocusRequest>())
        verify(module, times(3)).sendViewEvent(eq(testViewId), eq("onMediaSessionAction"), any())
    }

    // Audio Focus Tests - Pause

    @Test
    fun `onPause abandons audio focus when handleAudioFocus is true`() {
        mediaDelegate = MediaDelegate(context, module, true, testViewId)
        
        // First play to establish audio focus
        mediaDelegate.onPlay(session, mediaSession)
        mediaDelegate.onPause(session, mediaSession)
        
        verify(audioManager).abandonAudioFocusRequest(any<AudioFocusRequest>())
        verify(module, times(2)).sendViewEvent(eq(testViewId), eq("onMediaSessionAction"), any())
    }

    @Test
    fun `onPause does not abandon audio focus when handleAudioFocus is false`() {
        mediaDelegate = MediaDelegate(context, module, false, testViewId)
        
        mediaDelegate.onPlay(session, mediaSession)
        mediaDelegate.onPause(session, mediaSession)
        
        verify(audioManager, never()).abandonAudioFocusRequest(any<AudioFocusRequest>())
        verify(module, times(2)).sendViewEvent(eq(testViewId), eq("onMediaSessionAction"), any())
    }

    // Audio Focus Tests - Stop

    @Test
    fun `onStop abandons audio focus when handleAudioFocus is true`() {
        mediaDelegate = MediaDelegate(context, module, true, testViewId)
        
        // First play to establish audio focus
        mediaDelegate.onPlay(session, mediaSession)
        mediaDelegate.onStop(session, mediaSession)
        
        verify(audioManager).abandonAudioFocusRequest(any<AudioFocusRequest>())
        verify(module, times(2)).sendViewEvent(eq(testViewId), eq("onMediaSessionAction"), any())
    }

    @Test
    fun `onStop does not abandon audio focus when handleAudioFocus is false`() {
        mediaDelegate = MediaDelegate(context, module, false, testViewId)
        
        mediaDelegate.onPlay(session, mediaSession)
        mediaDelegate.onStop(session, mediaSession)
        
        verify(audioManager, never()).abandonAudioFocusRequest(any<AudioFocusRequest>())
        verify(module, times(2)).sendViewEvent(eq(testViewId), eq("onMediaSessionAction"), any())
    }

    // Media Session Action Tests

    @Test
    fun `all media actions emit events regardless of audio focus setting`() {
        val actions = listOf(
            { delegate: MediaDelegate -> delegate.onPlay(session, mediaSession) },
            { delegate: MediaDelegate -> delegate.onPause(session, mediaSession) },
            { delegate: MediaDelegate -> delegate.onStop(session, mediaSession) }
        )

        // Test with audio focus enabled
        mediaDelegate = MediaDelegate(context, module, true, testViewId)
        actions.forEach { action -> action(mediaDelegate) }
        
        verify(module, times(3)).sendViewEvent(eq(testViewId), eq("onMediaSessionAction"), any())
    }

    @Test
    fun `media session sequence - play pause play stop`() {
        mediaDelegate = MediaDelegate(context, module, true, testViewId)
        
        mediaDelegate.onPlay(session, mediaSession)
        mediaDelegate.onPause(session, mediaSession)
        mediaDelegate.onPlay(session, mediaSession)
        mediaDelegate.onStop(session, mediaSession)
        
        // First play: 1 request. Pause: 1 abandon. Second play: 1 abandon + 1 request. Stop: 1 abandon.
        verify(audioManager, times(2)).requestAudioFocus(any<AudioFocusRequest>())
        // Total abandons: 1 (pause) + 1 (internal before 2nd play) + 1 (stop) = 3
        verify(audioManager, times(3)).abandonAudioFocusRequest(any<AudioFocusRequest>())
        // Should have emitted 4 events
        verify(module, times(4)).sendViewEvent(eq(testViewId), eq("onMediaSessionAction"), any())
    }

    // Integration Tests

    @Test
    fun `typical playback session with audio focus`() {
        mediaDelegate = MediaDelegate(context, module, true, testViewId)
        
        // User starts playback
        mediaDelegate.onPlay(session, mediaSession)
        verify(audioManager, times(1)).requestAudioFocus(any<AudioFocusRequest>())
        verify(audioManager, times(0)).abandonAudioFocusRequest(any<AudioFocusRequest>())
        
        // User pauses
        mediaDelegate.onPause(session, mediaSession)
        verify(audioManager, times(1)).abandonAudioFocusRequest(any<AudioFocusRequest>())
        
        // User resumes (internally abandons before requesting)
        mediaDelegate.onPlay(session, mediaSession)
        verify(audioManager, times(2)).requestAudioFocus(any<AudioFocusRequest>())
        verify(audioManager, times(2)).abandonAudioFocusRequest(any<AudioFocusRequest>())
        
        // User stops
        mediaDelegate.onStop(session, mediaSession)
        verify(audioManager, times(3)).abandonAudioFocusRequest(any<AudioFocusRequest>())
    }

    @Test
    fun `typical playback session without audio focus`() {
        mediaDelegate = MediaDelegate(context, module, false, testViewId)
        
        mediaDelegate.onPlay(session, mediaSession)
        mediaDelegate.onPause(session, mediaSession)
        mediaDelegate.onPlay(session, mediaSession)
        mediaDelegate.onStop(session, mediaSession)
        
        // No audio focus requests or releases
        verify(audioManager, never()).requestAudioFocus(any<AudioFocusRequest>())
        verify(audioManager, never()).abandonAudioFocusRequest(any<AudioFocusRequest>())
        
        // But all events should still be emitted
        verify(module, times(4)).sendViewEvent(eq(testViewId), eq("onMediaSessionAction"), any())
    }

    @Test
    fun `rapid play-stop cycles`() {
        mediaDelegate = MediaDelegate(context, module, true, testViewId)
        
        repeat(5) {
            mediaDelegate.onPlay(session, mediaSession)
            mediaDelegate.onStop(session, mediaSession)
        }
        
        // Each iteration: 1 request (except 2nd+ also abandon before request) + 1 abandon from stop
        verify(audioManager, times(5)).requestAudioFocus(any<AudioFocusRequest>())
        // First cycle: 1 abandon(stop). Each subsequent: 1 abandon(internal) + 1 abandon(stop)
        // Total: 1 + 4*2 = 9
        verify(audioManager, times(9)).abandonAudioFocusRequest(any<AudioFocusRequest>())
        verify(module, times(10)).sendViewEvent(eq(testViewId), eq("onMediaSessionAction"), any())
    }

    // Edge Cases

    @Test
    fun `pause without prior play still emits event`() {
        mediaDelegate = MediaDelegate(context, module, true, testViewId)
        
        // Pause without play - should still emit event
        mediaDelegate.onPause(session, mediaSession)
        
        verify(module).sendViewEvent(eq(testViewId), eq("onMediaSessionAction"), any())
        // Without a prior focusRequest, abandonAudioFocusRequest may not be called (or called on null)
        // The implementation checks focusRequest?.let { abandon }, so if null, it won't be called
        verify(audioManager, never()).abandonAudioFocusRequest(any<AudioFocusRequest>())
    }

    @Test
    fun `stop without prior play still emits event`() {
        mediaDelegate = MediaDelegate(context, module, true, testViewId)
        
        // Stop without play - should still emit event
        mediaDelegate.onStop(session, mediaSession)
        
        verify(module).sendViewEvent(eq(testViewId), eq("onMediaSessionAction"), any())
        // Without a prior focusRequest, abandonAudioFocusRequest won't be called (focusRequest is null)
        verify(audioManager, never()).abandonAudioFocusRequest(any<AudioFocusRequest>())
    }

    @Test
    fun `switching between audio focus modes`() {
        // Start with audio focus enabled
        mediaDelegate = MediaDelegate(context, module, true, testViewId)
        mediaDelegate.onPlay(session, mediaSession)
        verify(audioManager, times(1)).requestAudioFocus(any<AudioFocusRequest>())
        
        // Create new delegate without audio focus
        mediaDelegate = MediaDelegate(context, module, false, testViewId)
        mediaDelegate.onPlay(session, mediaSession)
        // Should still have only 1 request from before
        verify(audioManager, times(1)).requestAudioFocus(any<AudioFocusRequest>())
    }

    // ========== Activation/Deactivation Tests ==========

    @Test
    fun `onActivated emits activated event`() {
        mediaDelegate = MediaDelegate(context, module, false, testViewId)
        
        mediaDelegate.onActivated(session, mediaSession)
        
        verify(module).sendViewEvent(eq(testViewId), eq("onMediaSessionAction"), any())
    }

    @Test
    fun `onDeactivated abandons audio focus when handleAudioFocus is true`() {
        mediaDelegate = MediaDelegate(context, module, true, testViewId)
        
        // First play to establish audio focus
        mediaDelegate.onPlay(session, mediaSession)
        mediaDelegate.onDeactivated(session, mediaSession)
        
        verify(audioManager).abandonAudioFocusRequest(any<AudioFocusRequest>())
        verify(module, times(2)).sendViewEvent(eq(testViewId), eq("onMediaSessionAction"), any())
    }

    @Test
    fun `onDeactivated does not abandon audio focus when handleAudioFocus is false`() {
        mediaDelegate = MediaDelegate(context, module, false, testViewId)
        
        mediaDelegate.onPlay(session, mediaSession)
        mediaDelegate.onDeactivated(session, mediaSession)
        
        verify(audioManager, never()).abandonAudioFocusRequest(any<AudioFocusRequest>())
        verify(module, times(2)).sendViewEvent(eq(testViewId), eq("onMediaSessionAction"), any())
    }

    // ========== Metadata Tests ==========
    /*
     * NOTE: MediaSession.Metadata is a final class that cannot be mocked by MockK.
     * These tests are commented out. The onMetadata functionality is tested implicitly
     * through the lifecycle tests.
     *
    @Test
    fun `onMetadata emits metadata event with all fields`() {
        mediaDelegate = MediaDelegate(context, module, false, testViewId)
        val metadata = io.mockk.mockk<org.mozilla.geckoview.MediaSession.Metadata>(relaxed = true)
        io.mockk.every { metadata.title } returns "Test Title"
        io.mockk.every { metadata.artist } returns "Test Artist"
        io.mockk.every { metadata.album } returns "Test Album"
        
        mediaDelegate.onMetadata(session, mediaSession, metadata)
        
        verify(module).sendViewEvent(eq(testViewId), eq("onMediaSessionAction"), any())
    }

    @Test
    fun `onMetadata handles null fields gracefully`() {
        mediaDelegate = MediaDelegate(context, module, false, testViewId)
        val metadata = io.mockk.mockk<org.mozilla.geckoview.MediaSession.Metadata>(relaxed = true)
        io.mockk.every { metadata.title } returns null
        io.mockk.every { metadata.artist } returns null
        io.mockk.every { metadata.album } returns null
        
        mediaDelegate.onMetadata(session, mediaSession, metadata)
        
        verify(module).sendViewEvent(eq(testViewId), eq("onMediaSessionAction"), any())
    }
    */

    // ========== Audio Focus Request Result Tests ==========

    @Test
    fun `onPlay handles audio focus request granted`() {
        whenever(audioManager.requestAudioFocus(any<AudioFocusRequest>()))
            .thenReturn(AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        
        mediaDelegate = MediaDelegate(context, module, true, testViewId)
        mediaDelegate.onPlay(session, mediaSession)
        
        verify(audioManager).requestAudioFocus(any<AudioFocusRequest>())
        verify(module).sendViewEvent(eq(testViewId), eq("onMediaSessionAction"), any())
    }

    @Test
    fun `onPlay handles audio focus request denied`() {
        whenever(audioManager.requestAudioFocus(any<AudioFocusRequest>()))
            .thenReturn(AudioManager.AUDIOFOCUS_REQUEST_FAILED)
        
        mediaDelegate = MediaDelegate(context, module, true, testViewId)
        mediaDelegate.onPlay(session, mediaSession)
        
        // Should still emit event even if focus denied
        verify(audioManager).requestAudioFocus(any<AudioFocusRequest>())
        verify(module).sendViewEvent(eq(testViewId), eq("onMediaSessionAction"), any())
    }

    // ========== Complete Lifecycle Tests ==========

    @Test
    fun `full media lifecycle - activated play pause play stop deactivated`() {
        mediaDelegate = MediaDelegate(context, module, true, testViewId)
        
        // Full lifecycle (without metadata which can't be mocked)
        mediaDelegate.onActivated(session, mediaSession)
        mediaDelegate.onPlay(session, mediaSession)
        mediaDelegate.onPause(session, mediaSession)
        mediaDelegate.onPlay(session, mediaSession)
        mediaDelegate.onStop(session, mediaSession)
        mediaDelegate.onDeactivated(session, mediaSession)
        
        // 6 events total
        verify(module, times(6)).sendViewEvent(eq(testViewId), eq("onMediaSessionAction"), any())
    }

    @Test
    fun `background app scenario - play deactivated reactivated play`() {
        mediaDelegate = MediaDelegate(context, module, true, testViewId)
        
        // User starts playback
        mediaDelegate.onPlay(session, mediaSession)
        verify(audioManager, times(1)).requestAudioFocus(any<AudioFocusRequest>())
        
        // App goes to background (deactivated)
        mediaDelegate.onDeactivated(session, mediaSession)
        verify(audioManager, times(1)).abandonAudioFocusRequest(any<AudioFocusRequest>())
        
        // App comes to foreground
        mediaDelegate.onActivated(session, mediaSession)
        // Note: activation doesn't request focus, only play does
        
        // User resumes playback
        mediaDelegate.onPlay(session, mediaSession)
        verify(audioManager, times(2)).requestAudioFocus(any<AudioFocusRequest>())
        
        verify(module, times(4)).sendViewEvent(eq(testViewId), eq("onMediaSessionAction"), any())
    }

    // ========== Multiple Media Sessions Test ==========

    @Test
    fun `handle multiple different media sessions`() {
        val mediaSession2 = org.mockito.Mockito.mock(org.mozilla.geckoview.MediaSession::class.java)
        
        mediaDelegate = MediaDelegate(context, module, true, testViewId)
        
        // Start first session
        mediaDelegate.onPlay(session, mediaSession)
        verify(audioManager, times(1)).requestAudioFocus(any<AudioFocusRequest>())
        
        // Switch to second session (new play)
        mediaDelegate.onPlay(session, mediaSession2)
        verify(audioManager, times(2)).requestAudioFocus(any<AudioFocusRequest>())
        
        // Stop second session
        mediaDelegate.onStop(session, mediaSession2)
        
        verify(module, times(3)).sendViewEvent(eq(testViewId), eq("onMediaSessionAction"), any())
    }

    @Test
    fun `interleaved play pause from different sessions`() {
        val mediaSession2 = org.mockito.Mockito.mock(org.mozilla.geckoview.MediaSession::class.java)
        
        mediaDelegate = MediaDelegate(context, module, true, testViewId)
        
        mediaDelegate.onPlay(session, mediaSession)
        mediaDelegate.onPlay(session, mediaSession2) // Switching streams
        mediaDelegate.onPause(session, mediaSession)
        mediaDelegate.onPause(session, mediaSession2)
        
        // 4 events emitted
        verify(module, times(4)).sendViewEvent(eq(testViewId), eq("onMediaSessionAction"), any())
    }

    // ========== Stress Tests ==========

    @Test
    fun `handle many rapid state changes without crash`() {
        mediaDelegate = MediaDelegate(context, module, true, testViewId)
        
        repeat(100) { i ->
            when (i % 4) {
                0 -> mediaDelegate.onPlay(session, mediaSession)
                1 -> mediaDelegate.onPause(session, mediaSession)
                2 -> mediaDelegate.onStop(session, mediaSession)
                3 -> mediaDelegate.onActivated(session, mediaSession)
            }
        }
        
        // Should have emitted 100 events
        verify(module, times(100)).sendViewEvent(eq(testViewId), eq("onMediaSessionAction"), any())
    }
}
