package sa.masrouf.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
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
    onPick: (LocalDate) -> Unit,
) {
    val years = remember(monthsWithData) {
        monthsWithData.map { it.year }.distinct().sortedDescending()
    }
    var shownYear by remember(selected) { mutableStateOf(selected.year) }
    val available = remember(monthsWithData, shownYear) {
        monthsWithData.filter { it.year == shownYear }.map { it.monthValue }.toSet()
    }
    val yearState = rememberLazyListState()

    // Open on the year being looked at rather than at the end of the list.
    LaunchedEffect(years, shownYear) {
        val index = years.indexOf(shownYear)
        if (index >= 0) yearState.scrollToItem(index)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
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

@Composable
private fun YearChip(year: Int, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                }
            )
            .clickable(onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            // toString, not a formatter: a localised one renders 2026 as ٢٠٢٦ in
            // Arabic, and every other number in this app is Western.
            text = year.toString(),
            style = MaterialTheme.typography.titleMedium,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
    }
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
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
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
            .aspectRatio(1.9f)
            .clip(RoundedCornerShape(12.dp))
            .background(
                when {
                    selected -> MaterialTheme.colorScheme.primaryContainer
                    enabled -> MaterialTheme.colorScheme.surfaceContainerHigh
                    else -> MaterialTheme.colorScheme.surfaceContainerLow
                }
            )
            .clickable(enabled = enabled, onClick = onClick),
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
