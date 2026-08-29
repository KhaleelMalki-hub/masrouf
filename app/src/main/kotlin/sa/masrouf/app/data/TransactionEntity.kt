package sa.masrouf.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import sa.masrouf.core.model.Direction
import sa.masrouf.core.model.Source
import sa.masrouf.core.model.Status
import sa.masrouf.core.model.Transaction
import sa.masrouf.core.model.TransactionType
import sa.masrouf.core.money.Money
import java.time.Instant

/**
 * The stored form of a [Transaction].
 *
 * A separate type from the core model on purpose. [Transaction] is a value the
 * rest of the app reasons about - it holds a [Money], an [Instant] and enums, and
 * refuses a negative amount in its constructor. Annotating that class with Room
 * would drag a database into `:core`, which is the one module that must stay
 * buildable without Android at all.
 *
 * The mapping is deliberately dumb: every field is stored in a form that survives
 * a round trip exactly, and nothing is derived on the way in or out.
 */
@Entity(
    tableName = "transactions",
    indices = [
        // Enforced, not advisory: the fingerprint is the app's answer to "have I
        // stored this record already", and re-running capture over the same
        // notification history must not be able to write it twice.
        Index(value = ["fingerprint"], unique = true),
        Index(value = ["occurred_at_millis"]),
    ],
)
data class TransactionEntity(
    @PrimaryKey val id: String,

    /**
     * Integer halalas, never a floating point riyal value.
     *
     * SQLite has no decimal type: a REAL column would reintroduce exactly the
     * representation error `Money` exists to prevent, at the storage layer where it
     * would be hardest to notice.
     */
    @ColumnInfo(name = "amount_halalas") val amountHalalas: Long,

    val direction: String,
    val type: String,

    /**
     * Epoch milliseconds UTC. The calendar day is never stored: it is derived
     * through `RiyadhTime` on read, so there is only one place that decides which
     * day an instant belongs to.
     */
    @ColumnInfo(name = "occurred_at_millis") val occurredAtMillis: Long,

    @ColumnInfo(name = "account_id") val accountId: String?,

    /**
     * Last four digits of the card the money moved on, when the message revealed
     * them. The only fragment of an account number ever stored.
     *
     * Not on the core [Transaction] model, which has no field for it, but kept here
     * because [sa.masrouf.core.dedup.DuplicateDetector] treats a missing card as
     * compatible with any other. Dropping it therefore does not make matching
     * cautious - it makes it credulous, and two different cards charged the same
     * amount in the same minute would merge into one record.
     */
    @ColumnInfo(name = "account_last4") val accountLast4: String?,
    @ColumnInfo(name = "category_id") val categoryId: String?,

    /**
     * Who decided [categoryId]. See [CategorySource]; null when nothing has.
     *
     * Not on the core [Transaction], which is what the screens reason about and
     * has no business knowing how a category got there. It is storage's answer to
     * one question: may a re-file overwrite this row.
     */
    @ColumnInfo(name = "category_source") val categorySource: String?,
    @ColumnInfo(name = "merchant_raw") val merchantRaw: String?,
    @ColumnInfo(name = "merchant_key") val merchantKey: String?,
    val note: String?,
    val source: String,
    val status: String,
    val fingerprint: String,

    /**
     * The original message body, when there was one.
     *
     * Null for a record the user typed, and null for anything `MessageGate`
     * flagged as carrying a credential - an OTP body is never written here.
     */
    @ColumnInfo(name = "raw_text") val rawText: String?,

    val currency: String,

    /** Which bank's parser read the message. See [sa.masrouf.core.model.Transaction.bankId]. */
    @ColumnInfo(name = "bank_id") val bankId: String? = null,
)

fun Transaction.toEntity(
    accountLast4: String? = null,
    categorySource: CategorySource? = null,
): TransactionEntity = TransactionEntity(
    id = id,
    amountHalalas = amount.halalas,
    direction = direction.name,
    type = type.name,
    occurredAtMillis = occurredAt.toEpochMilli(),
    accountId = accountId,
    accountLast4 = accountLast4,
    categoryId = categoryId,
    categorySource = categoryId?.let { (categorySource ?: CategorySource.AUTOMATIC).name },
    merchantRaw = merchantRaw,
    merchantKey = merchantKey,
    note = note,
    source = source.name,
    status = status.name,
    fingerprint = fingerprint,
    rawText = rawText,
    currency = currency,
    bankId = bankId,
)

/**
 * @throws IllegalArgumentException if a stored enum name is not one this build
 *   knows. That is a downgrade or a corrupted row, and continuing with a
 *   substituted default would file the transaction under the wrong meaning -
 *   an `UNKNOWN` fallback for [TransactionType] silently drops it out of the
 *   monthly total, which is the failure hardest to see.
 */
fun TransactionEntity.toModel(): Transaction = Transaction(
    id = id,
    amount = Money.ofHalalas(amountHalalas),
    direction = enumValueOf<Direction>(direction),
    type = enumValueOf<TransactionType>(type),
    occurredAt = Instant.ofEpochMilli(occurredAtMillis),
    accountId = accountId,
    categoryId = categoryId,
    merchantRaw = merchantRaw,
    merchantKey = merchantKey,
    note = note,
    source = enumValueOf<Source>(source),
    status = enumValueOf<Status>(status),
    fingerprint = fingerprint,
    rawText = rawText,
    currency = currency,
    // Read back onto the model, unlike on the way in: the card and the bank are
    // recorded by the capture path, which passes them alongside, and the screen
    // needs both to say which card a purchase was on.
    accountLast4 = accountLast4,
    bankId = bankId,
)
