package com.alexaplayer.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alexaplayer.R
import com.alexaplayer.core.designsystem.component.Artwork
import com.alexaplayer.core.designsystem.component.DecorativeIcon
import com.alexaplayer.core.designsystem.theme.AlexaRadius
import com.alexaplayer.core.designsystem.theme.AlexaSizes
import com.alexaplayer.core.designsystem.theme.AlexaSpacing
import com.alexaplayer.core.designsystem.theme.SectionHeaderStyle
import com.alexaplayer.core.model.Album
import com.alexaplayer.core.model.Artist
import com.alexaplayer.core.model.LibraryFolder
import com.alexaplayer.core.model.Playlist
import com.alexaplayer.core.util.DurationFormatter
import com.alexaplayer.core.util.RelativeTime

/** Section title with an optional trailing text action. Never more than one per section. */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = SectionHeaderStyle,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (actionLabel != null && onAction != null) {
            Text(
                text = actionLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(AlexaRadius.sm))
                    .clickable(onClick = onAction)
                    .padding(horizontal = AlexaSpacing.sm, vertical = AlexaSpacing.xs),
            )
        }
    }
}

/** Square album tile used inside grids. Title and artist clamp to two lines each. */
@Composable
fun AlbumCard(
    album: Album,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Column(modifier = modifier) {
        Box {
            Artwork(
                artworkUri = album.artworkUri,
                seed = album.id,
                contentDescription = null,
                shape = RoundedCornerShape(AlexaRadius.md),
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(AlexaRadius.md))
                    .clickable(role = Role.Button, onClick = onClick)
                    .semantics {
                        contentDescription = "${album.title}, ${album.artist}, " +
                            "${album.songCount}"
                    },
            )
            if (trailing != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(AlexaSpacing.xs),
                ) { trailing() }
            }
        }
        Spacer(Modifier.height(AlexaSpacing.sm))
        Text(
            text = album.title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = album.artist,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Playlist tile: up to four covers, in the same grid rhythm as albums. */
@Composable
fun PlaylistCard(
    playlist: Playlist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Column(modifier = modifier) {
        Box {
            PlaylistArtwork(
                playlist = playlist,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(AlexaRadius.md))
                    .clickable(role = Role.Button, onClick = onClick)
                    .semantics {
                        contentDescription = "${playlist.name}, ${playlist.songCount}"
                    },
            )
            if (trailing != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(AlexaSpacing.xs),
                ) { trailing() }
            }
        }
        Spacer(Modifier.height(AlexaSpacing.sm))
        Text(
            text = playlist.name,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = songCountLabel(playlist.songCount),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/**
 * Collage of up to four covers. A playlist with fewer songs shows fewer tiles rather than
 * stretching one image, which keeps the grid honest.
 */
@Composable
fun PlaylistArtwork(
    playlist: Playlist,
    modifier: Modifier = Modifier,
) {
    val covers = playlist.artworkUris.take(4)
    Box(modifier = modifier) {
        when (covers.size) {
            0 -> Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                DecorativeIcon(
                    iconRes = R.drawable.ic_playlists,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    size = AlexaSizes.iconLg,
                )
            }

            1 -> Artwork(
                artworkUri = covers.first(),
                seed = playlist.id,
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
            )

            2 -> Row(modifier = Modifier.matchParentSize()) {
                covers.forEachIndexed { index, uri ->
                    Artwork(
                        artworkUri = uri,
                        seed = playlist.id + index,
                        contentDescription = null,
                        shape = RoundedCornerShape(0.dp),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    )
                }
            }

            else -> Column(modifier = Modifier.matchParentSize()) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    covers.take(2).forEachIndexed { index, uri ->
                        Artwork(
                            artworkUri = uri,
                            seed = playlist.id + index,
                            contentDescription = null,
                            shape = RoundedCornerShape(0.dp),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    covers.drop(2).take(2).forEachIndexed { index, uri ->
                        Artwork(
                            artworkUri = uri,
                            seed = playlist.id + index + 2,
                            contentDescription = null,
                            shape = RoundedCornerShape(0.dp),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

/** Compact album entry for the horizontal rows on Home. */
@Composable
fun AlbumRowItem(
    album: Album,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: androidx.compose.ui.unit.Dp = 132.dp,
) {
    Column(
        modifier = modifier
            .width(width)
            .clip(RoundedCornerShape(AlexaRadius.md))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = AlexaSpacing.xs),
    ) {
        Artwork(
            artworkUri = album.artworkUri,
            seed = album.id,
            contentDescription = null,
            shape = RoundedCornerShape(AlexaRadius.md),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
        )
        Spacer(Modifier.height(AlexaSpacing.sm))
        Text(
            text = album.title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = album.artist,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Artist row: circular cover, name, album and song counts. */
@Composable
fun ArtistRowItem(
    artist: Artist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AlexaRadius.md))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = AlexaSpacing.md, vertical = AlexaSpacing.sm)
            .semantics {
                contentDescription = "${artist.name}, ${artist.songCount}"
            },
    ) {
        Artwork(
            artworkUri = artist.artworkUri,
            seed = artist.id,
            contentDescription = null,
            shape = RoundedCornerShape(AlexaRadius.pill),
            modifier = Modifier.size(AlexaSizes.artworkRow),
        )
        Spacer(Modifier.width(AlexaSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = artist.name,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = artistSummary(artist),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/** Folder row: folder icon, full path, song count. */
@Composable
fun FolderRowItem(
    folder: LibraryFolder,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AlexaRadius.md))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = AlexaSpacing.md, vertical = AlexaSpacing.sm)
            .semantics { contentDescription = "${folder.name}, ${folder.songCount}" },
    ) {
        Box(
            modifier = Modifier
                .size(AlexaSizes.artworkRow)
                .clip(RoundedCornerShape(AlexaRadius.sm))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            DecorativeIcon(
                iconRes = R.drawable.ic_folder,
                tint = MaterialTheme.colorScheme.primary,
                size = AlexaSizes.iconMd,
            )
        }
        Spacer(Modifier.width(AlexaSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = folder.name,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = folder.path,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (trailing != null) trailing() else {
            Text(
                text = songCountLabel(folder.songCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Tiles for the top of Home. Fixed height so the row never jumps as labels change length.
 */
@Composable
fun ShortcutTile(
    label: String,
    iconRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .height(96.dp)
            .clip(RoundedCornerShape(AlexaRadius.lg))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(AlexaSpacing.sm),
    ) {
        DecorativeIcon(
            iconRes = iconRes,
            tint = MaterialTheme.colorScheme.primary,
            size = AlexaSizes.iconMd,
        )
        Spacer(Modifier.height(AlexaSpacing.sm))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Duration and count on one line, used under detail headers. */
@Composable
fun SummaryLine(
    parts: List<String>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(AlexaSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        parts.filter { it.isNotBlank() }.forEach { part ->
            Text(
                text = part,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun artistSummary(artist: Artist): String {
    val albums = songCountLabel(artist.albumCount)
    val songs = songCountLabel(artist.songCount)
    return "$albums · $songs"
}

/** "1 song" / "12 songs". One helper so the wording never drifts between screens. */
@Composable
fun songCountLabel(count: Int): String = pluralStringResource(
    id = R.plurals.plural_song_count,
    count = count,
    count = count,
)

@Composable
fun albumDurationLabel(durationMs: Long): String = DurationFormatter.format(durationMs)

@Composable
fun updatedLabel(timestamp: Long): String =
    RelativeTime.format(LocalContext.current, timestamp)
