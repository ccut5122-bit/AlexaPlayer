package com.alexaplayer.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.alexaplayer.core.designsystem.theme.AlexaRadius

/**
 * Album art with a deterministic fallback.
 *
 * The placeholder gradient is derived from the item id, so the same album always looks
 * the same. Random colours per load would read as a bug; a curated palette does not.
 */
@Composable
fun Artwork(
    artworkUri: String?,
    seed: Long,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    shape: Shape = androidx.compose.foundation.shape.RoundedCornerShape(AlexaRadius.artwork),
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(Brush.linearGradient(placeholderColors(seed))),
    ) {
        if (artworkUri != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(artworkUri.toUri())
                    .crossfade(true)
                    .build(),
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private val PLACEHOLDER_PAIRS = listOf(
    Color(0xFF1E2A44) to Color(0xFF141C2E),
    Color(0xFF243B4A) to Color(0xFF14232B),
    Color(0xFF2C2A4A) to Color(0xFF191730),
    Color(0xFF24404A) to Color(0xFF13242A),
    Color(0xFF33293F) to Color(0xFF1D1626),
    Color(0xFF1F3A4D) to Color(0xFF12212C),
    Color(0xFF2A3550) to Color(0xFF171D2E),
    Color(0xFF22303F) to Color(0xFF141C25),
)

fun placeholderColors(seed: Long): List<Color> {
    val size = PLACEHOLDER_PAIRS.size
    val index = ((seed % size + size) % size).toInt()
    val (start, end) = PLACEHOLDER_PAIRS[index]
    return listOf(start, end)
}
