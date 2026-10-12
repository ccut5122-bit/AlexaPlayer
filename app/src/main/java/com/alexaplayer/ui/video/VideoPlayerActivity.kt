package com.alexaplayer.ui.video

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.alexaplayer.AlexaPlayerApp
import com.alexaplayer.R

/**
 * Full-screen video player used for YouTube "video songs". It owns a plain ExoPlayer of
 * its own (separate from the background audio service) so navigating back returns you to
 * whatever the audio session was playing.
 *
 * YouTube only ships muxed files up to 720p, so for anything higher the activity builds a
 * [MergingMediaSource] from a video-only and an audio-only track. While it is open the
 * background audio is paused, otherwise the same song would play twice at once.
 */
@OptIn(UnstableApi::class)
class VideoPlayerActivity : Activity() {

    private var player: ExoPlayer? = null
    private var resumeMusicOnExit = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            setContentView(R.layout.activity_video_player)
            // Must run after the content view exists: the decor view (and therefore its
            // insets controller) is null before setContentView on several OEM roms.
            hideSystemBars()

            val videoUrl = intent.getStringExtra(EXTRA_URL)
            if (videoUrl.isNullOrBlank()) {
                finish()
                return
            }
            val audioUrl = intent.getStringExtra(EXTRA_AUDIO_URL)

            // Stop the background session so only the video is heard.
            val connection = (application as? AlexaPlayerApp)?.container?.playerConnection
            resumeMusicOnExit = connection?.state?.value?.isPlaying == true
            if (resumeMusicOnExit) connection?.pause()

            val playerView = findViewById<androidx.media3.ui.PlayerView>(R.id.player_view)
            val source = runCatching { buildSource(videoUrl, audioUrl) }
                .getOrElse {
                    com.alexaplayer.util.CrashLog.write(this, it, "VideoPlayerSource")
                    ProgressiveMediaSource.Factory(DefaultDataSource.Factory(this))
                        .createMediaSource(MediaItem.fromUri(videoUrl))
                }
            player = ExoPlayer.Builder(this).build().also { exo ->
                exo.setMediaSource(source)
                exo.prepare()
                exo.playWhenReady = true
                playerView.player = exo
            }
        } catch (error: Throwable) {
            com.alexaplayer.util.CrashLog.write(this, error, "VideoPlayer")
            android.widget.Toast.makeText(
                this,
                "Video could not be opened: ${error.message ?: error::class.java.simpleName}",
                android.widget.Toast.LENGTH_LONG,
            ).show()
            finish()
        }
    }

    /** A merged video+audio source, or just the single source when there is no extra track. */
    private fun buildSource(videoUrl: String, audioUrl: String?): MediaSource {
        val video = sourceFor(videoUrl)
        return if (audioUrl.isNullOrBlank() || audioUrl == videoUrl) {
            video
        } else {
            MergingMediaSource(video, sourceFor(audioUrl))
        }
    }

    /** Progressive source with the exact request headers the extractor signed the URL with. */
    private fun sourceFor(url: String): MediaSource {
        val httpFactory = DefaultHttpDataSource.Factory()
        val headers = com.alexaplayer.playback.StreamHeaders.of(url)
        if (headers.isNotEmpty()) httpFactory.setDefaultRequestProperties(headers)
        return ProgressiveMediaSource.Factory(DefaultDataSource.Factory(this, httpFactory))
            .createMediaSource(MediaItem.fromUri(url))
    }

    private fun hideSystemBars() {
        // Best effort: an OEM rom returning a null decor view must not take playback down.
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.setDecorFitsSystemWindows(false)
                window.insetsController?.let {
                    it.hide(
                        android.view.WindowInsets.Type.statusBars() or
                            android.view.WindowInsets.Type.navigationBars(),
                    )
                    it.systemBarsBehavior =
                        android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
            }
        }
    }

    @Suppress("DEPRECATION")
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        runCatching {
            if (hasFocus) window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        }
    }

    override fun onDestroy() {
        player?.release()
        player = null
        // Hand the song back to the audio session when the user leaves fullscreen.
        if (resumeMusicOnExit) {
            (application as? AlexaPlayerApp)?.container?.playerConnection?.play()
        }
        super.onDestroy()
    }

    companion object {
        const val EXTRA_URL = "video_url"
        const val EXTRA_AUDIO_URL = "video_audio_url"
        const val EXTRA_TITLE = "video_title"

        fun intent(context: Context, url: String, title: String): Intent =
            Intent(context, VideoPlayerActivity::class.java)
                .putExtra(EXTRA_URL, url)
                .putExtra(EXTRA_TITLE, title)

        fun intent(context: Context, videoUrl: String, audioUrl: String?, title: String): Intent =
            Intent(context, VideoPlayerActivity::class.java)
                .putExtra(EXTRA_URL, videoUrl)
                .putExtra(EXTRA_AUDIO_URL, audioUrl)
                .putExtra(EXTRA_TITLE, title)
    }
}
