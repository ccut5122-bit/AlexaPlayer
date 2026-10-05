package com.alexaplayer.ui.screen.queue

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alexaplayer.R
import com.alexaplayer.core.designsystem.component.AlexaIconButton
import com.alexaplayer.core.designsystem.component.Artwork
import com.alexaplayer.core.designsystem.component.EmptyState
import com.alexaplayer.core.designsystem.theme.AlexaRadius
import com.alexaplayer.core.designsystem.theme.AlexaSizes
import com.alexaplayer.core.designsystem.theme.AlexaSpacing
import com.alexaplayer.core.designsystem.theme.SongMetaStyle
import com.alexaplayer.core.designsystem.theme.SongTitleStyle
import com.alexaplayer.core.model.Song
import com.alexaplayer.core.util.DurationFormatter
import com.alexaplayer.ui.component.AlexaTopBar
import com.alexaplayer.ui.component.ConfirmDialog
import com.alexaplayer.ui.component.PlayingIndicator
import com.alexaplayer.ui.component.SectionHeader
import com.alexaplayer.ui.viewmodel.QueueViewModel

/** What is playing, and what comes after it. Nothing else on this screen. */
@Composable
fun QueueScreen(
    state: QueueViewModel.QueueUiState,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onOpenPlayer: () -> Unit,
    onPlayIndex: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onClearUpcoming: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmClear by remember { mutableStateOf(false) }

    if (confirmClear) {
        ConfirmDialog(
            title = stringResource(R.string.action_clear_queue),
            message = stringResource(R.string.queue_clear_confirm),
            confirmLabel = stringResource(R.string.action_clear),
            onConfirm = {
                confirmClear = false
                onClearUpcoming()
            },
            onDismiss = { confirmClear = false },
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        AlexaTopBar(
            title = stringResource(R.string.queue_title),
            onBack = onBack,
        )

        if (!state.hasQueue) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
                contentAlignment = Alignment.Center,
            ) {
                EmptyState(
                    iconRes = R.drawable.ic_queue,
                    title = stringResource(R.string.queue_empty_title),
                    body = stringResource(R.string.queue_empty_body),
                )
            }
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                bottom = contentPadding.calculateBottomPadding() + AlexaSpacing.xxl,
            ),
        ) {
            state.current?.let { current ->
                item(key = "current") {
                    SectionHeader(
                        title = stringResource(R.string.queue_now_playing),
                        modifier = Modifier.padding(
                            start = AlexaSpacing.lg,
                            end = AlexaSpacing.lg,
                            top = AlexaSpacing.sm,
                        ),
                    )
                    CurrentSongCard(
                        song = current,
                        isPlaying = state.isPlaying,
                        onClick = onOpenPlayer,
                        modifier = Modifier.padding(horizontal = AlexaSpacing.md),
                    )
                }
            }

            if (state.upNext.isNotEmpty()) {
                item(key = "up_next") {
                    SectionHeader(
                        title = stringResource(R.string.queue_up_next),
                        actionLabel = stringResource(R.string.action_clear),
                        onAction = { confirmClear = true },
                        modifier = Modifier.padding(
                            start = AlexaSpacing.lg,
                            end = AlexaSpacing.lg,
                            top = AlexaSpacing.lg,
                            bottom = AlexaSpacing.xs,
                        ),
                    )
                }
                itemsIndexed(state.upNext, key = { index, song -> "${song.id}_$index" }) { index, song ->
                    QueueRow(
                        song = song,
                        index = index + 1,
                        onPlay = { onPlayIndex(index) },
                        onRemove = { onRemove(index) },
                    )
                }
            }

            item(key = "total") {
                Text(
                    text = DurationFormatter.formatTotal(
                        state.upNext.sumOf { it.durationMs } +
                            (state.current?.durationMs ?: 0L),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(AlexaSpacing.lg),
                )
            }
        }
    }
}

@Composable
private fun CurrentSongCard(
    song: Song,
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AlexaRadius.lg))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(AlexaSpacing.md),
    ) {
        Artwork(
            artworkUri = song.artworkUri,
            seed = song.id,
            contentDescription = null,
            shape = RoundedCornerShape(AlexaRadius.md),
            modifier = Modifier.size(ALBUM_SIZE),
        )
        Spacer(Modifier.width(AlexaSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = SongTitleStyle,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = song.artist.ifBlank { stringResource(R.string.label_unknown) },
                style = SongMetaStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (isPlaying) {
            PlayingIndicator(active = true)
        }
    }
}

@Composable
private fun QueueRow(
    song: Song,
    index: Int,
    onPlay: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AlexaSpacing.md),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onPlay)
            .padding(horizontal = AlexaSpacing.lg, vertical = AlexaSpacing.sm),
    ) {
        Text(
            text = index.toString(),
            style = SongMetaStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(INDEX_WIDTH),
        )
        Artwork(
            artworkUri = song.artworkUri,
            seed = song.id,
            contentDescription = null,
            shape = RoundedCornerShape(AlexaRadius.sm),
            modifier = Modifier.size(QUEUE_ART_SIZE),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = SongTitleStyle,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = song.artist,
                style = SongMetaStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = DurationFormatter.format(song.durationMs),
            style = SongMetaStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AlexaIconButton(
            iconRes = R.drawable.ic_close,
            contentDescription = stringResource(R.string.action_remove_from_queue),
            onClick = onRemove,
        )
    }
}

private val ALBUM_SIZE = 72.dp
private val QUEUE_ART_SIZE = 44.dp
private val INDEX_WIDTH = 24.dp