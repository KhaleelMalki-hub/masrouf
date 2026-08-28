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

    private class Rule(
        val type: TransactionType,
        val direction: Direction,
        /** All of these must be present. Stored folded, matched against folded text. */
        requiredTokens: List<String>,
    ) {
        /**
         * Arabic tokens are matched as substrings, because they are stems: "حوال"
         * has to find حوالة, حوالات and الحوالة alike, and Arabic attaches its
         * prefixes directly to the word.
         *
         * Latin tokens are matched as whole words. Substring matching there is
         * actively wrong: "IN" occurs inside "OUTGOING", so a `Cash in` rule
         * matched as a substring would claim every outgoing transfer.
         */
        private val matchers: List<(String) -> Boolean> = requiredTokens.map { token ->
            val folded = ArabicText.foldForMatching(token)
            if (folded.all { it.code < 128 }) {
                val word = Regex("(?<![A-Z0-9])${Regex.escape(folded)}(?![A-Z0-9])")
                ({ text: String -> word.containsMatchIn(text) })
            } else {
                ({ text: String -> text.contains(folded) })
            }
        }

        fun matches(foldedText: String): Boolean = matchers.all { it(foldedText) }
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

        // English wording for the same thing, and it has to precede the generic
        // transfer rules below: every one of these messages also says "Transfer".
        Rule(TransactionType.OWN_TRANSFER, Direction.DEBIT, listOf("TRANSFER", "BETWEEN", "ACCOUNTS")),
        Rule(TransactionType.OWN_TRANSFER, Direction.DEBIT, listOf("INTERNAL", "TRANSFER")),

        // Paying off your own credit card. Not spending: the purchases that built
        // the balance were already counted when they happened, and counting the
        // payment too charges the same riyals twice.
        Rule(TransactionType.OWN_TRANSFER, Direction.DEBIT, listOf("CREDIT", "CARD", "PAYMENT")),

        Rule(TransactionType.REFUND, Direction.CREDIT, listOf("استرداد")),
        Rule(TransactionType.REFUND, Direction.CREDIT, listOf("REFUND")),
        // A reversal - the bank undoing its own entry. Money comes back, so it is
        // a credit, and it must be tested before شراء because the message repeats
        // the original purchase's wording.
        Rule(TransactionType.REFUND, Direction.CREDIT, listOf("عكس", "عملي")),
        Rule(TransactionType.REFUND, Direction.CREDIT, listOf("استرجاع")),

        // "بطاقة فيزا:سداد" - paying off a credit card.
        Rule(TransactionType.BILL_PAYMENT, Direction.DEBIT, listOf("سداد")),

        Rule(TransactionType.ATM_DEPOSIT, Direction.CREDIT, listOf("ايداع", "صراف")),
        // Monthly profit paid into a savings account. Income, not spending.
        Rule(TransactionType.TRANSFER_IN, Direction.CREDIT, listOf("ايداع", "ارباح")),
        Rule(TransactionType.ATM_WITHDRAWAL, Direction.DEBIT, listOf("سحب", "صراف")),
        Rule(TransactionType.ATM_WITHDRAWAL, Direction.DEBIT, listOf("سحب", "نقدي")),

        Rule(TransactionType.SALARY, Direction.CREDIT, listOf("راتب")),

        // Wallet top-ups funded from the user's own card. Not spending: the same
        // riyals are reported again by the wallet as they are actually spent.
        Rule(TransactionType.OWN_TRANSFER, Direction.DEBIT, listOf("CASH", "IN")),

        // Transfer wording, as stems. Banks insert words into the middle of their
        // own phrases ("حوالة صادرة محلية", "حوالة محلية صادرة", "حوالات فورية
        // واردة"), and statements use a different noun than messages do - حوالة in
        // one, تحويل in the other - so both roots are listed.
        Rule(TransactionType.TRANSFER_IN, Direction.CREDIT, listOf("حوال", "وارد")),
        Rule(TransactionType.TRANSFER_IN, Direction.CREDIT, listOf("تحويل", "وارد")),
        Rule(TransactionType.TRANSFER_OUT, Direction.DEBIT, listOf("حوال", "صادر")),
        Rule(TransactionType.TRANSFER_OUT, Direction.DEBIT, listOf("تحويل", "صادر")),

        // barq writes in English.
        Rule(TransactionType.BILL_PAYMENT, Direction.DEBIT, listOf("BILL", "PAYMENT")),
        Rule(TransactionType.TRANSFER_OUT, Direction.DEBIT, listOf("LOCAL", "TRANSFER")),

        Rule(TransactionType.TRANSFER_IN, Direction.CREDIT, listOf("MONEY", "ADDED")),
        Rule(TransactionType.TRANSFER_IN, Direction.CREDIT, listOf("INCOMING", "TRANSFER")),
        Rule(TransactionType.TRANSFER_OUT, Direction.DEBIT, listOf("OUTGOING", "TRANSFER")),
        Rule(TransactionType.REFUND, Direction.CREDIT, listOf("CASH", "REWARD")),

        Rule(TransactionType.PURCHASE, Direction.DEBIT, listOf("شراء")),
        Rule(TransactionType.PURCHASE, Direction.DEBIT, listOf("PURCHASE")),
        // AlRajhi's English point-of-sale template says only "PoS". Matched as a
        // whole word, so it cannot fire inside another word.
        Rule(TransactionType.PURCHASE, Direction.DEBIT, listOf("POS")),
        Rule(TransactionType.PURCHASE, Direction.DEBIT, listOf("CARD", "TRANSACTION")),

        // Last resort: the wording says a transfer happened but not which way
        // ("عملية تحويل داخلية"). The direction here is a placeholder - a statement
        // corrects it from its debit/credit column, which is unambiguous.
        Rule(TransactionType.TRANSFER_OUT, Direction.DEBIT, listOf("حوال")),
        Rule(TransactionType.TRANSFER_OUT, Direction.DEBIT, listOf("تحويل")),
    )

    /**
     * @return the intent, or null when the wording matches no known rule. Null is a
     *   normal outcome for service notices and marketing, and callers must not
     *   invent [TransactionType.UNKNOWN] transactions from it.
     */
    fun classify(text: String): Intent? {
        val folded = ArabicText.foldForMatching(text)
        val rule = RULES.firstOrNull { it.matches(folded) } ?: return null
        return Intent(rule.type, rule.direction)
    }
}
