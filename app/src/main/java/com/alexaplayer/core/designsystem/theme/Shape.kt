package com.alexaplayer.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** Corner radius system: few, consistent values so surfaces feel related. */
val AlexaShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

object AlexaRadius {
    val xs = 6.dp
    val sm = 10.dp
    val md = 14.dp
    val lg = 20.dp
    val xl = 28.dp
    val artwork = 12.dp
    val artworkLarge = 22.dp
    val pill = 100.dp
}

/**
 * 8dp spacing grid. Components must reference these instead of inventing values,
 * which is what keeps rhythm consistent between screens.
 */
object AlexaSpacing {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val xxxl = 40.dp
    val huge = 48.dp
}

object AlexaSizes {
    /** Android accessibility guidance: interactive targets stay at 48dp. */
    val minTouchTarget = 48.dp
    val iconXs = 16.dp
    val iconSm = 20.dp
    val iconMd = 24.dp
    val iconLg = 32.dp
    val iconXl = 48.dp
    val artworkRow = 52.dp
    val artworkCard = 64.dp
    val artworkGrid = 152.dp
    val miniPlayerHeight = 64.dp
    val seekBarHeight = 28.dp
    val navBarHeight = 64.dp
}
