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
import androidx.media3.exoplayer.ExoPlayer
import com.alexaplayer.R

/**
 * Full-screen video player used for YouTube "video songs". It owns a plain ExoPlayer of
 * its own (separate from the background audio service) so navigating back returns you to
 * whatever the audio session was playing.
 */
@OptIn(UnstableApi::class)
class VideoPlayerActivity : Activity() {

    private var player: ExoPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            setContentView(R.layout.activity_video_player)
            // Must run after the content view exists: the decor view (and therefore its
            // insets controller) is null before setContentView on several OEM roms.
            hideSystemBars()

            val url = intent.getStringExtra(EXTRA_URL)
            if (url.isNullOrBlank()) {
                finish()
                return
            }

            val playerView = findViewById<androidx.media3.ui.PlayerView>(R.id.player_view)
            // Prefer a source factory that replays the extractor's request headers; fall back to
            // the default player if building it fails, so fullscreen still works regardless.
            val builder = runCatching {
                ExoPlayer.Builder(this).setMediaSourceFactory(mediaSourceFactory(url))
            }.getOrElse {
                com.alexaplayer.util.CrashLog.write(this, it, "VideoPlayerFactory")
                ExoPlayer.Builder(this)
            }
            player = builder.build().also { exo ->
                exo.setMediaItem(MediaItem.fromUri(url))
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

    /** YouTube needs back the same request headers the extractor signed the URL with. */
    private fun mediaSourceFactory(url: String): androidx.media3.exoplayer.source.MediaSource.Factory {
        val httpFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
        val headers = com.alexaplayer.playback.StreamHeaders.of(url)
        if (headers.isNotEmpty()) httpFactory.setDefaultRequestProperties(headers)
        return androidx.media3.exoplayer.source.DefaultMediaSourceFactory(
            androidx.media3.datasource.DefaultDataSource.Factory(this, httpFactory),
        )
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
        if (hasFocus) {
            runCatching {
                window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            }
        }
    }

    override fun onDestroy() {
        player?.release()
        player = null
        super.onDestroy()
    }

    companion object {
        const val EXTRA_URL = "video_url"
        const val EXTRA_TITLE = "video_title"

        fun intent(context: Context, url: String, title: String): Intent =
            Intent(context, VideoPlayerActivity::class.java)
                .putExtra(EXTRA_URL, url)
                .putExtra(EXTRA_TITLE, title)
    }
}