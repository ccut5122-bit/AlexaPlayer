package com.alexaplayer.ui.screen.playlists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.alexaplayer.R
import com.alexaplayer.core.designsystem.component.AlexaIconButton
import com.alexaplayer.core.designsystem.component.EmptyState
import com.alexaplayer.core.designsystem.component.QuietButton
import com.alexaplayer.core.designsystem.component.SecondaryButton
import com.alexaplayer.core.designsystem.theme.AlexaRadius
import com.alexaplayer.core.designsystem.theme.AlexaSpacing
import com.alexaplayer.core.model.Song
import com.alexaplayer.core.util.formatDurationLong
import com.alexaplayer.ui.component.AlexaTopBar
import com.alexaplayer.ui.component.ConfirmDialog
import com.alexaplayer.ui.component.NameInputDialog
import com.alexaplayer.ui.component.SectionHeader
import com.alexaplayer.ui.component.SongRow
import com.alexaplayer.ui.component.rememberReorderState
import com.alexaplayer.ui.component.reorderable
import com.alexaplayer.ui.viewmodel.PlaylistDetailViewModel

@Composable
fun PlaylistDetailScreen(
    state: PlaylistDetailViewModel.PlaylistDetailUiState,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onPlay: (Int) -> Unit,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onMove: (Int, Int) -> Unit,
    onRemoveSong: (Song) -> Unit,
    onToggleFavorite: (Song) -> Unit,
    onAddSongs: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onMore: (Song) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showRename by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }

    if (showRename) {
        NameInputDialog(
            title = stringResource(R.string.playlist_rename_title),
            confirmLabel = stringResource(R.string.action_save),
            initialValue = state.name,
            onConfirm = {
                showRename = false
                onRename(it)
            },
            onDismiss = { showRename = false },
        )
    }
    if (showDelete) {
        ConfirmDialog(
            title = stringResource(R.string.playlist_delete_title),
            message = state.name,
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = {
                showDelete = false
                onDelete()
            },
            onDismiss = { showDelete = false },
        )
    }

    val listState = rememberLazyListState()
    val reorderState = rememberReorderState(listState) { from, to -> onMove(from, to) }

    Column(modifier = modifier.fillMaxSize()) {
        AlexaTopBar(
            title = state.name,
            onBack = onBack,
            actions = {
                AlexaIconButton(
                    iconRes = R.drawable.ic_more_vert,
                    contentDescription = stringResource(R.string.cd_more_options),
                    onClick = { showRename = true },
                )
            },
        )

        if (state.songs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
                contentAlignment = Alignment.Center,
            ) {
                EmptyState(
                    iconRes = R.drawable.ic_playlists,
                    title = stringResource(R.string.playlist_empty_title),
                    body = stringResource(R.string.playlist_empty_body),
                    action = {
                        PrimaryPlaylistButton(onClick = onAddSongs, text = stringResource(R.string.playlist_add_songs))
                    },
                )
            }
            return@Column
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .reorderable(reorderState),
            contentPadding = PaddingValues(
                bottom = contentPadding.calculateBottomPadding() + AlexaSpacing.xxl,
            ),
        ) {
            item(key = "header") {
                PlaylistHeader(
                    songCount = state.songs.size,
                    totalDurationMs = state.totalDurationMs,
                    onPlayAll = onPlayAll,
                    onShuffle = onShuffle,
                    onAddSongs = onAddSongs,
                )
            }
            item(key = "hint") {
                Text(
                    text = stringResource(R.string.playlist_reorder_hint),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(
                        start = AlexaSpacing.lg,
                        end = AlexaSpacing.lg,
                        bottom = AlexaSpacing.sm,
                    ),
                )
            }
            itemsIndexed(state.songs, key = { _, song -> "s_${song.id}" }) { index, song ->
                SongRow(
                    song = song,
                    isPlaying = song.id == state.currentSongId,
                    onClick = { onPlay(index) },
                    onLongClick = { onMore(song) },
                    trailing = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AlexaIconButton(
                                iconRes = R.drawable.ic_drag_handle,
                                contentDescription = stringResource(R.string.playlist_reorder_hint),
                                onClick = {},
                            )
                        }
                    },
                    modifier = Modifier
                        .padding(horizontal = AlexaSpacing.sm)
                        .zIndex(if (reorderState.draggedIndex == index) 1f else 0f),
                )
            }
        }
    }
}

@Composable
private fun PlaylistHeader(
    songCount: Int,
    totalDurationMs: Long,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onAddSongs: () -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = AlexaSpacing.lg)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(AlexaSpacing.sm),
            modifier = Modifier.padding(bottom = AlexaSpacing.md),
        ) {
            SecondaryButton(
                text = stringResource(R.string.action_play),
                iconRes = R.drawable.ic_play,
                onClick = onPlayAll,
                modifier = Modifier.weight(1f),
            )
            QuietButton(
                text = stringResource(R.string.action_shuffle),
                iconRes = R.drawable.ic_shuffle,
                onClick = onShuffle,
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            text = stringResource(R.string.playlist_summary, songCount, formatDurationLong(totalDurationMs)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        QuietButton(
            text = stringResource(R.string.playlist_add_songs),
            iconRes = R.drawable.ic_add,
            onClick = onAddSongs,
            modifier = Modifier.padding(top = AlexaSpacing.sm),
        )
        SectionHeader(
            title = stringResource(R.string.playlist_songs_header),
            modifier = Modifier.padding(top = AlexaSpacing.md),
        )
    }
}

@Composable
private fun PrimaryPlaylistButton(text: String, onClick: () -> Unit) {
    androidx.compose.material3.Button(onClick = onClick) { Text(text) }
}