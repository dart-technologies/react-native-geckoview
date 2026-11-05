package com.reactnative.geckoview

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.facebook.react.bridge.Arguments
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.MediaSession

/**
 * Handles media session events from GeckoView.
 * Implements MediaSession.Delegate to receive playback state changes and
 * manages audio focus acquisition and release based on playback state.
 * 
 * Audio handling is OPT-IN via the handleAudioFocus parameter.
 */
class MediaDelegate(
    context: Context,
    module: GeckoViewModule,
    handleAudioFocus: Boolean = false,
    viewId: Int = 0
) : MediaSession.Delegate {

    // The application's AudioManager, not one scoped to the view's Activity:
    // this delegate stays with its pooled session after the view is dropped
    // (see [detachFromView]).
    private val audioManager: AudioManager =
        context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    // The view this delegate reports to ([attachToView]); the module is null
    // while no view shows the session.
    private var module: GeckoViewModule? = module
    private var viewId: Int = viewId
    private var handleAudioFocus: Boolean = handleAudioFocus

    private var activeMediaSession: MediaSession? = null
    // Between onActivated and onDeactivated, so a view that binds the session
    // later can be told ([attachToView]).
    private var activated = false
    private var focusRequest: AudioFocusRequest? = null

    // Internal so tests can deliver focus changes.
    internal val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                GeckoLog.d(TAG, "Audio focus gained")
                activeMediaSession?.muteAudio(false)
            }
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                GeckoLog.d(TAG, "Audio focus lost")
                activeMediaSession?.pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                GeckoLog.d(TAG, "Audio focus ducked")
                activeMediaSession?.muteAudio(true)
            }
        }
    }

    override fun onPlay(session: GeckoSession, mediaSession: MediaSession) {
        GeckoLog.d(TAG, "onPlay called for MediaSession")
        activeMediaSession = mediaSession
        
        if (handleAudioFocus) {
            requestAudioFocus()
        }

        val params = Arguments.createMap().apply {
            putString("action", "play")
        }
        module?.sendViewEvent(viewId, "onMediaSessionAction", params)
    }

    override fun onPause(session: GeckoSession, mediaSession: MediaSession) {
        GeckoLog.d(TAG, "onPause called for MediaSession")
        activeMediaSession = null
        
        if (handleAudioFocus) {
            abandonAudioFocus()
        }

        val params = Arguments.createMap().apply {
            putString("action", "pause")
        }
        module?.sendViewEvent(viewId, "onMediaSessionAction", params)
    }

    override fun onStop(session: GeckoSession, mediaSession: MediaSession) {
        GeckoLog.d(TAG, "onStop called for MediaSession")
        activeMediaSession = null
        
        if (handleAudioFocus) {
            abandonAudioFocus()
        }

        val params = Arguments.createMap().apply {
            putString("action", "stop")
        }
        module?.sendViewEvent(viewId, "onMediaSessionAction", params)
    }

    override fun onActivated(session: GeckoSession, mediaSession: MediaSession) {
        GeckoLog.d(TAG, "onActivated called for MediaSession")
        activated = true
        // Don't request focus on activation, wait for actual play event
        val params = Arguments.createMap().apply {
            putString("action", "activated")
        }
        module?.sendViewEvent(viewId, "onMediaSessionAction", params)
    }

    override fun onDeactivated(session: GeckoSession, mediaSession: MediaSession) {
        GeckoLog.d(TAG, "onDeactivated called for MediaSession")
        activeMediaSession = null
        activated = false

        if (handleAudioFocus) {
            abandonAudioFocus()
        }

        val params = Arguments.createMap().apply {
            putString("action", "deactivated")
        }
        module?.sendViewEvent(viewId, "onMediaSessionAction", params)
    }

    override fun onMetadata(
        session: GeckoSession,
        mediaSession: MediaSession,
        meta: MediaSession.Metadata
    ) {
        GeckoLog.d(TAG, "onMetadata called")
        val params = Arguments.createMap().apply {
            putString("action", "metadata")
            putString("title", meta.title)
            putString("artist", meta.artist)
            putString("album", meta.album)
        }
        module?.sendViewEvent(viewId, "onMediaSessionAction", params)
    }

    /**
     * Give up audio focus and forget the active media session. Called when the
     * session that owns this delegate closes mid-playback, including when
     * GeckoView closed it itself: no onPause/onStop is guaranteed to arrive
     * afterwards, so focus taken in [onPlay] would otherwise be held until the
     * process dies.
     */
    fun release() {
        activeMediaSession = null
        activated = false
        if (handleAudioFocus) {
            abandonAudioFocus()
        }
        focusRequest = null
    }

    /**
     * The view was dropped, but its pooled session stays open. Keep handling
     * the session's audio focus (hold it while the page plays, pause the page
     * on a loss) until the session closes ([release]) or another view binds it
     * ([attachToView]); stop sending events.
     */
    fun detachFromView() {
        module = null
    }

    /**
     * Report to the view that now binds this delegate's session, and tell it
     * what it missed: that the page's media session is active, and that it is
     * playing. Focus held for media still playing stays held, with no new
     * request that would interrupt other audio; [setHandleAudioFocus] applies
     * the view's own setting.
     */
    fun attachToView(module: GeckoViewModule, viewId: Int) {
        this.module = module
        this.viewId = viewId
        if (activated) sendAction("activated")
        if (activeMediaSession != null) sendAction("play")
    }

    /**
     * Apply the bound view's `handleAudioFocus`: take focus for media already
     * playing, or give it up. Nothing changes when the setting is the same.
     */
    fun setHandleAudioFocus(enabled: Boolean) {
        if (enabled == handleAudioFocus) return
        handleAudioFocus = enabled
        if (!enabled) {
            abandonAudioFocus()
            focusRequest = null
        } else if (activeMediaSession != null) {
            requestAudioFocus()
        }
    }

    private fun sendAction(action: String) {
        val params = Arguments.createMap().apply {
            putString("action", action)
        }
        module?.sendViewEvent(viewId, "onMediaSessionAction", params)
    }

    private fun requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Abandon any existing request to prevent leaks
            focusRequest?.let {
                audioManager.abandonAudioFocusRequest(it)
            }

            val playbackAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            
            val newFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(playbackAttributes)
                // On the UI thread, where GeckoView calls this delegate. Without
                // a Handler, changes arrive on the looper of whichever thread
                // first created the application's AudioManager.
                .setOnAudioFocusChangeListener(audioFocusChangeListener, Handler(Looper.getMainLooper()))
                .build()
            
            focusRequest = newFocusRequest

            val result = audioManager.requestAudioFocus(newFocusRequest)
            if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                GeckoLog.d(TAG, "Audio focus granted")
            } else {
                GeckoLog.w(TAG, "Audio focus request denied")
            }
        } else {
            @Suppress("DEPRECATION")
            val result = audioManager.requestAudioFocus(
                audioFocusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
            if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                GeckoLog.d(TAG, "Audio focus granted")
            } else {
                GeckoLog.w(TAG, "Audio focus request denied")
            }
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let {
                audioManager.abandonAudioFocusRequest(it)
                GeckoLog.d(TAG, "Audio focus abandoned")
            }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(audioFocusChangeListener)
            GeckoLog.d(TAG, "Audio focus abandoned")
        }
    }

    companion object {
        private const val TAG = "MediaDelegate"

        /**
         * GeckoView closed [session] itself because its content process died;
         * no pause or stop follows, so give up the focus held for its media.
         */
        fun releaseFor(session: GeckoSession) {
            (session.mediaSessionDelegate as? MediaDelegate)?.release()
        }
    }
}
