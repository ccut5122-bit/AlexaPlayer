package com.alexaplayer.ui.screen.streams

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.alexaplayer.R
import com.alexaplayer.core.designsystem.component.AlexaIconButton
import com.alexaplayer.core.designsystem.component.EmptyState
import com.alexaplayer.core.designsystem.component.PrimaryButton
import com.alexaplayer.core.designsystem.theme.AlexaSpacing
import com.alexaplayer.core.model.Song
import com.alexaplayer.ui.component.AlexaTopBar
import com.alexaplayer.ui.component.NameInputDialog
import com.alexaplayer.ui.component.SongRow

/** User added network streams; played through the same Media3 session as local files. */
@Composable
fun StreamsScreen(
    streams: List<Song>,
    contentPadding: PaddingValues,
    onBack: (() -> Unit)?,
    onPlay: (List<Song>, Int) -> Unit,
    onAdd: (String, String) -> Unit,
    onDelete: (Song) -> Unit,
    onMore: (Song) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showAdd by remember { mutableStateOf(false) }

    if (showAdd) {
        StreamInputDialog(
            onConfirm = { url, title ->
                showAdd = false
                onAdd(url, title)
            },
            onDismiss = { showAdd = false },
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        AlexaTopBar(
            title = stringResource(R.string.streams_title),
            onBack = onBack,
            actions = {
                AlexaIconButton(
                    iconRes = R.drawable.ic_add,
                    contentDescription = stringResource(R.string.streams_add),
                    onClick = { showAdd = true },
                )
            },
        )

        if (streams.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
                contentAlignment = Alignment.Center,
            ) {
                EmptyState(
                    iconRes = R.drawable.ic_wifi_off,
                    title = stringResource(R.string.streams_empty_title),
                    body = stringResource(R.string.streams_empty_body),
                    action = {
                        PrimaryButton(
                            text = stringResource(R.string.streams_add),
                            onClick = { showAdd = true },
                        )
                    },
                )
            }
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                bottom = contentPadding.calculateBottomPadding() + AlexaSpacing.xxl,
            ),
            verticalArrangement = Arrangement.spacedBy(AlexaSpacing.xs),
        ) {
            itemsIndexed(streams, key = { _, song -> "s_${song.id}" }) { index, song ->
                SongRow(
                    song = song,
                    onClick = { onPlay(streams, index) },
                    onLongClick = { onMore(song) },
                    trailing = {
                        AlexaIconButton(
                            iconRes = R.drawable.ic_delete,
                            contentDescription = stringResource(R.string.action_delete),
                            onClick = { onDelete(song) },
                        )
                    },
                    modifier = Modifier.padding(horizontal = AlexaSpacing.sm),
                )
            }
        }
    }
}

@Composable
private fun StreamInputDialog(onConfirm: (url: String, title: String) -> Unit, onDismiss: () -> Unit) {
    var url by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }

    NameInputDialog(
        title = stringResource(R.string.streams_add),
        label = stringResource(R.string.streams_url_label),
        confirmLabel = stringResource(R.string.action_save),
        value = url,
        onValueChange = { url = it },
        errorText = if (url.isBlank() || url.startsWith("http")) {
            null
        } else {
            stringResource(R.string.streams_url_error)
        },
        onConfirm = { onConfirm(url.trim(), title.ifBlank { url.trim() }) },
        onDismiss = onDismiss,
        supportingText = title.ifBlank { null },
    )
}