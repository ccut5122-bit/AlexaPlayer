@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.alexaplayer.ui.component

import com.alexaplayer.core.designsystem.component.QuietButton
import com.alexaplayer.core.designsystem.component.DecorativeIcon
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alexaplayer.R
import com.alexaplayer.core.designsystem.theme.AlexaRadius
import com.alexaplayer.core.designsystem.theme.AlexaSizes
import com.alexaplayer.core.designsystem.theme.AlexaSpacing
import com.alexaplayer.core.model.Song

/** One row inside a sheet. */
data class SheetOption(
    val label: String,
    val iconRes: Int? = null,
    val selected: Boolean = false,
    val destructive: Boolean = false,
    val enabled: Boolean = true,
    val value: String? = null,
)

/**
 * The app has one sheet shape: a title, a list of actions, and nothing else. Sort order,
 * theme choice, audio focus and the per-song menu are all this, so they behave identically.
 */
@Composable
fun OptionsSheet(
    title: String,
    options: List<SheetOption>,
    onSelect: (SheetOption) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        SheetHeader(title = title)
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = SHEET_MAX_HEIGHT)
                .padding(bottom = AlexaSpacing.lg),
        ) {
            items(options, key = { it.label + (it.value ?: "") }) { option ->
                SheetOptionRow(option = option, onClick = { onSelect(option) })
            }
        }
    }
}

@Composable
private fun SheetHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(
            start = AlexaSpacing.lg,
            end = AlexaSpacing.lg,
            top = AlexaSpacing.xs,
            bottom = AlexaSpacing.sm,
        ),
    )
}

@Composable
private fun SheetOptionRow(
    option: SheetOption,
    onClick: () -> Unit,
) {
    val contentColor = when {
        option.destructive -> MaterialTheme.colorScheme.error
        option.selected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = option.enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = AlexaSpacing.lg, vertical = AlexaSpacing.md),
    ) {
        if (option.iconRes != null) {
            DecorativeIcon(
                iconRes = option.iconRes,
                tint = contentColor,
                size = AlexaSizes.iconSm,
            )
            Spacer(Modifier.width(AlexaSpacing.md))
        }
        Text(
            text = option.label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (option.enabled) contentColor else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (option.value != null) {
            Text(
                text = option.value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (option.selected) {
            Spacer(Modifier.width(AlexaSpacing.sm))
            DecorativeIcon(
                iconRes = R.drawable.ic_check,
                tint = MaterialTheme.colorScheme.primary,
                size = AlexaSizes.iconSm,
            )
        }
    }
}

/**
 * Song picker used when adding to a playlist. Search first, multi-select second, and the
 * confirmation stays disabled until something is actually picked.
 */
@Composable
fun PickSongsSheet(
    title: String,
    songs: List<Song>,
    selectedIds: Set<Long>,
    onToggle: (Song) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(bottom = AlexaSpacing.md)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AlexaSpacing.lg, vertical = AlexaSpacing.sm),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                QuietButton(
                    text = stringResource(R.string.action_done),
                    contentColor = if (selectedIds.isEmpty()) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    enabled = selectedIds.isNotEmpty(),
                    onClick = onConfirm,
                )
            }
            if (songs.isEmpty()) {
                Text(
                    text = stringResource(R.string.playlist_empty_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(AlexaSpacing.lg),
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = SHEET_MAX_HEIGHT),
                ) {
                    items(songs, key = { it.id }) { song ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(role = Role.Checkbox) { onToggle(song) }
                                .padding(horizontal = AlexaSpacing.lg, vertical = AlexaSpacing.xs),
                        ) {
                            Checkbox(
                                checked = song.id in selectedIds,
                                onCheckedChange = { onToggle(song) },
                            )
                            Spacer(Modifier.width(AlexaSpacing.sm))
                            Text(
                                text = song.title,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Circular badge used for counts on top of icons. */
@Composable
fun CountBadge(count: Int, modifier: Modifier = Modifier) {
    if (count <= 0) return
    Box(
        modifier = modifier
            .size(BADGE_SIZE)
            .clip(RoundedCornerShape(AlexaRadius.pill))
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = count.coerceAtMost(99).toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

private val SHEET_MAX_HEIGHT = 520.dp
private val BADGE_SIZE = 18.dp
