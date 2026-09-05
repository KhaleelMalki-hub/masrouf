package sa.masrouf.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import sa.masrouf.app.capture.HistoryImport
import sa.masrouf.app.data.CardBalance
import sa.masrouf.app.data.IncomeMonth
import sa.masrouf.app.data.TransactionRepository
import sa.masrouf.app.data.categoryShares
import sa.masrouf.app.data.investedTotal
import sa.masrouf.app.data.spendingByCardKind
import sa.masrouf.app.data.spendingTotal
import sa.masrouf.core.model.CardKind
import sa.masrouf.core.model.Category
import sa.masrouf.core.model.Direction
import sa.masrouf.core.model.SaudiCategories
import sa.masrouf.core.model.Status
import sa.masrouf.core.model.Transaction
import sa.masrouf.core.model.TransactionType
import sa.masrouf.core.money.Money
import sa.masrouf.core.text.ArabicText
import sa.masrouf.core.time.RiyadhTime
import java.time.YearMonth
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

/** The transaction types a person records by hand. Order is the order they appear in. */
val MANUAL_TYPES: List<TransactionType> = listOf(
    TransactionType.PURCHASE,
    TransactionType.BILL_PAYMENT,
    TransactionType.TRANSFER_OUT,
    TransactionType.ATM_WITHDRAWAL,
    TransactionType.OWN_TRANSFER,
)

data class AddExpenseState(
    val typedAmount: String = "",
    val merchant: String = "",
    val note: String = "",
    val type: TransactionType = TransactionType.PURCHASE,
    /**
     * What the expense was for, chosen while typing it.
     *
     * Optional: filing can wait, recording cannot, and a required category is how
     * a five-second entry becomes a fifteen-second one that gets skipped.
     */
    val category: Category? = null,
    /**
     * True once the user has tried to save. The amount error is withheld until
     * then, so an empty field is not scolded before it has been filled in.
     */
    val submitAttempted: Boolean = false,
    /**
     * True when the last save was accepted and then failed to store.
     *
     * Distinct from an amount error, which is the user's to fix: this one is the
     * app's, and the only honest thing to do with it is say so and keep what was
     * typed on screen.
     */
    val saveFailed: Boolean = false,
    /**
     * True while a save is in flight.
     *
     * The Save button is deliberately always enabled, and clearing the form only
     * after the write returns is deliberate too - so without this, two taps a
     * tenth of a second apart both read the same form and write two records. Manual
     * entry has no deduplication by design, so nothing downstream catches it.
     */
    val isSaving: Boolean = false,
) {
    val amountResult: AmountInput.Result get() = AmountInput.parse(typedAmount)

    /** The error to show under the amount field, or null when there is nothing to say yet. */
    val amountError: AmountError?
        get() = when (amountResult) {
            is AmountInput.Result.Valid -> null
            AmountInput.Result.Empty -> if (submitAttempted) AmountError.REQUIRED else null
            // Shown as soon as it is typed, without waiting for a save attempt:
            // unlike an empty field, this one is already wrong.
            AmountInput.Result.Invalid -> AmountError.INVALID
        }

    enum class AmountError { REQUIRED, INVALID }
}

@OptIn(ExperimentalCoroutinesApi::class)
class AddExpenseViewModel(
    private val repository: TransactionRepository,
    private val clock: Clock = Clock.system(RiyadhTime.ZONE),
    private val readInbox: (suspend () -> List<sa.masrouf.core.capture.RawMessage>)? = null,
    private val maintenance: suspend () -> Unit = {},
    /**
     * Where launch-time work runs.
     *
     * Injectable because it was `Dispatchers.Default` in the `init` block, which a
     * test cannot advance or wait for: the coroutine outlived the test that
     * started it, reached back into the test's own dispatcher, and threw - and the
     * exception surfaced against whichever test ran NEXT, as "uncaught exceptions
     * before the test started". A flake with a cause, not a mystery.
     */
    private val background: CoroutineContext = Dispatchers.Default,
) : ViewModel() {

    /** What the one-off history import is doing, for the dashboard to report. */
    sealed interface ImportState {
        data object Idle : ImportState
        data class Running(val examined: Int) : ImportState

        /** Re-filing the whole history. No count: it is one database transaction. */
        data object Refiling : ImportState
        data class Done(val stored: Int, val examined: Int) : ImportState
        data class Filed(val count: Int) : ImportState
        data class Confirmed(val count: Int) : ImportState
    }

    private val _importState = MutableStateFlow<ImportState>(ImportState.Idle)
    val importState: StateFlow<ImportState> = _importState.asStateFlow()

    private val _form = MutableStateFlow(AddExpenseState())
    val form: StateFlow<AddExpenseState> = _form.asStateFlow()

    val recent: StateFlow<List<Transaction>> =
        repository.observeRecent()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * The month being looked at, as its first day.
     *
     * Defaults to now. Everything the dashboard shows - the total, the strip, the
     * history - reads from this one value, so the page can never be showing one
     * month's total above another month's transactions.
     */
    private val _selectedMonth = MutableStateFlow(
        RiyadhTime.localDate(Instant.now(clock)).withDayOfMonth(1)
    )
    val selectedMonth: StateFlow<LocalDate> = _selectedMonth.asStateFlow()

    /** The current month. Nothing may be selected after it; the future has no spending. */
    val currentMonth: LocalDate
        get() = RiyadhTime.localDate(Instant.now(clock)).withDayOfMonth(1)

    /**
     * The first month with anything in it.
     *
     * Paging stops here rather than running backwards for ever through empty
     * months, which reads as data loss rather than as the end of the record.
     */
    /** Every month that has something in it, for the picker. */
    val monthsWithData: StateFlow<List<LocalDate>> =
        repository.observeMonthsWithData()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Jumps straight to a month, for the picker. Bounded like the arrows are. */
    fun showMonth(month: LocalDate) {
        val first = month.withDayOfMonth(1)
        if (!first.isAfter(currentMonth)) _selectedMonth.value = first
    }

    val earliestMonth: StateFlow<LocalDate?> =
        repository.observeEarliestMonth()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** A merchant substring the user is looking for, or blank for everything. */
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /** What the list is narrowed to, set by tapping a row in the legend. */
    private val _categoryFilter = MutableStateFlow<HistoryFilter?>(null)
    val categoryFilter: StateFlow<HistoryFilter?> = _categoryFilter.asStateFlow()

    fun onQueryChanged(value: String) { _query.value = value }

    /**
     * Tapping the same band again clears the filter, so it is its own undo.
     *
     * A null [category] is the legend's uncategorised band, which selects the
     * user's filing worklist rather than clearing the filter.
     */
    fun toggleCategoryFilter(category: Category?) {
        val next = category?.let(HistoryFilter::OfCategory) ?: HistoryFilter.Unfiled
        _categoryFilter.value = if (_categoryFilter.value == next) null else next
    }

    fun clearFilters() {
        _query.value = ""
        _categoryFilter.value = null
    }

    /** The salary the bank last announced, for the month line until the user types one. */
    val detectedSalary: StateFlow<Money?> =
        repository.observeLatestSalary()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Which bank each card belongs to.
     *
     * Built from the records that know rather than stored per row, so one message
     * naming a card and its bank labels every other record on that card, back
     * through the whole history.
     */
    val cardBanks: StateFlow<Map<String, String>> =
        repository.observeCardBanks()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /**
     * What each card last said was left.
     *
     * Fed by the messages alone: no bank connection, no account, nothing leaves the
     * device. It is as current as the last message from that card, and says so.
     */
    val cardBalances: StateFlow<List<CardBalance>> =
        repository.observeCardBalances()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Everything confirmed in the selected month, newest first. */
    private val confirmedThisMonth: StateFlow<List<Transaction>> =
        _selectedMonth
            .flatMapLatest { month -> repository.observeMonth(month) }
            .map { rows -> rows.filter { it.status == Status.CONFIRMED } }
            .flowOn(background)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * The month's records after the search box and the category filter.
     *
     * With 22,000 transactions across 146 months, a list with no way to narrow it
     * is a list nobody can answer a question with. Matching is on the folded
     * merchant key, the same normalisation deduplication uses, so a search finds a
     * merchant however the bank happened to spell it that day.
     */
    val monthTransactions: StateFlow<List<Transaction>> =
        combine(confirmedThisMonth, _query, _categoryFilter) { rows, query, category ->
            val needle = ArabicText.foldForMatching(query).trim()
            rows.asSequence()
                .filter { row ->
                    when (category) {
                        null -> true
                        HistoryFilter.Unfiled -> SaudiCategories.byId(row.categoryId) == null
                        is HistoryFilter.OfCategory -> row.categoryId == category.category.id
                    }
                }
                .filter { row ->
                    needle.isEmpty() ||
                        ArabicText.foldForMatching(row.merchantRaw.orEmpty()).contains(needle) ||
                        ArabicText.foldForMatching(row.note.orEmpty()).contains(needle)
                }
                .toList()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** How many confirmed records this month have no category the build can name. */
    val monthUnfiled: StateFlow<Int> =
        confirmedThisMonth
            .map { rows -> rows.count { SaudiCategories.byId(it.categoryId) == null } }
            .flowOn(background)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /**
     * What this month put into investments, or null when it put in nothing.
     *
     * Shown beside the total rather than inside it. The money did not leave.
     */
    val monthInvested: StateFlow<Money?> =
        confirmedThisMonth
            .map { rows -> rows.investedTotal().takeIf { !it.isZero } }
            .flowOn(background)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Salary and bonuses by month, newest first, for the income screen.
     *
     * Not scoped to the selected month: this is the series the owner asked for -
     * "how my salary and bonuses ran over the years" - and a month at a time is the
     * question the other screen already answers.
     */
    val incomeByMonth: StateFlow<List<IncomeMonth>> =
        repository.observeIncomeByMonth()
            .flowOn(background)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Whether the income series has arrived. See [monthLoaded]: the same distinction,
     * and the income screen was making the same mistake - "لا يوجد دخل مسجّل بعد"
     * over twelve years of salaries, for as long as the query took.
     */
    val incomeLoaded: StateFlow<Boolean> =
        repository.observeIncomeByMonth()
            .map { true }
            .flowOn(background)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /**
     * The individual deposits behind [incomeByMonth], keyed by month.
     *
     * Grouped here rather than in the screen so the screen holds no logic about
     * what month a deposit belongs to - the same Riyadh boundary the query uses.
     */
    val incomeDeposits: StateFlow<Map<YearMonth, List<Transaction>>> =
        repository.observeIncomeRows()
            .map { rows -> rows.groupBy { YearMonth.from(RiyadhTime.localDate(it.occurredAt)) } }
            .flowOn(background)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /**
     * What the previous month came to, for comparison.
     *
     * A month's total on its own says nothing about whether it was a normal month.
     * The comparison is the cheapest thing that turns a number into information.
     */
    val previousMonthTotal: StateFlow<Money?> =
        _selectedMonth
            .flatMapLatest { month -> repository.observeMonth(month.minusMonths(1)) }
            .map { rows -> rows.takeIf { it.isNotEmpty() }?.spendingTotal() }
            .flowOn(background)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun showPreviousMonth() {
        val earliest = earliestMonth.value ?: return
        val candidate = _selectedMonth.value.minusMonths(1)
        if (!candidate.isBefore(earliest)) _selectedMonth.value = candidate
    }

    fun showNextMonth() {
        val candidate = _selectedMonth.value.plusMonths(1)
        if (!candidate.isAfter(currentMonth)) _selectedMonth.value = candidate
    }

    /**
     * Captured records the user has not vouched for yet.
     *
     * Kept out of [monthTotal] until they are confirmed - see `spendingTotal`.
     */
    val pending: StateFlow<List<Transaction>> =
        repository.observePending()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Spending for the current Riyadh month.
     *
     * Follows [selectedMonth], so the number and the strip beneath it are always
     * describing the same month. The month the app opens on is read from the clock
     * once; paging is then explicit, which also removes the old problem of a
     * ViewModel surviving midnight on the 1st and quietly showing last month under
     * this month's heading.
     */
    /**
     * The month split into the bands the strip draws, largest share first.
     *
     * Derived from the same flow as [monthTotal] rather than a second query, so the
     * strip cannot be showing one month while the number above it shows another.
     */
    /**
     * Whether the month has been read from the database yet.
     *
     * Every flow on this screen starts at its empty value, so for the first frames
     * over a 26,000-record history the screen said the month came to 0.00 and that
     * there was nothing in it - the same words it uses for a month that really is
     * empty - and then everything appeared at once. "I have not looked yet" and
     * "there is nothing" are different sentences, and the screen was only able to
     * say the second.
     */
    val monthLoaded: StateFlow<Boolean> =
        _selectedMonth
            .flatMapLatest { month -> repository.observeMonth(month) }
            .map { true }
            .flowOn(background)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val monthShares: StateFlow<List<Pair<Category?, Money>>> =
        _selectedMonth
            .flatMapLatest { month -> repository.observeMonth(month) }
            .map { it.categoryShares() }
            .flowOn(background)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * What kind of card each one is. Read once: it is a fact about the card, not
     * about the month, and deciding it reads every stored body.
     */
    private val _cardKinds = MutableStateFlow<Map<String, CardKind>>(emptyMap())
    val cardKinds: StateFlow<Map<String, CardKind>> = _cardKinds.asStateFlow()

    /** The month split into mada and credit, for the cards whose kind is known. */
    val monthByCardKind: StateFlow<List<Pair<CardKind, Money>>> =
        combine(confirmedThisMonth, _cardKinds) { rows, kinds -> rows.spendingByCardKind(kinds) }
            .flowOn(background)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val monthTotal: StateFlow<Money> =
        _selectedMonth
            .flatMapLatest { month -> repository.observeMonth(month) }
            .map { it.spendingTotal() }
            .flowOn(background)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Money.ZERO)

    fun onAmountChanged(typed: String) {
        _form.value = _form.value.copy(typedAmount = typed)
    }

    fun onMerchantChanged(value: String) {
        _form.value = _form.value.copy(merchant = value)
    }

    fun onNoteChanged(value: String) {
        _form.value = _form.value.copy(note = value)
    }

    fun onTypeChanged(type: TransactionType) {
        _form.value = _form.value.copy(type = type)
    }

    fun onCategoryChanged(category: Category?) {
        _form.value = _form.value.copy(category = category)
    }

    /**
     * The user vouched for a captured record and said what it was for.
     *
     * One action, because it is one decision made while looking at the bank's own
     * message. A null category is allowed - filing can wait, vouching cannot.
     */
    fun confirm(id: String, categoryId: String? = null, chosenByUser: Boolean = false) {
        viewModelScope.launch { repository.confirmWithCategory(id, categoryId, chosenByUser) }
    }

    /**
     * Reads the SMS inbox once and pulls past bank transactions out of it.
     *
     * Everything it finds lands PENDING, like any capture - a backfill that
     * silently added hundreds of confirmed rows would put numbers the user has
     * never seen into their totals.
     */
    fun importHistory() {
        val read = readInbox ?: return
        if (_importState.value is ImportState.Running) return

        viewModelScope.launch {
            _importState.value = ImportState.Running(0)
            val report = runCatching {
                val messages = read()
                val imported = HistoryImport(repository).run(messages) { examined, _ ->
                    _importState.value = ImportState.Running(examined)
                }
                // Filing runs as part of importing rather than as a second thing to
                // remember. An import of twenty thousand records that lands with no
                // categories on any of them is a history the user cannot read, and
                // "now go and press the other button" is not a design.
                repository.fileUncategorised()
                imported
            }.getOrNull()

            _importState.value = report
                ?.let { ImportState.Done(stored = it.stored, examined = it.examined) }
                ?: ImportState.Idle
        }
    }

    /**
     * Confirms everything waiting, in one action.
     *
     * Reported through [ImportState] like the other bulk operations, because an
     * action over a thousand records that says nothing afterwards is
     * indistinguishable from one that failed.
     */
    fun confirmAllPending() {
        viewModelScope.launch {
            // The bar first, then the work. Confirming three thousand records takes
            // seconds, and the dialog has already closed by then - so the screen sat
            // inert with nothing to say it was busy, which is the state this app is
            // careful everywhere else not to leave a user in.
            _importState.value = ImportState.Refiling
            _importState.value = ImportState.Confirmed(repository.confirmAllPending())
        }
    }

    /** Files every unfiled record whose merchant is recognised. */
    fun fileHistory() {
        viewModelScope.launch {
            _importState.value = ImportState.Refiling
            _importState.value = ImportState.Filed(repository.fileUncategorised())
        }
    }

    /**
     * Throws away the app's own filing and does it again with the current rules.
     *
     * Separate from [fileHistory], and destructive where that one is not, so it is
     * behind a confirmation. What it destroys is only what the app decided; a
     * category the user chose is kept.
     */
    init {
        // Launch-time maintenance, from the view model's own scope rather than a
        // LaunchedEffect: a LaunchedEffect needs a frame, and an activity started
        // behind the lock screen does not get one. What runs, and how often, is
        // the application's decision - see MasroufApp.runMaintenance.
        // Off the main thread: the filing pass alone runs two thousand rows through
        // two hundred rules, and on Main it froze the first three seconds of every
        // launch - legend drawn, total stuck at 0.00, strip blank.
        viewModelScope.launch(background) {
            maintenance()
            // After maintenance, not before: it purges bodies the gate now refuses
            // and re-parses the rest, and this reads those bodies.
            _cardKinds.value = repository.cardKinds()
        }
    }

    fun refileEverything() {
        if (_importState.value is ImportState.Refiling) return
        viewModelScope.launch {
            _importState.value = ImportState.Refiling
            _importState.value = ImportState.Filed(repository.refileAll())
        }
    }

    fun clearImportState() {
        _importState.value = ImportState.Idle
    }

    /**
     * Files every transaction from a merchant, and remembers the decision.
     *
     * The whole merchant rather than the one row: refiling only what is on screen
     * leaves the other forty from the same shop wrong, and the user would have to
     * do it again next month.
     */
    fun fileMerchant(merchantKey: String, categoryId: String) {
        viewModelScope.launch {
            _importState.value = ImportState.Refiling
            _importState.value = ImportState.Filed(repository.fileMerchant(merchantKey, categoryId))
        }
    }

    /**
     * Drops a learned rule for a merchant and lets the built-in rules answer.
     *
     * The undo for [fileMerchant]. Needed because a filing decision made from a
     * truncated name is still a decision, and the app defends it against every
     * later correction until it is taken back.
     */
    fun forgetMerchant(merchantKey: String) {
        viewModelScope.launch {
            _importState.value = ImportState.Refiling
            _importState.value = ImportState.Filed(repository.forgetMerchant(merchantKey))
        }
    }

    /** Files one merchant as it arrives through one bank; see the repository. */
    fun fileMerchantAtBank(merchantKey: String, bankId: String, categoryId: String) {
        viewModelScope.launch {
            _importState.value = ImportState.Refiling
            _importState.value = ImportState.Filed(repository.fileMerchantAtBank(merchantKey, bankId, categoryId))
        }
    }

    /** Refiles a record the user categorised wrongly the first time. */
    fun setCategory(id: String, categoryId: String?) {
        viewModelScope.launch { repository.setCategory(id, categoryId) }
    }

    /** The user rejected a captured record - a misparse, or not theirs. */
    fun dismiss(id: String) {
        viewModelScope.launch { repository.dismiss(id) }
    }

    /** The user removed a record from their history. Asked for on screen first. */
    fun delete(id: String) {
        viewModelScope.launch { repository.delete(id) }
    }

    /**
     * Saves, if the amount is valid.
     *
     * The timestamp comes from the clock, not from anything typed: a manual entry
     * is recorded when it is recorded. Back-dating is a feature that can be added
     * with a date picker, and guessing at it silently is not.
     */
    private val _saved = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Emits once per stored record. The entry sheet closes on it and nothing else. */
    val saved: SharedFlow<Unit> = _saved.asSharedFlow()

    fun save() {
        val current = _form.value
        if (current.isSaving) return
        val amount = (current.amountResult as? AmountInput.Result.Valid)?.amount
        if (amount == null) {
            _form.value = current.copy(submitAttempted = true, saveFailed = false)
            return
        }

        _form.value = current.copy(isSaving = true, saveFailed = false)
        viewModelScope.launch {
            try {
                repository.recordManual(
                    amount = amount,
                    direction = Direction.DEBIT,
                    type = current.type,
                    occurredAt = Instant.now(clock),
                    merchantRaw = current.merchant,
                    note = current.note,
                    categoryId = current.category?.id,
                )
                // Cleared only after the write returns, so a failed insert leaves
                // the user's typing on screen instead of discarding it.
                _form.value = AddExpenseState(
                    type = current.type,
                    category = current.category,
                )
                // The sheet closes on THIS, not on the tap. A tap that failed
                // validation used to close it too, so the error the form had just
                // armed was never on screen and the expense was simply not there.
                _saved.tryEmit(Unit)
            } catch (e: Exception) {
                // Releasing the flag is load-bearing: without it a failed write
                // locks the form forever, which is the mirror-image defect of the
                // double-write this flag prevents.
                //
                // And it is reported rather than rethrown. This runs in
                // viewModelScope with no handler, so throwing here killed the app
                // on a failed insert - the loudest possible way to lose an entry
                // the user had already typed.
                _form.value = current.copy(isSaving = false, saveFailed = true)
            }
        }
    }

    class Factory(
        private val repository: TransactionRepository,
        private val readInbox: (suspend () -> List<sa.masrouf.core.capture.RawMessage>)? = null,
        private val maintenance: suspend () -> Unit = {},
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            AddExpenseViewModel(repository, readInbox = readInbox, maintenance = maintenance) as T
    }
}
