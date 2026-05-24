package com.reactnative.geckoview

import android.content.Context
import android.media.AudioFocusRequest
import android.media.AudioManager
import com.facebook.react.uimanager.ThemedReactContext
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.MediaSession
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests for [MediaDelegate] — media-session event emission and
 * audio-focus lifecycle.
 *
 * `MediaSession.Metadata` is final and cannot be mocked by MockK under
 * Robolectric; metadata paths are covered by device integration tests.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MediaDelegateTest {

    private lateinit var context: ThemedReactContext
    private val module: GeckoViewModule = mockk(relaxed = true)
    private val session: GeckoSession = mockk(relaxed = true)
    private val audioManager: AudioManager = mockk(relaxed = true)
    private val mediaSession: MediaSession = mockk(relaxed = true)

    private lateinit var mediaDelegate: MediaDelegate
    private val testViewId = 789

    @Before
    fun setUp() {
        TestHelpers.mockArguments()
        context = mockk(relaxed = true)
        every { context.getSystemService(Context.AUDIO_SERVICE) } returns audioManager
        every { context.applicationContext } returns context
    }

    @After
    fun tearDown() {
        TestHelpers.unmockArguments()
    }

    // --- Audio focus: play/pause/stop ---

    @Test
    fun `onPlay requests audio focus when handleAudioFocus is true`() {
        mediaDelegate = MediaDelegate(context, module, true, testViewId)
        mediaDelegate.onPlay(session, mediaSession)

        verify { audioManager.requestAudioFocus(any<AudioFocusRequest>()) }
        verify { module.sendViewEvent(testViewId, "onMediaSessionAction", any()) }
    }

    @Test
    fun `onPlay does not request audio focus when handleAudioFocus is false`() {
        mediaDelegate = MediaDelegate(context, module, false, testViewId)
        mediaDelegate.onPlay(session, mediaSession)

        verify(exactly = 0) { audioManager.requestAudioFocus(any<AudioFocusRequest>()) }
        verify { module.sendViewEvent(testViewId, "onMediaSessionAction", any()) }
    }

    @Test
    fun `onPlay handles multiple consecutive play calls with audio focus`() {
        mediaDelegate = MediaDelegate(context, module, true, testViewId)
        repeat(3) { mediaDelegate.onPlay(session, mediaSession) }

        verify(exactly = 3) { audioManager.requestAudioFocus(any<AudioFocusRequest>()) }
        verify(exactly = 2) { audioManager.abandonAudioFocusRequest(any<AudioFocusRequest>()) }
        verify(exactly = 3) { module.sendViewEvent(testViewId, "onMediaSessionAction", any()) }
    }

    @Test
    fun `onPause abandons audio focus when handleAudioFocus is true`() {
        mediaDelegate = MediaDelegate(context, module, true, testViewId)
        mediaDelegate.onPlay(session, mediaSession)
        mediaDelegate.onPause(session, mediaSession)

        verify { audioManager.abandonAudioFocusRequest(any<AudioFocusRequest>()) }
        verify(exactly = 2) { module.sendViewEvent(testViewId, "onMediaSessionAction", any()) }
    }

    @Test
    fun `onPause does not abandon audio focus when handleAudioFocus is false`() {
        mediaDelegate = MediaDelegate(context, module, false, testViewId)
        mediaDelegate.onPlay(session, mediaSession)
        mediaDelegate.onPause(session, mediaSession)

        verify(exactly = 0) { audioManager.abandonAudioFocusRequest(any<AudioFocusRequest>()) }
        verify(exactly = 2) { module.sendViewEvent(testViewId, "onMediaSessionAction", any()) }
    }

    @Test
    fun `onStop abandons audio focus when handleAudioFocus is true`() {
        mediaDelegate = MediaDelegate(context, module, true, testViewId)
        mediaDelegate.onPlay(session, mediaSession)
        mediaDelegate.onStop(session, mediaSession)

        verify { audioManager.abandonAudioFocusRequest(any<AudioFocusRequest>()) }
        verify(exactly = 2) { module.sendViewEvent(testViewId, "onMediaSessionAction", any()) }
    }

    @Test
    fun `onStop does not abandon audio focus when handleAudioFocus is false`() {
        mediaDelegate = MediaDelegate(context, module, false, testViewId)
        mediaDelegate.onPlay(session, mediaSession)
        mediaDelegate.onStop(session, mediaSession)

        verify(exactly = 0) { audioManager.abandonAudioFocusRequest(any<AudioFocusRequest>()) }
        verify(exactly = 2) { module.sendViewEvent(testViewId, "onMediaSessionAction", any()) }
    }

    // --- Event emission regardless of focus handling ---

    @Test
    fun `all media actions emit events regardless of audio focus setting`() {
        mediaDelegate = MediaDelegate(context, module, true, testViewId)
        mediaDelegate.onPlay(session, mediaSession)
        mediaDelegate.onPause(session, mediaSession)
        mediaDelegate.onStop(session, mediaSession)

        verify(exactly = 3) { module.sendViewEvent(testViewId, "onMediaSessionAction", any()) }
    }

    @Test
    fun `media session sequence - play pause play stop`() {
        mediaDelegate = MediaDelegate(context, module, true, testViewId)
        mediaDelegate.onPlay(session, mediaSession)
        mediaDelegate.onPause(session, mediaSession)
        mediaDelegate.onPlay(session, mediaSession)
        mediaDelegate.onStop(session, mediaSession)

        verify(exactly = 2) { audioManager.requestAudioFocus(any<AudioFocusRequest>()) }
        verify(exactly = 3) { audioManager.abandonAudioFocusRequest(any<AudioFocusRequest>()) }
        verify(exactly = 4) { module.sendViewEvent(testViewId, "onMediaSessionAction", any()) }
    }

    // --- Integration ---

    @Test
    fun `typical playback session with audio focus`() {
        mediaDelegate = MediaDelegate(context, module, true, testViewId)

        mediaDelegate.onPlay(session, mediaSession)
        mediaDelegate.onPause(session, mediaSession)
        mediaDelegate.onPlay(session, mediaSession)
        mediaDelegate.onStop(session, mediaSession)

        verify(exactly = 2) { audioManager.requestAudioFocus(any<AudioFocusRequest>()) }
        verify(exactly = 3) { audioManager.abandonAudioFocusRequest(any<AudioFocusRequest>()) }
    }

    @Test
    fun `typical playback session without audio focus`() {
        mediaDelegate = MediaDelegate(context, module, false, testViewId)
        mediaDelegate.onPlay(session, mediaSession)
        mediaDelegate.onPause(session, mediaSession)
        mediaDelegate.onPlay(session, mediaSession)
        mediaDelegate.onStop(session, mediaSession)

        verify(exactly = 0) { audioManager.requestAudioFocus(any<AudioFocusRequest>()) }
        verify(exactly = 0) { audioManager.abandonAudioFocusRequest(any<AudioFocusRequest>()) }
        verify(exactly = 4) { module.sendViewEvent(testViewId, "onMediaSessionAction", any()) }
    }

    @Test
    fun `rapid play-stop cycles`() {
        mediaDelegate = MediaDelegate(context, module, true, testViewId)
        repeat(5) {
            mediaDelegate.onPlay(session, mediaSession)
            mediaDelegate.onStop(session, mediaSession)
        }

        verify(exactly = 5) { audioManager.requestAudioFocus(any<AudioFocusRequest>()) }
        verify(exactly = 9) { audioManager.abandonAudioFocusRequest(any<AudioFocusRequest>()) }
        verify(exactly = 10) { module.sendViewEvent(testViewId, "onMediaSessionAction", any()) }
    }

    // --- Edge cases ---

    @Test
    fun `pause without prior play still emits event`() {
        mediaDelegate = MediaDelegate(context, module, true, testViewId)
        mediaDelegate.onPause(session, mediaSession)

        verify { module.sendViewEvent(testViewId, "onMediaSessionAction", any()) }
        verify(exactly = 0) { audioManager.abandonAudioFocusRequest(any<AudioFocusRequest>()) }
    }

    @Test
    fun `stop without prior play still emits event`() {
        mediaDelegate = MediaDelegate(context, module, true, testViewId)
        mediaDelegate.onStop(session, mediaSession)

        verify { module.sendViewEvent(testViewId, "onMediaSessionAction", any()) }
        verify(exactly = 0) { audioManager.abandonAudioFocusRequest(any<AudioFocusRequest>()) }
    }

    // --- Activation / deactivation ---

    @Test
    fun `onActivated emits activated event`() {
        mediaDelegate = MediaDelegate(context, module, false, testViewId)
        mediaDelegate.onActivated(session, mediaSession)

        verify { module.sendViewEvent(testViewId, "onMediaSessionAction", any()) }
    }

    @Test
    fun `onDeactivated abandons audio focus when handleAudioFocus is true`() {
        mediaDelegate = MediaDelegate(context, module, true, testViewId)
        mediaDelegate.onPlay(session, mediaSession)
        mediaDelegate.onDeactivated(session, mediaSession)

        verify { audioManager.abandonAudioFocusRequest(any<AudioFocusRequest>()) }
        verify(exactly = 2) { module.sendViewEvent(testViewId, "onMediaSessionAction", any()) }
    }

    @Test
    fun `onDeactivated does not abandon audio focus when handleAudioFocus is false`() {
        mediaDelegate = MediaDelegate(context, module, false, testViewId)
        mediaDelegate.onPlay(session, mediaSession)
        mediaDelegate.onDeactivated(session, mediaSession)

        verify(exactly = 0) { audioManager.abandonAudioFocusRequest(any<AudioFocusRequest>()) }
        verify(exactly = 2) { module.sendViewEvent(testViewId, "onMediaSessionAction", any()) }
    }

    // --- Audio-focus result handling ---

    @Test
    fun `onPlay handles audio focus request granted`() {
        every { audioManager.requestAudioFocus(any<AudioFocusRequest>()) } returns
            AudioManager.AUDIOFOCUS_REQUEST_GRANTED

        mediaDelegate = MediaDelegate(context, module, true, testViewId)
        mediaDelegate.onPlay(session, mediaSession)

        verify { audioManager.requestAudioFocus(any<AudioFocusRequest>()) }
        verify { module.sendViewEvent(testViewId, "onMediaSessionAction", any()) }
    }

    @Test
    fun `onPlay handles audio focus request denied`() {
        every { audioManager.requestAudioFocus(any<AudioFocusRequest>()) } returns
            AudioManager.AUDIOFOCUS_REQUEST_FAILED

        mediaDelegate = MediaDelegate(context, module, true, testViewId)
        mediaDelegate.onPlay(session, mediaSession)

        verify { audioManager.requestAudioFocus(any<AudioFocusRequest>()) }
        verify { module.sendViewEvent(testViewId, "onMediaSessionAction", any()) }
    }

    // --- Complete lifecycles ---

    @Test
    fun `full media lifecycle - activated play pause play stop deactivated`() {
        mediaDelegate = MediaDelegate(context, module, true, testViewId)
        mediaDelegate.onActivated(session, mediaSession)
        mediaDelegate.onPlay(session, mediaSession)
        mediaDelegate.onPause(session, mediaSession)
        mediaDelegate.onPlay(session, mediaSession)
        mediaDelegate.onStop(session, mediaSession)
        mediaDelegate.onDeactivated(session, mediaSession)

        verify(exactly = 6) { module.sendViewEvent(testViewId, "onMediaSessionAction", any()) }
    }

    @Test
    fun `background app scenario - play deactivated reactivated play`() {
        mediaDelegate = MediaDelegate(context, module, true, testViewId)
        mediaDelegate.onPlay(session, mediaSession)
        mediaDelegate.onDeactivated(session, mediaSession)
        mediaDelegate.onActivated(session, mediaSession)
        mediaDelegate.onPlay(session, mediaSession)

        verify(exactly = 2) { audioManager.requestAudioFocus(any<AudioFocusRequest>()) }
        verify(exactly = 4) { module.sendViewEvent(testViewId, "onMediaSessionAction", any()) }
    }

    // --- Multiple sessions ---

    @Test
    fun `handle multiple different media sessions`() {
        val mediaSession2: MediaSession = mockk(relaxed = true)
        mediaDelegate = MediaDelegate(context, module, true, testViewId)

        mediaDelegate.onPlay(session, mediaSession)
        mediaDelegate.onPlay(session, mediaSession2)
        mediaDelegate.onStop(session, mediaSession2)

        verify(exactly = 2) { audioManager.requestAudioFocus(any<AudioFocusRequest>()) }
        verify(exactly = 3) { module.sendViewEvent(testViewId, "onMediaSessionAction", any()) }
    }

    @Test
    fun `interleaved play pause from different sessions`() {
        val mediaSession2: MediaSession = mockk(relaxed = true)
        mediaDelegate = MediaDelegate(context, module, true, testViewId)

        mediaDelegate.onPlay(session, mediaSession)
        mediaDelegate.onPlay(session, mediaSession2)
        mediaDelegate.onPause(session, mediaSession)
        mediaDelegate.onPause(session, mediaSession2)

        verify(exactly = 4) { module.sendViewEvent(testViewId, "onMediaSessionAction", any()) }
    }

    // --- Stress ---

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

        verify(exactly = 100) { module.sendViewEvent(testViewId, "onMediaSessionAction", any()) }
    }
}
