package sa.masrouf.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
) {
    val amountResult: AmountInput.Result get() = AmountInput.parse(typedAmount)

    val canSave: Boolean get() = amountResult is AmountInput.Result.Valid

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

    val pendingCount: StateFlow<Int> =
        repository.observePendingCount()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val monthTotal: StateFlow<Money> =
        repository.observeMonth(RiyadhTime.localDate(Instant.now(clock)))
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

    /**
     * Saves, if the amount is valid.
     *
     * The timestamp comes from the clock, not from anything typed: a manual entry
     * is recorded when it is recorded. Back-dating is a feature that can be added
     * with a date picker, and guessing at it silently is not.
     */
    fun save() {
        val current = _form.value
        val amount = (current.amountResult as? AmountInput.Result.Valid)?.amount
        if (amount == null) {
            _form.value = current.copy(submitAttempted = true)
            return
        }

        viewModelScope.launch {
            repository.recordManual(
                amount = amount,
                direction = Direction.DEBIT,
                type = current.type,
                occurredAt = Instant.now(clock),
                merchantRaw = current.merchant,
                note = current.note,
            )
            // Cleared only after the write returns, so a failed insert leaves the
            // user's typing on screen instead of discarding it.
            _form.value = AddExpenseState(type = current.type)
        }
    }

    class Factory(private val repository: TransactionRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            AddExpenseViewModel(repository) as T
    }
}
