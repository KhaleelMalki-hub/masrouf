package sa.masrouf.app.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import sa.masrouf.app.R
import sa.masrouf.core.model.Category
import sa.masrouf.core.model.Direction
import sa.masrouf.core.model.SaudiCategories
import sa.masrouf.core.model.Source
import sa.masrouf.core.model.Transaction
import sa.masrouf.core.model.TransactionType
import java.time.LocalDate
import sa.masrouf.core.money.Money

/**
 * The landing page.
 *
 * Reading order is the order of the questions someone actually asks: what have I
 * spent, what did it go on, what needs me, what did I do recently. Recording an
 * expense is a button rather than a form parked on the page, because the form was
 * five fields of empty boxes sitting above the answers - the app looked like data
 * entry when its job is to have already done the entry for you.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseScreen(
    viewModel: AddExpenseViewModel,
    captureEnabled: Boolean,
    onEnableCapture: () -> Unit,
    smsEnabled: Boolean,
    onEnableSms: () -> Unit,
    modifier: Modifier = Modifier,
    canImportHistory: Boolean = false,
    onRequestHistoryAccess: () -> Unit = {},
    onSwitchLanguage: () -> Unit = {},
    themeMode: ThemeMode = ThemeMode.System,
    onThemeModeChange: (ThemeMode) -> Unit = {},
) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val recent by viewModel.recent.collectAsStateWithLifecycle()
    val monthTotal by viewModel.monthTotal.collectAsStateWithLifecycle()
    val pending by viewModel.pending.collectAsStateWithLifecycle()
    val shares by viewModel.monthShares.collectAsStateWithLifecycle()
    val selectedMonth by viewModel.selectedMonth.collectAsStateWithLifecycle()
    val earliestMonth by viewModel.earliestMonth.collectAsStateWithLifecycle()
    val monthRows by viewModel.monthTransactions.collectAsStateWithLifecycle()
    val importState by viewModel.importState.collectAsStateWithLifecycle()
    val currency = stringResource(R.string.currency_sar)

    var entryOpen by remember { mutableStateOf(false) }
    var confirming by remember { mutableStateOf<DestructiveAction?>(null) }
    var confirmingAll by remember { mutableStateOf(false) }

    if (confirmingAll) {
        AlertDialog(
            onDismissRequest = { confirmingAll = false },
            title = { Text(stringResource(R.string.confirm_all_title)) },
            text = {
                Text(stringResource(R.string.confirm_all_body, pending.size.toString()))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.confirmAllPending()
                        confirmingAll = false
                    },
                ) { Text(stringResource(R.string.confirm_all)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingAll = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    confirming?.let { action ->
        DestructiveConfirmation(
            action = action,
            currencyLabel = currency,
            onConfirm = {
                when (action) {
                    is DestructiveAction.Delete -> viewModel.delete(action.transaction.id)
                    is DestructiveAction.Dismiss -> viewModel.dismiss(action.transaction.id)
                }
                confirming = null
            },
            onCancel = { confirming = null },
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            AddExpenseTopBar(
                onSwitchLanguage = onSwitchLanguage,
                themeMode = themeMode,
                onThemeModeChange = onThemeModeChange,
                importRunning = importState is AddExpenseViewModel.ImportState.Running,
                onImportHistory = {
                    if (canImportHistory) viewModel.importHistory() else onRequestHistoryAccess()
                },
                onFileHistory = viewModel::fileHistory,
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { entryOpen = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) { Text(stringResource(R.string.add_expense)) }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (importState !is AddExpenseViewModel.ImportState.Idle) {
                item { ImportStatus(state = importState) }
            }

            item {
                MonthPanel(
                    month = selectedMonth,
                    total = monthTotal.toPlainString(),
                    shares = shares,
                    currencyLabel = currency,
                    pendingCount = pending.size,
                    canGoBack = earliestMonth?.let { selectedMonth.isAfter(it) } == true,
                    canGoForward = selectedMonth.isBefore(viewModel.currentMonth),
                    onPrevious = viewModel::showPreviousMonth,
                    onNext = viewModel::showNextMonth,
                )
            }

            if (pending.isNotEmpty()) {
                item {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.pending_title),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            // Only offered once the queue is long enough that
                            // working through it one at a time is not realistic.
                            if (pending.size >= BULK_CONFIRM_THRESHOLD) {
                                FilledTonalButton(
                                    onClick = { confirmingAll = true },
                                    modifier = Modifier.heightIn(min = 48.dp),
                                ) { Text(stringResource(R.string.confirm_all)) }
                            }
                        }
                        Text(
                            text = stringResource(R.string.pending_explain),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(pending, key = { it.id }) { transaction ->
                    ReceiptSlip(
                        transaction = transaction,
                        currencyLabel = currency,
                        onConfirm = { categoryId -> viewModel.confirm(transaction.id, categoryId) },
                        onDismiss = { confirming = DestructiveAction.Dismiss(transaction) },
                    )
                }
            }

            if (!captureEnabled) {
                item {
                    AccessPrompt(
                        title = stringResource(R.string.capture_off_title),
                        body = stringResource(R.string.capture_off_body),
                        action = stringResource(R.string.capture_enable),
                        onAct = onEnableCapture,
                    )
                }
            }

            if (!smsEnabled) {
                item {
                    AccessPrompt(
                        title = stringResource(R.string.sms_off_title),
                        body = stringResource(R.string.sms_off_body),
                        action = stringResource(R.string.sms_enable),
                        onAct = onEnableSms,
                    )
                }
            }

            item { HorizontalDivider() }

            item {
                Text(
                    text = stringResource(R.string.history_all).uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (monthRows.isEmpty()) {
                item { Text(stringResource(R.string.month_empty)) }
            } else {
                items(monthRows, key = { it.id }) { transaction ->
                    TransactionRow(
                        transaction = transaction,
                        currencyLabel = currency,
                        onDelete = { confirming = DestructiveAction.Delete(transaction) },
                    )
                }
            }

            // Clearance for the floating button, which would otherwise sit on the
            // last row of the history.
            item { Spacer(Modifier.height(72.dp)) }
        }
    }

    if (entryOpen) {
        ModalBottomSheet(
            onDismissRequest = { entryOpen = false },
            // Fully expanded on open. The half-height state gives the content no
            // spare room, so the pinned save button sat below the screen edge - and
            // a half-open sheet also hides the category row, which is the second
            // decision this screen exists to collect.
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            EntrySheet(
                form = form,
                currencyLabel = currency,
                onAmountChanged = viewModel::onAmountChanged,
                onTypeChanged = viewModel::onTypeChanged,
                onCategoryChanged = viewModel::onCategoryChanged,
                onMerchantChanged = viewModel::onMerchantChanged,
                onNoteChanged = viewModel::onNoteChanged,
                onSave = {
                    viewModel.save()
                    entryOpen = false
                },
            )
        }
    }
}

/**
 * The two things that can be done to a whole history at once.
 *
 * In the top bar, not in the list. They used to sit below the history, which was
 * fine when the history was short and became unusable the moment a real import
 * put 1,664 records above them: a once-in-the-life-of-the-app action was parked
 * behind a scroll nobody would finish. An overflow menu is where an infrequent
 * action belongs, and it is one tap from anywhere in the page.
 */
@Composable
private fun MoreMenu(
    importRunning: Boolean,
    onImportHistory: () -> Unit,
    onFileHistory: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }

    Box {
        TextButton(
            onClick = { open = true },
            modifier = Modifier.heightIn(min = 48.dp),
        ) {
            Text(
                text = "\u22EE",
                style = MaterialTheme.typography.titleLarge,
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.import_history)) },
                enabled = !importRunning,
                onClick = {
                    onImportHistory()
                    open = false
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.file_history)) },
                onClick = {
                    onFileHistory()
                    open = false
                },
            )
        }
    }
}

/**
 * What a bulk action just did, shown at the top where it will be seen.
 *
 * An operation over a thousand records that reports nothing is indistinguishable
 * from one that failed.
 */
@Composable
private fun ImportStatus(state: AddExpenseViewModel.ImportState) {
    val text = when (state) {
        is AddExpenseViewModel.ImportState.Running ->
            stringResource(R.string.import_running, state.examined.toString())
        is AddExpenseViewModel.ImportState.Done ->
            if (state.stored == 0) {
                stringResource(R.string.import_none)
            } else {
                stringResource(
                    R.string.import_done,
                    state.stored.toString(),
                    state.examined.toString(),
                )
            }
        is AddExpenseViewModel.ImportState.Filed ->
            stringResource(R.string.file_history_done, state.count.toString())
        is AddExpenseViewModel.ImportState.Confirmed ->
            stringResource(R.string.confirm_all_done, state.count.toString())
        AddExpenseViewModel.ImportState.Idle -> return
    }

    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
    )
}

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
private fun EntrySheet(
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
            .fillMaxHeight(0.85f),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
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

        // Pinned, never scrolled past. The previous sheet put this below two text
        // fields, so completing the task required scrolling a form the user had
        // already finished with.
        Button(
            onClick = onSave,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
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
private fun AmountHero(
    value: String,
    error: AddExpenseState.AmountError?,
    currencyLabel: String,
    focusRequester: FocusRequester,
    onValueChange: (String) -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.Bottom) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MoneyStyle.merge(MaterialTheme.typography.displaySmall).copy(
                    color = if (error != null) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                // Decimal rather than Number: the halala separator has to be typeable.
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Done,
                ),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                decorationBox = { inner ->
                    if (value.isEmpty()) {
                        Text(
                            text = stringResource(R.string.amount_hint),
                            style = MoneyStyle.merge(MaterialTheme.typography.displaySmall),
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                    inner()
                },
            )
            Text(
                text = currencyLabel,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp, bottom = 6.dp),
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
private fun SheetLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddExpenseTopBar(
    onSwitchLanguage: () -> Unit,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    importRunning: Boolean,
    onImportHistory: () -> Unit,
    onFileHistory: () -> Unit,
) {
    TopAppBar(
        title = { Text(stringResource(R.string.dashboard_title)) },
        actions = {
            MoreMenu(
                importRunning = importRunning,
                onImportHistory = onImportHistory,
                onFileHistory = onFileHistory,
            )
            ThemeMenu(mode = themeMode, onSelect = onThemeModeChange)
            // The label is the language you would switch TO, not the one you are
            // in: a control that names the current state reads as a status, and
            // people tap it expecting nothing to happen.
            TextButton(
                onClick = onSwitchLanguage,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                Text(
                    text = stringResource(R.string.language_toggle),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            actionIconContentColor = MaterialTheme.colorScheme.primary,
        ),
    )
}

/**
 * Auto, light, dark.
 *
 * A menu rather than a cycling button: three states cannot be cycled through
 * predictably, and "auto" is not a stop on a line between the other two - it is a
 * different kind of answer, which a list can show and a toggle cannot.
 */
@Composable
private fun ThemeMenu(mode: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    var open by remember { mutableStateOf(false) }

    Box {
        TextButton(
            onClick = { open = true },
            modifier = Modifier.heightIn(min = 48.dp),
        ) {
            Text(
                text = stringResource(mode.labelRes),
                style = MaterialTheme.typography.labelLarge,
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            ThemeMode.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(stringResource(option.labelRes)) },
                    onClick = {
                        onSelect(option)
                        open = false
                    },
                    trailingIcon = {
                        if (option == mode) {
                            Text(
                                text = "\u2713",
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    },
                )
            }
        }
    }
}

@get:StringRes
private val ThemeMode.labelRes: Int
    get() = when (this) {
        ThemeMode.System -> R.string.theme_system
        ThemeMode.Light -> R.string.theme_light
        ThemeMode.Dark -> R.string.theme_dark
    }

@Composable
private fun MonthPanel(
    month: LocalDate,
    total: String,
    shares: List<Pair<Category?, Money>>,
    currencyLabel: String,
    pendingCount: Int,
    canGoBack: Boolean,
    canGoForward: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
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
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(16.dp))
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
        )

        Column {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = total,
                    style = MoneyStyle.merge(MaterialTheme.typography.displayMedium),
                )
                Text(
                    text = currencyLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp, bottom = 6.dp),
                )
            }
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

        MonthStrip(bands = bands)

        if (bands.isEmpty()) {
            Text(
                text = stringResource(R.string.month_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            BandLegend(bands = bands, currencyLabel = currencyLabel)
        }
    }
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
private fun MonthNavigator(
    month: LocalDate,
    canGoBack: Boolean,
    canGoForward: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = month.monthLabel(),
            style = MaterialTheme.typography.titleMedium,
        )
        Row {
            TextButton(
                onClick = onPrevious,
                enabled = canGoBack,
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text("\u2039", style = MaterialTheme.typography.titleLarge) }
            TextButton(
                onClick = onNext,
                enabled = canGoForward,
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text("\u203A", style = MaterialTheme.typography.titleLarge) }
        }
    }
}

/**
 * Shown while a permission the app needs is missing.
 *
 * Without it the automatic half is silently inert, and nothing on screen would
 * explain why bank messages the user can see arriving are not becoming
 * transactions. One composable for both permissions, so the two cannot drift into
 * looking like different kinds of problem.
 */
@Composable
private fun AccessPrompt(
    title: String,
    body: String,
    action: String,
    onAct: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(
            onClick = onAct,
            modifier = Modifier.heightIn(min = 48.dp),
        ) { Text(action) }
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

/**
 * A captured record with the two things the user can do about it.
 *
 * The amount and the merchant are shown as the parser read them, because the
 * question being asked is whether that reading is right. There is no edit here on
 * purpose: a wrong amount is dismissed and typed in by hand, which keeps the one
 * number the user vouched for a number they actually entered.
 */
@Composable
fun SignedAmount(
    transaction: Transaction,
    currencyLabel: String,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
) {
    val isCredit = transaction.direction == Direction.CREDIT
    Text(
        text = if (isCredit) {
            "+ ${transaction.amount.forDisplay(currencyLabel)}"
        } else {
            transaction.amount.forDisplay(currencyLabel)
        },
        style = style,
        color = if (isCredit) {
            MaterialTheme.colorScheme.tertiary
        } else {
            MaterialTheme.colorScheme.onSurface
        },
    )
}

@Composable
private fun TransactionRow(
    transaction: Transaction,
    currencyLabel: String,
    onDelete: () -> Unit,
) {
    val category = SaudiCategories.byId(transaction.categoryId)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The category's own dye, as a thread down the edge of the row. Without it
        // the strip is the only place colour means anything and the history reads
        // as an unrelated list; with it a month can be scanned for one category
        // without reading a single word.
        Box(
            modifier = Modifier
                .padding(vertical = 8.dp)
                .size(width = 3.dp, height = 34.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(bandColour(category)),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
        ) {
            Text(
                text = transaction.merchantRaw
                    ?: transaction.note
                    ?: stringResource(transaction.type.labelRes),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                // Date and category on one line: two facts about the same row, and
                // stacking them would make a two-line row into a three-line one.
                text = category
                    ?.let { "${transaction.dayLabel()}  ·  ${stringResource(it.labelRes)}" }
                    ?: transaction.dayLabel(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SignedAmount(transaction = transaction, currencyLabel = currencyLabel)
        TextButton(
            onClick = onDelete,
            modifier = Modifier.heightIn(min = 48.dp),
        ) {
            Text(
                text = "\u00D7",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Which irreversible thing the user just asked for. */
private sealed interface DestructiveAction {
    val transaction: Transaction

    /** Remove a record from the history. A manual one can simply be typed again. */
    data class Delete(override val transaction: Transaction) : DestructiveAction

    /**
     * Reject a captured record. Strictly worse than [Delete]: it also destroys
     * `rawText`, the original bank message, which is the one field the user cannot
     * reproduce from memory. It was previously the only unguarded one.
     */
    data class Dismiss(override val transaction: Transaction) : DestructiveAction
}

/**
 * Names what is about to be destroyed before destroying it.
 *
 * The amount and day are shown rather than a generic "are you sure": the mistake
 * this guards against is acting on the wrong row, which a yes/no question about an
 * unnamed record cannot prevent.
 */
@Composable
private fun DestructiveConfirmation(
    action: DestructiveAction,
    currencyLabel: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val titleRes = when (action) {
        is DestructiveAction.Delete -> R.string.delete_title
        is DestructiveAction.Dismiss -> R.string.dismiss_title
    }
    val bodyRes = when (action) {
        is DestructiveAction.Delete -> R.string.delete_body
        is DestructiveAction.Dismiss -> R.string.dismiss_body
    }
    val actionRes = when (action) {
        is DestructiveAction.Delete -> R.string.delete
        is DestructiveAction.Dismiss -> R.string.dismiss
    }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(titleRes)) },
        text = {
            Text(
                stringResource(
                    bodyRes,
                    action.transaction.amount.forDisplay(currencyLabel),
                    action.transaction.dayLabel(),
                )
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(actionRes)) } },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) } },
    )
}

/**
 * Below this, working through the queue one record at a time is reasonable and the
 * bulk action would only invite skipping the review the queue exists for.
 */
private const val BULK_CONFIRM_THRESHOLD = 10
