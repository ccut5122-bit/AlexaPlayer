package com.alexaplayer.ui.screen.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.alexaplayer.R
import com.alexaplayer.core.designsystem.component.AlexaIconButton
import com.alexaplayer.core.designsystem.component.EmptyState
import com.alexaplayer.core.designsystem.component.LoadingList
import com.alexaplayer.core.designsystem.component.SecondaryButton
import com.alexaplayer.core.designsystem.theme.AlexaSpacing
import com.alexaplayer.core.model.Album
import com.alexaplayer.core.model.Artist
import com.alexaplayer.core.model.LibraryFolder
import com.alexaplayer.core.model.LibraryTab
import com.alexaplayer.core.util.DurationFormatter
import com.alexaplayer.core.model.Song
import com.alexaplayer.ui.component.AlexaTopBar
import com.alexaplayer.ui.component.SearchField
import com.alexaplayer.ui.component.SongRow
import com.alexaplayer.ui.viewmodel.LibraryFilter
import com.alexaplayer.ui.viewmodel.LibraryViewModel
import com.alexaplayer.ui.component.AlbumCard
import com.alexaplayer.ui.component.ArtistRowItem
import com.alexaplayer.ui.component.FolderRowItem

/**
 * The library is one screen with five tabs rather than five screens, because switching
 * between songs, albums, artists and folders should feel like turning a page, not like
 * navigating away.
 */
@Composable
fun LibraryScreen(
    state: LibraryViewModel.LibraryUiState,
    currentSongId: Long?,
    contentPadding: PaddingValues,
    query: String,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onSelectTab: (LibraryTab) -> Unit,
    onSelectFilter: (LibraryFilter) -> Unit,
    onOpenSort: () -> Unit,
    onPlay: (List<Song>, Int) -> Unit,
    onPlayAll: (List<Song>) -> Unit,
    onShuffleAll: (List<Song>) -> Unit,
    onToggleFavorite: (Song) -> Unit,
    onOpenAlbum: (Long) -> Unit,
    onOpenArtist: (Long) -> Unit,
    onOpenFolder: (String, String) -> Unit,
    onMore: (Song) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        AlexaTopBar(
            title = stringResource(R.string.library_title),
            actions = {
                AlexaIconButton(
                    iconRes = R.drawable.ic_sort,
                    contentDescription = stringResource(R.string.action_sort),
                    onClick = onOpenSort,
                )
            },
        )

        SearchField(
            value = query,
            onValueChange = onQueryChange,
            onClear = onClearQuery,
            placeholder = stringResource(R.string.library_search_hint),
            modifier = Modifier.padding(bottom = AlexaSpacing.xs),
        )

        TabRow(
            selectedTabIndex = LibraryTab.entries.indexOf(state.tab).coerceAtLeast(0),
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
        ) {
            LibraryTab.entries.forEach { tab ->
                val selected = tab == state.tab
                Tab(
                    selected = selected,
                    onClick = { onSelectTab(tab) },
                    text = {
                        Text(
                            text = stringResource(tab.labelRes),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    },
                    selectedContentColor = MaterialTheme.colorScheme.primary,
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (state.tab == LibraryTab.SONGS || state.tab == LibraryTab.FAVORITES) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(AlexaSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AlexaSpacing.lg, vertical = AlexaSpacing.sm),
            ) {
                LibraryFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = state.filter == filter,
                        onClick = { onSelectFilter(filter) },
                        label = {
                            Text(
                                text = stringResource(filter.labelRes),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                            selectedLabelColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                }
                Spacer(Modifier.width(AlexaSpacing.xs))
                Text(
                    text = stringResource(state.sortOrder.labelRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            if (state.isLoading) {
                LoadingList(
                    modifier = Modifier.padding(top = AlexaSpacing.md),
                )
            } else {
                when (state.tab) {
                    LibraryTab.SONGS, LibraryTab.FAVORITES -> SongTab(
                        songs = filterSongs(state.songs, query),
                        currentSongId = currentSongId,
                        contentPadding = contentPadding,
                        onPlay = onPlay,
                        onPlayAll = onPlayAll,
                        onShuffleAll = onShuffleAll,
                        onToggleFavorite = onToggleFavorite,
                        onMore = onMore,
                    )

                    LibraryTab.ALBUMS -> AlbumTab(
                        albums = state.albums,
                        contentPadding = contentPadding,
                        onOpenAlbum = onOpenAlbum,
                    )

                    LibraryTab.ARTISTS -> ArtistTab(
                        artists = state.artists,
                        contentPadding = contentPadding,
                        onOpenArtist = onOpenArtist,
                    )

                    LibraryTab.FOLDERS -> FolderTab(
                        folders = state.folders,
                        contentPadding = contentPadding,
                        onOpenFolder = onOpenFolder,
                    )
                }
            }
        }
    }
}

@Composable
private fun SongTab(
    songs: List<Song>,
    currentSongId: Long?,
    contentPadding: PaddingValues,
    onPlay: (List<Song>, Int) -> Unit,
    onPlayAll: (List<Song>) -> Unit,
    onShuffleAll: (List<Song>) -> Unit,
    onToggleFavorite: (Song) -> Unit,
    onMore: (Song) -> Unit,
) {
    if (songs.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            contentAlignment = Alignment.Center,
        ) {
            EmptyState(
                iconRes = R.drawable.ic_song,
                title = stringResource(R.string.library_empty_title),
                body = stringResource(R.string.library_empty_body),
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            bottom = contentPadding.calculateBottomPadding() + AlexaSpacing.xxl,
        ),
    ) {
        item(key = "play_all") {
            Row(
                horizontalArrangement = Arrangement.spacedBy(AlexaSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AlexaSpacing.lg, vertical = AlexaSpacing.sm),
            ) {
                SecondaryButton(
                    text = stringResource(R.string.action_play_all),
                    onClick = { onPlayAll(songs) },
                )
                SecondaryButton(
                    text = stringResource(R.string.action_shuffle),
                    onClick = { onShuffleAll(songs) },
                )
            }
        }
        itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
            SongRow(
                song = song,
                isPlaying = song.id == currentSongId,
                onClick = { onPlay(songs, index) },
                onLongClick = { onMore(song) },
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = DurationFormatter.format(song.durationMs),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        AlexaIconButton(
                            iconRes = R.drawable.ic_more_vert,
                            contentDescription = stringResource(R.string.cd_more_options),
                            onClick = { onMore(song) },
                        )
                    }
                },
                modifier = Modifier.padding(horizontal = AlexaSpacing.sm),
            )
        }
    }
}

@Composable
private fun AlbumTab(
    albums: List<Album>,
    contentPadding: PaddingValues,
    onOpenAlbum: (Long) -> Unit,
) {
    if (albums.isEmpty()) {
        EmptyTab(
            iconRes = R.drawable.ic_album,
            titleRes = R.string.library_album_empty_title,
            bodyRes = R.string.library_album_empty_body,
            contentPadding = contentPadding,
        )
        return
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
        items(albums, key = { it.id }) { album ->
            AlbumCard(album = album, onClick = { onOpenAlbum(album.id) })
        }
    }
}

@Composable
private fun ArtistTab(
    artists: List<Artist>,
    contentPadding: PaddingValues,
    onOpenArtist: (Long) -> Unit,
) {
    if (artists.isEmpty()) {
        EmptyTab(
            iconRes = R.drawable.ic_artist,
            titleRes = R.string.library_artist_empty_title,
            bodyRes = R.string.library_artist_empty_body,
            contentPadding = contentPadding,
        )
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(
            bottom = contentPadding.calculateBottomPadding() + AlexaSpacing.xxl,
        ),
    ) {
        items(artists, key = { it.id }) { artist ->
            ArtistRowItem(artist = artist, onClick = { onOpenArtist(artist.id) })
        }
    }
}

@Composable
private fun FolderTab(
    folders: List<LibraryFolder>,
    contentPadding: PaddingValues,
    onOpenFolder: (String, String) -> Unit,
) {
    if (folders.isEmpty()) {
        EmptyTab(
            iconRes = R.drawable.ic_folder,
            titleRes = R.string.library_folder_empty_title,
            bodyRes = R.string.library_folder_empty_body,
            contentPadding = contentPadding,
        )
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(
            bottom = contentPadding.calculateBottomPadding() + AlexaSpacing.xxl,
        ),
    ) {
        items(folders, key = { it.path }) { folder ->
            FolderRowItem(
                folder = folder,
                onClick = { onOpenFolder(folder.path, folder.name) },
            )
        }
    }
}

@Composable
private fun EmptyTab(
    iconRes: Int,
    titleRes: Int,
    bodyRes: Int,
    contentPadding: PaddingValues,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentAlignment = Alignment.Center,
    ) {
        EmptyState(
            iconRes = iconRes,
            title = stringResource(titleRes),
            body = stringResource(bodyRes),
        )
    }
}

private fun filterSongs(songs: List<Song>, query: String): List<Song> {
    if (query.isBlank()) return songs
    return songs.filter { song ->
        song.title.contains(query, ignoreCase = true) ||
            song.artist.contains(query, ignoreCase = true) ||
            song.album.contains(query, ignoreCase = true)
    }
}
