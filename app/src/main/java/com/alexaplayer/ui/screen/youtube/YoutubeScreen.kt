package com.alexaplayer.ui.screen.youtube

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alexaplayer.R
import com.alexaplayer.core.designsystem.component.AlexaIconButton
import com.alexaplayer.core.designsystem.component.Artwork
import com.alexaplayer.core.designsystem.component.EmptyState
import com.alexaplayer.core.designsystem.component.PrimaryButton
import com.alexaplayer.core.designsystem.theme.AlexaSpacing
import com.alexaplayer.core.util.DurationFormatter
import com.alexaplayer.data.youtube.YoutubeTrack
import com.alexaplayer.ui.component.AlexaTopBar
import com.alexaplayer.ui.component.SearchField
import com.alexaplayer.ui.viewmodel.YoutubeUiState

/**
 * YouTube search and playback. Extraction only starts on tap, so scrolling results stays
 * free of network work and a single failing row never takes the list down with it.
 */
@Composable
fun YoutubeScreen(
    state: YoutubeUiState,
    contentPadding: PaddingValues,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onClear: () -> Unit,
    onPlay: (YoutubeTrack) -> Unit,
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

        when {
            state.loading && state.results.isEmpty() -> {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    CircularProgressIndicator()
                }
            }

            !state.hasSearched && state.results.isEmpty() -> {
                EmptyState(
                    iconRes = R.drawable.ic_headphones,
                    title = stringResource(R.string.youtube_hint_title),
                    body = stringResource(R.string.youtube_hint_body),
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
                            resolving = state.resolvingId == track.videoId,
                            enabled = state.resolvingId == null,
                            onClick = { onPlay(track) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun YoutubeResultRow(
    track: YoutubeTrack,
    resolving: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
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

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.width(48.dp),
        ) {
            if (resolving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                AlexaIconButton(
                    iconRes = R.drawable.ic_play,
                    contentDescription = stringResource(R.string.action_play),
                    onClick = onClick,
                    enabled = enabled,
                )
            }
        }
    }
}
