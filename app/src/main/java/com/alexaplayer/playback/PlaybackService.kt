package com.alexaplayer.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionCommands as MediaSessionCommands
import androidx.media3.session.SessionResult
import com.alexaplayer.AlexaPlayerApp
import com.alexaplayer.MainActivity
import com.alexaplayer.R
import com.alexaplayer.core.model.AudioFocusBehaviour
import com.alexaplayer.core.util.DispatcherProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Owns playback.
 *
 * The player lives here, not in the UI, which is what makes audio survive the Activity
 * being destroyed, the app being swiped away or the screen turning off. The UI talks to
 * it exclusively through a [androidx.media3.session.MediaController].
 */
@UnstableApi
class PlaybackService : MediaSessionService() {

    private lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaSession
    private lateinit var audioFocus: AudioFocusController

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + kotlinx.coroutines.Dispatchers.Main.immediate)
    private val dispatchers: DispatcherProvider get() = (application as AlexaPlayerApp).container.dispatchers

    private var positionJob: Job? = null
    private var gapJob: Job? = null
    private var resumeAfterFocusLoss = false
    private var gaplessPlayback = true

    /** Exposed for the in-app player so it can resolve titles and favourite state. */
    var container: com.alexaplayer.di.AppContainer? = null
        private set

    override fun onCreate() {
        super.onCreate()
        container = (application as AlexaPlayerApp).container

        player = ExoPlayer.Builder(this)
            // YouTube's CDN is picky about the client it resolved for, so playback has to
            // present the exact same UA the extractor just used.
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(
                    DefaultDataSource.Factory(
                        this,
                        DefaultHttpDataSource.Factory().setUserAgent(STREAM_USER_AGENT),
                    ),
                ),
            )
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                // Focus is managed explicitly so the user's pause/duck choice is honoured.
                false,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .setSeekForwardIncrementMs(SEEK_INCREMENT_MS)
            .setSeekBackIncrementMs(SEEK_INCREMENT_MS)
            .build()

        audioFocus = AudioFocusController(
            context = this,
            behaviour = { currentAudioFocusBehaviour },
            callbacks = focusCallbacks,
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivityIntent())
            .setCallback(SessionCallback())
            .setId(SESSION_ID)
            .build()

        mediaSession.setCustomLayout(customLayout())

        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelId(NOTIFICATION_CHANNEL_ID)
                .setChannelName(R.string.app_name)
                .setNotificationId(NOTIFICATION_ID)
                .build(),
        )

        player.addListener(playerListener)

        observeSettings()
    }

    private val currentAudioFocusBehaviour: AudioFocusBehaviour
        get() = container?.audioFocusBehaviour ?: AudioFocusBehaviour.PAUSE

    private fun observeSettings() {
        serviceScope.launch {
            container?.settingsRepository?.settings?.collect { settings ->
                gaplessPlayback = settings.gaplessPlayback
                if (!settings.mediaNotificationEnabled && player.isPlaying) {
                    // The user opted out of the media notification: keep the audio but
                    // detach it from the shade.
                    stopForeground(STOP_FOREGROUND_DETACH)
                }
            }
        }
    }


    private val focusCallbacks = object : AudioFocusController.Callbacks {
        override fun onPauseForLoss() {
            resumeAfterFocusLoss = false
            player.pause()
        }

        override fun onPauseForTransientLoss(wasPlaying: Boolean) {
            resumeAfterFocusLoss = wasPlaying && player.isPlaying
            player.pause()
        }

        override fun onDuck(enabled: Boolean) {
            player.volume = if (enabled) DUCK_VOLUME else 1f
        }

        override fun onGain() {
            player.volume = 1f
            if (resumeAfterFocusLoss) {
                resumeAfterFocusLoss = false
                player.play()
            }
        }
    }

    /** With gapless disabled the user gets a deliberate short pause between tracks. */
    private fun scheduleNonGaplessPause() {
        gapJob?.cancel()
        gapJob = serviceScope.launch {
            delay(NON_GAPLESS_PAUSE_MS)
            if (player.isPlaying) {
                player.pause()
                player.play()
            }
        }
    }

    private fun startPositionUpdates() {
        if (positionJob?.isActive == true) return
        positionJob = serviceScope.launch {
            while (true) {
                persistPosition()
                delay(POSITION_SAVE_INTERVAL_MS)
            }
        }
    }

    private fun persistPosition(forceClear: Boolean = false) {
        val songId = player.getCurrentMediaItem()?.songId() ?: return
        val position = player.currentPosition
        val duration = player.duration
        val library = container?.libraryRepository ?: return
        val finished = forceClear || (duration > 0 && position >= duration - FINISHED_THRESHOLD_MS)
        serviceScope.launch(dispatchers.io) {
            library.savePosition(songId, if (finished) 0L else position)
        }
    }

private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                audioFocus.request()
                startPositionUpdates()
            } else {
                audioFocus.abandon()
                player.volume = 1f
                persistPosition()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                persistPosition(forceClear = true)
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val songId = mediaItem?.songId()
            if (songId != null) {
                serviceScope.launch { container?.libraryRepository?.markPlayed(songId) }
                startPositionUpdates()
            }
            if (!gaplessPlayback && reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                scheduleNonGaplessPause()
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            // Keep the queue intact: skip to the next item so a broken file does not
            // stop the whole session, and let the UI report it from the error flow.
            val hasNext = player.hasNextMediaItem()
            if (hasNext && player.mediaItemCount > 1) {
                player.seekToNextMediaItem()
                player.prepare()
            }
        }
    }

private inner class SessionCallback : MediaSession.Callback {

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult =
            MediaSession.ConnectionResult.AcceptedResultBuilder(session).build()


    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Swiping the app away should not stop music that is still playing, but it must
        // not leave an idle service behind either.
        if (!player.isPlaying) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        persistPosition()
        positionJob?.cancel()
        gapJob?.cancel()
        audioFocus.abandon()
        player.removeListener(playerListener)
        mediaSession.release()
        player.release()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun sessionActivityIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        },
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun customLayout(): List<CommandButton> = listOf(
        CommandButton.Builder()
            .setDisplayName(getString(R.string.action_close))
            .setIconResId(R.drawable.ic_notification_stop)
            .setPlayerCommand(Player.COMMAND_STOP)
            .build(),
    )

    companion object {
        const val SESSION_ID = "AlexaPlayerSession"
        const val NOTIFICATION_CHANNEL_ID = "alexa_playback"
        const val NOTIFICATION_ID = 4711

        private const val SEEK_INCREMENT_MS = 10_000L

        /**
         * Must match the header [com.alexaplayer.data.youtube.YoutubeBridge] resolved with:
         * YouTube's CDN answers a different client than the one that asked for the stream
         * with 403.
         */
        private const val STREAM_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 11) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/120.0 Mobile Safari/537.36"
        private const val POSITION_SAVE_INTERVAL_MS = 5_000L
        private const val FINISHED_THRESHOLD_MS = 3_000L
        private const val NON_GAPLESS_PAUSE_MS = 450L
        private const val DUCK_VOLUME = 0.25f
    }
}
