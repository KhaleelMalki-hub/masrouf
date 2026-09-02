package sa.masrouf.core.model

import sa.masrouf.core.text.ArabicText

/**
 * Whether a card is a mada card or a credit card.
 *
 * The owner asked for this because it decides what he can pay with: a few shops
 * still take mada and nothing else. It is also the split he wants a month read
 * against - what went on credit, what came straight out of an account.
 *
 * Read off the banks' own wording, never inferred from a card number. Absent
 * evidence the answer is null, and nothing is shown: a card labelled with the
 * wrong kind is a confident wrong answer he has no reason to doubt.
 */
enum class CardKind { MADA, CREDIT }

/**
 * What a message says about the card it names.
 *
 * ## Why the network is not evidence
 *
 * "فيزا" and "ماستر" say which rails the money travelled on, not whether the card
 * borrows. The owner's 7536 is a MasterCard drawn on his SNB account - a debit
 * card by every meaning that matters here - and `CardIssuers` already records that
 * lesson for the tiles. So the only credit evidence is the word for credit itself.
 *
 * Matched against [ArabicText.foldForMatching], which collapses both spellings of
 * the hamza: ائتمانية and إئتمانية both fold to the same string, and a keyword
 * written in one spelling would otherwise miss half the corpus.
 */
object CardKinds {

    // "ائتمانية", "إئتمانية", "الائتمانية" - all fold to a string containing this.
    private val CREDIT_WORDS = listOf("ايتمان", "CREDIT").map(ArabicText::foldForMatching)

    // "مدى" folds to "مدي"; the Latin spelling arrives from the English templates.
    private val MADA_WORDS = listOf("مدى", "MADA").map(ArabicText::foldForMatching)

    /** What one message body says, or null when it says neither. */
    fun of(rawText: String?): CardKind? {
        val folded = rawText?.let(ArabicText::foldForMatching) ?: return null
        val credit = CREDIT_WORDS.any(folded::contains)
        val mada = MADA_WORDS.any(folded::contains)
        return when {
            credit && !mada -> CardKind.CREDIT
            mada && !credit -> CardKind.MADA
            // Both words, or neither. A settlement message names the credit card
            // being paid and the mada card paying it, so one body can carry both -
            // and a body that carries both says nothing about either.
            else -> null
        }
    }

    /**
     * The verdict for a card, from every message that named it.
     *
     * A plain majority, because a card's own templates change over the years and a
     * single stray body should not overturn hundreds. A tie is null: the app says
     * nothing rather than choosing.
     */
    fun verdict(bodies: List<String?>): CardKind? {
        var credit = 0
        var mada = 0
        for (body in bodies) {
            when (of(body)) {
                CardKind.CREDIT -> credit++
                CardKind.MADA -> mada++
                null -> Unit
            }
        }
        return when {
            credit > mada -> CardKind.CREDIT
            mada > credit -> CardKind.MADA
            else -> null
        }
    }
}
