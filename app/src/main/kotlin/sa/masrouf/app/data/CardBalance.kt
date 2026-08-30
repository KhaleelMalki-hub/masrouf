package sa.masrouf.app.data

/**
 * A card, and the last thing its messages said was left - if they ever said.
 *
 * [kind] is the name of a `BalanceReader.Kind`: a current account's balance, or
 * what remains of a credit card's limit. They are shown under different words,
 * because the second is not money the user has. Both are null for a card whose
 * bank never puts a figure in its messages; the card is still real.
 */
data class CardBalance(
    val last4: String,
    val halalas: Long?,
    val kind: String?,
    val atMillis: Long,
    val bankId: String?,
)
