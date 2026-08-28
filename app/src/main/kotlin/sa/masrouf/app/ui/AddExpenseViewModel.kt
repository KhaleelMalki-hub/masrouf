package sa.masrouf.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import sa.masrouf.app.capture.HistoryImport
import sa.masrouf.app.data.TransactionRepository
import sa.masrouf.app.data.categoryShares
import sa.masrouf.app.data.spendingTotal
import sa.masrouf.core.model.Category
import sa.masrouf.core.model.Direction
import sa.masrouf.core.model.Status
import sa.masrouf.core.model.Transaction
import sa.masrouf.core.model.TransactionType
import sa.masrouf.core.money.Money
import sa.masrouf.core.text.ArabicText
import sa.masrouf.core.time.RiyadhTime
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
) : ViewModel() {

    /** What the one-off history import is doing, for the dashboard to report. */
    sealed interface ImportState {
        data object Idle : ImportState
        data class Running(val examined: Int) : ImportState
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

    /** A category the list is narrowed to, set by tapping its row in the legend. */
    private val _categoryFilter = MutableStateFlow<Category?>(null)
    val categoryFilter: StateFlow<Category?> = _categoryFilter.asStateFlow()

    fun onQueryChanged(value: String) { _query.value = value }

    /** Tapping the same category again clears the filter, so it is its own undo. */
    fun toggleCategoryFilter(category: Category?) {
        _categoryFilter.value = if (_categoryFilter.value?.id == category?.id) null else category
    }

    fun clearFilters() {
        _query.value = ""
        _categoryFilter.value = null
    }

    /** Everything confirmed in the selected month, newest first. */
    private val confirmedThisMonth: StateFlow<List<Transaction>> =
        _selectedMonth
            .flatMapLatest { month -> repository.observeMonth(month) }
            .map { rows -> rows.filter { it.status == Status.CONFIRMED } }
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
                .filter { category == null || it.categoryId == category.id }
                .filter { row ->
                    needle.isEmpty() ||
                        ArabicText.foldForMatching(row.merchantRaw.orEmpty()).contains(needle) ||
                        ArabicText.foldForMatching(row.note.orEmpty()).contains(needle)
                }
                .toList()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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
    val monthShares: StateFlow<List<Pair<Category?, Money>>> =
        _selectedMonth
            .flatMapLatest { month -> repository.observeMonth(month) }
            .map { it.categoryShares() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val monthTotal: StateFlow<Money> =
        _selectedMonth
            .flatMapLatest { month -> repository.observeMonth(month) }
            .map { it.spendingTotal() }
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
    fun confirm(id: String, categoryId: String? = null) {
        viewModelScope.launch { repository.confirmWithCategory(id, categoryId) }
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
                HistoryImport(repository).run(messages) { examined, _ ->
                    _importState.value = ImportState.Running(examined)
                }
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
            _importState.value = ImportState.Confirmed(repository.confirmAllPending())
        }
    }

    /** Files every unfiled record whose merchant is recognised. */
    fun fileHistory() {
        viewModelScope.launch {
            _importState.value = ImportState.Filed(repository.fileUncategorised())
        }
    }

    fun clearImportState() {
        _importState.value = ImportState.Idle
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
    fun save() {
        val current = _form.value
        if (current.isSaving) return
        val amount = (current.amountResult as? AmountInput.Result.Valid)?.amount
        if (amount == null) {
            _form.value = current.copy(submitAttempted = true)
            return
        }

        _form.value = current.copy(isSaving = true)
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
            } catch (e: Exception) {
                // Releasing the flag is load-bearing: without it a failed write
                // locks the form forever, which is the mirror-image defect of the
                // double-write this flag prevents.
                _form.value = current.copy(isSaving = false)
                throw e
            }
        }
    }

    class Factory(
        private val repository: TransactionRepository,
        private val readInbox: (suspend () -> List<sa.masrouf.core.capture.RawMessage>)? = null,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            AddExpenseViewModel(repository, readInbox = readInbox) as T
    }
}
