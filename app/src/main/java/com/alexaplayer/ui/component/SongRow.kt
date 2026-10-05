package com.alexaplayer.ui.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alexaplayer.R
import com.alexaplayer.core.designsystem.component.AlexaIconButton
import com.alexaplayer.core.designsystem.component.Artwork
import com.alexaplayer.core.designsystem.theme.AlexaRadius
import com.alexaplayer.core.designsystem.theme.AlexaSizes
import com.alexaplayer.core.designsystem.theme.AlexaSpacing
import com.alexaplayer.core.designsystem.theme.SongMetaStyle
import com.alexaplayer.core.designsystem.theme.SongTitleStyle
import com.alexaplayer.core.model.Song
import com.alexaplayer.core.util.DurationFormatter

/**
 * One track in a list.
 *
 * The whole row is the target, the heart is a separate target, and the trailing slot lets
 * screens add their own action without forking this component.
 */
@Composable
fun SongRow(
    song: Song,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = false,
    subtitle: String? = null,
    leadingIndex: Int? = null,
    trailing: (@Composable () -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    showDuration: Boolean = true,
    enabled: Boolean = true,
) {
    val meta = subtitle ?: buildString {
        append(song.artist)
        if (song.album.isNotBlank() && song.album != song.artist) {
            append(" · ")
            append(song.album)
        }
    }
    val duration = DurationFormatter.format(song.durationMs)
    val rowDescription = if (meta.isBlank()) {
        "$song.title, $duration"
    } else {
        "$song.title, $meta, $duration"
    }

    val clickableModifier = if (onLongClick != null) {
        Modifier.combinedClickable(
            enabled = enabled,
            role = Role.Button,
            onLongClick = onLongClick,
            onClick = onClick,
        )
    } else {
        Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AlexaRadius.md))
            .background(
                if (isPlaying) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent,
            )
            .then(clickableModifier)
            .padding(horizontal = AlexaSpacing.md, vertical = AlexaSpacing.sm)
            .semantics(mergeDescendants = true) {
                contentDescription = rowDescription
                role = Role.Button
            },
    ) {
        if (leadingIndex != null) {
            Box(
                modifier = Modifier
                    .size(AlexaSizes.artworkRow)
                    .clearAndSetSemantics { },
                contentAlignment = Alignment.Center,
            ) {
                if (isPlaying) {
                    PlayingIndicator(active = true)
                } else {
                    Text(
                        text = leadingIndex.toString(),
                        style = SongMetaStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            Artwork(
                artworkUri = song.artworkUri,
                seed = song.id,
                contentDescription = null,
                shape = RoundedCornerShape(AlexaRadius.sm),
                modifier = Modifier.size(AlexaSizes.artworkRow),
            )
        }

        Spacer(Modifier.width(AlexaSpacing.md))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = SongTitleStyle,
                color = if (isPlaying) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (meta.isNotBlank()) {
                Text(
                    text = meta,
                    style = SongMetaStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (trailing != null) {
            Spacer(Modifier.width(AlexaSpacing.xs))
            trailing()
        } else if (showDuration) {
            Text(
                text = duration,
                style = SongMetaStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = AlexaSpacing.sm),
            )
        }
    }
}

/** Heart that toggles without stealing the row's click target. */
@Composable
fun FavoriteButton(
    isFavorite: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlexaIconButton(
        iconRes = if (isFavorite) R.drawable.ic_favorite_filled else R.drawable.ic_favorite,
        contentDescription = stringResource(
            if (isFavorite) R.string.action_favorite_remove else R.string.action_favorite_add,
        ),
        onClick = onToggle,
        tint = if (isFavorite) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = modifier,
    )
}

/**
 * Three bars that breathe while the track plays and settle when it is paused. This is the
 * only place in the app where decoration is allowed to move.
 */
@Composable
fun PlayingIndicator(
    active: Boolean,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    val transition = rememberInfiniteTransition(label = "playingIndicator")
    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier.size(width = 14.dp, height = 16.dp),
    ) {
        BAR_SPEEDS_MS.forEachIndexed { index, duration ->
            val animated by transition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = duration, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "bar$index",
            )
            val heightFraction = if (active) animated else 0.3f
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .size(width = 3.dp, height = 16.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color)
                    .graphicsLayer {
                        scaleY = heightFraction
                        transformOrigin = TransformOrigin(0.5f, 1f)
                    },
            )
        }
    }
}

private val BAR_SPEEDS_MS = listOf(620, 460, 780)