package sa.masrouf.app.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import sa.masrouf.app.R
import sa.masrouf.core.model.Category
import sa.masrouf.core.model.SaudiCategories

/**
 * The eight bands, as a row you can file into.
 *
 * A LazyRow with content padding rather than a scrolling Row: the padding belongs
 * to the scrolling content, so the first and last chips come fully into view at
 * either end. With the padding on the container instead, the end chip is clipped
 * by the container edge and no amount of scrolling reveals it - which reads as a
 * rendering fault rather than as "there is more this way".
 *
 * @param selected the current filing, or null for unfiled.
 * @param onSelect receives null when the chosen chip is tapped again, so filing
 *   can be undone without hunting for a separate control.
 */
@Composable
fun CategoryChips(
    selected: Category?,
    onSelect: (Category?) -> Unit,
    modifier: Modifier = Modifier,
    edgePadding: androidx.compose.ui.unit.Dp = 0.dp,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = edgePadding),
    ) {
        items(SaudiCategories.ALL, key = { it.id }) { category ->
            val isSelected = category.id == selected?.id
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(if (isSelected) null else category) },
                label = { Text(stringResource(category.labelRes)) },
                colors = FilterChipDefaults.filterChipColors(
                    // The category's own dye, so choosing one is the same colour
                    // event as seeing it in the strip.
                    selectedContainerColor = bandColour(category),
                    selectedLabelColor = MaterialTheme.colorScheme.surface,
                ),
                modifier = Modifier.heightIn(min = 48.dp),
            )
        }
    }
}

/** Category names live in resources so both locales stay in step. */
@get:StringRes
val Category.labelRes: Int
    get() = when (id) {
        SaudiCategories.FOOD.id -> R.string.category_food
        SaudiCategories.GROCERIES.id -> R.string.category_groceries
        SaudiCategories.TRANSPORT.id -> R.string.category_transport
        SaudiCategories.BILLS.id -> R.string.category_bills
        SaudiCategories.HEALTH.id -> R.string.category_health
        SaudiCategories.SHOPPING.id -> R.string.category_shopping
        SaudiCategories.HOUSING.id -> R.string.category_housing
        SaudiCategories.EDUCATION.id -> R.string.category_education
        SaudiCategories.SERVICES.id -> R.string.category_services
        SaudiCategories.ENTERTAINMENT.id -> R.string.category_entertainment
        SaudiCategories.FEES.id -> R.string.category_fees
        SaudiCategories.CHARITY.id -> R.string.category_charity
        SaudiCategories.CASH.id -> R.string.category_cash
        SaudiCategories.INVESTMENT.id -> R.string.category_investment
        SaudiCategories.INCOME.id -> R.string.category_income
        SaudiCategories.TRANSFERS.id -> R.string.category_transfers
        else -> R.string.category_other
    }
