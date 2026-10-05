package com.alexaplayer.ui.screen.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alexaplayer.R
import com.alexaplayer.core.designsystem.component.AlexaIconButton
import com.alexaplayer.core.designsystem.component.Artwork
import com.alexaplayer.core.designsystem.component.EmptyState
import com.alexaplayer.core.designsystem.component.PrimaryButton
import com.alexaplayer.core.designsystem.component.QuietButton
import com.alexaplayer.core.designsystem.theme.AlexaRadius
import com.alexaplayer.core.designsystem.theme.AlexaSpacing
import com.alexaplayer.core.model.Song
import com.alexaplayer.core.util.DurationFormatter
import com.alexaplayer.ui.component.AlexaTopBar
import com.alexaplayer.ui.component.SectionHeader
import com.alexaplayer.ui.component.SongRow
import com.alexaplayer.ui.viewmodel.DetailViewModel
import com.alexaplayer.ui.component.AlbumRowItem

/** Shared screen for an album or an artist: header, primary actions and the song list. */
@Composable
fun DetailScreen(
    state: DetailViewModel.DetailUiState,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onPlay: (Int) -> Unit,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onToggleFavorite: (Song) -> Unit,
    onOpenAlbum: (Long) -> Unit,
    onMore: (Song) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        AlexaTopBar(title = state.title, onBack = onBack)

        if (state.songs.isEmpty() && !state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
                contentAlignment = Alignment.Center,
            ) {
                EmptyState(
                    iconRes = R.drawable.ic_song,
                    title = stringResource(R.string.detail_empty_title),
                    body = state.subtitle,
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
            item(key = "header") {
                DetailHeader(state = state, onPlayAll = onPlayAll, onShuffle = onShuffle)
            }

            if (state.albums.size > 1) {
                item(key = "albums_header") {
                    SectionHeader(
                        title = stringResource(R.string.detail_albums_header),
                        modifier = Modifier.padding(
                            start = AlexaSpacing.lg,
                            end = AlexaSpacing.lg,
                            top = AlexaSpacing.md,
                        ),
                    )
                }
                item(key = "albums_row") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = AlexaSpacing.lg),
                        horizontalArrangement = Arrangement.spacedBy(AlexaSpacing.md),
                    ) {
                        items(state.albums, key = { "al_${it.id}" }) { album ->
                            AlbumRowItem(album = album, onClick = { onOpenAlbum(album.id) })
                        }
                    }
                }
            }

            itemsIndexed(state.songs, key = { _, song -> "s_${song.id}" }) { index, song ->
                SongRow(
                    song = song,
                    isPlaying = song.id == state.currentSongId,
                    onClick = { onPlay(index) },
                    onLongClick = { onMore(song) },
                    trailing = {
                        AlexaIconButton(
                            iconRes = R.drawable.ic_more_vert,
                            contentDescription = stringResource(R.string.cd_more_options),
                            onClick = { onMore(song) },
                        )
                    },
                    modifier = Modifier.padding(horizontal = AlexaSpacing.sm),
                )
            }
        }
    }
}

@Composable
private fun DetailHeader(
    state: DetailViewModel.DetailUiState,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AlexaSpacing.lg, vertical = AlexaSpacing.md),
    ) {
        Artwork(
            artworkUri = state.artworkUri,
            seed = state.seed,
            contentDescription = state.title,
            shape = RoundedCornerShape(AlexaRadius.artworkLarge),
            modifier = Modifier.size(180.dp),
        )
        Text(
            text = state.title,
            style = MaterialTheme.typography.headlineSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = AlexaSpacing.md),
        )
        Text(
            text = state.subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = AlexaSpacing.xs),
        )
        Text(
            text = stringResource(
                R.string.playlist_summary,
                state.songCount,
                DurationFormatter.formatTotal(state.totalDurationMs),
            ),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = AlexaSpacing.xs),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(AlexaSpacing.sm),
            modifier = Modifier.padding(top = AlexaSpacing.lg),
        ) {
            PrimaryButton(
                text = stringResource(R.string.action_play),
                onClick = onPlayAll,
                modifier = Modifier.weight(1f),
            )
            QuietButton(
                text = stringResource(R.string.action_shuffle),
                onClick = onShuffle,
                modifier = Modifier.weight(1f),
            )
        }
    }
}