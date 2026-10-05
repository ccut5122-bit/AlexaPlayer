package com.alexaplayer.ui.screen.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alexaplayer.R
import com.alexaplayer.core.designsystem.component.AlexaIconButton
import com.alexaplayer.core.designsystem.component.EmptyState
import com.alexaplayer.core.designsystem.theme.AlexaSpacing
import com.alexaplayer.core.model.Song
import com.alexaplayer.ui.component.AlexaTopBar
import com.alexaplayer.ui.component.SearchField
import com.alexaplayer.ui.component.SectionHeader
import com.alexaplayer.ui.component.SongRow
import com.alexaplayer.data.repository.SearchResults
import com.alexaplayer.ui.component.AlbumRowItem
import com.alexaplayer.ui.component.ArtistRowItem
import com.alexaplayer.ui.component.PlaylistCard
import com.alexaplayer.ui.component.SettingsRow

/**
 * Search across songs, albums, artists and playlists at once. History is only shown while
 * the field is empty, and it disappears the moment someone starts typing.
 */
@Composable
fun SearchScreen(
    results: SearchResults,
    recentSearches: List<String>,
    currentSongId: Long?,
    contentPadding: PaddingValues,
    query: String,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onSubmit: () -> Unit,
    onForget: (String) -> Unit,
    onClearHistory: () -> Unit,
    onPlay: (List<Song>, Int) -> Unit,
    onOpenAlbum: (Long) -> Unit,
    onOpenArtist: (Long) -> Unit,
    onOpenPlaylist: (Long) -> Unit,
    onMore: (Song) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        AlexaTopBar(
            title = stringResource(R.string.search_title),
            onBack = onBack,
        )

        SearchField(
            value = query,
            onValueChange = onQueryChange,
            onClear = onClearQuery,
            onSubmit = onSubmit,
        )

        if (query.isBlank()) {
            RecentSearches(
                recent = recentSearches,
                contentPadding = contentPadding,
                onPick = { value ->
                    onQueryChange(value)
                    onSubmit()
                },
                onForget = onForget,
                onClearHistory = onClearHistory,
            )
            return@Column
        }

        if (results.isEmpty) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
                contentAlignment = Alignment.Center,
            ) {
                EmptyState(
                    iconRes = R.drawable.ic_search,
                    title = stringResource(R.string.search_no_results_title),
                    body = stringResource(R.string.search_no_results_body),
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
            if (results.songs.isNotEmpty()) {
                item(key = "songs_header") {
                    SectionHeader(
                        title = stringResource(R.string.search_section_songs),
                        modifier = Modifier.padding(
                            start = AlexaSpacing.lg,
                            end = AlexaSpacing.lg,
                            top = AlexaSpacing.md,
                        ),
                    )
                }
                itemsIndexed(results.songs, key = { _, song -> "s_${song.id}" }) { index, song ->
                    SongRow(
                        song = song,
                        isPlaying = song.id == currentSongId,
                        onClick = { onPlay(results.songs, index) },
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

            if (results.artists.isNotEmpty()) {
                item(key = "artists_header") {
                    SectionHeader(
                        title = stringResource(R.string.search_section_artists),
                        modifier = Modifier.padding(
                            start = AlexaSpacing.lg,
                            end = AlexaSpacing.lg,
                            top = AlexaSpacing.lg,
                        ),
                    )
                }
                items(results.artists, key = { "ar_${it.id}" }) { artist ->
                    ArtistRowItem(artist = artist, onClick = { onOpenArtist(artist.id) })
                }
            }

            if (results.albums.isNotEmpty()) {
                item(key = "albums_header") {
                    SectionHeader(
                        title = stringResource(R.string.search_section_albums),
                        modifier = Modifier.padding(
                            start = AlexaSpacing.lg,
                            end = AlexaSpacing.lg,
                            top = AlexaSpacing.lg,
                        ),
                    )
                }
                item(key = "albums_row") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = AlexaSpacing.lg),
                        horizontalArrangement = Arrangement.spacedBy(AlexaSpacing.md),
                    ) {
                        items(results.albums, key = { "al_${it.id}" }) { album ->
                            AlbumRowItem(album = album, onClick = { onOpenAlbum(album.id) })
                        }
                    }
                }
            }

            if (results.playlists.isNotEmpty()) {
                item(key = "playlists_header") {
                    SectionHeader(
                        title = stringResource(R.string.search_section_playlists),
                        modifier = Modifier.padding(
                            start = AlexaSpacing.lg,
                            end = AlexaSpacing.lg,
                            top = AlexaSpacing.lg,
                        ),
                    )
                }
                item(key = "playlists_row") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = AlexaSpacing.lg),
                        horizontalArrangement = Arrangement.spacedBy(AlexaSpacing.md),
                    ) {
                        items(results.playlists, key = { "pl_${it.id}" }) { playlist ->
                            PlaylistCard(
                                playlist = playlist,
                                onClick = { onOpenPlaylist(playlist.id) },
                                modifier = Modifier.width(SEARCH_CARD_WIDTH),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentSearches(
    recent: List<String>,
    contentPadding: PaddingValues,
    onPick: (String) -> Unit,
    onForget: (String) -> Unit,
    onClearHistory: () -> Unit,
) {
    if (recent.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            contentAlignment = Alignment.Center,
        ) {
            EmptyState(
                iconRes = R.drawable.ic_history,
                title = stringResource(R.string.search_title),
                body = stringResource(R.string.search_hint),
            )
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = AlexaSpacing.md),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AlexaSpacing.lg),
        ) {
            SectionHeader(
                title = stringResource(R.string.search_recent),
                modifier = Modifier.weight(1f),
            )
            AlexaIconButton(
                iconRes = R.drawable.ic_delete,
                contentDescription = stringResource(R.string.search_clear_recent),
                onClick = onClearHistory,
            )
        }
        recent.forEach { entry ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AlexaSpacing.lg),
            ) {
                SettingsRow(
                    title = entry,
                    onClick = { onPick(entry) },
                    modifier = Modifier.weight(1f),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
                AlexaIconButton(
                    iconRes = R.drawable.ic_close,
                    contentDescription = stringResource(R.string.action_delete),
                    onClick = { onForget(entry) },
                )
            }
        }
    }
}

private val SEARCH_CARD_WIDTH = 140.dp