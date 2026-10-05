package com.alexaplayer.ui.screen.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.alexaplayer.R
import com.alexaplayer.core.designsystem.component.AlexaIconButton
import com.alexaplayer.core.designsystem.component.EmptyState
import com.alexaplayer.core.designsystem.component.PrimaryButton
import com.alexaplayer.core.designsystem.component.SecondaryButton
import com.alexaplayer.core.designsystem.theme.AlexaRadius
import com.alexaplayer.core.designsystem.theme.AlexaSizes
import com.alexaplayer.core.designsystem.theme.AlexaSpacing
import com.alexaplayer.core.designsystem.theme.AlexaTheme
import com.alexaplayer.core.model.Song
import com.alexaplayer.ui.component.AlbumRowItem
import com.alexaplayer.ui.component.PlaylistCard
import com.alexaplayer.ui.component.SectionHeader
import com.alexaplayer.ui.component.SongRow
import com.alexaplayer.ui.component.ShortcutTile
import com.alexaplayer.ui.viewmodel.HomeViewModel
import java.time.LocalTime

/**
 * Home answers three questions in order: what do I want to hear again, what is new, and
 * what did I save. Everything else is one tap away from the library.
 */
@Composable
fun HomeScreen(
    state: HomeViewModel.HomeUiState,
    currentSongId: Long?,
    contentPadding: PaddingValues,
    onPlay: (List<Song>, Int) -> Unit,
    onPlayAll: (List<Song>) -> Unit,
    onShuffleAll: (List<Song>) -> Unit,
    onToggleFavorite: (Song) -> Unit,
    onOpenAlbum: (Long) -> Unit,
    onOpenPlaylist: (Long) -> Unit,
    onOpenPlaylists: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenSettings: () -> Unit,
    onMore: (Song) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!state.isLoading && state.songCount == 0) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(contentPadding),
            contentAlignment = Alignment.Center,
        ) {
            EmptyState(
                iconRes = R.drawable.ic_library,
                title = stringResource(R.string.library_empty_title),
                body = stringResource(R.string.library_empty_body),
                action = {
                    SecondaryButton(
                        text = stringResource(R.string.home_browse_library),
                        onClick = onOpenLibrary,
                    )
                },
            )
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + AlexaSpacing.sm,
            bottom = contentPadding.calculateBottomPadding() + AlexaSpacing.xxl,
        ),
        verticalArrangement = Arrangement.spacedBy(AlexaSpacing.xs),
    ) {
        item(key = "greeting") {
            GreetingHeader(songCount = state.songCount)
        }

        item(key = "quick_play") {
            QuickPlayCard(
                songs = state.allSongs,
                fallback = state.recentlyPlayed,
                onPlayAll = onPlayAll,
                onShuffleAll = onShuffleAll,
            )
        }

        item(key = "shortcuts") {
            Row(
                horizontalArrangement = Arrangement.spacedBy(AlexaSpacing.md),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AlexaSpacing.lg, vertical = AlexaSpacing.md),
            ) {
                ShortcutTile(
                    label = stringResource(R.string.home_favorites),
                    iconRes = R.drawable.ic_favorite_filled,
                    onClick = onOpenLibrary,
                    modifier = Modifier.weight(1f),
                )
                ShortcutTile(
                    label = stringResource(R.string.nav_playlists),
                    iconRes = R.drawable.ic_playlists,
                    onClick = onOpenPlaylists,
                    modifier = Modifier.weight(1f),
                )
                ShortcutTile(
                    label = stringResource(R.string.nav_library),
                    iconRes = R.drawable.ic_album,
                    onClick = onOpenLibrary,
                    modifier = Modifier.weight(1f),
                )
                ShortcutTile(
                    label = stringResource(R.string.nav_settings),
                    iconRes = R.drawable.ic_settings,
                    onClick = onOpenSettings,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (state.recentlyPlayed.isNotEmpty()) {
            item(key = "recently_played_header") {
                SectionHeader(
                    title = stringResource(R.string.home_recently_played),
                    modifier = Modifier.padding(horizontal = AlexaSpacing.lg),
                )
            }
            items(state.recentlyPlayed, key = { "rp_${it.id}" }) { song ->
                SongRow(
                    song = song,
                    isPlaying = song.id == currentSongId,
                    onClick = { onPlay(state.recentlyPlayed, state.recentlyPlayed.indexOf(song)) },
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

        if (state.recentAlbums.isNotEmpty()) {
            item(key = "albums_header") {
                SectionHeader(
                    title = stringResource(R.string.home_recently_added),
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
                    items(state.recentAlbums, key = { "album_${it.id}" }) { album ->
                        AlbumRowItem(album = album, onClick = { onOpenAlbum(album.id) })
                    }
                }
            }
        }

        if (state.playlists.isNotEmpty()) {
            item(key = "playlists_header") {
                SectionHeader(
                    title = stringResource(R.string.home_your_playlists),
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
                    items(state.playlists, key = { "playlist_${it.id}" }) { playlist ->
                        PlaylistCard(
                            playlist = playlist,
                            onClick = { onOpenPlaylist(playlist.id) },
                            modifier = Modifier.width(PLAYLIST_CARD_WIDTH),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GreetingHeader(songCount: Int) {
    Column(modifier = Modifier.padding(horizontal = AlexaSpacing.lg, vertical = AlexaSpacing.sm)) {
        Text(
            text = greeting(),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (songCount > 0) {
            Text(
                text = pluralSongCount(songCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The one hero on the screen. It plays whatever the person was last listening to, or the
 * whole library when there is no history yet.
 */
@Composable
private fun QuickPlayCard(
    songs: List<Song>,
    fallback: List<Song>,
    onPlayAll: (List<Song>) -> Unit,
    onShuffleAll: (List<Song>) -> Unit,
) {
    val extended = AlexaTheme.extended
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AlexaSpacing.lg, vertical = AlexaSpacing.sm)
            .clip(RoundedCornerShape(AlexaRadius.lg))
            .background(
                Brush.linearGradient(
                    listOf(
                        extended.accentCyan.copy(alpha = 0.16f),
                        extended.accentViolet.copy(alpha = 0.12f),
                    ),
                ),
            )
            .padding(AlexaSpacing.lg),
    ) {
        Text(
            text = stringResource(R.string.home_quick_play),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(AlexaSpacing.xs))
        Text(
            text = stringResource(R.string.home_quick_play_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(AlexaSpacing.lg))
        Row(horizontalArrangement = Arrangement.spacedBy(AlexaSpacing.md)) {
            PrimaryButton(
                text = stringResource(R.string.action_play),
                leadingIcon = {
                    com.alexaplayer.core.designsystem.component.DecorativeIcon(
                        iconRes = R.drawable.ic_play,
                        size = AlexaSizes.iconSm,
                    )
                },
                onClick = {
                    val queue = if (fallback.isNotEmpty()) fallback else songs
                    onPlayAll(queue)
                },
            )
            SecondaryButton(
                text = stringResource(R.string.action_shuffle),
                leadingIcon = {
                    com.alexaplayer.core.designsystem.component.DecorativeIcon(
                        iconRes = R.drawable.ic_shuffle,
                        size = AlexaSizes.iconSm,
                    )
                },
                onClick = {
                    val queue = if (songs.isNotEmpty()) songs else fallback
                    onShuffleAll(queue)
                },
            )
        }
    }
}

@Composable
private fun greeting(): String {
    val hour = runCatching { LocalTime.now().hour }.getOrDefault(12)
    return when (hour) {
        in 5..11 -> stringResource(R.string.home_greeting_morning)
        in 12..16 -> stringResource(R.string.home_greeting_afternoon)
        in 17..20 -> stringResource(R.string.home_greeting_evening)
        else -> stringResource(R.string.home_greeting_night)
    }
}

@Composable
private fun pluralSongCount(count: Int): String = pluralStringResource(
    id = R.plurals.plural_song_count,
    count = count,
)

private val PLAYLIST_CARD_WIDTH = 140.dp