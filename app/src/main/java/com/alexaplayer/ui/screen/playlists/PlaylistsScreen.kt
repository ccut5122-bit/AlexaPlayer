package com.alexaplayer.ui.screen.playlists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.alexaplayer.R
import com.alexaplayer.core.designsystem.component.AlexaIconButton
import com.alexaplayer.core.designsystem.component.EmptyState
import com.alexaplayer.core.designsystem.component.PlaylistCard
import com.alexaplayer.core.designsystem.component.PrimaryButton
import com.alexaplayer.core.designsystem.theme.AlexaSpacing
import com.alexaplayer.core.model.Playlist
import com.alexaplayer.ui.component.AlexaTopBar
import com.alexaplayer.ui.component.ConfirmDialog
import com.alexaplayer.ui.component.NameInputDialog

@Composable
fun PlaylistsScreen(
    playlists: List<Playlist>,
    contentPadding: PaddingValues,
    onBack: (() -> Unit)?,
    onCreate: (String) -> Unit,
    onRename: (Long, String) -> Unit,
    onDelete: (Long) -> Unit,
    onOpen: (Long) -> Unit,
    onMore: (Playlist) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showCreate by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Playlist?>(null) }
    var deleting by remember { mutableStateOf<Playlist?>(null) }

    if (showCreate) {
        NameInputDialog(
            title = stringResource(R.string.playlists_create),
            confirmLabel = stringResource(R.string.action_create),
            onConfirm = {
                showCreate = false
                onCreate(it)
            },
            onDismiss = { showCreate = false },
        )
    }

    renaming?.let { playlist ->
        NameInputDialog(
            title = stringResource(R.string.playlist_rename_title),
            confirmLabel = stringResource(R.string.action_save),
            initialValue = playlist.name,
            onConfirm = {
                renaming = null
                onRename(playlist.id, it)
            },
            onDismiss = { renaming = null },
        )
    }

    deleting?.let { playlist ->
        ConfirmDialog(
            title = stringResource(R.string.playlist_delete_title),
            message = playlist.name,
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = {
                deleting = null
                onDelete(playlist.id)
            },
            onDismiss = { deleting = null },
        )
    }

    androidx.compose.foundation.layout.Column(modifier = modifier.fillMaxSize()) {
        AlexaTopBar(
            title = stringResource(R.string.playlists_title),
            onBack = onBack,
            actions = {
                AlexaIconButton(
                    iconRes = R.drawable.ic_playlist_add,
                    contentDescription = stringResource(R.string.playlists_create),
                    onClick = { showCreate = true },
                )
            },
        )

        if (playlists.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
                contentAlignment = Alignment.Center,
            ) {
                EmptyState(
                    iconRes = R.drawable.ic_playlists,
                    title = stringResource(R.string.playlists_empty_title),
                    body = stringResource(R.string.playlists_empty_body),
                    action = {
                        PrimaryButton(
                            text = stringResource(R.string.playlists_create),
                            onClick = { showCreate = true },
                        )
                    },
                )
            }
            return@Column
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(
                start = AlexaSpacing.lg,
                end = AlexaSpacing.lg,
                top = AlexaSpacing.md,
                bottom = contentPadding.calculateBottomPadding() + AlexaSpacing.xxl,
            ),
            horizontalArrangement = Arrangement.spacedBy(AlexaSpacing.md),
            verticalArrangement = Arrangement.spacedBy(AlexaSpacing.lg),
        ) {
            items(playlists, key = { it.id }) { playlist ->
                PlaylistCard(
                    playlist = playlist,
                    onClick = { onOpen(playlist.id) },
                    trailing = {
                        AlexaIconButton(
                            iconRes = R.drawable.ic_more_vert,
                            contentDescription = stringResource(R.string.cd_more_options),
                            onClick = { onMore(playlist) },
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    },
                )
            }
        }
    }
}