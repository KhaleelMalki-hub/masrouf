package sa.masrouf.app.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import sa.masrouf.core.dedup.Fingerprint
import sa.masrouf.core.model.Direction
import sa.masrouf.core.model.Source
import sa.masrouf.core.model.Status
import sa.masrouf.core.model.Transaction
import sa.masrouf.core.model.TransactionType
import sa.masrouf.core.money.Money
import sa.masrouf.core.text.ArabicText
import sa.masrouf.core.time.RiyadhTime
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * The app's only route to stored transactions.
 *
 * Screens are given [Transaction] values, never entities, so no screen can start
 * reading a stored enum name or a raw halala count and reach its own conclusion
 * about what it means.
 */
class TransactionRepository(private val dao: TransactionDao) {

    fun observeRecent(limit: Int = RECENT_LIMIT): Flow<List<Transaction>> =
        dao.observeRecent(limit).map { rows -> rows.map(TransactionEntity::toModel) }

    /** Everything belonging to a Riyadh calendar month, newest first. */
    fun observeMonth(anyDayInMonth: LocalDate): Flow<List<Transaction>> {
        val first = anyDayInMonth.withDayOfMonth(1)
        return dao.observeBetween(
            fromMillis = RiyadhTime.startOfDay(first).toEpochMilli(),
            untilMillis = RiyadhTime.startOfDay(first.plusMonths(1)).toEpochMilli(),
        ).map { rows -> rows.map(TransactionEntity::toModel) }
    }

    /**
     * Stores a transaction the user typed.
     *
     * Recorded as [Status.CONFIRMED], which is not a contradiction of the rule that
     * nothing auto-confirms: that rule is about *captured* records, where a parser
     * decided the amount. Here the user read the amount and typed it, so asking
     * them to confirm their own keystrokes would only teach them to dismiss the
     * confirmation step that matters.
     */
    suspend fun recordManual(
        amount: Money,
        direction: Direction,
        type: TransactionType,
        occurredAt: Instant,
        merchantRaw: String?,
        note: String?,
        accountId: String? = null,
        categoryId: String? = null,
    ): Transaction {
        val id = UUID.randomUUID().toString()
        val transaction = Transaction(
            id = id,
            amount = amount,
            direction = direction,
            type = type,
            occurredAt = occurredAt,
            accountId = accountId,
            categoryId = categoryId,
            merchantRaw = merchantRaw?.takeIf { it.isNotBlank() },
            merchantKey = merchantRaw
                ?.let(ArabicText::normalizeMerchant)
                ?.takeIf { it.isNotBlank() },
            note = note?.takeIf { it.isNotBlank() },
            source = Source.MANUAL,
            status = Status.CONFIRMED,
            // Keyed on the generated id, so a manual record can never collide with
            // another record - the user meaning to enter the same amount twice is a
            // thing they are allowed to do.
            fingerprint = Fingerprint.forManual(id),
            rawText = null,
        )
        dao.insert(transaction.toEntity())
        return transaction
    }

    companion object {
        const val RECENT_LIMIT = 50
    }
}

/**
 * What the user spent over these transactions.
 *
 * Delegates the "does this count" decision to [TransactionType.countsAsSpending]
 * rather than listing types here. Two screens each deciding for themselves is how
 * two surfaces come to disagree about the same month.
 */
fun List<Transaction>.spendingTotal(): Money =
    filter { it.direction == Direction.DEBIT && it.type.countsAsSpending }
        .fold(Money.ZERO) { running, transaction -> running + transaction.amount }
