package sa.masrouf.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.TextStyle

/**
 * Choosing a month out of twelve years of them.
 *
 * The arrows alone were fine for a handful of months and became unusable the
 * moment a full history import produced 146: reaching 2014 from 2026 is 145 taps.
 * Two levels fixes that - pick a year, pick a month - so any month in the record
 * is two taps away regardless of how far back it is.
 *
 * Months with nothing in them are shown but not selectable. Hiding them would make
 * the grid jump around between years and leave the user unsure whether a month is
 * missing or simply empty; dimming says "nothing happened here" without moving
 * anything.
 */
@Composable
fun MonthPicker(
    selected: LocalDate,
    monthsWithData: List<LocalDate>,
    /** Always reachable, even before anything has been captured in it. */
    currentMonth: LocalDate,
    onPick: (LocalDate) -> Unit,
) {
    val years = remember(monthsWithData, currentMonth) {
        (monthsWithData.map { it.year } + currentMonth.year).distinct().sortedDescending()
    }
    var shownYear by remember(selected) { mutableStateOf(selected.year) }
    // The current month is selectable whether or not anything has landed in it. On
    // the first of a quiet month it was dimmed like a month that does not exist, and
    // a user who had paged back could not get home through the picker at all.
    val available = remember(monthsWithData, currentMonth, shownYear) {
        monthsWithData.filter { it.year == shownYear }.map { it.monthValue }.toSet() +
            setOfNotNull(currentMonth.monthValue.takeIf { shownYear == currentMonth.year })
    }
    val yearState = rememberLazyListState()

    // Once per opening, not once per tap. Keyed on shownYear it re-ran when the user
    // chose a year, and scrollToItem puts the item at the leading edge - so the chip
    // under their finger jumped across the row as they pressed it.
    LaunchedEffect(years) {
        val index = years.indexOf(shownYear)
        if (index >= 0) yearState.scrollToItem(index)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SHEET_EDGE)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        LazyRow(
            state = yearState,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(years, key = { it }) { year ->
                YearChip(
                    year = year,
                    selected = year == shownYear,
                    onClick = { shownYear = year },
                )
            }
        }

        MonthGrid(
            year = shownYear,
            selected = selected,
            available = available,
            onPick = onPick,
        )
    }
}

/**
 * M3's own selectable chip rather than a box painted to look like one. The
 * hand-rolled version carried no selected state to a screen reader, took the
 * ripple of a plain clickable instead of a chip's, and had to be re-tuned by hand
 * every time the theme moved.
 */
@Composable
private fun YearChip(year: Int, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            // toString, not a formatter: a localised one renders 2026 as ٢٠٢٦ in
            // Arabic, and every other number in this app is Western.
            Text(text = year.toString())
        },
        // A FilterChip is 32dp by default, under the 48dp minimum - the same floor
        // the category and type chips set, for the same reason.
        modifier = Modifier.heightIn(min = 48.dp),
    )
}

@Composable
private fun MonthGrid(
    year: Int,
    selected: LocalDate,
    available: Set<Int>,
    onPick: (LocalDate) -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        (0 until 4).forEach { row ->
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // The three cells of a row take the height of the tallest, so a
                // month whose name wraps at a large font scale lifts its whole row
                // instead of overflowing its own cell.
                (1..3).forEach { column ->
                    val month = row * 3 + column
                    val date = LocalDate.of(year, month, 1)
                    val enabled = month in available
                    MonthCell(
                        label = date.month.getDisplayName(TextStyle.SHORT_STANDALONE, locale),
                        selected = date == selected,
                        enabled = enabled,
                        onClick = { if (enabled) onPick(date) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun MonthCell(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            // A minimum with room to grow, not a ratio. A cell whose height was
            // computed from its width had no relation to the size of the text
            // inside it, and clipped the name at a large font scale.
            .fillMaxHeight()
            .heightIn(min = CELL_HEIGHT)
            .clip(MaterialTheme.shapes.medium)
            .background(
                when {
                    selected -> MaterialTheme.colorScheme.primaryContainer
                    enabled -> MaterialTheme.colorScheme.surfaceContainerHigh
                    else -> MaterialTheme.colorScheme.surfaceContainerLow
                }
            )
            // selectable, not clickable: the cell spent its `selected` entirely on a
            // background colour, so a screen reader could not tell which month was
            // chosen and neither could anyone reading by tone alone.
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = when {
                selected -> MaterialTheme.colorScheme.onPrimaryContainer
                enabled -> MaterialTheme.colorScheme.onSurface
                // Present but plainly inert: nothing happened in this month.
                else -> MaterialTheme.colorScheme.outline
            },
        )
    }
}

/** A month cell's floor: M3's minimum touch target with room for the label. */
private val CELL_HEIGHT = 52.dp
