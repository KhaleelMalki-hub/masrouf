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
    // STC Pay joined this set on 2026-09-01, seven years late. 670 records worth
    // 650,280 riyals were stored as purchases "لدى STC Pay" and counted as money
    // spent - they are top-ups of his own wallet, and what he actually bought with
    // them was reported by the wallet itself, to a sender no parser had ever read.
    val OWN_WALLETS = setOf("barq", "Tiqmo", "STC Pay", "stc pay", "STCPAY")

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
            // The 2015-2019 template, which writes the whole transaction on one
            // line and gives the merchant no label at all: "سحب مبلغ 289.00 SAR
            // بطاقة 1004* في EXTRA      MAKKAH   SA 2015/09/02 19:42". Its "سحب"
            // is a point-of-sale purchase, not a machine withdrawal - the ATM
            // messages of the same era name no merchant.
            //
            // Anchored on the fields around it rather than on the line, and it
            // stops at the date, which is the only thing that always follows.
            // 335 records in this corpus, 255,631 riyals, every one of them stored
            // with no party at all.
            Regex(
                """(?m)(?:سحب|شراء)\s+مبلغ\s+[\d.,]+\s*[A-Za-z]{0,3}\s*بطاقة\s*\d{3,4}\*?\s*""" +
                    """في\s+(.+?)(?=\s+\d{2}/\d{2}|\s+\d{4}/\d{2}/\d{2}|$)"""
            ),
        ),
        counterpartyPatterns = listOf(
            Regex("""(?m)^من\d{4}\s*;\s*(.+)$"""),
        ),
        cardPatterns = listOf(
            // "عبر:5763;مدى-جوجل باي" as well as "عبر5763": the colon is newer.
            Regex("""عبر\s*:?\s*(\d{4})"""),
            // And the halves the other way round: "عبر:فيزا;8134". AlRajhi started
            // sending this in April 2026 and ten settlements arrived with no card
            // at all - their amounts and balances stored correctly and attached to
            // nothing, so the tile for a card the owner had paid off in full went
            // on showing a figure from before he paid it.
            //
            // Card FIRST above, network first here, because the pattern that runs
            // first wins and "عبر:فيزا;8134" must not give up "فيزا".
            Regex("""عبر\s*:?\s*[^\d;\n]{1,12};\s*(\d{4})"""),
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
            // mada Pay names the field outright. 50 records carried it and not one
            // was read, because nothing looked for this label.
            Regex("""(?m)^اسم\s+المتجر\s*:?\s*(.+)$"""),
            // "من 21140 CENTERPOINT -DOM": a terminal id in front of the name. The
            // guard above refuses it - correctly, since the same shape is how an
            // account number appears - so the name is taken from after the digits
            // rather than by loosening a guard that 2,014 records paid for.
            Regex("""(?m)^من\s+\d{3,7}\s+(?=\D)(.+)$"""),
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
            // The sender on the heading line itself - "حوالة محلية واردة من امانة
            // العاصمة المقدسة", "تحويل من TALAL MAQADMI", "حوالة واردة من حسابك
            // الاستثماري" - which is how SNB wrote an incoming transfer until 2021.
            // 845 records had no party at all because of it, 1,280,957 riyals, and
            // among them two allowances from the owner's employer that were filed
            // as ordinary transfers: the name is the only thing that separates
            // money from an employer from money from anyone else.
            //
            // Whitespace, not `\b`: Java has no word boundary between a space and
            // an Arabic letter, so the guard would match nothing at all. That is
            // the second time in this file - see IntentClassifier.SENDER_LINE.
            // The capture stops at "إلى", which some of these templates put on the
            // same line: without it the party reads "حنين مقادمي إلى 104*010".
            Regex("""(?m)^(?:حوالة|حواله|تحويل)[^\n]*?\sمن\s+(?!\**\d)(.+?)(?=\s+(?:إلى|الى)\s|$)"""),
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

    /**
     * STC Pay, which became STC Bank. Closed now, but seven years of it - 2019 to
     * 2026 - sat in the inbox unread, because nothing in the app had ever claimed
     * the sender: 1,845 purchases, 52 international transfers, and 889 security
     * codes that only luck kept off the disk.
     *
     * Two habits of its own. It labels the merchant with "في:" - the same word it
     * uses for the date, hence the digit guard - and it writes the card and the
     * merchant under the same "من:" label, telling them apart only by the asterisk
     * that precedes an identifier.
     */
    val STC_PAY = BankProfile(
        id = "stcpay",
        senderIds = setOf("STCPAY", "STC PAY", "STCBANK", "STC BANK"),
        merchantPatterns = listOf(
            // "في: Health Endowment Fund", never "في: 26/06/26 01:58".
            Regex("""(?m)^في\s*+:?+\s*+(?!\d)(.+)$"""),
            // "من:AL DRE" beneath "من:*7667", which is the card.
            Regex("""(?m)^من\s*+:?+\s*+(?!\**\d)(.+)$"""),
        ),
        counterpartyPatterns = listOf(
            // The channel FIRST, and these are counterparty patterns rather than
            // merchant ones because a transfer is not merchant-bearing: the parser
            // reads a merchant only for a purchase or a refund.
            //
            // A wage sent abroad therefore files by HOW it was sent, which is a
            // fact about the money, rather than by WHO received it - a domestic
            // worker, whose name is a person's name and has no place in a shipped
            // category rule. The body keeps it either way.
            Regex("""(?m)^شركة الحوالات\s*:?\s*(.+)$"""),
            Regex("""(?m)^حوالة\s+(WU)\b"""),
            Regex("""(?m)^اسم المستلم\s*:?\s*(.+)$"""),
            Regex("""(?m)^اسم المرسل\s*:?\s*(.+)$"""),
            Regex("""(?m)^(?:الى|إلى)\s*+:?+\s*+(?!\**\d)(.+)$"""),
        ),
        cardPatterns = listOf(
            // "البطاقة: ***8611؛ VISA" and "رقم البطاقة: ****0926".
            Regex("""بطاقة\s*:?\s*\**\s*(\d{4})"""),
            // "عبر:*5763" - the funding card of a top-up.
            Regex("""عبر\s*:?\s*\**\s*(\d{4})"""),
            // "شراء VISA / من:*7667" - the card under the same label as a merchant,
            // told apart by the asterisk.
            Regex("""(?m)^من\s*:?\s*\*(\d{4})"""),
        ),
        ownWalletMerchants = OWN_WALLETS,
    )

    /**
     * SNB Capital, the brokerage. A separate sender from the bank, and one no
     * profile claimed: "SNB-Capital" folds to SNBCAPITAL, which contains none of
     * SNB's sender ids, so 1,136 messages were skipped as an unknown sender.
     *
     * What they carry is money the owner still has - 277 movements between his
     * current and investment accounts - and money he earned: share dividends. The
     * dividend's party is the phrase rather than the company, deliberately. The
     * company name would file an Aramco dividend under whatever Aramco's rule says
     * and a Jarir one under bookshops.
     */
    val SNB_CAPITAL = BankProfile(
        id = "snbcapital",
        senderIds = setOf("SNBCAPITAL", "SNB CAPITAL", "NCBCAPITAL", "NCB CAPITAL"),
        counterpartyPatterns = listOf(
            Regex("""(أرباح شركة)"""),
            Regex("""(الحساب الاستثماري)"""),
        ),
    )

    val ALL: List<BankProfile> =
        listOf(AL_RAJHI, SNB, D360, BARQ, EMIRATES_NBD, STC_PAY, SNB_CAPITAL)

    /** A registry covering every sender the app understands. */
    fun registry(): ParserRegistry = ParserRegistry(ALL.map(::BankMessageParser))
}
