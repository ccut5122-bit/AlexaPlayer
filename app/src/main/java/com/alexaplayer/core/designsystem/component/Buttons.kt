package com.alexaplayer.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.alexaplayer.core.designsystem.theme.AlexaRadius
import com.alexaplayer.core.designsystem.theme.AlexaSizes
import com.alexaplayer.core.designsystem.theme.AlexaSpacing

/** Filled accent button. One per screen, at most. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    val interaction = rememberPressInteractionSource()
    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interaction,
        shape = RoundedCornerShape(AlexaRadius.md),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        contentPadding = PaddingValues(horizontal = AlexaSpacing.xl, vertical = AlexaSpacing.md),
        modifier = modifier
            .defaultMinSize(minHeight = AlexaSizes.minTouchTarget)
            .pressFeedback(interaction, shape = RoundedCornerShape(AlexaRadius.md)),
    ) {
        if (leadingIcon != null) {
            leadingIcon()
            Spacer(Modifier.width(AlexaSpacing.sm))
        }
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

/** Quiet secondary action: outlined, never competes with the primary action. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    val interaction = rememberPressInteractionSource()
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interaction,
        shape = RoundedCornerShape(AlexaRadius.md),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
        contentPadding = PaddingValues(horizontal = AlexaSpacing.xl, vertical = AlexaSpacing.md),
        modifier = modifier
            .defaultMinSize(minHeight = AlexaSizes.minTouchTarget)
            .pressFeedback(interaction, shape = RoundedCornerShape(AlexaRadius.md)),
    ) {
        if (leadingIcon != null) {
            leadingIcon()
            Spacer(Modifier.width(AlexaSpacing.sm))
        }
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

/** Text only action, used inside dialogs and sheets. */
@Composable
fun QuietButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(AlexaRadius.sm),
        colors = ButtonDefaults.textButtonColors(contentColor = contentColor),
        modifier = modifier.defaultMinSize(minHeight = 44.dp),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

/**
 * Icon button with a guaranteed 48dp target. Always pass a content description, either
 * through [contentDescription] or the caller's own semantics.
 */
@Composable
fun AlexaIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = LocalContentColor.current,
    size: Dp = AlexaSizes.iconMd,
) {
    val interaction = rememberPressInteractionSource()
    IconButton(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interaction,
        colors = IconButtonDefaults.iconButtonColors(contentColor = tint),
        modifier = modifier
            .size(AlexaSizes.minTouchTarget)
            .pressFeedback(interaction, pressedScale = 0.9f, pressedAlpha = 0.7f),
    ) {
        Icon(imageVector = icon, contentDescription = contentDescription, modifier = Modifier.size(size))
    }
}

/** Icon button for the generated vector drawables. */
@Composable
fun AlexaIconButton(
    iconRes: Int,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = LocalContentColor.current,
    size: Dp = AlexaSizes.iconMd,
) {
    val interaction = rememberPressInteractionSource()
    IconButton(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interaction,
        colors = IconButtonDefaults.iconButtonColors(contentColor = tint),
        modifier = modifier
            .size(AlexaSizes.minTouchTarget)
            .pressFeedback(interaction, pressedScale = 0.9f, pressedAlpha = 0.7f),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            modifier = Modifier.size(size),
        )
    }
}

/** Decorative icon: no semantics, used next to text that already says everything. */
@Composable
fun DecorativeIcon(
    iconRes: Int,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    size: Dp = AlexaSizes.iconSm,
) {
    Icon(
        painter = painterResource(iconRes),
        contentDescription = null,
        tint = tint,
        modifier = modifier
            .size(size)
            .clearAndSetSemantics { },
    )
}

/** Small icon + label pill used for metadata (duration, count, source). */
@Composable
fun MetaChip(
    iconRes: Int,
    label: String,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AlexaSpacing.xs),
        modifier = modifier,
    ) {
        DecorativeIcon(iconRes = iconRes, tint = tint, size = AlexaSizes.iconXs)
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = tint,
            maxLines = 1,
        )
    }
}
