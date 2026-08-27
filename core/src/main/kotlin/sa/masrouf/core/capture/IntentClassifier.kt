package sa.masrouf.core.capture

import sa.masrouf.core.model.Direction
import sa.masrouf.core.model.TransactionType
import sa.masrouf.core.text.ArabicText

/**
 * Works out what a bank message is *about* - purchase, transfer, refund - from its
 * wording.
 *
 * Deliberately shared across all four senders rather than duplicated per bank.
 * AlRajhi, SNB, D360 and barq differ in punctuation, spacing and field order, but
 * they draw on the same Saudi banking vocabulary: `شراء`, `حوالة صادرة`,
 * `حوالة واردة`, `سداد`, `استرداد`, `ايداع صراف`. Four near-identical keyword
 * tables would drift apart the first time one bank changed a template.
 *
 * ## Why token sets rather than phrases
 *
 * Banks insert words into the middle of their own phrases:
 *
 *     حوالة صادرة محلية      (barq)
 *     حوالة محلية صادرة      (D360)
 *     حوالة صادرة داخلية     (SNB)
 *
 * A contiguous-substring match on `"حوالة صادرة"` finds only the first. Requiring
 * that all of `{حوالة, صادرة}` appear somewhere finds all three, and survives the
 * next bank that invents a fourth ordering.
 *
 * Matching runs on [ArabicText.foldForMatching] output, so spelling variants
 * (`إيداع`/`ايداع`, `حوالة`/`حواله`) collapse together before comparison.
 */
object IntentClassifier {

    data class Intent(val type: TransactionType, val direction: Direction)

    private data class Rule(
        val type: TransactionType,
        val direction: Direction,
        /** All of these must be present. Stored folded, matched against folded text. */
        val requiredTokens: List<String>,
    ) {
        val folded: List<String> = requiredTokens.map { ArabicText.foldForMatching(it) }
    }

    /**
     * Ordered most specific first. The first rule whose tokens are all present wins.
     *
     * Order is load-bearing: `تحويل بين حساباتك` must be tested before anything that
     * merely looks for a transfer, and `استرداد` before `شراء`, because a card refund
     * message also mentions the card.
     */
    private val RULES = listOf(
        // Between the user's own accounts. Must come first - it is a transfer by
        // every other rule's standard, but it is not spending.
        Rule(TransactionType.OWN_TRANSFER, Direction.DEBIT, listOf("تحويل", "بين", "حساباتك")),

        Rule(TransactionType.REFUND, Direction.CREDIT, listOf("استرداد")),
        Rule(TransactionType.REFUND, Direction.CREDIT, listOf("REFUND")),

        // "بطاقة فيزا:سداد" - paying off a credit card.
        Rule(TransactionType.BILL_PAYMENT, Direction.DEBIT, listOf("سداد")),

        Rule(TransactionType.ATM_DEPOSIT, Direction.CREDIT, listOf("ايداع", "صراف")),
        Rule(TransactionType.ATM_WITHDRAWAL, Direction.DEBIT, listOf("سحب", "صراف")),
        Rule(TransactionType.ATM_WITHDRAWAL, Direction.DEBIT, listOf("سحب", "نقدي")),

        Rule(TransactionType.SALARY, Direction.CREDIT, listOf("راتب")),

        Rule(TransactionType.TRANSFER_IN, Direction.CREDIT, listOf("حوالة", "واردة")),
        Rule(TransactionType.TRANSFER_OUT, Direction.DEBIT, listOf("حوالة", "صادرة")),

        // barq sends wallet top-ups in English.
        Rule(TransactionType.TRANSFER_IN, Direction.CREDIT, listOf("MONEY", "ADDED")),

        Rule(TransactionType.PURCHASE, Direction.DEBIT, listOf("شراء")),
        Rule(TransactionType.PURCHASE, Direction.DEBIT, listOf("PURCHASE")),
    )

    /**
     * @return the intent, or null when the wording matches no known rule. Null is a
     *   normal outcome for service notices and marketing, and callers must not
     *   invent [TransactionType.UNKNOWN] transactions from it.
     */
    fun classify(text: String): Intent? {
        val folded = ArabicText.foldForMatching(text)
        val rule = RULES.firstOrNull { candidate ->
            candidate.folded.all { token -> folded.contains(token) }
        } ?: return null
        return Intent(rule.type, rule.direction)
    }
}
