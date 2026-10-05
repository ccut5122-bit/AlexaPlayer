package com.alexaplayer.playback

import android.content.Context
import android.media.AudioAttributes as PlatformAudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import androidx.core.content.ContextCompat
import com.alexaplayer.core.model.AudioFocusBehaviour

/**
 * Owns Android audio focus explicitly instead of letting the player do it, because the
 * user can choose between pausing and ducking when something else interrupts playback.
 *
 * Callbacks are invoked on the main thread; the service is expected to be thread safe.
 */
class AudioFocusController(
    context: Context,
    private val behaviour: () -> AudioFocusBehaviour,
    private val callbacks: Callbacks,
) {

    interface Callbacks {
        /** Focus is gone for good: stop and stay stopped. */
        fun onPauseForLoss()

        /** Temporary interruption: remember whether we were playing. */
        fun onPauseForTransientLoss(wasPlaying: Boolean)

        /** Another app asked whether it may play alongside us. */
        fun onDuck(enabled: Boolean)

        /** Focus came back. */
        fun onGain()
    }

    private val audioManager = ContextCompat.getSystemService(context, AudioManager::class.java)

    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    private val attributes = PlatformAudioAttributes.Builder()
        .setUsage(PlatformAudioAttributes.USAGE_MEDIA)
        .setContentType(PlatformAudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    private val focusRequest: AudioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(attributes)
        .setWillPauseWhenDucked(false)
        .setOnAudioFocusChangeListener(::onFocusChange, mainHandler)
        .build()

    private var hasFocus = false

    /** Requests focus for playback. Safe to call repeatedly. */
    fun request() {
        if (hasFocus) return
        val manager = audioManager ?: return
        val result = manager.requestAudioFocus(focusRequest)
        hasFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    fun abandon() {
        if (!hasFocus) return
        audioManager?.abandonAudioFocusRequest(focusRequest)
        hasFocus = false
    }

    private fun onFocusChange(change: Int) {
        when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                hasFocus = true
                callbacks.onGain()
            }

            AudioManager.AUDIOFOCUS_LOSS -> {
                hasFocus = false
                callbacks.onPauseForLoss()
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                callbacks.onPauseForTransientLoss(wasPlaying = true)
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                if (behaviour() == AudioFocusBehaviour.DUCK) {
                    callbacks.onDuck(true)
                } else {
                    callbacks.onPauseForTransientLoss(wasPlaying = true)
                }
            }
        }
    }
}
