package sa.masrouf.app.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import sa.masrouf.app.R
import sa.masrouf.core.model.Category
import sa.masrouf.core.model.TransactionType

/**
 * The sheet that records an expense by hand, and the fields it is made of.
 *
 * Split out of `AddExpenseScreen` when that file passed two thousand lines. The
 * seam is real rather than arbitrary: nothing here reads the month, the history or
 * the view model - it takes a form and reports edits - so it is the one group that
 * can be read without the rest of the screen in mind.
 */

/**
 * Recording an expense by hand.
 *
 * The app is measured against doing this in about five seconds, and the previous
 * version could not: five equally-weighted fields, no keyboard until you tapped
 * one, and the save button below the fold. Everything here follows from that
 * number.
 *
 * The amount is the screen. It is the only required field, it is focused with the
 * keypad already up, and it is set at display size so there is no question where
 * to look. Everything else has a usable default.
 *
 * Merchant and note are behind a disclosure. They are genuinely optional - the
 * captured records that make up most of this app's data already carry a merchant -
 * and two more text fields in the primary path is what turns five seconds into
 * fifteen.
 */
@Composable
internal fun EntrySheet(
    form: AddExpenseState,
    currencyLabel: String,
    onAmountChanged: (String) -> Unit,
    onTypeChanged: (TransactionType) -> Unit,
    onCategoryChanged: (Category?) -> Unit,
    onMerchantChanged: (String) -> Unit,
    onNoteChanged: (String) -> Unit,
    onSave: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    var detailsOpen by remember { mutableStateOf(false) }

    // The keypad is up before the sheet has finished settling, so the first thing
    // the user does is type a number rather than aim at a field.
    LaunchedEffect(Unit) { focus.requestFocus() }

    // Bounded height, not wrap-content. A sheet that sizes to its content has no
    // spare space for `weight` to divide, so the pinned button was pushed below
    // the screen edge - pinned in the code and invisible in the app.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.85f)
            // The amount field takes focus as the sheet opens, so the keypad is up
            // immediately. Without this the sheet keeps its full height behind the
            // keypad: the merchant and note fields, and the pinned Save button,
            // sit under it with nothing to scroll them into view.
            .imePadding(),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = SHEET_EDGE),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                text = stringResource(R.string.entry_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            AmountHero(
                value = form.typedAmount,
                error = form.amountError,
                currencyLabel = currencyLabel,
                focusRequester = focus,
                onValueChange = onAmountChanged,
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SheetLabel(stringResource(R.string.category_prompt))
                CategoryChips(selected = form.category, onSelect = onCategoryChanged)
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SheetLabel(stringResource(R.string.type_label))
                TypeChips(selected = form.type, onSelect = onTypeChanged)
            }

            TextButton(
                onClick = { detailsOpen = !detailsOpen },
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(
                    stringResource(
                        if (detailsOpen) R.string.entry_details_hide else R.string.entry_details
                    )
                )
            }

            if (detailsOpen) {
                OutlinedTextField(
                    value = form.merchant,
                    onValueChange = onMerchantChanged,
                    label = { Text(stringResource(R.string.merchant_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = form.note,
                    onValueChange = onNoteChanged,
                    label = { Text(stringResource(R.string.note_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        if (form.saveFailed) {
            Text(
                text = stringResource(R.string.error_save_failed),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = SHEET_EDGE),
            )
        }

        // Pinned, never scrolled past. The previous sheet put this below two text
        // fields, so completing the task required scrolling a form the user had
        // already finished with.
        Button(
            onClick = onSave,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SHEET_EDGE)
                .padding(top = 12.dp, bottom = 28.dp)
                .heightIn(min = 56.dp),
        ) { Text(stringResource(R.string.save)) }
    }
}

/**
 * The amount, set at the size of the decision it represents.
 *
 * A borderless field rather than an outlined box: a box says "one of several
 * inputs", and this is the input. The currency sits beside it at label size
 * because it never changes and is not the information.
 */
@Composable
internal fun AmountHero(
    value: String,
    error: AddExpenseState.AmountError?,
    currencyLabel: String,
    focusRequester: FocusRequester,
    onValueChange: (String) -> Unit,
) {
    val amountName = stringResource(R.string.amount_label)
    Column {
        Row(verticalAlignment = Alignment.Bottom) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.displaySmall.copy(
                    color = if (error != null) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    // Digits carry no direction of their own, so in Arabic the
                    // paragraph resolved right-to-left and the halala point - typed
                    // on the way to 45.50 - was placed to the LEFT of the digits.
                    // The user saw ".45" and the caret jumped. An amount is one
                    // left-to-right number in both languages, as its style says.
                    textDirection = TextDirection.Ltr,
                    textAlign = TextAlign.End,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                // Decimal rather than Number: the halala separator has to be typeable.
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Done,
                ),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
                    // A BasicTextField has no label slot, so the app's one required
                    // field announced itself as an edit box with no name. The hint
                    // is "0.00", which names nothing.
                    .semantics { contentDescription = amountName },
                decorationBox = { inner ->
                    if (value.isEmpty()) {
                        Text(
                            text = stringResource(R.string.amount_hint),
                            style = MaterialTheme.typography.displaySmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                    inner()
                },
            )
            // See MonthPanel: the sign's height is the digit height of its own
            // style, so it is sized against the amount it stands beside.
            Text(
                text = currencyLabel,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp, bottom = 3.dp),
            )
        }
        HorizontalDivider(
            color = if (error != null) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.outlineVariant
            },
        )
        error?.let {
            Text(
                text = stringResource(it.messageRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
internal fun SheetLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}


@get:StringRes
private val AddExpenseState.AmountError.messageRes: Int
    get() = when (this) {
        AddExpenseState.AmountError.REQUIRED -> R.string.error_amount_required
        AddExpenseState.AmountError.INVALID -> R.string.error_amount_invalid
    }

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TypeChips(selected: TransactionType, onSelect: (TransactionType) -> Unit) {
    // No label of its own: the caller supplies one, the way it does for the
    // category row. Owning it here printed the heading twice.
    Column {
        // FlowRow, not Row. A plain Row gives every chip an equal share of a width
        // that five of them do not fit in, and Compose resolves that by wrapping the
        // text inside each chip - one letter per line - and dropping what still does
        // not fit off the edge. Wrapping onto a second line is also what keeps this
        // correct in Arabic, whose labels here are longer than the English ones.
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .selectableGroup(),
        ) {
            MANUAL_TYPES.forEach { type ->
                FilterChip(
                    selected = type == selected,
                    onClick = { onSelect(type) },
                    label = { Text(stringResource(type.labelRes)) },
                    // A FilterChip is 32dp by default, under the 48dp minimum, and
                    // these are the controls the five-second entry path must hit.
                    modifier = Modifier.heightIn(min = 48.dp),
                )
            }
        }
    }
}

/**
 * Every type has its own label.
 *
 * Not only for the manual-entry chips: this is also the fallback whenever a
 * captured row has no merchant, and `BankMessageParser` extracts a merchant only
 * for purchases and refunds - so the captured types are precisely the ones that
 * reach it. Grouping the rest under "purchase" was not a harmless default; it
 * rendered a salary as a purchase in the list the user is asked to vouch for, and
 * the catch-all also defeated the compile-time check this comment used to claim.
 */
@get:StringRes
val TransactionType.labelRes: Int
    get() = when (this) {
        TransactionType.PURCHASE -> R.string.type_purchase
        TransactionType.BILL_PAYMENT -> R.string.type_bill_payment
        TransactionType.TRANSFER_OUT -> R.string.type_transfer_out
        TransactionType.ATM_WITHDRAWAL -> R.string.type_atm_withdrawal
        TransactionType.OWN_TRANSFER -> R.string.type_own_transfer
        TransactionType.ATM_DEPOSIT -> R.string.type_atm_deposit
        TransactionType.TRANSFER_IN -> R.string.type_transfer_in
        TransactionType.SALARY -> R.string.type_salary
        TransactionType.REFUND -> R.string.type_refund
        TransactionType.FEE -> R.string.type_fee
        TransactionType.UNKNOWN -> R.string.type_unknown
    }

