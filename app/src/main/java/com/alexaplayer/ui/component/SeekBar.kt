package com.alexaplayer.ui.component

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.alexaplayer.core.designsystem.theme.AlexaRadius
import com.alexaplayer.core.designsystem.theme.AlexaTheme

/**
 * Seek bar with a real scrub interaction: press anywhere to jump, drag to preview, release
 * to commit. Composed by hand instead of using [androidx.compose.material3.Slider] because
 * the buffered bar and the growing thumb have to share one track.
 */
@Composable
fun PlaybackSeekBar(
    positionMs: Long,
    durationMs: Long,
    bufferedMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: String = "",
) {
    var trackWidthPx by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }

    val safeDuration = durationMs.coerceAtLeast(1L)
    val playedFraction = (positionMs.toFloat() / safeDuration).coerceIn(0f, 1f)
    val bufferedFraction = (bufferedMs.toFloat() / safeDuration).coerceIn(0f, 1f)

    val shownFraction by animateFloatAsState(
        targetValue = if (dragging) dragFraction else playedFraction,
        label = "seekFraction",
    )
    val thumbSize by animateDpAsState(
        targetValue = if (dragging) 16.dp else 11.dp,
        label = "seekThumb",
    )
    val extended = AlexaTheme.extended

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp)
            .semantics {
                if (label.isNotBlank()) contentDescription = label
                progressBarRangeInfo = ProgressBarRangeInfo(playedFraction, 0f..1f)
            }
            .pointerInput(enabled, safeDuration) {
                if (!enabled) return@pointerInput
                detectTapGestures { offset ->
                    val fraction = if (trackWidthPx > 0f) {
                        (offset.x / trackWidthPx).coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                    onSeek((fraction * safeDuration).toLong())
                }
            }
            .pointerInput(enabled, safeDuration) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        dragging = true
                        dragFraction = if (trackWidthPx > 0f) {
                            (offset.x / trackWidthPx).coerceIn(0f, 1f)
                        } else {
                            0f
                        }
                    },
                    onHorizontalDrag = { change, _ ->
                        dragFraction = if (trackWidthPx > 0f) {
                            (change.position.x / trackWidthPx).coerceIn(0f, 1f)
                        } else {
                            0f
                        }
                        change.consume()
                    },
                    onDragEnd = {
                        dragging = false
                        onSeek((dragFraction * safeDuration).toLong())
                    },
                    onDragCancel = { dragging = false },
                )
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(AlexaRadius.pill))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .onSizeChanged { trackWidthPx = it.width },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(bufferedFraction)
                    .height(4.dp)
                    .background(extended.textTertiary),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(shownFraction)
                    .height(4.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(extended.accentCyan, extended.accentViolet),
                        ),
                    ),
            )
        }
        Box(
            modifier = Modifier
                .offset {
                    val travel = trackWidthPx - thumbSize.toPx()
                    IntOffset(
                        x = (travel * shownFraction).toInt().coerceAtLeast(0),
                        y = 0,
                    )
                }
                .size(thumbSize)
                .clip(RoundedCornerShape(AlexaRadius.pill))
                .background(MaterialTheme.colorScheme.onSurface),
        )
    }
}