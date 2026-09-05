package sa.masrouf.app.ui

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseOutQuart
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import sa.masrouf.app.R
import sa.masrouf.core.model.CardKind
import sa.masrouf.core.model.Category
import sa.masrouf.core.model.Direction
import sa.masrouf.core.model.SaudiCategories
import java.time.LocalDate
import sa.masrouf.core.money.Money
import androidx.compose.foundation.clickable

/**
 * The month panel: the total, how it compares, the bands that make it up, and the
 * figures that deliberately sit outside it.
 *
 * The last seam out of `AddExpenseScreen`. Everything here answers one question -
 * what did this month come to and where did it go - and none of it knows about the
 * entry sheet or the history below.
 */
@Composable
internal fun MonthPanel(
    month: LocalDate,
    total: String,
    totalMoney: Money,
    salary: Money?,
    shares: List<Pair<Category?, Money>>,
    currencyLabel: String,
    pendingCount: Int,
    canGoBack: Boolean,
    canGoForward: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPickMonth: () -> Unit,
    previousTotal: Money?,
    invested: Money?,
    byCardKind: List<Pair<CardKind, Money>>,
    activeFilter: HistoryFilter?,
    onToggleCategory: (Category?) -> Unit,
    /**
     * Whether the month has been read yet.
     *
     * "I have not looked yet" and "there is nothing" are different sentences, and
     * without this the card could only say the second - over a 26,000-record
     * history, for the seconds before the first row arrives. The list below was
     * given this guard; the card that carries the number was not.
     */
    monthLoaded: Boolean,
) {
    val uncategorised = stringResource(R.string.uncategorised)
    val bands = shares.map { (category, amount) ->
        Band(
            category = category,
            label = category?.let { stringResource(it.labelRes) } ?: uncategorised,
            amount = amount,
            colour = bandColour(category),
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        MonthNavigator(
            month = month,
            canGoBack = canGoBack,
            canGoForward = canGoForward,
            onPrevious = onPrevious,
            onNext = onNext,
            onPickMonth = onPickMonth,
        )

        Column {
            Row(verticalAlignment = Alignment.Bottom) {
                // Shared-axis X, as M3 specifies for moving between siblings: the
                // new month's total slides in from the side the user is heading
                // towards, and the old one leaves the other way. Direction comes
                // from the month, not the number, so a larger total does not
                // "move forward" on its own.
                AnimatedContent(
                    targetState = month to total,
                    transitionSpec = {
                        // The slide is about MOVING BETWEEN months. When the month
                        // is the same and only the figure changed - confirming an
                        // expense, a repair landing - there is no direction to
                        // travel in, and sliding picked one anyway: the total left
                        // towards the previous month every time it grew. An
                        // in-place change gets the standard curve and no movement.
                        if (targetState.first == initialState.first) {
                            fadeIn(tween(Motion.SHORT, easing = Motion.standard))
                                .togetherWith(fadeOut(tween(Motion.SHORT, easing = Motion.standard)))
                        } else {
                        val forward = targetState.first.isAfter(initialState.first)
                        val towards = if (forward) SlideDirection.Start else SlideDirection.End
                        (slideIntoContainer(towards, tween(Motion.MEDIUM, easing = Motion.emphasizedDecelerate)) { it / 3 } +
                            fadeIn(tween(Motion.SHORT, delayMillis = 60)))
                            .togetherWith(
                                slideOutOfContainer(towards, tween(Motion.SHORT, easing = Motion.emphasizedAccelerate)) { it / 3 } +
                                    fadeOut(tween(Motion.SHORT)),
                            )
                        }
                    },
                    label = "monthTotal",
                ) { (_, shown) ->
                    Text(
                        text = shown,
                        style = MaterialTheme.typography.displayMedium,
                    )
                }
                // Sized against the figure, not set once and left. "ر.س" was a
                // two-letter word and read at any size; the riyal sign is a mark
                // whose height IS the digit height of whatever style draws it, so
                // at titleMedium beside a 45-point total it came out a speck. The
                // bottom padding is the difference between the two descents, which
                // is what makes bottom-aligned text share a baseline.
                Text(
                    text = currencyLabel,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 10.dp, bottom = 4.dp),
                )
            }
            MonthComparison(current = totalMoney, previous = previousTotal, currencyLabel = currencyLabel)
            SalaryShare(spent = totalMoney, salary = salary, currencyLabel = currencyLabel)
            if (pendingCount > 0) {
                Text(
                    text = pluralStringResource(
                        R.plurals.pending_count,
                        pendingCount,
                        pendingCount.toString(),
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        // A screen reader gets the same headline a glance at the strip gives: the
        // biggest category and how much of the month it took. The legend below is
        // already read row by row, so repeating all of it here would be the same
        // list twice.
        val strongest = bands.maxByOrNull { it.amount.halalas }
        val share = strongest
            ?.takeIf { totalMoney.halalas > 0L }
            ?.let { it.amount.halalas * 100 / totalMoney.halalas }
        MonthStrip(
            bands = bands,
            description = if (strongest != null && share != null) {
                stringResource(R.string.month_strip_description, strongest.label, share.toString())
            } else {
                ""
            },
        )

        if (bands.isEmpty() && monthLoaded) {
            Text(
                text = stringResource(R.string.month_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            BandLegend(
                bands = bands,
                currencyLabel = currencyLabel,
                selected = activeFilter,
                onSelect = onToggleCategory,
            )
            // Below the legend and below a rule, because the bands above have to add
            // up to the number at the top and this deliberately does not. It gets a
            // row rather than a sentence so it reads as the category it is - colour,
            // amount, and tappable to filter like the others - while the divider
            // says plainly that it sits outside the total.
            //
            // Income and bonuses were here too for one session. They came out when
            // they got a destination of their own: a figure shown in two places is
            // two places that can disagree, and the screen that owns it is the one
            // that can say more than a single month's total.
            if (invested != null) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                OutsideTotalRow(
                    category = SaudiCategories.INVESTMENT,
                    label = R.string.month_invested,
                    amount = invested,
                    currencyLabel = currencyLabel,
                    selected = activeFilter == HistoryFilter.OfCategory(SaudiCategories.INVESTMENT),
                    onClick = { onToggleCategory(SaudiCategories.INVESTMENT) },
                )
            }
            CardKindSplit(byCardKind = byCardKind, currencyLabel = currencyLabel)
            if (activeFilter == null) {
                // The legend has been the filter since it was built, and it was not
                // discovered: rows of a chart do not read as controls. One line
                // costs less than a second copy of the same list as a menu.
                Text(
                    text = stringResource(R.string.legend_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

/**
 * What the month put on mada and what it put on credit.
 *
 * The owner asked for it: a few shops still take mada and nothing else, and what
 * he borrowed is a different question from what he spent. Deliberately NOT another
 * band in the strip - the strip adds up to the total above it, and this is the same
 * money counted a second way.
 *
 * Cards whose messages never say which kind they are are left out, so these two
 * figures usually come to less than the month. Two plain lines rather than a
 * second chart: it is a comparison of two numbers, and a chart of two numbers is
 * decoration.
 */
@Composable
private fun CardKindSplit(byCardKind: List<Pair<CardKind, Money>>, currencyLabel: String) {
    if (byCardKind.isEmpty()) return

    Column(
        modifier = Modifier.padding(top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        for ((kind, amount) in byCardKind) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(kind.labelRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = amount.forDisplay(currencyLabel),
                    style = MaterialTheme.typography.bodySmall.merge(MoneyStyle),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** The word for a kind of card, in the language the interface is being read in. */
internal val CardKind.labelRes: Int
    get() = when (this) {
        CardKind.MADA -> R.string.card_kind_mada
        CardKind.CREDIT -> R.string.card_kind_credit
    }

/**
 * Paging between months.
 *
 * Arrows are laid out by reading direction rather than by absolute side, so
 * "back" is on the right in Arabic. Both are disabled at the ends rather than
 * hidden: a control that vanishes makes the user wonder where it went, one that
 * dims says there is nothing further this way.
 */
@Composable
internal fun MonthNavigator(
    month: LocalDate,
    canGoBack: Boolean,
    canGoForward: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPickMonth: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The label is the way into the picker. Arrows are for the month either
        // side; anything further than that is a jump, and with 146 months in a
        // real history the arrows alone are 145 taps to the beginning.
        TextButton(
            onClick = onPickMonth,
            modifier = Modifier.heightIn(min = 48.dp),
        ) {
            Text(
                text = month.monthLabel(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            // An icon rather than a glyph glued to the label. The composable below
            // records why the paging arrows stopped being characters: a screen
            // reader reads punctuation as punctuation. The same was true here, and
            // a character does not size with the icon set or mirror with the layout.
            Icon(
                imageVector = Icons.Outlined.ArrowDropDown,
                contentDescription = null,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        Row {
            // IconButtons with names, not bare quotation glyphs. A screen reader
            // read the previous button as "\u2039" and the next one as "\u203A" -
            // two punctuation marks, in an app whose whole history is paged with
            // them. AutoMirrored so back stays on the right in Arabic, which the
            // glyphs achieved only because Unicode mirrors them for us.
            IconButton(onClick = onPrevious, enabled = canGoBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                    contentDescription = stringResource(R.string.month_previous),
                )
            }
            IconButton(onClick = onNext, enabled = canGoForward) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.month_next),
                )
            }
        }
    }
}

/**
 * How this month compares with the one before it.
 *
 * A total on its own does not say whether the month was normal. This is the
 * cheapest thing that turns the number into information, and it is one line rather
 * than a second chart because the question it answers is one bit wide: more, or
 * less.
 */
@Composable
private fun MonthComparison(current: Money, previous: Money?, currencyLabel: String) {
    if (previous == null) return

    // From the Money, not from the string the screen prints. This used to re-parse
    // the formatted total - strip the commas and hand it to BigDecimal - with a
    // `getOrNull() ?: return` underneath, so the day the display gained the riyal
    // sign or a locale separator the comparison would simply have disappeared with
    // nothing reporting it. The panel already receives both amounts.
    val previousValue = previous.toBigDecimal()
    val difference = current.toBigDecimal().subtract(previousValue)
    val magnitude = Money.ofMajor(difference.abs())

    // Under one percent of the previous month is noise, not a change worth a
    // sentence. Naming a 12-riyal swing on a 60,000-riyal month as a difference
    // would train the user to ignore the line entirely.
    val negligible = previousValue.signum() != 0 &&
        difference.abs().multiply(java.math.BigDecimal(100)) < previousValue.abs()

    Text(
        text = when {
            negligible -> stringResource(R.string.compare_same)
            difference.signum() < 0 ->
                stringResource(R.string.compare_less, magnitude.forDisplay(currencyLabel))
            else ->
                stringResource(R.string.compare_more, magnitude.forDisplay(currencyLabel))
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * A category that sits outside the month's total.
 *
 * An investment leaving is not spending and does not belong in the bands above.
 * But a figure excluded from the total and shown nowhere else simply vanishes -
 * 14,710 riyals did exactly that, and a number that is absent cannot be questioned.
 *
 * Written to take its category rather than hard-coding one, because for a session
 * it carried income and bonuses too. Those moved to their own destination, and this
 * kept the shape: the next figure the total has to exclude gets a row, not a
 * fourth copy of one.
 */
@Composable
private fun OutsideTotalRow(
    category: Category,
    @StringRes label: Int,
    amount: Money,
    currencyLabel: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            // Background before clickable, or the fill covers the ripple.
            .then(
                if (selected) {
                    Modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest)
                } else {
                    Modifier
                }
            )
            // selectable rather than clickable: this row is a filter like the legend
            // rows above it, and which one is on was carried by a surface tone alone.
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            // A row that filters the month is a control, and a control is at least
            // 48dp tall however little text it holds.
            .heightIn(min = 48.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(width = 4.dp, height = 16.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(bandColour(category)),
            )
            Text(
                text = stringResource(label),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
        Text(
            text = amount.forDisplay(currencyLabel),
            style = MaterialTheme.typography.bodyMedium.merge(MoneyStyle),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * How much of the month is still to be filed, as a thing you can act on.
 *
 * The number was already in the legend, as the "بلا تصنيف" band, and nobody used
 * it: a band in a chart reads as a fact, not as work waiting. One line above the
 * history that names the count and opens the worklist on tap is the difference
 * between 2,457 records nobody filed and a queue somebody works through.
 *
 * Gone once the filter is on, because then the list itself is the answer, and
 * gone when the count is zero, because a banner saying "nothing to do" is noise.
 */
@Composable
internal fun UnfiledBanner(count: Int, active: Boolean, onOpen: () -> Unit) {
    AnimatedVisibility(
        visible = count > 0 && !active,
        enter = fadeIn(tween(Motion.SHORT)) +
            expandVertically(tween(Motion.MEDIUM, easing = Motion.emphasizedDecelerate)),
        exit = fadeOut(tween(Motion.SHORT)) +
            shrinkVertically(tween(Motion.SHORT, easing = Motion.emphasizedAccelerate)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .clickable(onClick = onOpen)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.HelpOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = pluralStringResource(R.plurals.unfiled_banner, count, count.toString()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** How far a filing decision made on one row reaches. */
enum class RefileScope { THIS_ONE, THIS_BANK, WHOLE_MERCHANT }

/**
 * The month against the salary.
 *
 * One line, only when the user has said what the salary is. It states a share
 * while the month is under it and the excess once it is over; it never colours
 * itself, never warns, never suggests. A person who typed their salary in wants
 * the arithmetic, and the product does not do budgets.
 */
@Composable
private fun SalaryShare(spent: Money, salary: Money?, currencyLabel: String) {
    if (salary == null || salary.isZero) return
    val text = if (spent.halalas <= salary.halalas) {
        val percent = (spent.halalas * 100 / salary.halalas).toInt()
        stringResource(R.string.salary_share, "$percent%")
    } else {
        stringResource(R.string.salary_over, Money.ofHalalas(spent.halalas - salary.halalas).forDisplay(currencyLabel))
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

