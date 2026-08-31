package sa.masrouf.core.capture

/**
 * The four senders this app currently understands, described as data.
 *
 * Every pattern here was written against a real captured message, never against a
 * guess at a format. A regex invented from imagination still compiles and still
 * matches something eventually - and what it matches will be wrong in a way no
 * test catches, because the test was invented from the same imagination.
 *
 * When a bank changes a template, the symptom is [ParseResult.Failed] with
 * "no recognised intent" or "no amount found" - which is why those two cases are
 * counted and surfaced rather than swallowed.
 */
object SaudiBanks {

    /** Wallet names that are the user's own, not a shop. See [BankProfile.ownWalletMerchants]. */
    private val OWN_WALLETS = setOf("barq", "Tiqmo")

    /**
     * AlRajhi. The terse sender: labels are glued to values ("بـSR 8.28",
     * "عبر2383;فيزا") and the merchant is introduced by a bare lam prefix
     * ("لIHERB ARA") on its own line.
     */
    val AL_RAJHI = BankProfile(
        id = "alrajhi",
        senderIds = setOf("ALRAJHIBANK", "ALRAJHI"),
        merchantPatterns = listOf(
            Regex("""(?m)^لدى\s*:?\s*(.+)$"""),
            Regex("""(?m)^التاجر\s*:?\s*(.+)$"""),
            // The English templates ("PoS", "Purchase") name the merchant after
            // "At:". Seen 38 times in a real corpus.
            Regex("""(?m)^At\s*:\s*(.+)$"""),
            // A line beginning with lam directly followed by a name. The two
            // negative lookaheads keep it off "لدى..." (handled above) and off
            // "لـ3016", the destination account of an incoming transfer, which
            // after tatweel removal becomes "ل3016".
            Regex("""(?m)^ل(?!دى)(?![*\d\s])(.+)$"""),
        ),
        counterpartyPatterns = listOf(
            Regex("""(?m)^من\d{4}\s*;\s*(.+)$"""),
        ),
        cardPatterns = listOf(
            // "عبر:5763;مدى-جوجل باي" as well as "عبر5763": the colon is newer.
            Regex("""عبر\s*:?\s*(\d{4})"""),
            Regex("""بطاقة\s*:?\s*\**\s*(\d{4})"""),
            // English templates: "By:1335 ;Visa" and "Card:1335 ;Visa".
            Regex("""(?m)^By\s*:\s*(\d{4})"""),
            Regex("""(?m)^Card\s*:\s*(\d{4})"""),
        ),
        ownWalletMerchants = OWN_WALLETS,
    )

    /**
     * SNB (AlAhli). Distinguishes the funding account from the merchant only by
     * whether the value after "من" starts with an asterisk: "من *0104" is the
     * account, "من barq" is the merchant.
     */
    val SNB = BankProfile(
        id = "snb",
        // No bare "SNB". Sender ids are matched as substrings of the folded
        // origin, and "SNB" is a substring of "EMIRATESNBD" - which meant every
        // Emirates NBD message was claimed by this profile and parsed with SNB's
        // patterns. Found by running a real 499-message corpus, where it looked
        // like success: 248 of them "captured", under the wrong bank, with the
        // card fragment silently dropped because the formats differ.
        //
        // The real senders are "SNB-AlAhli", "AlahliSMS" and "AlAhliSMS", which
        // fold to strings containing "SNB ALAHLI" or "ALAHLI", so nothing is lost.
        // "SNB NEO" rather than a bare "SNB". Sender ids are matched as substrings
        // of the folded origin, and "SNB" is a substring of "EMIRATESNBD" - which
        // meant every Emirates NBD message was claimed by this profile and parsed
        // with SNB's patterns. Found on a real 499-message corpus, where it looked
        // like success: 248 "captured", under the wrong bank, with the card
        // fragment dropped because the two banks mask it differently.
        //
        // Whole-word matching would be the structural fix and does not work here:
        // folding turns "com.snb.neo" into "COM SNB NEO", where SNB is a word, but
        // it turns "com.alrajhiretailapp" into "COM ALRAJHIRETAILAPP", where
        // ALRAJHI is only a prefix. So the packages are named instead, and
        // ObservedBankPackagesTest reads them off a real device to keep this list
        // honest.
        senderIds = setOf("SNB ALAHLI", "SNBALAHLI", "SNB NEO", "ALAHLI"),
        merchantPatterns = listOf(
            Regex("""(?m)^من\s+(?!\**\d)(.+)$"""),
            Regex("""(?m)^لدى\s*:?\s*(.+)$"""),
        ),
        counterpartyPatterns = listOf(
            // The party by name, first, because the account it used is in the same
            // message and reads as a name to a pattern that is not looking. 2,014
            // records carried an account for their party - "104*010", "3016",
            // "106*011" - while the person or body that sent or received the money
            // sat two lines away under مرسل or مستفيد. Nothing can be filed against
            // a number, so every one of them stayed unfiled.
            //
            // The negative lookaheads keep these off the account lines, which some
            // templates introduce with the very same words.
            Regex("""(?m)^مرسل\s*+:?+\s*+(?:من\s+)?+(?!\**\d)(.+)$"""),
            Regex("""(?m)^مستفيد\s*+:?+\s*+(?!\**\d)(.+)$"""),
            // "من1007* NAME" (incoming) and "ل0106* NAME" (outgoing).
            Regex("""(?m)^(?:من|ل)\d{4}\*\s*(.+)$"""),
        ),
        cardPatterns = listOf(
            // "بطاقة مدى *2907" and, newer, "بطاقة مدى: **2907".
            Regex("""مدى\s*:?\s*\*+\s*(\d{4})"""),
            Regex("""لبطاقة\s*\*\s*(\d{4})"""),
            Regex("""بطاقة\s*:?\s*\**\s*(\d{4})"""),
            // The card's kind between the word and the digits, which the pattern
            // above cannot cross: "بطاقة ائتمانية ***2887" (2,398 stored bodies
            // with no card read), "بطاقة فيزا: **2166" (435), "من بطاقة إئتمانية
            // **3396". Both spellings of the hamza, because both arrive.
            Regex("""بطاقة\s*(?:ا|إ)ئتمانية\s*:?\s*\*+\s*(\d{4})"""),
            Regex("""بطاقة\s*فيزا\s*:?\s*\*+\s*(\d{4})"""),
            // "مدى-أثير*2907": the wallet's own descriptor for a mada card.
            Regex("""أثير\s*\*\s*(\d{4})"""),
        ),
        ownWalletMerchants = OWN_WALLETS,
    )

    /**
     * D360. The most consistently labelled sender: every field is "label: value",
     * and identifiers are masked with trailing or leading asterisks.
     */
    val D360 = BankProfile(
        id = "d360",
        senderIds = setOf("D360 BANK", "D360BANK", "D360"),
        counterpartyPatterns = listOf(
            // `(?!\**\d)`, not `(?![*\d])`: D360 masks a name with LEADING asterisks
            // ("من: ****RECIPIENT NAME") and writes an account as digits with
            // trailing ones ("حساب: 2207****"). Refusing every asterisk refuses the
            // masked name too; refusing asterisks-then-digit refuses only the
            // account.
            //
            // Possessive quantifiers, and they are the whole fix. With plain `\s*:?\s*`
            // the engine gives the colon back to satisfy the lookahead and captures
            // ":3016" instead of refusing - the guard reads as present and does
            // nothing. `*+` and `?+` cannot be given back.
            //
            // The same negative lookahead SNB's patterns carry, and for the same
            // reason. reparseStoredBodies tries every profile and keeps whichever
            // reads the most, so an unguarded pattern here claims other banks'
            // bodies too: "الى:3016 / من:SENDER NAME" gave up the account number
            // while the name sat one line below it. 710 rows survived the repair
            // pass because these two lines did not have the guard.
            Regex("""(?m)^(?:إلى|الى)\s*+:?+\s*+(?!\**\d)(.+)$"""),
            Regex("""(?m)^من\s*+:?+\s*+(?!\**\d)(.+)$"""),
        ),
        cardPatterns = listOf(
            Regex("""(?m)^حساب\s*:?\s*\**\s*(\d{4})"""),
        ),
    )

    /**
     * barq wallet. Glues label, number and currency together with no separators at
     * all ("مبلغ2000.00SAR", "رصيد15.18"), and sends top-up notices in English.
     */
    val BARQ = BankProfile(
        id = "barq",
        senderIds = setOf("BARQ APP", "BARQAPP", "BARQ"),
        merchantPatterns = listOf(
            Regex("""(?m)^لدى\s*:?\s*(.+)$"""),
        ),
        counterpartyPatterns = listOf(
            Regex("""(?m)^(?:الى|إلى)\s+(.+)$"""),
        ),
        cardPatterns = listOf(
            Regex("""card\s*number\s*:?\s*\**\s*(\d{4})""", RegexOption.IGNORE_CASE),
            Regex("""بطاقة\s*\**\s*(\d{4})"""),
        ),
    )

    /**
     * Emirates NBD. Its Saudi messages mix Arabic and English templates and mask
     * the card as `XX9994` rather than with an asterisk, so it needs its own card
     * patterns even though the surrounding wording resembles SNB's.
     */
    val EMIRATES_NBD = BankProfile(
        id = "enbd",
        senderIds = setOf("EMIRATESNBD", "ENBD"),
        merchantPatterns = listOf(
            Regex("""(?m)^لدى\s*:?\s*(.+)$"""),
            Regex("""(?m)^At\s*:\s*(.+)$""", RegexOption.IGNORE_CASE),
        ),
        counterpartyPatterns = listOf(
            Regex("""(?m)^From\s*+:\s*+(?!\**\d)(.+)$""", RegexOption.IGNORE_CASE),
            Regex("""(?m)^(?:الى|إلى)\s*+:?+\s*+(?!\**\d)(.+)$"""),
        ),
        cardPatterns = listOf(
            // "بطاقة: فيزا الائتمانية XX9994" and "to Account: XX8101" - the digits
            // follow an XX mask with words in between, which the asterisk-shaped
            // patterns used by the other banks do not reach.
            Regex("""XX\s*(\d{4})""", RegexOption.IGNORE_CASE),
        ),
    )

    val ALL: List<BankProfile> = listOf(AL_RAJHI, SNB, D360, BARQ, EMIRATES_NBD)

    /** A registry covering every sender the app understands. */
    fun registry(): ParserRegistry = ParserRegistry(ALL.map(::BankMessageParser))
}
