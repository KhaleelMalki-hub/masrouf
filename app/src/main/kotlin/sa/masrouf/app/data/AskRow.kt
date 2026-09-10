package sa.masrouf.app.data

import sa.masrouf.core.model.Direction
import sa.masrouf.core.model.Source
import sa.masrouf.core.model.Status
import sa.masrouf.core.model.Transaction
import sa.masrouf.core.model.TransactionType
import sa.masrouf.core.money.Money
import java.time.Instant

/**
 * A row as the ask screen needs it: everything except the message body.
 *
 * `raw_text` holds twelve years of bank messages and is most of the database by
 * size. A question about a year selects a few thousand rows, and loading their
 * bodies to add up a column of integers would cost far more than the answer. The
 * screen never shows a body, so this never carries one.
 *
 * [toModel] fills `rawText` with null and `note` with null rather than pretending
 * to know them - the alternative would be a second Transaction type, and one model
 * that every screen already understands is worth two absent fields.
 */
data class AskRow(
    val id: String,
    val amountHalalas: Long,
    val direction: String,
    val type: String,
    val occurredAtMillis: Long,
    val categoryId: String?,
    val merchantRaw: String?,
    val merchantKey: String?,
    val status: String,
    val accountLast4: String?,
    val bankId: String?,
    val currency: String,
)

fun AskRow.toModel(): Transaction = Transaction(
    id = id,
    amount = Money.ofHalalas(amountHalalas),
    direction = enumValueOf<Direction>(direction),
    type = enumValueOf<TransactionType>(type),
    occurredAt = Instant.ofEpochMilli(occurredAtMillis),
    accountId = null,
    categoryId = categoryId,
    merchantRaw = merchantRaw,
    merchantKey = merchantKey,
    note = null,
    // The ask screen reads rows, never writes them, and nothing it does depends on
    // where a row came from. SMS is the honest majority rather than a claim.
    source = Source.SMS,
    status = enumValueOf<Status>(status),
    fingerprint = id,
    rawText = null,
    currency = currency,
    accountLast4 = accountLast4,
    bankId = bankId,
)
