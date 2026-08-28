package sa.masrouf.app.ui

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * A shape with a torn lower edge, like a receipt off a till roll.
 *
 * The launcher icon is a receipt and nothing inside the app was, so the mark and
 * the product did not look like the same thing. This is the slip that carries the
 * bank's own words, which is the one surface where "this is a receipt" is the
 * literal truth rather than a metaphor.
 *
 * The teeth are computed from the width so they stay the same size on any screen
 * rather than stretching, which is what makes a torn edge read as torn rather than
 * as decoration.
 */
class TornEdgeShape(private val toothWidth: Dp = 12.dp) : Shape {

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val tooth = with(density) { toothWidth.toPx() }.coerceAtLeast(1f)
        val depth = tooth * 0.45f
        val count = kotlin.math.max(1, (size.width / tooth).toInt())
        val step = size.width / count

        val path = Path().apply {
            moveTo(0f, 0f)
            lineTo(size.width, 0f)
            lineTo(size.width, size.height - depth)
            // Walk back along the bottom, alternating down and up.
            for (i in count - 1 downTo 0) {
                val x = i * step
                lineTo(x + step / 2f, size.height)
                lineTo(x, size.height - depth)
            }
            close()
        }
        return Outline.Generic(path)
    }
}
