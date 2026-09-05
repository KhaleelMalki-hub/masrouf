package sa.masrouf.app.ui

import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import sa.masrouf.core.model.Transaction
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.material3.Icon
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.Icons
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import sa.masrouf.app.R
import sa.masrouf.app.data.IncomeMonth
import sa.masrouf.core.model.SaudiCategories
import sa.masrouf.core.money.Money
import java.time.YearMonth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.text.style.TextOverflow

/**
 * Salary and bonuses, over the years.
 *
 * A destination of its own rather than a panel on the spending screen, because it
 * answers a different question over a different span: that screen asks where one
 * month's money went, this one asks how what arrives has changed. The owner asked
 * for it in those words - "the sequence of my salaries and bonuses over the years".
 *
 * Salary and bonuses are never summed into one bar. Keeping them apart is the whole
 * point: a month carrying both would otherwise show a single figure he could not
 * take apart, which is the objection that produced this screen.
 */
@Composable
fun IncomeScreen(
    months: List<IncomeMonth>,
    deposits: Map<YearMonth, List<Transaction>>,
    currencyLabel: String,
    /** Whether the series has been read yet. See AddExpenseViewModel.incomeLoaded. */
    loaded: Boolean,
    state: LazyListState,
    modifier: Modifier = Modifier,
) {
    // Nothing at all until the series has arrived: an empty list is not evidence of
    // an empty history, and this screen used to say so over twelve years of salaries.
    if (!loaded) return
    if (months.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.income_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(32.dp),
            )
        }
        return
    }

    val years = months.groupBy { it.month.year }.toSortedMap(compareByDescending { it })

    LazyColumn(
        state = state,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { IncomeSummary(months = months, currencyLabel = currencyLabel) }

        // One scale for the whole screen. Measured once here rather than per year:
        // scaled per year, a lean year would draw itself as tall as a fat one and
        // the comparison this screen exists for would be inverted.
        val tallest = months.maxOf { it.total.halalas }.coerceAtLeast(1L)

        for ((year, rows) in years) {
            item(key = "year-$year") {
                YearCard(
                    year = year,
                    months = rows,
                    deposits = deposits,
                    tallest = tallest,
                    currencyLabel = currencyLabel,
                )
            }
        }
    }
}

/**
 * The two lifetime figures.
 *
 * Shown before the years because it is the only number on the screen that does not
 * need scrolling to reach, and because a series is easier to read once its size is
 * known.
 */
@Composable
private fun IncomeSummary(months: List<IncomeMonth>, currencyLabel: String) {
    val salary = months.fold(Money.ZERO) { sum, m -> sum + m.salary }
    val bonus = months.fold(Money.ZERO) { sum, m -> sum + m.bonus }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = pluralStringResource(
                    R.plurals.income_months,
                    months.size,
                    months.size.toString(),
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LegendLine(
                colour = bandColour(SaudiCategories.INCOME),
                label = stringResource(R.string.category_income),
                amount = salary,
                currencyLabel = currencyLabel,
            )
            LegendLine(
                colour = bandColour(SaudiCategories.BONUS),
                label = stringResource(R.string.category_bonus),
                amount = bonus,
                currencyLabel = currencyLabel,
            )
        }
    }
}

@Composable
private fun LegendLine(colour: Color, label: String, amount: Money, currencyLabel: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(width = 4.dp, height = 16.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colour),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
        Text(
            text = amount.forDisplay(currencyLabel),
            style = MaterialTheme.typography.bodyMedium.merge(MoneyStyle),
        )
    }
}

/**
 * One year, with a bar per month.
 *
 * [tallest] is the largest month in the whole series, not in this year, so a bar
 * here means the same as a bar in the card above it.
 */
@Composable
private fun YearCard(
    year: Int,
    months: List<IncomeMonth>,
    deposits: Map<YearMonth, List<Transaction>>,
    tallest: Long,
    currencyLabel: String,
) {
    val yearSalary = months.fold(Money.ZERO) { sum, m -> sum + m.salary }
    val yearBonus = months.fold(Money.ZERO) { sum, m -> sum + m.bonus }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(text = year.toString(), style = MaterialTheme.typography.titleMedium)
                Text(
                    text = (yearSalary + yearBonus).forDisplay(currencyLabel),
                    style = MaterialTheme.typography.titleSmall.merge(MoneyStyle),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            for (month in months.sortedByDescending { it.month }) {
                MonthRow(
                    month = month,
                    deposits = deposits[month.month].orEmpty(),
                    tallest = tallest,
                    currencyLabel = currencyLabel,
                )
            }
        }
    }
}

/**
 * One month, and on tap the deposits it is made of.
 *
 * A month's total says a figure arrived. It does not say whether that was a salary
 * and one bonus or a salary and three, and the owner asked that question the moment
 * he saw the first total - so the answer is one tap away rather than on another
 * screen.
 */
@Composable
private fun MonthRow(
    month: IncomeMonth,
    deposits: List<Transaction>,
    tallest: Long,
    currencyLabel: String,
) {
    var expanded by rememberSaveable(key = month.month.toString()) { mutableStateOf(false) }

    val expandable = deposits.size > 1
    val openLabel = stringResource(R.string.income_show_deposits)
    val stateLabel = stringResource(
        if (expanded) R.string.state_expanded else R.string.state_collapsed
    )

    Column(modifier = Modifier.fillMaxWidth()) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            // Only where there is something to open. A month with one deposit is
            // already telling the whole truth, and a control that does nothing is
            // worse than none.
            .then(
                if (expandable) {
                    Modifier
                        // A bare clickable leaves a screen reader on an actionable
                        // node that announces nothing: the month, the bar and the
                        // amount are siblings, and nothing says the row opens or
                        // whether it is open. Merging gives it a name; the state
                        // description gives it the half a name cannot carry.
                        .semantics(mergeDescendants = true) {
                            stateDescription = stateLabel
                            onClick(label = openLabel) { false }
                        }
                        .clickable { expanded = !expanded }
                        // 48dp, like every other tappable row here. Text plus a
                        // 10dp bar and 6dp of padding came to about thirty.
                        .heightIn(min = 48.dp)
                } else {
                    Modifier
                }
            )
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Width tuned against Arabic wrapped "December 2025" onto two lines in
        // English while "October 2025" stayed on one, so row heights went ragged
        // inside a card. Rule 11: the locale that breaks first is the one that was
        // not measured. Wide enough for the longest English month, and one line.
        Text(
            text = month.month.arabicSafeLabel(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            // Clip is the default, and it cuts mid-glyph without saying it did.
            overflow = TextOverflow.Ellipsis,
            // A minimum, not a width: at a large font scale a fixed 108 points
            // truncated "September" to an ellipsis on one line.
            modifier = Modifier.widthIn(min = 108.dp),
        )
        // Two segments of one bar rather than two bars: the month's total is what
        // arrived, and the split is how it arrived. A bonus month reads as a longer
        // bar with a second colour on the end, which is the shape of the fact.
        StackedBar(
            salary = month.salary,
            bonus = month.bonus,
            tallest = tallest,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
        )
        Text(
            text = month.total.grouped(),
            style = MaterialTheme.typography.bodySmall.merge(MoneyStyle),
        )
        if (expandable) {
            Icon(
                imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        } else {
            Spacer(Modifier.width(18.dp))
        }
    }
    // The split, spelled out, whenever there is one. The bar shows it as a length
    // and the total shows it as one number, and neither answers "how much was the
    // salary and how much was the bonus" without the reader doing the subtraction -
    // which is what its owner said he was doing.
    if (month.bonus.halalas > 0) {
        Text(
            text = stringResource(
                R.string.income_split,
                month.salary.grouped(),
                month.bonus.grouped(),
            ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = SPLIT_LINE_INDENT, bottom = 4.dp),
        )
    }
    if (expanded) {
        for (deposit in deposits.sortedByDescending { it.occurredAt }) {
            DepositRow(deposit = deposit, currencyLabel = currencyLabel)
        }
    }
    }
}

/** One deposit: the day it landed, what it was, and how much. */
@Composable
private fun DepositRow(deposit: Transaction, currencyLabel: String) {
    // The one place that still needs to tell the two apart, and it asks the
    // category by name rather than restating what income is.
    val isBonus = deposit.categoryId == SaudiCategories.BONUS.id
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(width = 3.dp, height = 12.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(
                    bandColour(if (isBonus) SaudiCategories.BONUS else SaudiCategories.INCOME)
                ),
        )
        // The word, not only the chip. Colour was the sole carrier of which kind a
        // deposit was, and a 3x12dp sliver is not a carrier - the two greens it
        // shipped with were identical in the light theme and 11.9 apart in the
        // dark, which is a difference you can measure and not one you can see.
        Text(
            text = stringResource(
                if (isBonus) R.string.category_bonus else R.string.category_income
            ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp),
        )
        Text(
            text = deposit.occurredAt.dayLabel(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp).weight(1f),
        )
        Text(
            text = deposit.amount.forDisplay(currencyLabel),
            style = MaterialTheme.typography.labelSmall.merge(MoneyStyle),
        )
        Spacer(Modifier.width(18.dp))
    }
}

/**
 * One month's income as a length, split where the bonus begins.
 *
 * The track behind it is the tallest month in the series, so the bar's length is
 * the month's size against every other month - which is the comparison. A bar that
 * always filled its row would say only that money arrived.
 *
 * Two segments of one bar rather than two bars: the total is what arrived and the
 * split is how, so a bonus month reads as a longer bar with a second colour on the
 * end. That is the shape of the fact.
 */
@Composable
private fun StackedBar(salary: Money, bonus: Money, tallest: Long, modifier: Modifier = Modifier) {
    val salaryShare = (salary.halalas.toFloat() / tallest).coerceIn(0f, 1f)
    val bonusShare = (bonus.halalas.toFloat() / tallest).coerceIn(0f, 1f)
    // What is left of the track. Never zero: a weight of 0 makes Compose divide by
    // zero when every other weight is 0 too, which a month of exactly the series
    // maximum would otherwise do.
    val rest = (1f - salaryShare - bonusShare).coerceAtLeast(0.0001f)

    Row(
        modifier = modifier
            .height(10.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        if (salaryShare > 0f) {
            Box(
                modifier = Modifier
                    .weight(salaryShare)
                    .fillMaxSize()
                    .background(bandColour(SaudiCategories.INCOME)),
            )
        }
        if (bonusShare > 0f) {
            Box(
                modifier = Modifier
                    .weight(bonusShare)
                    .fillMaxSize()
                    .background(bandColour(SaudiCategories.BONUS)),
            )
        }
        Spacer(Modifier.weight(rest))
    }
}

/**
 * The month name from the locale, the year in Western digits.
 *
 * Same rule as [monthLabel], and the same reason: a localised formatter renders
 * 2026 as ٢٠٢٦ in Arabic while every other number on this screen is Western, and
 * one line carrying both numeral systems has already been fixed twice in this app.
 */
@Composable
private fun YearMonth.arabicSafeLabel(): String = atDay(1).monthLabel()

/**
 * The app's top-level destinations.
 *
 * Two, deliberately. M3 puts a navigation bar's floor at two and its ceiling at
 * five; this app had one screen until income needed a span of years, which is a
 * different question from where a month went and cannot share a screen with it
 * without one of the two becoming a panel on the other.
 */
enum class Destination(@get:StringRes val label: Int, val icon: ImageVector) {
    SPENDING(R.string.nav_spending, Icons.Outlined.Payments),
    INCOME(R.string.nav_income, Icons.Outlined.TrendingUp),
}

/**
 * Where the split line starts: the month label's width plus the bar's own padding,
 * so it begins exactly under the bar in either direction. Derived rather than
 * typed, because the label's width moved once already and this did not follow it.
 */
private val SPLIT_LINE_INDENT = 108.dp + 8.dp
