package com.reactnative.geckoview

import android.content.Context
import android.media.AudioFocusRequest
import android.media.AudioManager
import com.facebook.react.uimanager.ThemedReactContext
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
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
        // The pool hands views open sessions; a relaxed mock would report
        // closed, and the delegate ignores a closed session's play.
        every { session.isOpen } returns true
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

    // --- Teardown (Codex F2) ---

    @Test
    fun `release abandons the focus taken for active playback`() {
        mediaDelegate = MediaDelegate(context, module, true, testViewId)
        mediaDelegate.onPlay(session, mediaSession)

        // The owning view or session goes away while the page is still playing.
        mediaDelegate.release()

        verify(exactly = 1) { audioManager.abandonAudioFocusRequest(any<AudioFocusRequest>()) }
    }

    @Test
    fun `release abandons the request only once`() {
        mediaDelegate = MediaDelegate(context, module, true, testViewId)
        mediaDelegate.onPlay(session, mediaSession)

        mediaDelegate.release()
        mediaDelegate.release()

        verify(exactly = 1) { audioManager.abandonAudioFocusRequest(any<AudioFocusRequest>()) }
    }

    @Test
    fun `release leaves audio focus alone when the host manages it`() {
        mediaDelegate = MediaDelegate(context, module, false, testViewId)
        mediaDelegate.onPlay(session, mediaSession)

        mediaDelegate.release()

        verify(exactly = 0) { audioManager.abandonAudioFocusRequest(any<AudioFocusRequest>()) }
    }

    // --- A dropped view's pooled session (Codex final review F1) ---

    @Test
    fun `a focus loss pauses the playing media session`() {
        mediaDelegate = MediaDelegate(context, module, true, testViewId)
        mediaDelegate.onPlay(session, mediaSession)

        mediaDelegate.audioFocusChangeListener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS)

        verify(exactly = 1) { mediaSession.pause() }
    }

    @Test
    fun `a delegate detached from its view keeps handling focus without sending events`() {
        mediaDelegate = MediaDelegate(context, module, true, testViewId)
        mediaDelegate.onPlay(session, mediaSession)

        mediaDelegate.detachFromView()
        mediaDelegate.audioFocusChangeListener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS)
        mediaDelegate.onPause(session, mediaSession)

        verify(exactly = 1) { mediaSession.pause() }
        verify(exactly = 1) { audioManager.abandonAudioFocusRequest(any<AudioFocusRequest>()) }
        // Only onPlay, from before the view was dropped, reached the view.
        verify(exactly = 1) { module.sendViewEvent(testViewId, "onMediaSessionAction", any()) }
    }

    @Test
    fun `a delegate detached from its view takes focus again when the hidden page plays`() {
        mediaDelegate = MediaDelegate(context, module, true, testViewId)
        mediaDelegate.detachFromView()

        mediaDelegate.onPlay(session, mediaSession)

        verify(exactly = 1) { audioManager.requestAudioFocus(any<AudioFocusRequest>()) }
        verify(exactly = 0) { module.sendViewEvent(any(), any(), any()) }
    }

    @Test
    fun `a view that binds the session keeps the focus held for media still playing`() {
        mediaDelegate = MediaDelegate(context, module, true, testViewId)
        mediaDelegate.onPlay(session, mediaSession)
        mediaDelegate.detachFromView()
        val nextModule: GeckoViewModule = mockk(relaxed = true)

        mediaDelegate.attachToView(nextModule, testViewId + 1)
        mediaDelegate.setHandleAudioFocus(true)

        // No new request: one would interrupt whatever else is playing.
        verify(exactly = 1) { audioManager.requestAudioFocus(any<AudioFocusRequest>()) }
        verify(exactly = 0) { audioManager.abandonAudioFocusRequest(any<AudioFocusRequest>()) }
        mediaDelegate.audioFocusChangeListener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS)
        verify(exactly = 1) { mediaSession.pause() }
        mediaDelegate.onPause(session, mediaSession)
        verify(exactly = 1) { audioManager.abandonAudioFocusRequest(any<AudioFocusRequest>()) }
        verify(exactly = 2) { nextModule.sendViewEvent(testViewId + 1, "onMediaSessionAction", any()) }
    }

    @Test
    fun `a view that binds the session is told it is active and playing`() {
        // While no view showed the session its events went nowhere, and GeckoView
        // doesn't repeat them for a view that binds it later.
        mediaDelegate = MediaDelegate(context, module, true, testViewId)
        mediaDelegate.detachFromView()
        mediaDelegate.onActivated(session, mediaSession)
        mediaDelegate.onPlay(session, mediaSession)
        val nextModule: GeckoViewModule = mockk(relaxed = true)
        val actions = mutableListOf<String?>()
        every { nextModule.sendViewEvent(any(), any(), any()) } answers {
            actions += thirdArg<com.facebook.react.bridge.WritableMap?>()?.getString("action")
        }

        mediaDelegate.attachToView(nextModule, testViewId + 1)

        assertEquals(listOf("activated", "play"), actions)
    }

    @Test
    fun `a view that binds a paused session is told only that it is active`() {
        mediaDelegate = MediaDelegate(context, module, true, testViewId)
        mediaDelegate.onActivated(session, mediaSession)
        mediaDelegate.onPlay(session, mediaSession)
        mediaDelegate.detachFromView()
        mediaDelegate.onPause(session, mediaSession)
        val nextModule: GeckoViewModule = mockk(relaxed = true)

        mediaDelegate.attachToView(nextModule, testViewId + 1)

        verify(exactly = 1) { nextModule.sendViewEvent(testViewId + 1, "onMediaSessionAction", any()) }
    }

    @Test
    fun `media events delivered after the session closed leave nothing behind`() {
        // GeckoView posts media events to the UI thread, so an activation or a
        // play sent just before a close can run after it. Neither may take
        // focus that nothing would give back, reach the view, or be told to a
        // view that binds the session once the pool reopens it.
        mediaDelegate = MediaDelegate(context, module, true, testViewId)
        mediaDelegate.release()
        every { session.isOpen } returns false

        mediaDelegate.onActivated(session, mediaSession)
        mediaDelegate.onPlay(session, mediaSession)

        verify(exactly = 0) { audioManager.requestAudioFocus(any<AudioFocusRequest>()) }
        verify(exactly = 0) { module.sendViewEvent(any(), any(), any()) }
        every { session.isOpen } returns true
        val nextModule: GeckoViewModule = mockk(relaxed = true)
        mediaDelegate.attachToView(nextModule, testViewId + 1)
        verify(exactly = 0) { nextModule.sendViewEvent(any(), any(), any()) }
    }

    @Test
    fun `turning focus handling off gives up the focus held for the page`() {
        mediaDelegate = MediaDelegate(context, module, true, testViewId)
        mediaDelegate.onPlay(session, mediaSession)

        mediaDelegate.setHandleAudioFocus(false)
        verify(exactly = 1) { audioManager.abandonAudioFocusRequest(any<AudioFocusRequest>()) }

        // From then on the host manages focus for the page.
        mediaDelegate.onPause(session, mediaSession)
        mediaDelegate.onPlay(session, mediaSession)
        verify(exactly = 1) { audioManager.requestAudioFocus(any<AudioFocusRequest>()) }
        verify(exactly = 1) { audioManager.abandonAudioFocusRequest(any<AudioFocusRequest>()) }
    }

    @Test
    fun `turning focus handling on takes focus for media already playing`() {
        mediaDelegate = MediaDelegate(context, module, false, testViewId)
        mediaDelegate.onPlay(session, mediaSession)

        mediaDelegate.setHandleAudioFocus(true)

        verify(exactly = 1) { audioManager.requestAudioFocus(any<AudioFocusRequest>()) }
        mediaDelegate.audioFocusChangeListener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS)
        verify(exactly = 1) { mediaSession.pause() }
    }

    @Test
    fun `turning focus handling on requests none while nothing plays`() {
        mediaDelegate = MediaDelegate(context, module, false, testViewId)

        mediaDelegate.setHandleAudioFocus(true)

        verify(exactly = 0) { audioManager.requestAudioFocus(any<AudioFocusRequest>()) }
    }

    // --- Ducking (Codex review of the teardown fixes, round 1) ---

    @Test
    fun `turning focus handling off ends a duck the delegate applied`() {
        // The request it gives up never gets the AUDIOFOCUS_GAIN that would unmute.
        mediaDelegate = MediaDelegate(context, module, true, testViewId)
        mediaDelegate.onPlay(session, mediaSession)
        mediaDelegate.audioFocusChangeListener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK)
        verify(exactly = 1) { mediaSession.muteAudio(true) }

        mediaDelegate.setHandleAudioFocus(false)

        verify(exactly = 1) { mediaSession.muteAudio(false) }
    }

    @Test
    fun `a pause during a duck unmutes the page for when it plays again`() {
        mediaDelegate = MediaDelegate(context, module, true, testViewId)
        mediaDelegate.onPlay(session, mediaSession)
        mediaDelegate.audioFocusChangeListener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK)

        mediaDelegate.onPause(session, mediaSession)

        verify(exactly = 1) { mediaSession.muteAudio(false) }
    }

    @Test
    fun `focus coming back ends the duck, once`() {
        mediaDelegate = MediaDelegate(context, module, true, testViewId)
        mediaDelegate.onPlay(session, mediaSession)
        mediaDelegate.audioFocusChangeListener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK)

        mediaDelegate.audioFocusChangeListener.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN)
        verify(exactly = 1) { mediaSession.muteAudio(false) }
        mediaDelegate.onPause(session, mediaSession)

        verify(exactly = 1) { mediaSession.muteAudio(false) }
    }

    @Test
    fun `a fresh focus request ends a duck under the old one`() {
        mediaDelegate = MediaDelegate(context, module, true, testViewId)
        mediaDelegate.onPlay(session, mediaSession)
        mediaDelegate.audioFocusChangeListener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK)

        mediaDelegate.onPlay(session, mediaSession)

        verify(exactly = 1) { mediaSession.muteAudio(false) }
    }

    @Test
    fun `an unchanged focus setting changes nothing`() {
        mediaDelegate = MediaDelegate(context, module, true, testViewId)
        mediaDelegate.onPlay(session, mediaSession)

        mediaDelegate.setHandleAudioFocus(true)

        verify(exactly = 1) { audioManager.requestAudioFocus(any<AudioFocusRequest>()) }
        verify(exactly = 0) { audioManager.abandonAudioFocusRequest(any<AudioFocusRequest>()) }
    }

    @Test
    fun `focus changes arrive on the main looper`() {
        // The application's AudioManager otherwise calls back on the looper of
        // whichever thread created it first.
        val request = slot<AudioFocusRequest>()
        every { audioManager.requestAudioFocus(capture(request)) } returns AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        mediaDelegate = MediaDelegate(context, module, true, testViewId)

        mediaDelegate.onPlay(session, mediaSession)

        // Hidden API; Robolectric's android-all has it.
        val handler = AudioFocusRequest::class.java
            .getMethod("getOnAudioFocusChangeListenerHandler")
            .invoke(request.captured) as android.os.Handler
        assertSame(android.os.Looper.getMainLooper(), handler.looper)
    }

    @Test
    fun `the audio service comes from the application context, not the view's`() {
        // The delegate stays with its pooled session after the view is dropped,
        // so it doesn't use an AudioManager scoped to the view's Activity.
        val viewContext: ThemedReactContext = mockk(relaxed = true)
        val appContext: Context = mockk(relaxed = true)
        every { viewContext.applicationContext } returns appContext
        every { appContext.getSystemService(Context.AUDIO_SERVICE) } returns audioManager

        mediaDelegate = MediaDelegate(viewContext, module, true, testViewId)
        mediaDelegate.onPlay(session, mediaSession)

        verify(exactly = 0) { viewContext.getSystemService(any<String>()) }
        verify(exactly = 1) { audioManager.requestAudioFocus(any<AudioFocusRequest>()) }
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
