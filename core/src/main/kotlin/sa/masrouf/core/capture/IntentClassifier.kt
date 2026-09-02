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
        // meem's noun for the same movement: "حوالة واردة: بين حساباتك" and
        // "حوالة صادرة: بين حساباتك". Before the واردة/صادرة rules, which would
        // read the pair as income and spending.
        Rule(TransactionType.OWN_TRANSFER, Direction.DEBIT, listOf("حوال", "بين", "حساباتك")),
        // Vision Bank: "اكتمل تحويل الأموال / From: ***6000 / To: ***5001". Read off
        // the templates, not confirmed by the owner: 5001 and 4002 are the savings
        // accounts the same sender announced creating ("لقد تم إنشاء حساب التوفير
        // ***5001"), and a transfer to anyone else arrives as "حوالة صادرة محلية".
        Rule(TransactionType.OWN_TRANSFER, Direction.DEBIT, listOf("اكتمل تحويل الاموال")),

        // English wording for the same thing, and it has to precede the generic
        // transfer rules below: every one of these messages also says "Transfer".
        Rule(TransactionType.OWN_TRANSFER, Direction.DEBIT, listOf("TRANSFER", "BETWEEN", "ACCOUNTS")),
        Rule(TransactionType.OWN_TRANSFER, Direction.DEBIT, listOf("INTERNAL", "TRANSFER")),

        // Paying off your own credit card. Not spending: the purchases that built
        // the balance were already counted when they happened, and counting the
        // payment too charges the same riyals twice.
        Rule(TransactionType.OWN_TRANSFER, Direction.DEBIT, listOf("CREDIT", "CARD", "PAYMENT")),

        // "مشكور استلمنا مبلغ 450.07 SAR لبطاقتك الإئتمانية رقم" - meem's thanks
        // for a card payment, without the word سداد the rule below relies on.
        Rule(TransactionType.OWN_TRANSFER, Direction.DEBIT, listOf("استلمنا", "بطاق", "ائتمان")),

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

        // The funding leg of the same settlement, seen from the card that pays.
        //
        // The owner settles one credit card from another, in both directions. The
        // card being paid says سداد and is caught above; the card being charged
        // says "شراء إنترنت ... لدى: SADAD payment" and looked like an ordinary
        // online purchase, so one movement was counted once as a purchase of
        // 15,000 riyals and once - correctly - as nothing.
        //
        // The message never names where the money went: no biller code, no
        // beneficiary. What it does say is that the card charged is a credit card,
        // and the owner has confirmed he never pays a utility that way. A credit
        // card paying SADAD is settling another card.
        //
        // Deliberately not keyed on the card number, which changes when the card is
        // reissued, nor on one bank's wording, which changes when the bank feels
        // like it. Twenty-seven genuine utility bills paid by card between 2017 and
        // 2019 are left alone because their template never calls the card
        // ائتمانية - it says الصرف المتبقي instead.
        Rule(TransactionType.OWN_TRANSFER, Direction.DEBIT, listOf("SADAD", "ائتمان")),

        // "نشكر لك سداد مبلغ200.00 لحساب البطاقة رقم2650" - AlJazira thanking him
        // for paying his own card. Before the bare سداد rule below, which would
        // count the same riyals a second time as a bill.
        Rule(TransactionType.OWN_TRANSFER, Direction.DEBIT, listOf("سداد", "لحساب البطاق")),

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

        // Cash out, and it must be tested before the card rule below.
        //
        // "سحب نقدي بالريال - صراف الأهلي | بطاقة مدى *2907 | موقع K.FAHAD RES
        // COMPLEX" is a machine withdrawal that names the card it was made with, so
        // the card rule claimed it and 107 withdrawals worth 193,452 riyals were
        // counted as purchases. An ATM says نقدي or صراف; a shop says neither, which
        // is what keeps the 3,470 real card purchases below out of this rule.
        Rule(TransactionType.ATM_WITHDRAWAL, Direction.DEBIT, listOf("سحب", "صراف")),
        Rule(TransactionType.ATM_WITHDRAWAL, Direction.DEBIT, listOf("سحب", "نقدي")),

        // "سحب مبلغ 299.25 SAR بطاقة 9552* من SHBABIK RESTAURANT" - a card
        // purchase, despite the word سحب. The card is what distinguishes it from
        // the bare account withdrawal further down.
        Rule(TransactionType.PURCHASE, Direction.DEBIT, listOf("سحب", "بطاق")),

        Rule(TransactionType.ATM_DEPOSIT, Direction.CREDIT, listOf("ايداع", "صراف")),
        // Monthly profit paid into a savings account. Income, not spending.
        Rule(TransactionType.TRANSFER_IN, Direction.CREDIT, listOf("ايداع", "ارباح")),

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

        // ---- The brokerage ----------------------------------------------------
        //
        // "تم تحويل مبلغ 826.56 ر.س من الحساب الاستثماري ... الى الحساب الجاري" and
        // the reverse. His own money moving between his own two accounts, 277 times
        // - and the generic transfer rules below would have called half of it
        // spending. Written as ordered pairs because which account is the SOURCE is
        // the entire question, and a token set has no order: each rule names the
        // account the money LEFT.
        Rule(TransactionType.OWN_TRANSFER, Direction.DEBIT, listOf("من الحساب الجاري", "الاستثماري")),
        Rule(TransactionType.OWN_TRANSFER, Direction.CREDIT, listOf("من الحساب الاستثماري", "الجاري")),

        // ---- The wallet's own vocabulary --------------------------------------
        //
        // STC Pay wrote none of the words above. 4,446 of its messages sat in the
        // inbox with no parser for the sender at all, so the wallet's seven years -
        // 1,845 purchases and 52 international transfers - were never in the app,
        // while the 670 top-ups that funded them were counted as spending from the
        // bank's side.

        // "تغذية محفظة عبر ماستركارد" and "إضافة أموال لحسابك | عبر:*5763": his own
        // money arriving in his own wallet. Never spending and never income, and
        // both directions of the same movement say so.
        Rule(TransactionType.OWN_TRANSFER, Direction.CREDIT, listOf("تغذي", "محفظ")),
        // "اضافة اموال عن طريق نقاط مكافأة" - reward points paid out into the
        // wallet. Money that came back, not money he moved, so before the top-up
        // rule below, whose tokens this message also carries.
        Rule(TransactionType.REFUND, Direction.CREDIT, listOf("نقاط مكافا")),
        Rule(TransactionType.OWN_TRANSFER, Direction.CREDIT, listOf("اضاف", "اموال")),

        // "خصم من المحفظة لـ (شحن خطوط الاتصال)" - phone credit bought from the
        // wallet. Nothing else in it says what happened.
        Rule(TransactionType.BILL_PAYMENT, Direction.DEBIT, listOf("خصم من المحفظ")),

        // Money arriving, in the words meem and urpay use for it: "تم إستلام حوالة
        // داخلية", "لقد استلمت حواله محلية", "جتك حواله داخليه", "وصلتك حوالة".
        // None says وارد, so the bare حوال rule at the end read every one as
        // money leaving.
        //
        // Phrases, not token pairs. {استلام, حوال} as two tokens claimed 81 stored
        // OUTGOING transfers: every Western Union body says "طريقة الاستلام" or
        // "حساب المستلم: استلام عبر ويسترين يونيون", and barq's "تم استلام حوالتك
        // الدولية" is the recipient's receipt of money HE sent. The verb has to sit
        // directly against the noun, and the noun has to be حوالة itself - folded
        // to حواله - not حوالتك.
        Rule(TransactionType.TRANSFER_IN, Direction.CREDIT, listOf("استلام حواله")),
        Rule(TransactionType.TRANSFER_IN, Direction.CREDIT, listOf("استلمت حواله")),
        Rule(TransactionType.TRANSFER_IN, Direction.CREDIT, listOf("جتك حواله")),
        Rule(TransactionType.TRANSFER_IN, Direction.CREDIT, listOf("وصلتك حواله")),

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
        // "Local Credit Transfer" (Vision Bank) and "Credit transfer: Local"
        // (meem): a credit TO the account. Before LOCAL+TRANSFER, which is the
        // outgoing kind and would claim these.
        Rule(TransactionType.TRANSFER_IN, Direction.CREDIT, listOf("CREDIT", "TRANSFER")),
        Rule(TransactionType.TRANSFER_OUT, Direction.DEBIT, listOf("LOCAL", "TRANSFER")),

        Rule(TransactionType.TRANSFER_IN, Direction.CREDIT, listOf("MONEY", "ADDED")),
        Rule(TransactionType.TRANSFER_IN, Direction.CREDIT, listOf("INCOMING", "TRANSFER")),
        Rule(TransactionType.TRANSFER_OUT, Direction.DEBIT, listOf("OUTGOING", "TRANSFER")),
        Rule(TransactionType.REFUND, Direction.CREDIT, listOf("CASH", "REWARD")),

        Rule(TransactionType.PURCHASE, Direction.DEBIT, listOf("شراء")),
        // "مشتريات إنترنت" and "مشتريات داخلية", the wallet's two purchase
        // templates - 1,762 of them. A different root from شراء, so no rule above
        // could reach either. Below استرداد, because a cashback notice says
        // "كاسترداد نقدي على مشترياتك" and is money coming back, not going out.
        Rule(TransactionType.PURCHASE, Direction.DEBIT, listOf("مشتري")),
        Rule(TransactionType.PURCHASE, Direction.DEBIT, listOf("PURCHASE")),
        // AlRajhi's English point-of-sale template says only "PoS". Matched as a
        // whole word, so it cannot fire inside another word.
        Rule(TransactionType.PURCHASE, Direction.DEBIT, listOf("POS")),
        Rule(TransactionType.PURCHASE, Direction.DEBIT, listOf("CARD", "TRANSACTION")),
        // meem, 2015-2019: "تمت عملية دفع عبر نقاط بيع من حسابك" and "تمت عملية
        // ناجحة بمبلغ: SAR 400 من: Nesma على بطاقتك الإئتمانية". Neither says
        // شراء.
        Rule(TransactionType.PURCHASE, Direction.DEBIT, listOf("دفع", "بيع")),
        Rule(TransactionType.PURCHASE, Direction.DEBIT, listOf("عملية ناجحة", "بطاق")),
        // "تم إيداع كاش في حسابك 207*** بمبلغ SAR 500 من ATM" - the machine named
        // in English, which the ايداع+صراف rule above cannot see.
        Rule(TransactionType.ATM_DEPOSIT, Direction.CREDIT, listOf("ايداع", "ATM")),

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
     *
     * Rebuilt only when [AccountOwner] changes, which is once at startup. Folding a
     * token costs a normalisation pass and this runs on every message; the cache is
     * the same lesson `MerchantMatch.Rules` records, where folding 260 keywords per
     * call took the dashboard's first reading with it.
     */
    @Volatile
    private var ownerSource: List<List<String>> = emptyList()

    @Volatile
    private var ownerRules: List<Rule> = emptyList()

    private fun ownerRules(): List<Rule> {
        val current = AccountOwner.nameTokens
        if (current !== ownerSource) {
            // Built before either field is published, so a concurrent reader sees
            // the old pair or the new pair, never a half-built one.
            ownerRules = current.map { Rule(TransactionType.OWN_TRANSFER, Direction.DEBIT, it) }
            ownerSource = current
        }
        return ownerRules
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
        if (rule.type == TransactionType.TRANSFER_OUT && namesOwner(withoutSenderLines(text))) {
            return Intent(TransactionType.OWN_TRANSFER, Direction.DEBIT)
        }
        return Intent(rule.type, rule.direction)
    }

    /**
     * The message with its SENDER lines removed.
     *
     * Every outgoing transfer names the owner - he is the one sending it. What
     * decides whether the money stayed with him is who RECEIVED it, and the
     * demotion above could not tell the two apart: it asked whether the name
     * appeared anywhere.
     *
     * It cost 68 transfers worth 94,126 riyals. STC Pay writes "اسم المرسل" on
     * every international transfer, so wages sent to domestic staff abroad read as
     * the owner moving money to himself and left his spending entirely.
     *
     * Only the lines that say sender are dropped. "من" is not among them: half the
     * templates use it for the funding ACCOUNT rather than a person, and a transfer
     * the owner makes to himself still names him on a beneficiary line, which is
     * what the demotion is for.
     */
    private fun withoutSenderLines(text: String): String =
        text.lineSequence().filterNot(SENDER_LINE::containsMatchIn).joinToString("\n")
            .let(ArabicText::foldForMatching)

    // `\b` only on the Latin alternative. Java defines a word boundary over
    // [A-Za-z0-9_], so between an Arabic letter and a colon there is no boundary at
    // all and "اسم المرسل:" matched nothing - the guard read as present and did
    // nothing, which is the same defect a lookahead had here once before.
    private val SENDER_LINE =
        Regex("""^\s*(?:اسم\s+المرسل|المرسل|مرسل|FROM\b)""", RegexOption.IGNORE_CASE)

    /** Whether folded text names the account holder. See [AccountOwner]. */
    private fun namesOwner(foldedText: String): Boolean =
        ownerRules().any { it.matches(foldedText) }
}
