package sa.masrouf.app.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import sa.masrouf.app.R
import sa.masrouf.core.model.Category
import sa.masrouf.core.model.SaudiCategories
import sa.masrouf.core.model.Source
import sa.masrouf.core.model.Transaction

/**
 * A captured record, shown the way the bank sent it.
 *
 * This is the one screen no other expense app can draw, because no other one keeps
 * the message. `rawText` exists so a parsing bug found in six months can be
 * replayed against real input - and it turns out to be the right thing to put in
 * front of the user at the exact moment they are being asked whether the parser
 * read it correctly. "Is 8.28 right?" is an impossible question; "is 8.28 right,
 * here is what the bank wrote" is a two-second one.
 *
 * Filing happens here too, in the same glance. Ask later, in a separate screen,
 * and it stops being answered - which is how an app ends up with a total it cannot
 * break down.
 */
@Composable
fun ReceiptSlip(
    transaction: Transaction,
    currencyLabel: String,
    onConfirm: (categoryId: String?) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var chosen by remember(transaction.id) {
        mutableStateOf(SaudiCategories.byId(transaction.categoryId))
    }
    val merchant = transaction.merchantRaw ?: stringResource(transaction.type.labelRes)
    val amount = transaction.amount.forDisplay(currencyLabel)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(Sadu.GroundRaised)
            .padding(16.dp)
            // One node for the whole slip: a screen reader should hear the record,
            // not eleven fragments it has to assemble before it can act on them.
            .semantics(mergeDescendants = true) {
                contentDescription = "$merchant، $amount"
            },
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.padding(end = 12.dp)) {
                Text(text = merchant, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "${transaction.dayLabel()} · ${stringResource(transaction.source.slipLabel)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SignedAmount(
                transaction = transaction,
                currencyLabel = currencyLabel,
                style = MaterialTheme.typography.headlineSmall,
            )
        }

        transaction.rawText?.takeIf { it.isNotBlank() }?.let { raw ->
            BankWords(raw)
        }

        Text(
            text = stringResource(R.string.category_prompt),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        CategoryChips(selected = chosen, onSelect = { chosen = it })

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalButton(
                onClick = { onConfirm(chosen?.id) },
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.confirm)) }
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text(stringResource(R.string.dismiss)) }
        }
    }
}

/**
 * The bank's message, verbatim.
 *
 * Scrolls sideways rather than wrapping. These are terse lines with glued labels
 * ("بـSR 8.28"), and reflowing them breaks the shape the user recognises from
 * their own inbox - which is the entire reason for showing it.
 */
@Composable
private fun BankWords(raw: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(3.dp))
            .background(Sadu.Ground)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text = stringResource(R.string.bank_said),
            style = MaterialTheme.typography.labelSmall,
            color = Sadu.Ash,
        )
        Text(
            text = raw.trim(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(top = 4.dp)
                .horizontalScroll(rememberScrollState()),
        )
    }
}

@Composable
private fun CategoryChips(selected: Category?, onSelect: (Category?) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SaudiCategories.ALL.forEach { category ->
            val isSelected = category.id == selected?.id
            FilterChip(
                selected = isSelected,
                // Tapping the chosen one again clears it: filing can be undone
                // without hunting for a separate control.
                onClick = { onSelect(if (isSelected) null else category) },
                label = { Text(stringResource(category.labelRes)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = category.band,
                    selectedLabelColor = Sadu.Ground,
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
        SaudiCategories.TRANSFERS.id -> R.string.category_transfers
        else -> R.string.category_other
    }

@get:StringRes
private val Source.slipLabel: Int
    get() = when (this) {
        Source.SMS -> R.string.source_sms
        Source.NOTIFICATION -> R.string.source_notification
        Source.MANUAL, Source.STATEMENT -> R.string.source_notification
    }
