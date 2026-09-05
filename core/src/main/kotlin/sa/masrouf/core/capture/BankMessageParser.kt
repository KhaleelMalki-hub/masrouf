package sa.masrouf.core.capture

import sa.masrouf.core.model.TransactionDraft
import sa.masrouf.core.model.TransactionType
import sa.masrouf.core.text.ArabicText

/**
 * Everything that differs between one Saudi bank's messages and another's.
 *
 * The vocabulary and the structure are shared (see [IntentClassifier]); what
 * actually varies is punctuation, spacing and which label introduces which field.
 * So the differences live in data here, and the logic lives once in
 * [BankMessageParser].
 *
 * Every pattern must capture its value in group 1 and is matched against
 * [ArabicText.normalize]d text with `^`/`$` bound to lines.
 */
data class BankProfile(
    val id: String,

    /**
     * Folded sender ids and notification package names this profile claims.
     * Matched as substrings, so "ALRAJHIBANK" claims "AlRajhiBank".
     */
    val senderIds: Set<String>,

    /** Tried in order; the first match wins. Only consulted for purchases and refunds. */
    val merchantPatterns: List<Regex> = emptyList(),

    /** The other party in a transfer. Tried in order. */
    val counterpartyPatterns: List<Regex> = emptyList(),

    /** Last four digits of the card or account the money moved on. */
    val cardPatterns: List<Regex> = emptyList(),

    /**
     * Merchants that are really the user's own wallet.
     *
     * Topping up a wallet appears on the funding bank as an ordinary online
     * purchase, and the wallet then reports the *same* riyals again as they are
     * spent. Left as a purchase, every top-up is counted twice: once moving into
     * the wallet and once leaving it. Matching merchant names here reclassifies the
     * top-up as [TransactionType.OWN_TRANSFER], which is exactly what it is.
     *
     * Compared using [ArabicText.normalizeMerchant].
     */
    val ownWalletMerchants: Set<String> = emptySet(),
)

/**
 * Turns one bank's messages into transaction drafts.
 *
 * This parser never invents a value. If the wording matches no known intent, or no
 * amount can be extracted with confidence, it reports [ParseResult.Failed] rather
 * than producing a draft with a plausible guess in it. A missing transaction is
 * visible and fixable; a wrong one is neither.
 *
 * It assumes [MessageGate] has already run - see [CapturePipeline].
 */
class BankMessageParser(private val profile: BankProfile) : MessageParser {

    override val id: String = profile.id

    override fun canParse(message: RawMessage): Boolean {
        val origin = ArabicText.foldForMatching(
            listOfNotNull(message.sender, message.packageName).joinToString(" ")
        )
        if (origin.isBlank()) return false
        return profile.senderIds.any { origin.contains(it) }
    }

    override fun parse(message: RawMessage): ParseResult {
        val text = ArabicText.normalize(message.fullText)

        val intent = IntentClassifier.classify(text)
            ?: return ParseResult.Failed(id, "no recognised intent")

        // Refused before the amount is even looked for. A message priced in another
        // currency usually still quotes a SAR balance, and the extractor happily
        // returns that: "سحب مبلغ 1058.66 AED ... الصرف المتبقي 6127.16 SAR" was
        // read as 6127.16 riyals spent. Not a rounding error, a different number
        // with a different meaning - the balance, recorded as a purchase. 159 such
        // messages in one real inbox.
        //
        // This app stores integer halalas of SAR and has nowhere to put a currency,
        // so the honest outcome is a refusal that stays visible in the
        // NotUnderstood count until the model can hold one.
        if (FOREIGN_AMOUNT.containsMatchIn(text)) {
            return ParseResult.Failed(id, "amount in a foreign currency")
        }

        val amount = AmountExtractor.extractOrNull(text)
            ?: return ParseResult.Failed(id, "no amount found")

        // Zero is not an amount a bank moves. Reaching here with one means the
        // extractor matched a number that was not the transaction's - a prize
        // figure in an advert, a fee line on a message with no principal - and a
        // zero-riyal transaction in the user's history is a row that explains
        // nothing and can never be reconciled against a statement.
        if (amount.money.isZero) return ParseResult.Failed(id, "amount was zero")

        val isMerchantBearing =
            intent.type == TransactionType.PURCHASE || intent.type == TransactionType.REFUND

        val merchant = if (isMerchantBearing) firstMatch(profile.merchantPatterns, text) else null
        val counterparty = firstMatch(profile.counterpartyPatterns, text)
        val party = merchant ?: counterparty

        val type = reclassifyIfOwnWallet(intent.type, merchant)

        val draft = TransactionDraft(
            amount = amount.money,
            direction = intent.direction,
            type = type,
            // The message's own date is not trusted for ordering: these bodies are
            // read out of an RTL view where a Latin run's display order is not its
            // stored order, and the two are indistinguishable after the fact. The
            // device's receipt time is within seconds of the transaction and has no
            // such ambiguity.
            occurredAt = message.receivedAt,
            merchantRaw = party,
            accountLast4 = firstMatch(profile.cardPatterns, text)?.takeIf { it.length == 4 },
            note = if (merchant != null && counterparty != null && merchant != counterparty) {
                counterparty
            } else {
                null
            },
            currency = amount.currency ?: "SAR",
            // What was PARSED, not the half of it the body happens to hold. The gate
            // folds `fullText` and this parser normalises `fullText`, and for a
            // notification that is the title joined to the body - so storing the body
            // alone threw away text the decision was made on. Rule 2 says a marker
            // added later must also remove what an earlier gate let through, and
            // `purgeRejectedBodies` re-asks the gate about the stored text: a marker
            // that appears only in a title could never be found again.
            rawText = message.fullText,
        )

        return ParseResult.Parsed(draft, id, confidenceOf(party))
    }

    /**
     * A purchase whose merchant is the user's own wallet is a top-up, not spending.
     * See [BankProfile.ownWalletMerchants].
     */
    private fun reclassifyIfOwnWallet(type: TransactionType, merchant: String?): TransactionType {
        if (type != TransactionType.PURCHASE || merchant == null) return type
        val key = ArabicText.normalizeMerchant(merchant)
        val isOwnWallet = profile.ownWalletMerchants.any { ArabicText.normalizeMerchant(it) == key }
        return if (isOwnWallet) TransactionType.OWN_TRANSFER else type
    }

    /**
     * Confidence reflects how much of the message was understood, not how likely
     * the amount is to be right.
     *
     * It stays below [ParserRegistry.CONFIRMATION_THRESHOLD] in every case, so
     * nothing auto-confirms. The threshold is lowered per parser only after that
     * parser has been measured against real captured messages.
     */
    private fun confidenceOf(party: String?): Float = if (party != null) 0.9f else 0.7f

    /**
     * A number carrying a currency this app cannot store.
     *
     * Matched on either side, because banks write both "USD 1" and "1058.66 AED".
     * SAR and SR are deliberately absent: those are the currency the app is in.
     */
    private val FOREIGN_AMOUNT = Regex(
        """(?:\b(?:USD|AED|EUR|GBP|KWD|BHD|QAR|OMR|JOD|EGP|TRY|CNY|JPY|CHF|CAD|AUD|INR|PKR)\b\s*[\d.,]+""" +
            """|[\d.,]+\s*\b(?:USD|AED|EUR|GBP|KWD|BHD|QAR|OMR|JOD|EGP|TRY|CNY|JPY|CHF|CAD|AUD|INR|PKR)\b)""",
        RegexOption.IGNORE_CASE,
    )

    private fun firstMatch(patterns: List<Regex>, text: String): String? {
        for (pattern in patterns) {
            val value = pattern.find(text)?.groupValues?.getOrNull(1)?.let(::cleanCaptured)
            if (!value.isNullOrBlank()) return value
        }
        return null
    }

    /**
     * Banks mask identifiers with asterisks on either side ("****NAME", "NAME****",
     * "2207****") and separate fields with `;`. None of that is part of the value.
     */
    private fun cleanCaptured(raw: String): String = raw.trim().trim('*', ';', ',', '-', ':').trim()
}
