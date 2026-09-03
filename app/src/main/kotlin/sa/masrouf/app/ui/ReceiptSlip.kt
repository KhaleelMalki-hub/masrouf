package sa.masrouf.app.ui

import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import sa.masrouf.app.R
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
            .clip(TornEdgeShape())
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(top = 16.dp, bottom = 24.dp)
            // One node for the whole slip: a screen reader should hear the record,
            // not eleven fragments it has to assemble before it can act on them.
            .semantics(mergeDescendants = true) {
                contentDescription = "$merchant، $amount"
            },
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = SLIP_PADDING),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            // Weighted, and the amount is not: in a SpaceBetween row an unweighted
            // column takes everything it asks for, and a long Arabic merchant name
            // wraps until the figure the user is being asked to confirm has no
            // width left. The name gives way; the money does not.
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    text = merchant,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
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
            BankWords(raw, modifier = Modifier.padding(horizontal = SLIP_PADDING))
        }

        Text(
            text = stringResource(R.string.category_prompt),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = SLIP_PADDING),
        )
        CategoryChips(
            selected = chosen,
            onSelect = { chosen = it },
            edgePadding = SLIP_PADDING,
        )

        Row(
            modifier = Modifier.padding(horizontal = SLIP_PADDING),
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
private fun BankWords(raw: String, modifier: Modifier = Modifier) {
    // A quiet inset, not a hole. `surfaceContainerLowest` under a dynamic dark
    // scheme is within a hair of pure black, and a black rectangle inside an
    // elevated card reads as a rendering fault, not as quoted material. One step
    // down from the card with a hairline is how M3 quotes: still recessed, still
    // the same material.
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(8.dp),
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text = stringResource(R.string.bank_said),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = raw.trim(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(top = 4.dp)
                // The zero point of a sideways scroll is the LEFT edge in either
                // direction; unreversed, an Arabic message opened showing its END.
                .horizontalScroll(rememberScrollState(), reverseScrolling = isRtl),
        )
    }
}

@get:StringRes
private val Source.slipLabel: Int
    get() = when (this) {
        Source.SMS -> R.string.source_sms
        Source.NOTIFICATION -> R.string.source_notification
        Source.MANUAL, Source.STATEMENT -> R.string.source_notification
    }

private val SLIP_PADDING = 16.dp
