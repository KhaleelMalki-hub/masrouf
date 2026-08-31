package sa.masrouf.app.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBar
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.outlined.Add
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.rememberTooltipState
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material.icons.outlined.MoreVert
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import sa.masrouf.app.R
import sa.masrouf.core.model.Transaction
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
    salary: Money? = null,
    onSalaryChange: (Money?) -> Unit = {},
) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val recent by viewModel.recent.collectAsStateWithLifecycle()
    val monthTotal by viewModel.monthTotal.collectAsStateWithLifecycle()
    val invested by viewModel.monthInvested.collectAsStateWithLifecycle()
    val cardBalances by viewModel.cardBalances.collectAsStateWithLifecycle()
    val recurring by viewModel.recurring.collectAsStateWithLifecycle()
    val detectedSalary by viewModel.detectedSalary.collectAsStateWithLifecycle()
    // What the user typed wins; otherwise what the bank last announced.
    val effectiveSalary = salary ?: detectedSalary
    val monthUnfiled by viewModel.monthUnfiled.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    // The bar leaves on the way down and returns on the way up, as M3 top bars do
    // over a scrolling list; the month is the thing to look at, not the title.
    val topBarScroll = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val fabExpanded by remember {
        derivedStateOf { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0 }
    }
    val pending by viewModel.pending.collectAsStateWithLifecycle()
    val shares by viewModel.monthShares.collectAsStateWithLifecycle()
    val selectedMonth by viewModel.selectedMonth.collectAsStateWithLifecycle()
    val earliestMonth by viewModel.earliestMonth.collectAsStateWithLifecycle()
    val monthRows by viewModel.monthTransactions.collectAsStateWithLifecycle()
    val cardBanks by viewModel.cardBanks.collectAsStateWithLifecycle()
    val incomeMonths by viewModel.incomeByMonth.collectAsStateWithLifecycle()
    val incomeDeposits by viewModel.incomeDeposits.collectAsStateWithLifecycle()
    // Survives rotation and process death: coming back to a screen the user was not
    // on is a small betrayal, and it costs one line not to.
    var destination by rememberSaveable { mutableStateOf(Destination.SPENDING) }

    val monthsWithData by viewModel.monthsWithData.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val categoryFilter by viewModel.categoryFilter.collectAsStateWithLifecycle()
    val previousTotal by viewModel.previousMonthTotal.collectAsStateWithLifecycle()
    val importState by viewModel.importState.collectAsStateWithLifecycle()
    // Outcomes of the bulk actions arrive as a snackbar, M3's own vehicle for a
    // transient result, and leave on their own. They used to be a line pinned at
    // the top of the list that stayed until the next action replaced it.
    val snackbarHost = remember { SnackbarHostState() }
    val resultText = importResultText(importState)
    LaunchedEffect(importState) {
        if (resultText != null) {
            snackbarHost.showSnackbar(resultText)
            viewModel.clearImportState()
        }
    }
    val currency = stringResource(R.string.currency_sar)

    var entryOpen by remember { mutableStateOf(false) }
    var confirming by remember { mutableStateOf<DestructiveAction?>(null) }
    var confirmingAll by remember { mutableStateOf(false) }
    var pickingMonth by remember { mutableStateOf(false) }
    var refiling by remember { mutableStateOf<Transaction?>(null) }
    var editingSalary by remember { mutableStateOf(false) }

    if (editingSalary) {
        SalaryDialog(
            current = salary,
            detected = detectedSalary,
            currencyLabel = currency,
            onSave = { onSalaryChange(it); editingSalary = false },
            onCancel = { editingSalary = false },
        )
    }

    refiling?.let { target ->
        ModalBottomSheet(
            onDismissRequest = { refiling = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            RefileSheet(
                transaction = target,
                onDelete = {
                    refiling = null
                    confirming = DestructiveAction.Delete(target)
                },
                onForget = {
                    target.merchantKey?.let(viewModel::forgetMerchant)
                    refiling = null
                },
                onPick = { category, scope ->
                    val key = target.merchantKey
                    val bank = target.bankId
                    when {
                        category == null || key == null || scope == RefileScope.THIS_ONE ->
                            viewModel.setCategory(target.id, category?.id)
                        scope == RefileScope.THIS_BANK && bank != null ->
                            viewModel.fileMerchantAtBank(key, bank, category.id)
                        else -> viewModel.fileMerchant(key, category.id)
                    }
                    refiling = null
                },
            )
        }
    }

    if (pickingMonth) {
        ModalBottomSheet(
            onDismissRequest = { pickingMonth = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            MonthPicker(
                selected = selectedMonth,
                monthsWithData = monthsWithData,
                onPick = { month ->
                    viewModel.showMonth(month)
                    pickingMonth = false
                },
            )
        }
    }

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
                    DestructiveAction.RefileAll -> viewModel.refileEverything()
                }
                confirming = null
            },
            onCancel = { confirming = null },
        )
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(topBarScroll.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            Column {
              AddExpenseTopBar(
                // Import history, re-file everything and edit salary all act on the
                // spending history, and all three were reachable from the income
                // screen - including the destructive one, which is confirmation-
                // gated but should not have been offered there at all. The
                // destination decides, in the one place that knows it.
                showHistoryActions = destination == Destination.SPENDING,
                scrollBehavior = topBarScroll,
                onSwitchLanguage = onSwitchLanguage,
                themeMode = themeMode,
                onThemeModeChange = onThemeModeChange,
                importRunning = importState is AddExpenseViewModel.ImportState.Running ||
                    importState is AddExpenseViewModel.ImportState.Refiling,
                onImportHistory = {
                    if (canImportHistory) viewModel.importHistory() else onRequestHistoryAccess()
                },
                onFileHistory = viewModel::fileHistory,
                onRefileAll = { confirming = DestructiveAction.RefileAll },
                onEditSalary = { editingSalary = true },
            )
              // Work in progress, where M3 puts it: a linear indicator under the bar.
              // Determinate while the inbox is being read, because the count is
              // known; indeterminate while re-filing, because it is one transaction.
              when (val st = importState) {
                  is AddExpenseViewModel.ImportState.Running -> LinearProgressIndicator(
                      modifier = Modifier.fillMaxWidth(),
                  )
                  AddExpenseViewModel.ImportState.Refiling -> LinearProgressIndicator(
                      modifier = Modifier.fillMaxWidth(),
                  )
                  else -> Unit
              }
            }
        },
        bottomBar = {
            // Two destinations, which is M3's floor for a navigation bar and the
            // reason there was none until now: a bar over one screen is a control
            // with nothing to control. Income earned it by being a different
            // question over a different span - what arrives, over years, rather
            // than where one month went.
            //
            // It does not hide on scroll, and it does not float. Both were tried.
            //
            // Hiding was the worse of the two and it was my own suggestion: M3 hides
            // APP bars on scroll, never the navigation bar, and the reason showed up
            // on the first screenshot - one small downward drag took the bar away,
            // and with it the only route to the other destination. Navigation you
            // have to hunt for costs more than the 80dp it saves.
            //
            // Floating is Google Photos' own pattern rather than anything in the
            // specification, and this screen already has a FAB in the same corner:
            // two floating things over a column of figures is how a number gets
            // covered, which has happened here once. On the screen edge, a target is
            // effectively infinite to hit.
            NavigationBar {
                for (target in Destination.entries) {
                    NavigationBarItem(
                        selected = destination == target,
                        onClick = { destination = target },
                        icon = { Icon(target.icon, contentDescription = null) },
                        label = { Text(stringResource(target.label)) },
                    )
                }
            }
        },
        floatingActionButton = {
            // Only where it does something. A record is added to the spending
            // history; the income screen is a reading of what the banks reported
            // and has nothing to type into.
            if (destination == Destination.SPENDING) {
                // Extended while the top of the page is in view, a plain FAB once
                // the user is down in the history - M3's own behaviour for a
                // scrolling list, and it stops the wider English label covering rows.
                val addLabel = stringResource(R.string.add_expense)
                ExtendedFloatingActionButton(
                    onClick = { entryOpen = true },
                    expanded = fabExpanded,
                    // The icon carries the name only while the text slot is gone.
                    // Collapsed, the label is not in the tree at all, and the app's
                    // primary action was an unnamed button to a screen reader for
                    // every scrolled screen - which is most of them.
                    icon = {
                        Icon(
                            imageVector = Icons.Outlined.Add,
                            contentDescription = if (fabExpanded) null else addLabel,
                        )
                    },
                    text = { Text(addLabel) },
                )
            }
        },
    ) { padding ->
        if (destination == Destination.INCOME) {
            IncomeScreen(
                months = incomeMonths,
                deposits = incomeDeposits,
                currencyLabel = currency,
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            // The Scaffold's PaddingValues covers the bars and never the floating
            // button, so the last rows sat under it - and this app's rows end in a
            // money value, which was being clipped to "12.25" and ".00". Padding
            // the content rather than appending a spacer means the space is part of
            // the scroll range, so the final row can be brought clear.
            //
            // Measured from the FAB, not from the bar: the Scaffold already pads
            // for the bar, but it pads for the bar's CURRENT height, and the bar
            // shrinks as it hides. Taking the larger of the two keeps the last row
            // reachable at either end of that gesture.
            contentPadding = PaddingValues(bottom = FAB_CLEARANCE + NAV_BAR_HEIGHT),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (importState !is AddExpenseViewModel.ImportState.Idle) {
            }

            item {
                CardsPanel(
                    cards = cardBalances,
                    currencyLabel = currency,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
            }
            item {
                MonthPanel(
                    month = selectedMonth,
                    total = monthTotal.grouped(),
                    totalMoney = monthTotal,
                    salary = effectiveSalary,
                    shares = shares,
                    currencyLabel = currency,
                    pendingCount = pending.size,
                    canGoBack = earliestMonth?.let { selectedMonth.isAfter(it) } == true,
                    canGoForward = selectedMonth.isBefore(viewModel.currentMonth),
                    onPrevious = viewModel::showPreviousMonth,
                    onNext = viewModel::showNextMonth,
                    onPickMonth = { pickingMonth = true },
                    previousTotal = previousTotal,
                    invested = invested,
                    activeFilter = categoryFilter,
                    onToggleCategory = viewModel::toggleCategoryFilter,
                )
            }
            item {
                RecurringPanel(
                    recurring = recurring,
                    currencyLabel = currency,
                    modifier = Modifier.padding(top = 12.dp),
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
                    text = stringResource(R.string.history_all),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            item {
                UnfiledBanner(
                    count = monthUnfiled,
                    active = categoryFilter == HistoryFilter.Unfiled,
                    onOpen = { viewModel.toggleCategoryFilter(null) },
                )
                // A search box over an empty month is a control with nothing to
                // act on. It stays while a filter is on, because clearing the
                // filter is what brings the rows back.
                if (monthRows.isNotEmpty() || query.isNotBlank() || categoryFilter != null) {
                    HistoryFilters(
                        query = query,
                        onQueryChange = viewModel::onQueryChanged,
                        activeFilter = categoryFilter,
                        onClear = viewModel::clearFilters,
                    )
                }
            }

            if (monthRows.isEmpty()) {
                item {
                    Text(
                        text = stringResource(
                            if (query.isNotBlank() || categoryFilter != null) {
                                R.string.results_none
                            } else {
                                R.string.month_empty
                            }
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(monthRows, key = { it.id }) { transaction ->
                    TransactionRow(
                        modifier = Modifier.animateItem(
                            fadeInSpec = tween(Motion.SHORT, easing = Motion.emphasizedDecelerate),
                            fadeOutSpec = tween(Motion.SHORT, easing = Motion.emphasizedAccelerate),
                            placementSpec = tween(Motion.MEDIUM, easing = Motion.standard),
                        ),
                        transaction = transaction,
                        currencyLabel = currency,
                        cardBanks = cardBanks,
                        salary = effectiveSalary,
                        onRefile = { refiling = transaction },
                    )
                }
            }

            // Clearance is contentPadding above, not an item here.
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
    onRefileAll: () -> Unit,
    onEditSalary: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }

    Box {
        LabelledIconButton(label = stringResource(R.string.more_actions), onClick = { open = true }) {
            Icon(imageVector = Icons.Outlined.MoreVert, contentDescription = null)
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
            DropdownMenuItem(
                text = { Text(stringResource(R.string.salary_menu)) },
                onClick = {
                    onEditSalary()
                    open = false
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.refile_all)) },
                enabled = !importRunning,
                onClick = {
                    onRefileAll()
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
/** What a finished bulk action says, or null while nothing has finished. */
@Composable
private fun importResultText(state: AddExpenseViewModel.ImportState): String? = when (state) {
    is AddExpenseViewModel.ImportState.Done ->
        if (state.stored == 0) {
            stringResource(R.string.import_none)
        } else {
            stringResource(R.string.import_done, state.stored.toString(), state.examined.toString())
        }
    is AddExpenseViewModel.ImportState.Filed ->
        if (state.count == 0) stringResource(R.string.file_history_none)
        else stringResource(R.string.file_history_done, state.count.toString())
    is AddExpenseViewModel.ImportState.Confirmed -> stringResource(R.string.confirm_all_done, state.count.toString())
    else -> null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddExpenseTopBar(
    scrollBehavior: TopAppBarScrollBehavior,
    onSwitchLanguage: () -> Unit,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    importRunning: Boolean,
    onImportHistory: () -> Unit,
    onFileHistory: () -> Unit,
    onRefileAll: () -> Unit,
    onEditSalary: () -> Unit,
    showHistoryActions: Boolean = true) {
    TopAppBar(
        title = { Text(stringResource(R.string.dashboard_title)) },
        scrollBehavior = scrollBehavior,
        actions = {
            // The menu acts on the spending history. Language and theme are the
            // app's, so they stay everywhere.
            if (showHistoryActions) {
                MoreMenu(
                    importRunning = importRunning,
                    onImportHistory = onImportHistory,
                    onFileHistory = onFileHistory,
                    onRefileAll = onRefileAll,
                    onEditSalary = onEditSalary,
                )
            }
            ThemeMenu(mode = themeMode, onSelect = onThemeModeChange)
            // An icon, like the other two actions. A two-letter text button in a row
            // of icons read as a status rather than a control; the tooltip-free
            // answer is the standard translate glyph, with the target language as
            // its description for a screen reader.
            LabelledIconButton(label = stringResource(R.string.language_toggle), onClick = onSwitchLanguage) {
                Icon(imageVector = Icons.Outlined.Translate, contentDescription = null)
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
        // An icon, not the word. The three states are a sun, a crescent and a
        // half-and-half disc, which say what they mean at a glance and stop the
        // top bar from being three words of Arabic in a row. The name is still
        // there for anyone who cannot see the shape: it is the content
        // description, and it is the menu item's own label.
        LabelledIconButton(
            label = stringResource(mode.labelRes),
            onClick = { open = true },
        ) {
            Icon(painter = painterResource(mode.iconRes), contentDescription = null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            ThemeMode.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(stringResource(option.labelRes)) },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(option.iconRes),
                            contentDescription = null,
                        )
                    },
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

@get:DrawableRes
private val ThemeMode.iconRes: Int
    get() = when (this) {
        ThemeMode.System -> R.drawable.ic_theme_auto
        ThemeMode.Light -> R.drawable.ic_theme_light
        ThemeMode.Dark -> R.drawable.ic_theme_dark
    }

@get:StringRes
private val ThemeMode.labelRes: Int
    get() = when (this) {
        ThemeMode.System -> R.string.theme_system
        ThemeMode.Light -> R.string.theme_light
        ThemeMode.Dark -> R.string.theme_dark
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
            .clip(MaterialTheme.shapes.medium)
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
        DestructiveAction.RefileAll -> R.string.refile_all_title
    }
    val actionRes = when (action) {
        is DestructiveAction.Delete -> R.string.delete
        is DestructiveAction.Dismiss -> R.string.dismiss
        DestructiveAction.RefileAll -> R.string.refile_all_confirm
    }
    val body = when (action) {
        is DestructiveAction.Delete -> stringResource(
            R.string.delete_body,
            action.transaction.amount.forDisplay(currencyLabel),
            action.transaction.dayLabel(),
        )
        is DestructiveAction.Dismiss -> stringResource(
            R.string.dismiss_body,
            action.transaction.amount.forDisplay(currencyLabel),
            action.transaction.dayLabel(),
        )
        DestructiveAction.RefileAll -> stringResource(R.string.refile_all_body)
    }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(titleRes)) },
        text = { Text(body) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(actionRes)) } },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) } },
    )
}

/**
 * Below this, working through the queue one record at a time is reasonable and the
 * bulk action would only invite skipping the review the queue exists for.
 */
private const val BULK_CONFIRM_THRESHOLD = 10

/** Where the salary is typed. One field; clearing it removes the line. */
@Composable
private fun SalaryDialog(
    current: Money?,
    detected: Money?,
    currencyLabel: String,
    onSave: (Money?) -> Unit,
    onCancel: () -> Unit,
) {
    var typed by remember { mutableStateOf(current?.grouped()?.replace(",", "") ?: "") }
    val parsed = AmountInput.parse(typed)

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.salary_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.salary_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (detected != null) {
                    Text(
                        text = stringResource(R.string.salary_detected, detected.forDisplay(currencyLabel)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    singleLine = true,
                    suffix = { Text(currencyLabel) },
                    isError = parsed is AmountInput.Result.Invalid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = parsed !is AmountInput.Result.Invalid,
                onClick = { onSave((parsed as? AmountInput.Result.Valid)?.amount) },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) } },
    )
}

/**
 * An icon-only action with the name M3 says it must have: a tooltip on a long
 * press, and the same name for a screen reader.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LabelledIconButton(
    label: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .heightIn(min = 48.dp)
                .semantics { contentDescription = label },
        ) { content() }
    }
}
