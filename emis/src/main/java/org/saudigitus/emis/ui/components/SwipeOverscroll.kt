package org.saudigitus.emis.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.sign

/** How far content can be dragged past the first/last item before resistance caps it. */
val OverscrollMaxDrag: Dp = 40.dp

/** Spring used to bounce overscrolled content back into place on release. */
val OverscrollBounceSpring = spring<Float>(dampingRatio = Spring.DampingRatioMediumBouncy)

// Tapers the raw drag distance off asymptotically towards maxPx instead of following
// the finger 1:1 - the usual "rubber band" feel for dragging past a scrollable edge.
fun rubberBandOffset(dragPx: Float, maxPx: Float): Float {
    if (dragPx == 0f) return 0f
    val magnitude = abs(dragPx)
    return sign(dragPx) * maxPx * (1f - 1f / (magnitude / maxPx + 1f))
}

/**
 * A soft shadow at the start or end edge that grows with [offsetPx], signalling that
 * content has been dragged past its first/last item.
 */
@Composable
fun BoxScope.EdgeOverscrollShadow(offsetPx: Float, maxPx: Float) {
    if (offsetPx == 0f) return
    val alpha = (abs(offsetPx) / maxPx).coerceIn(0f, 1f) * 0.35f
    val atStart = offsetPx > 0f
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(28.dp)
            .align(if (atStart) Alignment.CenterStart else Alignment.CenterEnd)
            .background(
                Brush.horizontalGradient(
                    colors = if (atStart) {
                        listOf(Color.Black.copy(alpha = alpha), Color.Transparent)
                    } else {
                        listOf(Color.Transparent, Color.Black.copy(alpha = alpha))
                    },
                ),
            ),
    )
}
