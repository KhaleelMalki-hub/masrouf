package sa.masrouf.app.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import sa.masrouf.app.R
import sa.masrouf.core.model.Status
import sa.masrouf.core.model.Transaction
import sa.masrouf.core.model.TransactionType

@Composable
fun AddExpenseScreen(
    viewModel: AddExpenseViewModel,
    captureEnabled: Boolean,
    onEnableCapture: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val recent by viewModel.recent.collectAsStateWithLifecycle()
    val monthTotal by viewModel.monthTotal.collectAsStateWithLifecycle()
    val pendingCount by viewModel.pendingCount.collectAsStateWithLifecycle()
    val currency = stringResource(R.string.currency_sar)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { AddExpenseTopBar() },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!captureEnabled) {
                item { CapturePrompt(onEnable = onEnableCapture) }
            }

            item {
                MonthTotal(
                    text = monthTotal.forDisplay(currency),
                    pendingCount = pendingCount,
                )
            }

            item {
                AmountField(
                    value = form.typedAmount,
                    error = form.amountError,
                    onValueChange = viewModel::onAmountChanged,
                )
            }

            item {
                TypeChips(selected = form.type, onSelect = viewModel::onTypeChanged)
            }

            item {
                OutlinedTextField(
                    value = form.merchant,
                    onValueChange = viewModel::onMerchantChanged,
                    label = { Text(stringResource(R.string.merchant_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item {
                OutlinedTextField(
                    value = form.note,
                    onValueChange = viewModel::onNoteChanged,
                    label = { Text(stringResource(R.string.note_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item {
                TextButton(
                    onClick = viewModel::save,
                    // Enabled even when the amount is not yet valid, so that pressing
                    // it is what reveals the reason. A button that is simply dead
                    // tells the user nothing about what is missing.
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.save))
                }
            }

            item { HorizontalDivider() }

            item {
                Text(
                    text = stringResource(R.string.recent_title),
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            if (recent.isEmpty()) {
                item { Text(stringResource(R.string.recent_empty)) }
            } else {
                items(recent, key = { it.id }) { transaction ->
                    TransactionRow(transaction = transaction, currencyLabel = currency)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddExpenseTopBar() {
    TopAppBar(title = { Text(stringResource(R.string.add_expense_title)) })
}

@Composable
private fun MonthTotal(text: String, pendingCount: Int) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.month_total_label),
                style = MaterialTheme.typography.labelLarge,
            )
            Text(text = text, style = MaterialTheme.typography.headlineMedium)
            // Captured records are not in the total above until the user confirms
            // them, so the count is shown next to it. Otherwise the total looks
            // simply wrong to someone who watched the notification arrive.
            if (pendingCount > 0) {
                Text(
                    text = stringResource(R.string.pending_count, pendingCount),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

/**
 * Shown only while notification access is missing.
 *
 * Without it the app's whole automatic half is silently inert, and nothing on
 * screen would explain why the bank messages the user can see in their status bar
 * are not turning into transactions.
 */
@Composable
private fun CapturePrompt(onEnable: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.capture_off_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.capture_off_body),
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = onEnable) {
                Text(stringResource(R.string.capture_enable))
            }
        }
    }
}

@Composable
private fun AmountField(
    value: String,
    error: AddExpenseState.AmountError?,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(R.string.amount_label)) },
        // Decimal rather than Number: the halala separator has to be typeable.
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Decimal,
            imeAction = ImeAction.Next,
        ),
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { { Text(stringResource(it.messageRes)) } },
        modifier = Modifier.fillMaxWidth(),
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
private fun TypeChips(selected: TransactionType, onSelect: (TransactionType) -> Unit) {
    Column {
        Text(
            text = stringResource(R.string.type_label),
            style = MaterialTheme.typography.labelLarge,
        )
        // FlowRow, not Row. A plain Row gives every chip an equal share of a width
        // that five of them do not fit in, and Compose resolves that by wrapping the
        // text inside each chip - one letter per line - and dropping what still does
        // not fit off the edge. Wrapping onto a second line is also what keeps this
        // correct in Arabic, whose labels here are longer than the English ones.
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .fillMaxWidth()
                .selectableGroup(),
        ) {
            MANUAL_TYPES.forEach { type ->
                FilterChip(
                    selected = type == selected,
                    onClick = { onSelect(type) },
                    label = { Text(stringResource(type.labelRes)) },
                )
            }
        }
    }
}

/**
 * Only the types offered for manual entry are mapped.
 *
 * `when` over the whole enum without an else, so adding a type to [MANUAL_TYPES]
 * without a label fails to compile rather than showing a raw enum name.
 */
@get:StringRes
private val TransactionType.labelRes: Int
    get() = when (this) {
        TransactionType.PURCHASE -> R.string.type_purchase
        TransactionType.BILL_PAYMENT -> R.string.type_bill_payment
        TransactionType.TRANSFER_OUT -> R.string.type_transfer_out
        TransactionType.ATM_WITHDRAWAL -> R.string.type_atm_withdrawal
        TransactionType.OWN_TRANSFER -> R.string.type_own_transfer
        TransactionType.ATM_DEPOSIT,
        TransactionType.TRANSFER_IN,
        TransactionType.SALARY,
        TransactionType.REFUND,
        TransactionType.FEE,
        TransactionType.UNKNOWN,
        -> R.string.type_purchase
    }

@Composable
private fun TransactionRow(transaction: Transaction, currencyLabel: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            Text(
                text = transaction.merchantRaw
                    ?: transaction.note
                    ?: stringResource(transaction.type.labelRes),
                style = MaterialTheme.typography.bodyLarge,
            )
            if (transaction.status == Status.PENDING) {
                Text(
                    text = stringResource(R.string.status_pending),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        Text(
            text = transaction.amount.forDisplay(currencyLabel),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}
