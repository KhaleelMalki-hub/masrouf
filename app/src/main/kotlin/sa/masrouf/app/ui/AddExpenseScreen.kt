package sa.masrouf.app.ui

import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBar
import androidx.annotation.DrawableRes
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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseOutQuart
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.outlined.Add
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.ReadOnlyComposable
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
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
import sa.masrouf.core.model.MerchantNames
import sa.masrouf.core.model.countsAsSpending
import sa.masrouf.core.model.SaudiCategories
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

    // How much of the navigation bar is hidden, in pixels, driven by the same
    // gesture the top bar reads. Held here rather than inside the bar because the
    // Scaffold measures the bar's height to pad its content: shrinking the bar
    // without telling the Scaffold would leave a strip of dead space under the
    // list, and moving it without shrinking it would leave the list padded for a
    // bar that is no longer there.
    val barHeightPx = with(LocalDensity.current) { NAV_BAR_HEIGHT.toPx() }
    var navBarHidden by remember { mutableFloatStateOf(0f) }
    val navBarScroll = remember(barHeightPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                navBarHidden = (navBarHidden - available.y).coerceIn(0f, barHeightPx)
                return Offset.Zero
            }
        }
    }
    val navBarOffset = navBarHidden
    val navBarHeight = with(LocalDensity.current) { (barHeightPx - navBarHidden).toDp() }
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
            .nestedScroll(topBarScroll.nestedScrollConnection)
            .nestedScroll(navBarScroll),
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
            // It leaves on the way down and returns on the way up, mirroring the
            // top bar above it. A floating bar was considered and refused: that is
            // Google's own pattern rather than anything in the M3 specification,
            // and this screen already has a FAB in the same corner - two floating
            // things over a column of figures is how a number gets covered, which
            // has happened here once already. Hiding on scroll buys the same height
            // back without leaving the specification, and keeps the bar on the
            // screen edge, where a target is effectively infinite to hit.
            NavigationBar(
                modifier = Modifier
                    .height(navBarHeight)
                    .graphicsLayer { translationY = navBarOffset }
            ) {
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

@Composable
private fun MonthPanel(
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
    activeFilter: HistoryFilter?,
    onToggleCategory: (Category?) -> Unit,
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
                        val forward = targetState.first.isAfter(initialState.first)
                        val towards = if (forward) SlideDirection.Start else SlideDirection.End
                        (slideIntoContainer(towards, tween(Motion.MEDIUM, easing = Motion.emphasizedDecelerate)) { it / 3 } +
                            fadeIn(tween(Motion.SHORT, delayMillis = 60)))
                            .togetherWith(
                                slideOutOfContainer(towards, tween(Motion.SHORT, easing = Motion.emphasizedAccelerate)) { it / 3 } +
                                    fadeOut(tween(Motion.SHORT)),
                            )
                    },
                    label = "monthTotal",
                ) { (_, shown) ->
                    Text(
                        text = shown,
                        style = MoneyStyle.merge(MaterialTheme.typography.displayMedium),
                    )
                }
                Text(
                    text = currencyLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp, bottom = 6.dp),
                )
            }
            MonthComparison(current = total, previous = previousTotal, currencyLabel = currencyLabel)
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

        MonthStrip(bands = bands)

        if (bands.isEmpty()) {
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
                text = month.monthLabel() + "  \u25BE",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
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
    modifier: Modifier = Modifier,
    currencyLabel: String,
    cardBanks: Map<String, String>,
    salary: Money?,
    onRefile: () -> Unit,
) {
    val category = SaudiCategories.byId(transaction.categoryId)
    val aboveSalary = salary != null && transaction.direction == Direction.DEBIT &&
        transaction.countsAsSpending && transaction.amount.halalas >= salary.halalas
    // The record's own bank first. Falling back to the card is not a guess: a card
    // belongs to one bank, so a record that names both answers it for every other
    // record on that card - including the years captured before the app recorded a
    // bank at all.
    val mark = bankMark(transaction.bankId ?: transaction.accountLast4?.let(cardBanks::get))

    ListItem(
        modifier = modifier
            // One node to a screen reader: the merchant, the amount, the day and the
            // category are one transaction, not four things to reassemble.
            .semantics(mergeDescendants = true) {}
            // The row is the way to refile it. A wrong category is the most common
            // thing a person wants to change about a transaction, and it should not
            // require finding a control.
            .clickable(onClick = onRefile),
        // Transparent so the list stays one surface; M3's default container would
        // paint every row and turn the history into a stack of cards.
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = {
            // The category, as a glyph in a disc tinted with its own colour. This was
            // a 3dp coloured stripe down the edge of the row until the product doc
            // named side-stripe borders as the thing this app must not look like.
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(bandColour(category).copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = category.icon,
                    contentDescription = null,
                    tint = bandColour(category),
                    modifier = Modifier.size(20.dp),
                )
            }
        },
        headlineContent = {
            Text(
                // The Arabic name when there is one. The bank's own descriptor is
                // still what is stored and searched; this only changes what a
                // person reads.
                text = MerchantNames.forMerchant(transaction.merchantRaw)?.forLocale()
                    ?: transaction.merchantRaw
                    ?: transaction.note
                    ?: stringResource(transaction.type.labelRes),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (aboveSalary) {
                    // One purchase worth a whole month's salary is the thing the
                    // user asked to have marked. A label, not a colour: colour
                    // already means category on this row.
                    Text(
                        text = stringResource(R.string.above_salary),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.extraSmall)
                            .background(MaterialTheme.colorScheme.errorContainer)
                            .padding(horizontal = 6.dp, vertical = 1.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                }
                if (mark != null || transaction.accountLast4 != null) {
                    CardMark(mark = mark, last4 = transaction.accountLast4)
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    // Date and category on one line: two facts about the same row,
                    // and stacking them would make a two-line row a three-line one.
                    text = category
                        ?.let { "${transaction.dayLabel()}  ·  ${stringResource(it.labelRes)}" }
                        ?: transaction.dayLabel(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        // No delete control on the row. M3 lists carry at most one trailing element,
        // and a close glyph on every row took the width the category needed, so
        // the category read as "...". Deleting is in the row's own sheet, where
        // the row's name is on screen when the question is asked.
        trailingContent = { SignedAmount(transaction = transaction, currencyLabel = currencyLabel) },
    )
}

/** Which irreversible thing the user just asked for. */
private sealed interface DestructiveAction {

    /** Remove a record from the history. A manual one can simply be typed again. */
    data class Delete(val transaction: Transaction) : DestructiveAction

    /**
     * Reject a captured record. Strictly worse than [Delete]: it also destroys
     * `rawText`, the original bank message, which is the one field the user cannot
     * reproduce from memory. It was previously the only unguarded one.
     */
    data class Dismiss(val transaction: Transaction) : DestructiveAction

    /**
     * Throw away every category the app filed and file again with current rules.
     *
     * Destroys no transaction, so it names no amount and no day. What it destroys
     * is the app's own filing across the whole history, which is why it is here and
     * not a menu item that acts on the first tap.
     */
    data object RefileAll : DestructiveAction
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

/**
 * How this month compares with the one before it.
 *
 * A total on its own does not say whether the month was normal. This is the
 * cheapest thing that turns the number into information, and it is one line rather
 * than a second chart because the question it answers is one bit wide: more, or
 * less.
 */
@Composable
private fun MonthComparison(current: String, previous: Money?, currencyLabel: String) {
    if (previous == null) return
    val currentValue = runCatching {
        java.math.BigDecimal(current.replace(",", ""))
    }.getOrNull() ?: return

    val previousValue = previous.toBigDecimal()
    val difference = currentValue.subtract(previousValue)
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
 * Narrowing the month's list.
 *
 * With 22,000 records across the history, a list you cannot search is a list
 * nobody can answer a question with. The category chip is set by tapping a legend
 * row rather than by a second control, because the legend already names every
 * category and repeating them in a filter menu would be the same list twice.
 */
@Composable
private fun HistoryFilters(
    query: String,
    onQueryChange: (String) -> Unit,
    activeFilter: HistoryFilter?,
    onClear: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text(stringResource(R.string.search_hint)) },
            singleLine = true,
            // The search bar's own shape in M3 is the full pill.
            shape = MaterialTheme.shapes.extraLarge,
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier.fillMaxWidth(),
        )
        if (activeFilter != null || query.isNotBlank()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                activeFilter?.let { filter ->
                    val name = when (filter) {
                        HistoryFilter.Unfiled -> stringResource(R.string.uncategorised)
                        is HistoryFilter.OfCategory -> stringResource(filter.category.labelRes)
                    }
                    Text(
                        text = stringResource(R.string.showing_category, name),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                TextButton(
                    onClick = onClear,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text(stringResource(R.string.filter_clear)) }
            }
        }
    }
}

/**
 * Refiling a merchant.
 *
 * The decision is about the merchant, not the row: a person deciding that ALDREES
 * is transport has decided it for all forty of them, and for next month's. Saying
 * so plainly is better than a checkbox nobody reads, and better than silently
 * doing it and surprising them.
 */
@Composable
private fun RefileSheet(
    transaction: Transaction,
    onPick: (Category?, RefileScope) -> Unit,
    onForget: () -> Unit,
    onDelete: () -> Unit,
) {
    val current = SaudiCategories.byId(transaction.categoryId)
    val merchant = transaction.merchantRaw ?: stringResource(transaction.type.labelRes)
    var scope by remember { mutableStateOf(RefileScope.WHOLE_MERCHANT) }
    val bank = transaction.bankId?.let { bankMark(it) }
    val isArabic = LocalConfiguration.current.locales[0].language == "ar"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.refile_title, merchant),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        if (transaction.merchantKey != null) {
            // How far the decision reaches. The whole merchant is the useful
            // default - one tap files forty rows - but a card network sends the
            // same word for a cafe and a bakery, and then the bank that announced
            // the purchase is the only thing that tells them apart. "This one" is
            // for the odd purchase that belongs to neither rule.
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
            ) {
                val options = buildList {
                    add(RefileScope.THIS_ONE to stringResource(R.string.refile_scope_one))
                    if (bank != null) {
                        add(RefileScope.THIS_BANK to stringResource(R.string.refile_scope_bank, if (isArabic) bank.labelAr else bank.labelEn))
                    }
                    add(RefileScope.WHOLE_MERCHANT to stringResource(R.string.refile_scope_all))
                }
                options.forEachIndexed { index, (value, label) ->
                    SegmentedButton(
                        selected = scope == value,
                        onClick = { scope = value },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                    ) { Text(label, maxLines = 1) }
                }
            }
        }
        CategoryChips(
            selected = current,
            onSelect = { onPick(it, scope) },
            edgePadding = 20.dp,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            if (transaction.merchantKey != null) {
                // The undo for a filing decision. Without it a choice made once -
                // often from a truncated name that looked like something else -
                // outranks every built-in rule for ever.
                TextButton(onClick = onForget, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.refile_forget))
                }
            } else {
                Spacer(Modifier.width(1.dp))
            }
            // Destructive, so it sits apart from the filing controls and in the
            // error colour, and it still goes through the confirmation that names
            // the amount and the day.
            TextButton(
                onClick = onDelete,
                modifier = Modifier.heightIn(min = 48.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text(stringResource(R.string.delete))
            }
        }
    }
}

/**
 * Which card, and whose.
 *
 * A tinted chip rather than another coloured bar: the row already carries the
 * category's colour down its leading edge, and a second colour system competing
 * with it would make both mean less. The bank's colour is inside a shape that is
 * clearly a label, so the two never read as the same kind of signal.
 *
 * The digits are kept even when the bank is known. Two cards from one bank is the
 * ordinary case, and "الراجحي" alone cannot tell them apart.
 */
@Composable
private fun CardMark(mark: BankMark?, last4: String?) {
    val colour = mark?.colour ?: MaterialTheme.colorScheme.onSurfaceVariant
    // Digits alone, with no leading dots to say "and four more". In a right-to-left
    // row the dots land on the wrong side of the number and read as part of it; the
    // chip is already unmistakably a tag, and does that work without them.
    val isArabic = LocalConfiguration.current.locales[0].language == "ar"
    val text = listOfNotNull(mark?.let { if (isArabic) it.labelAr else it.labelEn }, last4)
        .joinToString(" ")

    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = colour,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(colour.copy(alpha = 0.14f))
            .padding(horizontal = 6.dp, vertical = 1.dp),
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
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .then(
                if (selected) {
                    Modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest)
                } else {
                    Modifier
                }
            )
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
            style = MoneyStyle.merge(MaterialTheme.typography.bodyMedium),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The merchant name in the language the interface is being read in.
 *
 * Not the device language: the app has its own switch, and someone reading the
 * English interface on an Arabic phone is asking for English.
 */
@Composable
@ReadOnlyComposable
private fun MerchantNames.MerchantName.forLocale(): String =
    if (LocalConfiguration.current.locales[0].language == "ar") ar else en

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
private fun UnfiledBanner(count: Int, active: Boolean, onOpen: () -> Unit) {
    AnimatedVisibility(
        visible = count > 0 && !active,
        enter = fadeIn(tween(200)) + expandVertically(tween(250, easing = EaseOutQuart)),
        exit = fadeOut(tween(150)) + shrinkVertically(tween(200, easing = EaseOutQuart)),
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
