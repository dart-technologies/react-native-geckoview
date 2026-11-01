package com.reactnative.geckoview

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
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
    private val module: GeckoViewModule,
    private val handleAudioFocus: Boolean = false,
    private val viewId: Int = 0
) : MediaSession.Delegate {

    private val audioManager: AudioManager = 
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    
    private var activeMediaSession: MediaSession? = null
    private var focusRequest: AudioFocusRequest? = null

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
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
        module.sendViewEvent(viewId, "onMediaSessionAction", params)
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
        module.sendViewEvent(viewId, "onMediaSessionAction", params)
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
        module.sendViewEvent(viewId, "onMediaSessionAction", params)
    }

    override fun onActivated(session: GeckoSession, mediaSession: MediaSession) {
        GeckoLog.d(TAG, "onActivated called for MediaSession")
        // Don't request focus on activation, wait for actual play event
        val params = Arguments.createMap().apply {
            putString("action", "activated")
        }
        module.sendViewEvent(viewId, "onMediaSessionAction", params)
    }

    override fun onDeactivated(session: GeckoSession, mediaSession: MediaSession) {
        GeckoLog.d(TAG, "onDeactivated called for MediaSession")
        activeMediaSession = null
        
        if (handleAudioFocus) {
            abandonAudioFocus()
        }

        val params = Arguments.createMap().apply {
            putString("action", "deactivated")
        }
        module.sendViewEvent(viewId, "onMediaSessionAction", params)
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
        module.sendViewEvent(viewId, "onMediaSessionAction", params)
    }

    /**
     * Give up audio focus and forget the active media session. Called when the
     * view or session that owns this delegate goes away mid-playback: no
     * onPause/onStop is guaranteed to arrive afterwards, so focus taken in
     * [onPlay] would otherwise be held until the process dies.
     */
    fun release() {
        activeMediaSession = null
        if (handleAudioFocus) {
            abandonAudioFocus()
        }
        focusRequest = null
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
                .setOnAudioFocusChangeListener(audioFocusChangeListener)
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
    }
}
