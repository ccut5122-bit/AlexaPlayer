package com.alexaplayer.core.designsystem.component

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.alexaplayer.R
import com.alexaplayer.core.designsystem.theme.AlexaRadius
import com.alexaplayer.core.designsystem.theme.AlexaSpacing
import com.alexaplayer.core.designsystem.theme.AlexaTheme

/**
 * Loading placeholder with a slow shimmer. Used instead of a spinner whenever the shape
 * of the content is known, because it keeps the layout from jumping when data arrives.
 */
@Composable
fun ShimmerBlock(
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(AlexaRadius.sm),
) {
    val extended = AlexaTheme.extended
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerProgress",
    )

    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        extended.shimmerBase,
                        extended.shimmerHighlight,
                        extended.shimmerBase,
                    ),
                    start = androidx.compose.ui.geometry.Offset(progress * 600f - 200f, 0f),
                    end = androidx.compose.ui.geometry.Offset(progress * 600f + 200f, 300f),
                ),
            ),
    )
}

/** Full screen loading state: a short skeleton of the list that is about to appear. */
@Composable
fun LoadingList(
    modifier: Modifier = Modifier,
    rows: Int = 6,
) {
    Column(
        modifier = modifier.padding(horizontal = AlexaSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(AlexaSpacing.md),
    ) {
        repeat(rows) { index ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AlexaSpacing.md),
                modifier = Modifier.fillMaxWidth(),
            ) {
                ShimmerBlock(
                    modifier = Modifier.size(52.dp),
                    shape = RoundedCornerShape(AlexaRadius.sm),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(AlexaSpacing.sm),
                ) {
                    ShimmerBlock(
                        modifier = Modifier
                            .fillMaxWidth(if (index % 3 == 0) 0.55f else 0.75f)
                            .height(14.dp),
                    )
                    ShimmerBlock(
                        modifier = Modifier
                            .fillMaxWidth(0.4f)
                            .height(11.dp),
                    )
                }
                ShimmerBlock(
                    modifier = Modifier
                        .width(36.dp)
                        .height(12.dp),
                )
            }
        }
    }
}

/**
 * Every "nothing here yet" moment. Always has an explanation and, when there is
 * something useful to do about it, an action.
 */
@Composable
fun EmptyState(
    iconRes: Int,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AlexaSpacing.xl, vertical = AlexaSpacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AlexaSpacing.md),
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(AlexaRadius.lg))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.material3.Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp),
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (action != null) {
            Box(modifier = Modifier.padding(top = AlexaSpacing.sm)) { action() }
        }
    }
}

/** Failure state with a retry affordance. Never shows an exception message. */
@Composable
fun ErrorState(
    title: String = stringResource(R.string.error_title),
    body: String = stringResource(R.string.error_generic),
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    EmptyState(
        iconRes = R.drawable.ic_warning,
        title = title,
        body = body,
        modifier = modifier,
        action = if (onRetry != null) {
            {
                SecondaryButton(
                    text = stringResource(R.string.action_retry),
                    onClick = onRetry,
                )
            }
        } else {
            null
        },
    )
}

/** Centred progress indicator for actions that block, such as a first library scan. */
@Composable
fun InlineLoading(
    label: String,
    modifier: Modifier = Modifier,
    indicatorSize: Dp = 20.dp,
    tint: Color = MaterialTheme.colorScheme.primary,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AlexaSpacing.sm),
    ) {
        androidx.compose.material3.CircularProgressIndicator(
            modifier = Modifier.size(indicatorSize),
            strokeWidth = 2.dp,
            color = tint,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
