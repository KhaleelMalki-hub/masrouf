package sa.masrouf.app.ui

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import sa.masrouf.app.R
import sa.masrouf.core.model.MerchantNames
import sa.masrouf.core.model.RecurringDetector
import sa.masrouf.core.model.RecurringDetector.spread
import sa.masrouf.core.model.RecurringDetector.Cadence
import sa.masrouf.core.model.SaudiCategories

/**
 * The payments the history shows arriving on a rhythm.
 *
 * Inferred, and the only thing in the app that is. So it is shown as a finding,
 * not a fact: one summary line, and the evidence per row on request - how many
 * times, how often, how much, and when the next is due. Nothing here nags. A
 * reminder that a subscription is coming is a budget tool, and the product
 * refuses those on purpose; this only answers "what am I paying for regularly".
 *
 * Collapsed by default. The summary is the answer most days; the rows are for
 * the day someone asks which ones.
 */
@Composable
fun RecurringPanel(
    recurring: List<RecurringDetector.Recurring>,
    currencyLabel: String,
    modifier: Modifier = Modifier,
) {
    if (recurring.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    val monthly = RecurringDetector.monthlyCost(recurring)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .animateContentSize(tween(Motion.MEDIUM, easing = Motion.standard)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { open = !open }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.recurring_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = stringResource(
                        R.string.recurring_summary,
                        pluralStringResource(R.plurals.recurring_count, recurring.size, recurring.size.toString()),
                        monthly.forDisplay(currencyLabel),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.Outlined.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.rotate(if (open) 180f else 0f),
            )
        }
        AnimatedVisibility(
            visible = open,
            enter = fadeIn(tween(Motion.SHORT)) + expandVertically(tween(Motion.MEDIUM, easing = Motion.emphasizedDecelerate)),
            exit = fadeOut(tween(Motion.SHORT)) + shrinkVertically(tween(Motion.SHORT, easing = Motion.emphasizedAccelerate)),
        ) {
            Column(modifier = Modifier.padding(bottom = 8.dp)) {
                recurring.forEach { RecurringRow(it, currencyLabel) }
            }
        }
    }
}

@Composable
private fun RecurringRow(item: RecurringDetector.Recurring, currencyLabel: String) {
    val category = SaudiCategories.byId(item.categoryId)
    val isArabic = LocalConfiguration.current.locales[0].language == "ar"
    val name = MerchantNames.forMerchant(item.merchantRaw)?.let { if (isArabic) it.ar else it.en } ?: item.merchantRaw

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(bandColour(category).copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = category.icon,
                contentDescription = null,
                tint = bandColour(category),
                modifier = Modifier.size(18.dp),
            )
        }
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(text = name, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = stringResource(item.cadence.labelRes) + "  ·  " +
                    stringResource(R.string.recurring_next, item.nextExpected.dayLabel()),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // A median shown alone reads as a promise. For a bill that moves - the
        // electricity between 299 and 554, the phone between 288 and 1,104 - the
        // owner read the single figure as what the app expected him to pay, and
        // said so. The figure is still the honest one to lead with; the range
        // beside it is what stops it being read as fixed.
        val varies = item.spread() > RecurringDetector.VARIES_ABOVE
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = if (varies) {
                    stringResource(R.string.recurring_about, item.typicalAmount.forDisplay(currencyLabel))
                } else {
                    item.typicalAmount.forDisplay(currencyLabel)
                },
                style = MoneyStyle.merge(MaterialTheme.typography.bodyMedium),
            )
            if (varies) {
                Text(
                    text = stringResource(
                        R.string.recurring_range,
                        item.lowAmount.grouped(),
                        item.highAmount.grouped(),
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@get:StringRes
private val Cadence.labelRes: Int
    get() = when (this) {
        Cadence.DAILY -> R.string.cadence_daily
        Cadence.WEEKLY -> R.string.cadence_weekly
        Cadence.FORTNIGHTLY -> R.string.cadence_fortnightly
        Cadence.MONTHLY -> R.string.cadence_monthly
        Cadence.QUARTERLY -> R.string.cadence_quarterly
        Cadence.YEARLY -> R.string.cadence_yearly
    }
