package com.alexaplayer.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Press feedback used across the app: a small scale dip and a slight alpha change.
 * It is barely visible, but it is the difference between a screen that feels alive and
 * one that feels like a web page.
 *
 * Pass the same [interactionSource] that the clickable uses so ripple and feedback stay
 * in sync.
 */
@Composable
fun Modifier.pressFeedback(
    interactionSource: MutableInteractionSource,
    shape: Shape = RectangleShape,
    pressedScale: Float = 0.97f,
    pressedAlpha: Float = 0.9f,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        label = "pressScale",
    )
    val alpha by animateFloatAsState(
        targetValue = if (pressed) pressedAlpha else 1f,
        label = "pressAlpha",
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
        this.alpha = alpha
    }
}

@Composable
fun rememberPressInteractionSource(): MutableInteractionSource = remember { MutableInteractionSource() }
