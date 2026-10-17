package com.alexaplayer.ui.video

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.alexaplayer.R
import com.alexaplayer.core.designsystem.component.AlexaIconButton
import com.alexaplayer.core.designsystem.theme.AlexaRadius
import com.alexaplayer.core.designsystem.theme.AlexaSpacing
import com.alexaplayer.playback.StreamHeaders

/**
 * Plays a resolved link right inside the Downloader, under the Play button. Separate
 * video-only and audio-only URLs are merged on the fly exactly like the full-screen player
 * does, so 1080p/4K resolve to a single picture with sound. The expand button hands the same
 * URLs to the immersive [VideoPlayerActivity] without re-resolving.
 */
@OptIn(UnstableApi::class)
@Composable
fun InlineVideoPlayer(
    videoUrl: String,
    audioUrl: String?,
    onFullscreen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val player = remember(videoUrl, audioUrl) {
        ExoPlayer.Builder(context).build().apply {
            setMediaSource(mergedSource(context, videoUrl, audioUrl))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) {
        onDispose { player.release() }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(AlexaRadius.md)),
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = true
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    this.player = player
                }
            },
            update = { it.player = player },
            onRelease = { it.player = null },
            modifier = Modifier.fillMaxSize(),
        )
        AlexaIconButton(
            iconRes = R.drawable.ic_fullscreen,
            contentDescription = stringResource(R.string.downloader_fullscreen),
            onClick = onFullscreen,
            tint = androidx.compose.ui.graphics.Color.White,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(AlexaSpacing.xs),
        )
    }
}

@UnstableApi
private fun mergedSource(context: Context, videoUrl: String, audioUrl: String?): MediaSource {
    val video = sourceFor(context, videoUrl)
    return if (audioUrl.isNullOrBlank() || audioUrl == videoUrl) {
        video
    } else {
        MergingMediaSource(video, sourceFor(context, audioUrl))
    }
}

@UnstableApi
private fun sourceFor(context: Context, url: String): MediaSource {
    val httpFactory = DefaultHttpDataSource.Factory()
    val headers = StreamHeaders.of(url)
    if (headers.isNotEmpty()) httpFactory.setDefaultRequestProperties(headers)
    return ProgressiveMediaSource.Factory(DefaultDataSource.Factory(context, httpFactory))
        .createMediaSource(MediaItem.fromUri(url))
}
