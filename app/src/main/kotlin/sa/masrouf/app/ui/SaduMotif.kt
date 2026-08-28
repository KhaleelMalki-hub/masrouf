package sa.masrouf.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * The woven band itself.
 *
 * The palette was named after Sadu but nothing on screen was actually woven, so
 * the reference was a colour choice rather than a design. This is the motif: the
 * row of opposed triangles that runs along the edge of a Sadu strip, drawn rather
 * than imaged so it scales, recolours and costs nothing.
 *
 * Used as a rule between sections instead of a plain divider. A divider is a line
 * that says "these are different things"; this says the same thing while also
 * being the one ornament the design is allowed, which is why it appears nowhere
 * else and never larger than this.
 */
@Composable
fun SaduBand(
    modifier: Modifier = Modifier,
    color: Color = Sadu.Loom,
    accent: Color = Sadu.Madder,
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(BAND_HEIGHT),
    ) {
        drawBand(color, accent)
    }
}

private fun DrawScope.drawBand(color: Color, accent: Color) {
    val unit = size.height
    if (unit <= 0f) return
    val count = (size.width / unit).toInt() + 1

    // Two hairlines with a row of triangles between them: the simplest true Sadu
    // element, and the one that reads at this size.
    drawLine(color, start = androidx.compose.ui.geometry.Offset(0f, 0.5f),
        end = androidx.compose.ui.geometry.Offset(size.width, 0.5f), strokeWidth = 1f)
    drawLine(color, start = androidx.compose.ui.geometry.Offset(0f, size.height - 0.5f),
        end = androidx.compose.ui.geometry.Offset(size.width, size.height - 0.5f), strokeWidth = 1f)

    repeat(count) { index ->
        val left = index * unit
        val path = Path().apply {
            moveTo(left, size.height - 1f)
            lineTo(left + unit / 2f, 1f)
            lineTo(left + unit, size.height - 1f)
        }
        // Every fourth triangle takes the accent dye, the way a weaver marks a
        // repeat rather than colouring every element.
        drawPath(
            path = path,
            color = if (index % 4 == 0) accent else color,
            style = Stroke(width = 1.2f),
        )
    }
}

private val BAND_HEIGHT = 10.dp

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
