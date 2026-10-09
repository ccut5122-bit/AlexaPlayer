package com.alexaplayer.ui.screen.nowplaying

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.alexaplayer.R
import com.alexaplayer.core.designsystem.component.AlexaIconButton
import com.alexaplayer.core.designsystem.component.Artwork
import com.alexaplayer.core.designsystem.component.EmptyState
import com.alexaplayer.core.designsystem.theme.AlexaRadius
import com.alexaplayer.core.designsystem.theme.AlexaSizes
import com.alexaplayer.core.designsystem.theme.AlexaSpacing
import com.alexaplayer.core.designsystem.theme.AlexaTheme
import com.alexaplayer.core.model.RepeatMode
import com.alexaplayer.core.model.Song
import com.alexaplayer.core.util.DurationFormatter
import com.alexaplayer.ui.component.AlexaTopBar
import com.alexaplayer.ui.component.FavoriteButton
import com.alexaplayer.ui.component.PlaybackSeekBar
import com.alexaplayer.ui.component.SectionHeader
import com.alexaplayer.ui.component.SongRow
import com.alexaplayer.ui.viewmodel.NowPlayingViewModel

/**
 * The full player. One screen, one job: make the current track obvious and everything else
 * one tap away.
 */
@Composable
fun NowPlayingScreen(
    state: NowPlayingViewModel.NowPlayingUiState,
    upNext: List<Song>,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onOpenQueue: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onToggleFavorite: (Song) -> Unit,
    onPlayUpNext: (Int) -> Unit,
    errorText: String?,
    playerProvider: () -> Player?,
    modifier: Modifier = Modifier,
) {
    val extended = AlexaTheme.extended
    val song = state.song

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        extended.accentViolet.copy(alpha = 0.14f),
                        MaterialTheme.colorScheme.surface,
                    ),
                ),
            ),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            AlexaTopBar(
                title = stringResource(R.string.now_playing_title),
                onBack = onBack,
                actions = {
                    AlexaIconButton(
                        iconRes = R.drawable.ic_queue,
                        contentDescription = stringResource(R.string.cd_queue),
                        onClick = onOpenQueue,
                    )
                },
            )

            if (song == null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(contentPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    EmptyState(
                        iconRes = R.drawable.ic_headphones,
                        title = stringResource(R.string.queue_empty_title),
                        body = stringResource(R.string.queue_empty_body),
                    )
                }
                return@Column
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = contentPadding.calculateTopPadding(),
                    bottom = contentPadding.calculateBottomPadding() + AlexaSpacing.xxl,
                ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                item(key = "artwork") {
                    val artAspect = if (song.isStream) 16f / 9f else 1f
                    Box(
                        modifier = Modifier
                            .padding(horizontal = AlexaSpacing.xl)
                            .fillMaxWidth()
                            .aspectRatio(artAspect),
                    ) {
                        Artwork(
                            artworkUri = song.artworkUri,
                            seed = song.id,
                            contentDescription = stringResource(R.string.cd_artwork),
                            shape = RoundedCornerShape(AlexaRadius.artworkLarge),
                            modifier = Modifier.fillMaxSize(),
                        )
                        if (song.isStream) {
                            VideoSurface(playerProvider)
                        }
                    }
                }

                item(key = "meta") {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = AlexaSpacing.xl, vertical = AlexaSpacing.lg),
                    ) {
                        Text(
                            text = song.title,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(AlexaSpacing.xs))
                        Text(
                            text = song.artist.ifBlank {
                                stringResource(R.string.now_playing_unknown_artist)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (song.album.isNotBlank()) {
                            Text(
                                text = song.album,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                item(key = "seek") {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        PlaybackSeekBar(
                            positionMs = state.positionMs,
                            durationMs = state.durationMs,
                            bufferedMs = state.bufferedMs,
                            onSeek = onSeekTo,
                            enabled = state.durationMs > 0L,
                            label = stringResource(R.string.cd_progress),
                            modifier = Modifier.padding(horizontal = AlexaSpacing.xl),
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = AlexaSpacing.xl),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = DurationFormatter.format(state.positionMs),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = DurationFormatter.format(state.durationMs),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                item(key = "controls") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = AlexaSpacing.lg, vertical = AlexaSpacing.lg),
                    ) {
                        AlexaIconButton(
                            iconRes = R.drawable.ic_shuffle,
                            contentDescription = stringResource(R.string.cd_shuffle),
                            onClick = onToggleShuffle,
                            tint = if (state.shuffleEnabled) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                        AlexaIconButton(
                            iconRes = R.drawable.ic_skip_previous,
                            contentDescription = stringResource(R.string.cd_previous),
                            onClick = onPrevious,
                            size = AlexaSizes.iconLg,
                        )
                        PlayPauseButton(
                            isPlaying = state.isPlaying,
                            isBuffering = state.isBuffering,
                            onClick = onTogglePlayPause,
                        )
                        AlexaIconButton(
                            iconRes = R.drawable.ic_skip_next,
                            contentDescription = stringResource(R.string.cd_next),
                            onClick = onNext,
                            size = AlexaSizes.iconLg,
                        )
                        AlexaIconButton(
                            iconRes = when (state.repeatMode) {
                                RepeatMode.ONE -> R.drawable.ic_repeat_one
                                else -> R.drawable.ic_repeat
                            },
                            contentDescription = stringResource(
                                if (state.repeatMode == RepeatMode.ONE) {
                                    R.string.cd_repeat_one
                                } else {
                                    R.string.cd_repeat
                                },
                            ),
                            onClick = onCycleRepeat,
                            tint = if (state.repeatMode == RepeatMode.OFF) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                        )
                    }
                }

                item(key = "secondary") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        FavoriteButton(
                            isFavorite = song.isFavorite,
                            onToggle = { onToggleFavorite(song) },
                        )
                    }
                }

                if (errorText != null) {
                    item(key = "error") {
                        Text(
                            text = errorText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(
                                horizontal = AlexaSpacing.xl,
                                vertical = AlexaSpacing.md,
                            ),
                        )
                    }
                }

                if (upNext.isNotEmpty()) {
                    item(key = "up_next_header") {
                        SectionHeader(
                            title = stringResource(R.string.now_playing_up_next),
                            actionLabel = stringResource(R.string.action_queue),
                            onAction = onOpenQueue,
                            modifier = Modifier.padding(
                                start = AlexaSpacing.lg,
                                end = AlexaSpacing.lg,
                                top = AlexaSpacing.xl,
                            ),
                        )
                    }
                    items(upNext.take(UP_NEXT_PREVIEW), key = { "next_${it.id}" }) { song ->
                        SongRow(
                            song = song,
                            subtitle = song.artist,
                            onClick = {
                                onPlayUpNext(upNext.take(UP_NEXT_PREVIEW).indexOf(song))
                            },
                            modifier = Modifier.padding(horizontal = AlexaSpacing.sm),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayPauseButton(
    isPlaying: Boolean,
    isBuffering: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(PLAY_BUTTON_SIZE)
            .clip(RoundedCornerShape(AlexaRadius.pill))
            .background(MaterialTheme.colorScheme.primary)
            .clickable(
                role = Role.Button,
                onClickLabel = stringResource(if (isPlaying) R.string.action_pause else R.string.action_play),
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (isBuffering && !isPlaying) {
            androidx.compose.material3.CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        } else {
            com.alexaplayer.core.designsystem.component.DecorativeIcon(
                iconRes = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
                tint = MaterialTheme.colorScheme.onPrimary,
                size = 28.dp,
            )
        }
    }
}

private val PLAY_BUTTON_SIZE = 64.dp
private const val UP_NEXT_PREVIEW = 5

/**
 * Renders the playing stream's video on top of the artwork. A texture surface stays
 * transparent until actual video frames arrive, so audio-only streams keep showing the
 * artwork underneath. The screen is cleared on release so background playback continues
 * without rendering video.
 */
@Composable
private fun VideoSurface(playerProvider: () -> Player?) {
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                setSurfaceType(C.SURFACE_TYPE_TEXTURE_VIEW)
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            }
        },
        update = { view -> view.player = playerProvider() },
        onRelease = { it.player = null },
    )
}