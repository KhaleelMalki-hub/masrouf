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

        // Paying off a credit card, in Arabic. Must precede the bare سداد rule
        // below, which would otherwise call it a bill payment and count it as
        // spending - charging the same riyals twice, once when each purchase on
        // the card happened and again when the balance was settled. The English
        // wording of the same event is handled above.
        Rule(TransactionType.OWN_TRANSFER, Direction.DEBIT, listOf("بطاق", "ائتمان", "سداد")),

        // The same event, after AlRajhi changed the wording in April 2026: the card
        // is now named by its network rather than called ائتمانية, so the rule above
        // stopped firing and 43 settlements worth 180,954 riyals were counted as
        // bills. The bank renames the template; the money still never left.
        Rule(TransactionType.OWN_TRANSFER, Direction.DEBIT, listOf("بطاق", "فيزا", "سداد")),

        // "Bill Payment | Card:1335 ;Visa | Amount:SAR 442.75" - the English
        // settlement template. It must precede the BILL+PAYMENT rule further down,
        // which is the genuine SADAD bill and has no card field.
        Rule(TransactionType.OWN_TRANSFER, Direction.DEBIT, listOf("BILL", "PAYMENT", "CARD", "VISA")),

        // SADAD biller codes that are the user's own credit cards and wallets, not
        // a utility. The message is a bill payment by every word in it - only the
        // three-digit biller says where the money went. Confirmed by the user:
        // 255 is AlRajhi's cards, 016 AlAhli's cards and finance, 207 STC Pay.
        //
        // Folding turns every separator the banks use - ":", a space, or an
        // embedded direction mark - into the single space matched here, so one
        // spelling covers all three templates.
        Rule(TransactionType.OWN_TRANSFER, Direction.DEBIT, listOf("مفوتر 255")),
        Rule(TransactionType.OWN_TRANSFER, Direction.DEBIT, listOf("مفوتر 016")),
        Rule(TransactionType.OWN_TRANSFER, Direction.DEBIT, listOf("مفوتر 207")),

        Rule(TransactionType.BILL_PAYMENT, Direction.DEBIT, listOf("سداد")),

        // ---- AlAhli's older template family -------------------------------
        //
        // NCB/AlAhli wrote a different vocabulary from the SNB templates already
        // handled above, and 87% of a real 3,361-message corpus matched no rule at
        // all. These come from that corpus, most specific first.

        // "مدفوعات بطاقة ائتمانية" - settling the credit card. Not spending: the
        // purchases that built the balance were counted when they happened.
        Rule(TransactionType.OWN_TRANSFER, Direction.DEBIT, listOf("مدفوعات", "بطاق", "ائتمان")),

        // "إيداع في بطاقة 4007*" and "تمت عملية إيداع في بطاقاتك الائتمانية" -
        // money going ONTO a card. Credit, and never spending.
        Rule(TransactionType.TRANSFER_IN, Direction.CREDIT, listOf("ايداع", "بطاق")),

        // "سحب مبلغ 299.25 SAR بطاقة 9552* من SHBABIK RESTAURANT" - a card
        // purchase, despite the word سحب. The card is what distinguishes it from
        // the account withdrawal below, and it must be tested first.
        Rule(TransactionType.PURCHASE, Direction.DEBIT, listOf("سحب", "بطاق")),

        Rule(TransactionType.ATM_DEPOSIT, Direction.CREDIT, listOf("ايداع", "صراف")),
        // Monthly profit paid into a savings account. Income, not spending.
        Rule(TransactionType.TRANSFER_IN, Direction.CREDIT, listOf("ايداع", "ارباح")),
        Rule(TransactionType.ATM_WITHDRAWAL, Direction.DEBIT, listOf("سحب", "صراف")),
        Rule(TransactionType.ATM_WITHDRAWAL, Direction.DEBIT, listOf("سحب", "نقدي")),

        // "سحب من حساب104*010 مبلغSAR1500 ... الرصيد المتاح" - money leaving the
        // account with no card and no merchant named. Classified as a withdrawal
        // because that is what سحب says and there is nothing else to go on; it is
        // therefore not counted as spending, which errs toward a total that is too
        // low rather than one that invents purchases. The user can refile it.
        Rule(TransactionType.ATM_WITHDRAWAL, Direction.DEBIT, listOf("سحب", "حساب")),

        Rule(TransactionType.SALARY, Direction.CREDIT, listOf("راتب")),
        // The plural. "ايداع رواتب / مبلغ SAR 19491 / حساب0104*" is what the same
        // bank sends now, and رواتب does not contain راتب - the waw sits between
        // the letters. Sixty-odd salaries filed as incoming transfers, and the app
        // concluded the salary had stopped in 2021.
        Rule(TransactionType.SALARY, Direction.CREDIT, listOf("رواتب")),

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

        // AlAhli also writes the account inline, with no word for it at all:
        // "سحب من 010*104مبلغSAR1500". Last-resort forms, reached only after every
        // rule above has declined - in particular after سحب+بطاقة, so a card
        // purchase is never demoted to a withdrawal by these.
        //
        // Safe as a bare stem only because the gate now refuses the bank's own
        // marketing: "السحب الأسبوعي" is a prize draw and would otherwise land here.
        Rule(TransactionType.ATM_WITHDRAWAL, Direction.DEBIT, listOf("سحب")),
        Rule(TransactionType.TRANSFER_IN, Direction.CREDIT, listOf("ايداع")),

        // Last resort: the wording says a transfer happened but not which way
        // ("عملية تحويل داخلية"). The direction here is a placeholder - a statement
        // corrects it from its debit/credit column, which is unambiguous.
        Rule(TransactionType.TRANSFER_OUT, Direction.DEBIT, listOf("حوال")),
        Rule(TransactionType.TRANSFER_OUT, Direction.DEBIT, listOf("تحويل")),
    )

    /**
     * The owner's own name, reusing the rules' matcher so that Latin whole-word and
     * Arabic stem matching behave here exactly as they do above. One list, one set
     * of semantics; a second hand-rolled matcher would drift from the first.
     */
    private val OWNER_RULES = AccountOwner.NAME_TOKENS.map {
        Rule(TransactionType.OWN_TRANSFER, Direction.DEBIT, it)
    }

    /**
     * @return the intent, or null when the wording matches no known rule. Null is a
     *   normal outcome for service notices and marketing, and callers must not
     *   invent [TransactionType.UNKNOWN] transactions from it.
     */
    fun classify(text: String): Intent? {
        val folded = ArabicText.foldForMatching(text)
        val rule = RULES.firstOrNull { it.matches(folded) } ?: return null
        // An outgoing transfer addressed to the owner is money moving between their
        // own accounts. Applied after the rules rather than as more of them: the
        // banks write at least six different outgoing-transfer templates, and
        // pairing every one with every spelling of the name would be a cross
        // product that has to be extended twice whenever either side gains a form.
        //
        // Only ever demotes TRANSFER_OUT, and only to a type with the same
        // direction, so no other verdict can be changed by a name.
        if (rule.type == TransactionType.TRANSFER_OUT && namesOwner(folded)) {
            return Intent(TransactionType.OWN_TRANSFER, Direction.DEBIT)
        }
        return Intent(rule.type, rule.direction)
    }

    /** Whether folded text names the account holder. See [AccountOwner]. */
    private fun namesOwner(foldedText: String): Boolean =
        OWNER_RULES.any { it.matches(foldedText) }
}
