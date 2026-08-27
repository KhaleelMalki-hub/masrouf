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

    /**
     * Stores a record the capture pipeline produced.
     *
     * @return true when it was written, false when its fingerprint was already
     *   stored. A notification that Android reposts - an update, a reconnect - must
     *   not become a second transaction, and the unique index on `fingerprint` is
     *   what makes that a property of the database rather than of whoever calls it.
     */
    suspend fun recordCaptured(transaction: Transaction): Boolean =
        dao.insert(transaction.toEntity()) != -1L

    /** How many pending records are waiting for the user to confirm them. */
    fun observePendingCount(): Flow<Int> = dao.observePendingCount()

    companion object {
        const val RECENT_LIMIT = 50
    }
}

/**
 * What the user spent over these transactions.
 *
 * Two filters, and both are load-bearing.
 *
 * [Status.CONFIRMED] only. A captured record is a parser's reading of a bank
 * message, and a parser that misreads an amount is a certainty over a long enough
 * period. Letting a [Status.PENDING] record into this total is the exact failure
 * `Status` exists to prevent: a number the user never agreed to, presented to them
 * as fact. They are told how many are waiting instead.
 *
 * The "does this count" decision is delegated to [TransactionType.countsAsSpending]
 * rather than listed here, because two surfaces each deciding for themselves is how
 * they come to disagree about the same month.
 */
fun List<Transaction>.spendingTotal(): Money =
    filter { it.status == Status.CONFIRMED }
        .filter { it.direction == Direction.DEBIT && it.type.countsAsSpending }
        .fold(Money.ZERO) { running, transaction -> running + transaction.amount }
