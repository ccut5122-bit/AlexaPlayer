package com.alexaplayer.ui.screen.youtube

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alexaplayer.R
import com.alexaplayer.core.designsystem.component.Artwork
import com.alexaplayer.core.designsystem.component.EmptyState
import com.alexaplayer.core.designsystem.component.PrimaryButton
import com.alexaplayer.core.designsystem.theme.AlexaRadius
import com.alexaplayer.core.designsystem.theme.AlexaSpacing
import com.alexaplayer.core.util.DurationFormatter
import com.alexaplayer.data.youtube.ActiveDownload
import com.alexaplayer.data.youtube.YoutubeTrack
import com.alexaplayer.ui.component.AlexaTopBar
import com.alexaplayer.ui.component.SectionHeader
import com.alexaplayer.ui.component.SearchField
import com.alexaplayer.ui.viewmodel.YoutubeSection
import com.alexaplayer.ui.viewmodel.YoutubeUiState

/**
 * A compact YouTube Music front end. The tab opens on curated, preloaded genre rows
 * (phonk, Hindi, English, ...), and typing a query swaps it for full results. Every track
 * can stream as audio (service background playback) or video (full screen), and can be
 * downloaded in either container.
 */
@Composable
fun YoutubeScreen(
    state: YoutubeUiState,
    contentPadding: PaddingValues,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onClear: () -> Unit,
    onPlay: (YoutubeTrack) -> Unit,
    onPlayVideo: (YoutubeTrack) -> Unit,
    onDownload: (YoutubeTrack, String) -> Unit,
    onOpenGenre: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        AlexaTopBar(title = stringResource(R.string.youtube_title))

        SearchField(
            value = state.query,
            onValueChange = onQueryChange,
            onSubmit = onSubmit,
            onClear = onClear,
            placeholder = stringResource(R.string.youtube_search_hint),
        )

        state.error?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(
                    horizontal = AlexaSpacing.md,
                    vertical = AlexaSpacing.sm,
                ),
            )
        }

        state.message?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(
                    horizontal = AlexaSpacing.md,
                    vertical = AlexaSpacing.sm,
                ),
            )
        }

        if (state.showingResults) {
            YoutubeResults(
                state = state,
                contentPadding = contentPadding,
                onPlay = onPlay,
                onPlayVideo = onPlayVideo,
                onDownload = onDownload,
                onRetry = onRetry,
            )
        } else {
            YoutubeHome(
                state = state,
                contentPadding = contentPadding,
                onPlay = onPlay,
                onPlayVideo = onPlayVideo,
                onDownload = onDownload,
                onOpenGenre = onOpenGenre,
                onRetry = onRetry,
            )
        }
    }
}

@Composable
private fun YoutubeHome(
    state: YoutubeUiState,
    contentPadding: PaddingValues,
    onPlay: (YoutubeTrack) -> Unit,
    onPlayVideo: (YoutubeTrack) -> Unit,
    onDownload: (YoutubeTrack, String) -> Unit,
    onOpenGenre: (String) -> Unit,
    onRetry: () -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(
            top = AlexaSpacing.sm + contentPadding.calculateTopPadding(),
            bottom = AlexaSpacing.xl + contentPadding.calculateBottomPadding(),
        ),
    ) {
        if (state.loadingHome && state.sections.isEmpty()) {
            item {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = AlexaSpacing.xl),
                ) {
                    CircularProgressIndicator()
                }
            }
        }

        state.sections.forEach { section ->
            item(key = "header_${section.query}") {
                SectionHeader(
                    title = section.title,
                    actionLabel = stringResource(R.string.youtube_see_all),
                    onAction = { onOpenGenre(section.query) },
                )
            }

            if (section.loading) {
                item(key = "loading_${section.query}") {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = AlexaSpacing.lg),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    }
                }
            } else if (section.results.isEmpty()) {
                item(key = "empty_${section.query}") {
                    Text(
                        text = section.error ?: stringResource(R.string.youtube_section_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = AlexaSpacing.md, vertical = AlexaSpacing.sm),
                    )
                }
            } else {
                item(key = "row_${section.query}") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = AlexaSpacing.md),
                        horizontalArrangement = Arrangement.spacedBy(AlexaSpacing.sm),
                    ) {
                        items(section.results, key = { it.videoId }) { track ->
                            YoutubeCard(
                                track = track,
                                activeDownload = state.activeDownload(track),
                                resolving = state.resolvingId == track.videoId,
                                enabled = state.resolvingId == null,
                                onClick = { onPlay(track) },
                                onPlayVideo = { onPlayVideo(track) },
                                onDownload = { kind -> onDownload(track, kind) },
                            )
                        }
                    }
                }
            }
        }

        if (state.homeError != null) {
            item(key = "home_error") {
                EmptyState(
                    iconRes = R.drawable.ic_warning,
                    title = stringResource(R.string.youtube_home_error_title),
                    body = state.homeError,
                    modifier = Modifier.padding(vertical = AlexaSpacing.lg),
                )
            }
        }
    }
}

@Composable
private fun YoutubeResults(
    state: YoutubeUiState,
    contentPadding: PaddingValues,
    onPlay: (YoutubeTrack) -> Unit,
    onPlayVideo: (YoutubeTrack) -> Unit,
    onDownload: (YoutubeTrack, String) -> Unit,
    onRetry: () -> Unit,
) {
    when {
        state.searching && state.results.isEmpty() -> {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize(),
            ) {
                CircularProgressIndicator()
            }
        }

        state.hasSearched && state.results.isEmpty() && state.error == null -> {
            EmptyState(
                iconRes = R.drawable.ic_search,
                title = stringResource(R.string.youtube_no_results_title),
                body = stringResource(R.string.youtube_no_results_body),
                action = {
                    PrimaryButton(
                        text = stringResource(R.string.youtube_retry),
                        onClick = onRetry,
                    )
                },
            )
        }

        else -> {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = AlexaSpacing.md,
                    end = AlexaSpacing.md,
                    top = AlexaSpacing.sm + contentPadding.calculateTopPadding(),
                    bottom = AlexaSpacing.xl + contentPadding.calculateBottomPadding(),
                ),
                verticalArrangement = Arrangement.spacedBy(AlexaSpacing.sm),
            ) {
                items(state.results, key = { it.videoId }) { track ->
                    YoutubeResultRow(
                        track = track,
                        activeDownload = state.activeDownload(track),
                        resolving = state.resolvingId == track.videoId,
                        enabled = state.resolvingId == null,
                        onClick = { onPlay(track) },
                        onPlayVideo = { onPlayVideo(track) },
                        onDownload = { kind -> onDownload(track, kind) },
                    )
                }
            }
        }
    }
}

@Composable
private fun YoutubeCard(
    track: YoutubeTrack,
    activeDownload: ActiveDownload?,
    resolving: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    onPlayVideo: () -> Unit,
    onDownload: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(132.dp)
            .clickable(enabled = enabled && !resolving, onClick = onClick),
    ) {
        Box {
            Artwork(
                artworkUri = track.thumbnailUrl.takeIf { it.isNotBlank() },
                seed = track.videoId.hashCode().toLong(),
                contentDescription = null,
                modifier = Modifier.size(132.dp),
            )
            if (resolving) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(132.dp).background(Color.Black.copy(alpha = 0.45f)),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(26.dp),
                        strokeWidth = 2.5.dp,
                    )
                }
            } else if (track.durationMs > 0L) {
                DurationBadge(durationMs = track.durationMs)
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            SmallActionButton(
                painterRes = R.drawable.ic_videocam,
                contentDescription = stringResource(R.string.youtube_play_video),
                enabled = enabled && activeDownload == null,
                onClick = onPlayVideo,
            )
            if (activeDownload != null) {
                DownloadProgressIcon(activeDownload = activeDownload)
            } else {
                DownloadMenu(onDownload = onDownload)
            }
        }
        Text(
            text = track.title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = AlexaSpacing.xs),
        )
    }
}

@Composable
private fun YoutubeResultRow(
    track: YoutubeTrack,
    activeDownload: ActiveDownload?,
    resolving: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    onPlayVideo: () -> Unit,
    onDownload: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled && !resolving, onClick = onClick)
            .padding(vertical = AlexaSpacing.xs),
    ) {
        Artwork(
            artworkUri = track.thumbnailUrl.takeIf { it.isNotBlank() },
            seed = track.videoId.hashCode().toLong(),
            contentDescription = null,
            modifier = Modifier.size(56.dp),
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = AlexaSpacing.md),
        ) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = buildString {
                    append(track.channel)
                    if (track.durationMs > 0L) {
                        append("  •  ")
                        append(DurationFormatter.format(track.durationMs))
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (resolving) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.dp,
            )
        }
        IconButton(onClick = onPlayVideo, enabled = activeDownload == null) {
            Icon(
                painter = painterResource(R.drawable.ic_videocam),
                contentDescription = stringResource(R.string.youtube_play_video),
            )
        }
        DownloadBadge(
            activeDownload = activeDownload,
            onDownload = onDownload,
        )
    }
}

/** A small flat icon button for the trailing row of a track. */
@Composable
private fun RowScope.SmallActionButton(
    painterRes: Int,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(36.dp)) {
        Icon(
            painter = painterResource(painterRes),
            contentDescription = contentDescription,
            modifier = Modifier.size(18.dp),
        )
    }
}

/**
 * Per-track download indicator: running ring with percent, green tick when finished, a red
 * warning (tap to retry) when it failed, and the audio/video menu before anything started.
 */
@Composable
private fun DownloadBadge(
    activeDownload: ActiveDownload?,
    onDownload: (String) -> Unit,
) {
    when {
        activeDownload == null -> DownloadMenu(onDownload = onDownload)

        activeDownload.failed -> {
            IconButton(onClick = { onDownload(activeDownload.kind) }) {
                Icon(
                    painter = painterResource(R.drawable.ic_warning),
                    contentDescription = stringResource(R.string.youtube_download_failed),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }

        activeDownload.done -> {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(36.dp)) {
                Icon(
                    painter = painterResource(R.drawable.ic_check),
                    contentDescription = stringResource(R.string.youtube_download_done),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }

        else -> {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(36.dp)) {
                CircularProgressIndicator(
                    progress = { activeDownload.percent / 100f },
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                )
            }
        }
    }
}

/** Dropdown with "Download audio" / "Download video"; triggered by the download icon. */
@Composable
private fun DownloadMenu(onDownload: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(
                painter = painterResource(R.drawable.ic_download),
                contentDescription = stringResource(R.string.youtube_download),
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.youtube_download_audio)) },
                onClick = {
                    open = false
                    onDownload("audio")
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.youtube_download_video)) },
                onClick = {
                    open = false
                    onDownload("video")
                },
            )
        }
    }
}

@Composable
private fun BoxScope.DurationBadge(durationMs: Long) {
    Text(
        text = DurationFormatter.format(durationMs),
        style = MaterialTheme.typography.labelSmall,
        color = Color.White,
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(4.dp)
            .background(Color.Black.copy(alpha = 0.7f), shape = RoundedCornerShape(AlexaRadius.md))
            .padding(horizontal = 4.dp, vertical = 1.dp),
    )
}

private fun YoutubeUiState.activeDownload(track: YoutubeTrack): ActiveDownload? =
    downloads.firstOrNull { it.token.startsWith("${track.videoId}-") }