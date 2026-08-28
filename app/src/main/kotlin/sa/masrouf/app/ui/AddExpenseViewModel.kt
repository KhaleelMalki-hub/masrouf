package sa.masrouf.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import sa.masrouf.app.data.TransactionRepository
import sa.masrouf.app.data.spendingTotal
import sa.masrouf.core.model.Direction
import sa.masrouf.core.model.Transaction
import sa.masrouf.core.model.TransactionType
import sa.masrouf.core.money.Money
import sa.masrouf.core.time.RiyadhTime
import java.time.Clock
import java.time.Instant

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

class AddExpenseViewModel(
    private val repository: TransactionRepository,
    private val clock: Clock = Clock.system(RiyadhTime.ZONE),
) : ViewModel() {

    private val _form = MutableStateFlow(AddExpenseState())
    val form: StateFlow<AddExpenseState> = _form.asStateFlow()

    val recent: StateFlow<List<Transaction>> =
        repository.observeRecent()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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
     * The clock is read on every resubscription, not once at construction. A
     * ViewModel that survives midnight on the 1st would otherwise keep showing last
     * month's total under this month's heading, with new records falling outside
     * its fixed bounds and never appearing - a wrong number presented as fact,
     * which is the failure `Status` and `spendingTotal` exist to prevent, arriving
     * through the date instead of the amount.
     *
     * ponytail: corrects when the screen is backgrounded past the 5s stop timeout
     * and returned to. An app left in the foreground across midnight still shows
     * the old month; closing that needs a timer, which is not worth it here.
     */
    val monthTotal: StateFlow<Money> =
        flow { emitAll(repository.observeMonth(RiyadhTime.localDate(Instant.now(clock)))) }
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

    /** The user vouched for a captured record; it joins their totals. */
    fun confirm(id: String) {
        viewModelScope.launch { repository.confirm(id) }
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
            )
                // Cleared only after the write returns, so a failed insert leaves
                // the user's typing on screen instead of discarding it.
                _form.value = AddExpenseState(type = current.type)
            } catch (e: Exception) {
                // Releasing the flag is load-bearing: without it a failed write
                // locks the form forever, which is the mirror-image defect of the
                // double-write this flag prevents.
                _form.value = current.copy(isSaving = false)
                throw e
            }
        }
    }

    class Factory(private val repository: TransactionRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            AddExpenseViewModel(repository) as T
    }
}
