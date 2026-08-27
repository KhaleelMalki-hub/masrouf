package sa.masrouf.core.model

import sa.masrouf.core.money.Money
import sa.masrouf.core.time.RiyadhTime
import java.time.Instant
import java.time.LocalDate

/** Which way the money moved. Amounts are always stored positive; direction carries the sign. */
enum class Direction { DEBIT, CREDIT }

/** What kind of movement it was. Drives both categorisation defaults and reporting. */
enum class TransactionType {
    PURCHASE,
    ATM_WITHDRAWAL,
    TRANSFER_OUT,
    TRANSFER_IN,
    BILL_PAYMENT,
    SALARY,
    REFUND,
    FEE,
    UNKNOWN,
}

/**
 * Where the record came from. Kept forever, because it decides how much the
 * record can be trusted and how conflicts are resolved when the same purchase
 * arrives twice from two different sources.
 */
enum class Source {
    /** Typed in by hand. Highest trust: the user saw it and meant it. */
    MANUAL,

    /** Parsed from a bank push notification captured on device. */
    NOTIFICATION,

    /** Parsed from a bank SMS. */
    SMS,

    /** Imported from a downloaded bank statement. Authoritative for amounts. */
    STATEMENT,
}

/**
 * Confirmation state.
 *
 * Auto-captured records land as [PENDING] and never silently enter the user's
 * totals. A parser that misreads an amount is a certainty over a long enough
 * period, and a wrong number the user never agreed to is worse than a missing
 * one: it is a false report they will act on.
 */
enum class Status { PENDING, CONFIRMED }

/** A category in the spending taxonomy. Arabic label is the primary one. */
data class Category(
    val id: String,
    val labelAr: String,
    val labelEn: String,
    val parentId: String? = null,
)

/** A funding source: a bank account, a card, or a digital wallet. */
data class Account(
    val id: String,
    val labelAr: String,
    val institution: String,
    /** Last four digits of the card or account, when known. The only fragment ever stored. */
    val last4: String? = null,
    val kind: Kind = Kind.BANK_ACCOUNT,
) {
    enum class Kind { BANK_ACCOUNT, CARD, WALLET, CASH }

    init {
        require(last4 == null || last4.length == 4) { "last4 must be exactly 4 digits, was: $last4" }
    }
}

/**
 * A recorded movement of money.
 *
 * [amount] is always non-negative; [direction] carries the sign. Storing signed
 * amounts invites a whole class of bug where a sum over a mixed list silently
 * cancels expenses against income.
 */
data class Transaction(
    val id: String,
    val amount: Money,
    val direction: Direction,
    val type: TransactionType,
    val occurredAt: Instant,
    val accountId: String?,
    val categoryId: String?,
    /** Merchant exactly as captured, for display and for the user to recognise. */
    val merchantRaw: String?,
    /** Folded merchant key, for matching and deduplication. Never displayed. */
    val merchantKey: String?,
    val note: String?,
    val source: Source,
    val status: Status,
    val fingerprint: String,
    /**
     * The original message body or statement row.
     *
     * Kept so that a parsing bug found in six months can be replayed against real
     * historical input instead of guessed at. It is also the only way to show the
     * user "here is exactly what the bank said" when a number looks wrong.
     */
    val rawText: String?,
    val currency: String = "SAR",
) {
    init {
        require(!amount.isNegative) { "amount must be non-negative; use direction for the sign" }
    }

    /** The Riyadh calendar day this transaction belongs to. */
    val calendarDay: LocalDate get() = RiyadhTime.localDate(occurredAt)

    /** Signed value, for summing a mixed list. */
    val signedAmount: Money get() = if (direction == Direction.DEBIT) -amount else amount
}

/**
 * A transaction that has been parsed but not yet persisted.
 *
 * Separate from [Transaction] because a draft legitimately lacks an id, a
 * resolved account and a category, and modelling those as nullable on the
 * persisted type would push "can this be null here?" onto every call site.
 */
data class TransactionDraft(
    val amount: Money,
    val direction: Direction,
    val type: TransactionType,
    val occurredAt: Instant,
    val merchantRaw: String? = null,
    val accountLast4: String? = null,
    val note: String? = null,
    val currency: String = "SAR",
    val rawText: String? = null,
)
