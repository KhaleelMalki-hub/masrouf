package sa.masrouf.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import sa.masrouf.core.model.Category
import sa.masrouf.core.money.Money

/**
 * One category's share of the month.
 *
 * The label is resolved when the band is built, not looked up while drawing: a
 * string resource can only be read in composable context, and the legend is also
 * the strip's accessibility description.
 */
data class Band(
    val category: Category?,
    val label: String,
    val amount: Money,
    val colour: Color,
)

/**
 * The month as a woven strip.
 *
 * Not a bar chart. A bar chart answers "how much on each day", which is a question
 * nobody asks about their own money; this answers "what did the month go on",
 * which is the only question the total raises. The strip is one row, read in the
 * layout's own direction, and each band's width is that category's share - so the
 * shape of the month is legible before a single number is read.
 *
 * A band narrower than [MIN_VISIBLE_FRACTION] is still drawn at that width. A
 * 5-riyal coffee against a 5,000-riyal month is a real sliver of the truth, and
 * rounding it to nothing would make the strip claim the category is not there.
 * The total is stated separately, so the small distortion costs nothing a reader
 * relies on.
 */
@Composable
fun MonthStrip(
    bands: List<Band>,
    modifier: Modifier = Modifier,
    description: String = "",
) {
    val total = bands.fold(0L) { sum, band -> sum + band.amount.halalas }
    if (bands.isEmpty() || total <= 0L) {
        EmptyStrip(modifier)
        return
    }

    // One orchestrated moment rather than scattered effects: the bands grow from
    // nothing the first time the month is drawn, which is what a strip being woven
    // looks like. Keyed on the shape of the data, so it replays when the month
    // changes and stays still while the user is only scrolling.
    //
    // Keyed on NOTHING, and that is the fix rather than an oversight: keying it on
    // the data replayed the weave whenever a figure changed, and the strip lives
    // in a LazyColumn item, so scrolling it out of view and back disposed the
    // Animatable and wove it again from zero. It is an entrance, and an entrance
    // happens once. The bands themselves morph, because their widths are read
    // straight from `bands` on every draw.
    val woven = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        woven.animateTo(1f, animationSpec = tween(Motion.MEDIUM, easing = Motion.emphasizedDecelerate))
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(STRIP_HEIGHT)
            .semantics { if (description.isNotEmpty()) contentDescription = description },
    ) {
        drawStrip(bands, total, woven.value)
    }
}

private fun DrawScope.drawStrip(bands: List<Band>, total: Long, progress: Float) {
    val gap = BAND_GAP.toPx()
    val corner = BAND_CORNER.toPx()
    val usable = size.width - gap * (bands.size - 1).coerceAtLeast(0)
    // Canvas coordinates are always left-to-right, but a strip is read the way the
    // rest of the screen is read. In Arabic the largest band belongs on the right,
    // beside the total it explains; drawing it on the left made the strip and its
    // own legend disagree about which end the month starts at.
    val rightToLeft = layoutDirection == LayoutDirection.Rtl
    var consumed = 0f

    bands.forEach { band ->
        val share = (band.amount.halalas.toDouble() / total).toFloat()
        val width = (usable * share)
            .coerceAtLeast(usable * MIN_VISIBLE_FRACTION)
            .coerceAtMost(size.width - consumed)
        if (width <= 0f) return

        val left = if (rightToLeft) size.width - consumed - width else consumed
        val grown = size.height * progress
        drawRoundRect(
            color = band.colour,
            // Grows from the baseline up, the direction a weft is beaten in.
            topLeft = Offset(left, size.height - grown),
            size = Size(width, grown),
            cornerRadius = CornerRadius(corner, corner),
        )
        consumed += width + gap
        if (consumed >= size.width) return
    }
}

@Composable
private fun EmptyStrip(modifier: Modifier) {
    // Present rather than absent, so an empty month reads as "nothing yet" and
    // not as a component that failed to load.
    val empty = MaterialTheme.colorScheme.surfaceContainerHighest
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(STRIP_HEIGHT),
    ) {
        // The unwoven loom: present, so the strip's absence reads as "nothing yet"
        // rather than as a component that failed to load.
        val corner = BAND_CORNER.toPx()
        drawRoundRect(
            color = empty,
            size = size,
            cornerRadius = CornerRadius(corner, corner),
        )
    }
}

/** The strip's key. Shown beneath it, in the same order as the bands. */
@Composable
fun BandLegend(
    bands: List<Band>,
    currencyLabel: String,
    selected: HistoryFilter?,
    onSelect: (Category?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val largest = bands.maxOfOrNull { it.amount.halalas }?.takeIf { it > 0L } ?: 1L


    Column(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(tween(Motion.MEDIUM, easing = Motion.standard)),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        // Keyed by the category, not by position. Without it, paging to a month
        // with a different mix animated row three's fill from food's share to
        // transport's while the icon and colour swapped instantly.
        bands.forEach { band ->
            key(band.category?.id ?: UNCATEGORISED_KEY) {
            BandRow(
                colour = band.colour,
                icon = band.category.icon,
                name = band.label,
                amount = band.amount.forDisplay(currencyLabel),
                selected = band.isSelectedBy(selected),
                onClick = { onSelect(band.category) },
                // Each row is filled to its share of the LARGEST band, not of the
                // month. Against the month total the small categories would all be
                // slivers indistinguishable from each other, and the point of the
                // legend is comparing them to one another - the strip above already
                // shows the share of the whole.
                fraction = (band.amount.halalas.toFloat() / largest).coerceIn(0f, 1f),
            )
            }
        }
    }
}

/**
 * The uncategorised band has no category, and selecting it is what puts the user
 * in front of the rows still to be filed.
 */
private fun Band.isSelectedBy(filter: HistoryFilter?): Boolean = when (filter) {
    null -> false
    HistoryFilter.Unfiled -> category == null
    is HistoryFilter.OfCategory -> category?.id == filter.category.id
}

/** The legend key for the band that has no category. */
private const val UNCATEGORISED_KEY = "uncategorised"

@Composable
private fun BandRow(
    colour: Color,
    icon: ImageVector,
    name: String,
    amount: String,
    fraction: Float,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The fill grows to its new share rather than jumping, so paging months reads
    // as the same categories changing size, not as a new chart replacing the old.
    val shown by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(Motion.MEDIUM, easing = Motion.emphasizedDecelerate),
        label = "bandFill",
    )
    val highlight by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent,
        animationSpec = tween(Motion.SHORT, easing = Motion.standard),
        label = "bandHighlight",
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            // The legend already names every category, so it is also the filter.
            // A separate filter menu would be the same list printed twice.
            //
            // Background BEFORE the click: the other order paints the fill over the
            // ripple, and the row gave no press feedback at all.
            .background(highlight)
            // selectable, not clickable. Which category the history below is
            // narrowed to was carried by one surface tone and nothing else - not
            // spoken, and hard to see.
            .selectable(selected = selected, role = Role.Tab, onClick = onClick),
    ) {
        // The proportion, drawn behind the text rather than beside it. A swatch
        // tells you which colour a category is; this tells you how big it is
        // without anyone having to read two numbers and divide.
        // Sized to the row rather than to ROW_HEIGHT: at a large font scale the
        // label wraps and the row grows, and a fill fixed at 48 points left a
        // stripe floating above white space.
        Box(modifier = Modifier.matchParentSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(shown)
                    .fillMaxHeight()
                    .background(colour.copy(alpha = 0.22f)),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // A minimum, not a height. The touch target still holds at the
                // default scale, and the text is free to take the room it needs.
                .heightIn(min = ROW_HEIGHT)
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // The glyph is the swatch now: same colour, and a shape that
                // matches the disc on every row of the history below.
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = colour,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
            Text(
                text = amount,
                style = MaterialTheme.typography.bodyMedium.merge(MoneyStyle),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val STRIP_HEIGHT = 76.dp
internal val SWATCH_WIDTH = 4.dp
internal val SWATCH_HEIGHT = 16.dp
/** M3's minimum touch target; the rows are tappable filters. They were 38dp. */
private val ROW_HEIGHT = 48.dp

/**
 * Points, not pixels.
 *
 * These were raw pixel constants, which meant they shrank as the screen got denser:
 * on a 2.6x phone a 3-pixel gap is barely one point, so the strip drew as one
 * continuous bar where it was tuned to read as woven bands.
 */
private val BAND_GAP = 3.dp
private val BAND_CORNER = 6.dp

/** No category is allowed to vanish entirely; see [MonthStrip]. */
private const val MIN_VISIBLE_FRACTION = 0.012f
