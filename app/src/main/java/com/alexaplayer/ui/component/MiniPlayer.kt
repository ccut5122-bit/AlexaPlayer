package com.alexaplayer.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alexaplayer.R
import com.alexaplayer.core.designsystem.component.AlexaIconButton
import com.alexaplayer.core.designsystem.component.Artwork
import com.alexaplayer.core.designsystem.theme.AlexaRadius
import com.alexaplayer.core.designsystem.theme.AlexaSpacing
import com.alexaplayer.core.designsystem.theme.SongMetaStyle
import com.alexaplayer.core.designsystem.theme.SongTitleStyle
import com.alexaplayer.core.model.Song

/**
 * Persistent transport strip above the navigation bar. Deliberately quiet: artwork, the
 * track, and two controls. Everything else lives in the full player.
 */
@Composable
fun MiniPlayer(
    song: Song,
    isPlaying: Boolean,
    isBuffering: Boolean,
    progressFraction: Float,
    onOpenPlayer: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playLabel = stringResource(if (isPlaying) R.string.action_pause else R.string.action_play)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onOpenPlayer)
                .padding(horizontal = AlexaSpacing.sm, vertical = AlexaSpacing.sm)
                .semantics(mergeDescendants = true) {
                    contentDescription = "${song.title}, ${song.artist}"
                },
        ) {
            Artwork(
                artworkUri = song.artworkUri,
                seed = song.id,
                contentDescription = null,
                shape = RoundedCornerShape(AlexaRadius.sm),
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(AlexaRadius.sm)),
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
            AlexaIconButton(
                iconRes = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
                contentDescription = playLabel,
                onClick = onTogglePlayPause,
                tint = MaterialTheme.colorScheme.onSurface,
            )
            AlexaIconButton(
                iconRes = R.drawable.ic_skip_next,
                contentDescription = stringResource(R.string.action_next),
                onClick = onNext,
            )
            AlexaIconButton(
                iconRes = R.drawable.ic_more_horiz,
                contentDescription = stringResource(R.string.cd_mini_player_more),
                onClick = onMore,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        ) {
            if (isBuffering) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth(progressFraction.coerceIn(0f, 1f))
                    .height(2.dp)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}