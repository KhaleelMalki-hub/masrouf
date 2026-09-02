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
    //
    // urpay on 2026-09-02, for the same reason: 26 bank-side top-ups "لدى URPAY"
    // stood as purchases while the wallet's own sender - 179 messages, 49 of
    // them transactions - had never been read. Compared through
    // [ArabicText.normalizeMerchant], which uppercases, so one spelling is enough.
    val OWN_WALLETS = setOf("barq", "Tiqmo", "STC Pay", "stc pay", "STCPAY", "urpay")

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
            Regex("""(?m)^من\s+(?!\**\d)(?!X{2,}\d)(.+)$"""),
            Regex("""(?m)^لدى\s*:?\s*(.+)$"""),
            // mada Pay names the field outright. 50 records carried it and not one
            // was read, because nothing looked for this label.
            Regex("""(?m)^اسم\s+المتجر\s*:?\s*(.+)$"""),
            // "من 21140 CENTERPOINT -DOM": a terminal id in front of the name. The
            // guard above refuses it - correctly, since the same shape is how an
            // account number appears - so the name is taken from after the digits
            // rather than by loosening a guard that 2,014 records paid for.
            // "من 27040ADDIDAS KIDS YASM": the same, with no space after the id.
            Regex("""(?m)^من\s+\d{3,7}\s*(?=[A-Za-z])(.+)$"""),
            // 2014-2015: "تمت الموافقة لسحب مبلغ 7248.00 SAR من بطاقة 1004** فى
            // JARIR BOOK STORE         MAKKAH       SA بتاريخ 2015/01/19" - one
            // line, the shop after فى (alef maksura, which nothing looked for)
            // and before the date. 30 records, 62,000 riyals, no party at all.
            //
            // The padding after the shop is five spaces or more, which the
            // normaliser turns into a line break, so the shop ends at the break or
            // at the date - whichever comes first - and the city stays behind.
            Regex("""من\s+بطاقة\s+\d{4}\*+\s+(?:فى|في)\s+(.+?)(?=\s*\n|\s+بتاريخ)"""),
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
            Regex("""(?m)^مرسل\s*+:?+\s*+(?:من\s+)?+(?!\**\d)(?!X{2,}\d)(.+)$"""),
            Regex("""(?m)^مستفيد\s*+:?+\s*+(?!\**\d)(?!X{2,}\d)(.+)$"""),
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
            Regex("""(?m)^(?:حوالة|حواله|تحويل)[^\n]*?\sمن\s+(?!\**\d)(?!X{2,}\d)(.+?)(?=\s+(?:إلى|الى)\s|$)"""),
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
            // `(?!\**\d)(?!X{2,}\d)`, not `(?![*\d])`: D360 masks a name with LEADING asterisks
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
            Regex("""(?m)^(?:إلى|الى)\s*+:?+\s*+(?!\**\d)(?!X{2,}\d)(.+)$"""),
            Regex("""(?m)^من\s*+:?+\s*+(?!\**\d)(?!X{2,}\d)(.+)$"""),
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
            Regex("""(?m)^From\s*+:\s*+(?!\**\d)(?!X{2,}\d)(.+)$""", RegexOption.IGNORE_CASE),
            Regex("""(?m)^(?:الى|إلى)\s*+:?+\s*+(?!\**\d)(?!X{2,}\d)(.+)$"""),
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
            // "في: Health Endowment Fund", never "في: 26/06/26 01:58" and never
            // "في بطاقة **3396" - the cashback template puts the CARD after the
            // same word, and 66 refunds took the word بطاقة as their party.
            Regex("""(?m)^في\s*+:?+\s*+(?!\d)(?!بطاق)(.+)$"""),
            // "من:AL DRE" beneath "من:*7667", which is the card.
            Regex("""(?m)^من\s*+:?+\s*+(?!\**\d)(?!X{2,}\d)(.+)$"""),
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
            Regex("""(?m)^(?:الى|إلى)\s*+:?+\s*+(?!\**\d)(?!X{2,}\d)(.+)$"""),
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

    /**
     * Bank AlJazira, which writes to this owner in English. Card 3761, a mada debit
     * card he uses.
     *
     * "By:3761;mada" is the card and "At: barq" the merchant, while "From: 8001" is
     * the ACCOUNT - four digits under a label that names a party everywhere else,
     * which is why the counterparty guard matters here as much as it does at SNB.
     */
    val AL_JAZIRA = BankProfile(
        id = "aljazira",
        senderIds = setOf("ALJAZIRASMS", "JAZIRA BANK", "ALJAZIRA", "JAZIRABANK"),
        merchantPatterns = listOf(
            Regex("""(?m)^At\s*:\s*(.+)$""", RegexOption.IGNORE_CASE),
            Regex("""(?m)^لدى\s*:?\s*(.+)$"""),
        ),
        counterpartyPatterns = listOf(
            Regex("""(?m)^From\s*:\s*(?!\**\d)(?!X{2,}\d)(.+)$""", RegexOption.IGNORE_CASE),
        ),
        cardPatterns = listOf(
            // "By:3761;mada", "Mada card: 3761", "بطاقتك رقم2650".
            Regex("""(?m)^By\s*:?\s*\**\s*(\d{4})""", RegexOption.IGNORE_CASE),
            Regex("""card\s*:?\s*\**\s*(\d{4})""", RegexOption.IGNORE_CASE),
            Regex("""بطاقت?ك?\s*رقم\s*\**\s*(\d{4})"""),
        ),
        ownWalletMerchants = OWN_WALLETS,
    )

    /**
     * The Saudi Investment Bank. Card 9097, a mada debit card.
     *
     * It masks an account as "XXX1001" rather than with asterisks, so the guard
     * that keeps an account out of the party field has to refuse an X-mask too:
     * "من: XXX1001" is the funding account and "من: NAME XXX2001" is a person.
     */
    val SAIB = BankProfile(
        id = "saib",
        senderIds = setOf("SAIB", "SAIB AD"),
        merchantPatterns = listOf(
            Regex("""(?m)^لدى\s*:?\s*(.+)$"""),
        ),
        counterpartyPatterns = listOf(
            Regex("""(?m)^من\s*:?\s*(?!\**\d)(?!X{2,}\d)(.+)$""", RegexOption.IGNORE_CASE),
        ),
        cardPatterns = listOf(
            // "بطاقة: XXX9097 مدى" - an X mask, as Emirates NBD writes it.
            Regex("""بطاقة\s*:?\s*X*\s*(\d{4})""", RegexOption.IGNORE_CASE),
        ),
        ownWalletMerchants = OWN_WALLETS,
    )

    /**
     * urpay, the AlRajhi-group wallet. Card 4322. Used 2022-2024, then left; the
     * owner confirmed the account is his on 2026-09-01.
     *
     * Two template generations. The older writes the card as "بطاقة:  urpay بطاقة
     * ***4322" or "4322***;urpay بطاقة" and the merchant under "لدى:"; the newer
     * writes "بطاقة:4322" with the merchant under "من:" - the same word the wallet
     * never uses for a card here, so no asterisk guard is doing real work, but it
     * is kept for the day a template puts one there.
     *
     * Phone credit ("خصم من المحفظة لـ (شحن خطوط الاتصال)") names no shop, only the
     * operator, which is the party that matters for filing it.
     */
    val URPAY = BankProfile(
        id = "urpay",
        // "urpay" the SMS sender and "com.urpay.consumer" the installed package,
        // read off the phone on 2026-09-02.
        senderIds = setOf("URPAY"),
        merchantPatterns = listOf(
            Regex("""(?m)^لدى\s*:?\s*(.+)$"""),
            Regex("""(?m)^من\s*+:?+\s*+(?!\**\d)(?!X{2,}\d)(.+)$"""),
        ),
        counterpartyPatterns = listOf(
            // "مزوّد الخدمة: STC" (with a shadda the normaliser keeps) and, in the
            // newer template, "شركة:STC".
            Regex("""(?m)^(?:مزو\S*د الخدمة|شركة)\s*:?\s*(.+)$"""),
        ),
        cardPatterns = listOf(
            // "بطاقة:4322", "بطاقة:***4322", "بطاقة:  urpay بطاقة ***4322" and
            // "بطاقة: 4322***;urpay بطاقة" - all four read by one pattern, because
            // the digits are the first thing after the optional wallet name.
            Regex("""بطاقة\s*:?\s*(?:urpay\s*بطاقة\s*)?\**\s*(\d{4})""", RegexOption.IGNORE_CASE),
        ),
    )

    /**
     * Vision Bank, the digital bank he opened in 2025. Card 2455, mada, in
     * occasional use since.
     *
     * Every field is "Label: value" in English, with an Arabic twin. The card and
     * the account both carry a mask, so the card pattern names its label outright
     * rather than reading the first four digits after an asterisk run - which
     * would as happily return the account.
     *
     * A "Local Credit Transfer" is money ARRIVING; "Sender:" names who sent it,
     * "From:" only the bank it came through.
     */
    val VISION_BANK = BankProfile(
        id = "vision",
        // Not a bare "VISION": the inbox also holds a marketing sender called
        // "Vision 2030".
        senderIds = setOf("VISION BANK", "VISIONBANK"),
        merchantPatterns = listOf(
            // Possessive, as at D360: with a plain `\s*` the engine hands back the
            // space so the guard looks at " ***5001" instead of "***5001", passes,
            // and the account is captured with the space in front of it.
            Regex("""(?m)^From\s*+:\s*+(?!\**\d)(?!X{2,}\d)(.+)$""", RegexOption.IGNORE_CASE),
            Regex("""(?m)^من\s*+:?+\s*+(?!\**\d)(?!X{2,}\d)(.+)$"""),
        ),
        counterpartyPatterns = listOf(
            Regex("""(?m)^Sender\s*+:\s*+(.+)$""", RegexOption.IGNORE_CASE),
            Regex("""(?m)^From\s*+:\s*+(?!\**\d)(?!X{2,}\d)(.+)$""", RegexOption.IGNORE_CASE),
        ),
        cardPatterns = listOf(
            // "Card Number: ****2455", never "Account Number: ****6000".
            Regex("""Card\s*Number\s*:\s*\**\s*(\d{4})""", RegexOption.IGNORE_CASE),
            // "رقم البطاقة: ****2455", never "رقم حساب البطاقة:  ****6000".
            Regex("""رقم البطاقة\s*:?\s*\**\s*(\d{4})"""),
        ),
    )

    /**
     * meem, Gulf International Bank's retail brand. Three senders over the years
     * ("MEEMSMS" 2015-2018, "meemKSA" 2018-2024, "meem"/"meemSecure" for notices
     * and codes) and one installed package, "com.veripark.GIB". Credit cards 0891
     * and 0883, mada 5654. The owner confirmed the account is his; the bank's last
     * transaction in his inbox is from November 2024.
     *
     * The oldest templates are prose ("هلا ميمر! تمت عملية ناجحة بمبلغ: SAR 400
     * من: Nesma على بطاقتك الإئتمانية"), so one merchant pattern reads inline
     * between the amount and the words that always follow the shop. The credit
     * card is written in full around an X-run - "4399XXXXXXXX0891" - and the first
     * four digits are the BIN, not the card.
     */
    val MEEM = BankProfile(
        id = "meem",
        senderIds = setOf("MEEM", "VERIPARK GIB"),
        merchantPatterns = listOf(
            Regex("""(?m)^لدى\s*:?\s*(.+)$"""),
            // "من:Al Amteaz Center   Bakery, MAKKAH, SA" on a line of its own;
            // "من: ***2207" is the account and the guard refuses it.
            Regex("""(?m)^من\s*+:?+\s*+(?!\**\d)(?!X{2,}\d)(.+)$"""),
            // Inline, in the 2015-2019 prose: "بمبلغ: SAR 400 من: Nesma على بطاقتك"
            // and "بمبلغ 9.00 SAR من DUNKIN DONUTS 20059, MAKKAH, SA في 14/01/2019".
            // The shop ends where على or في begins, which is the only thing that
            // always follows it.
            Regex(
                """(?m)بمبلغ\s*:?\s*(?:SAR\s*)?[\d.,]+\s*(?:SAR)?\s+من\s*+:?+\s*+(?!\**\d)""" +
                    """(.+?)(?=\s+(?:على|في)\s|$)"""
            ),
        ),
        counterpartyPatterns = listOf(
            Regex("""(?m)^(?:اسم المستفيد|Beneficiary Name)\s*+:?+\s*+(.+)$""", RegexOption.IGNORE_CASE),
            Regex("""(?m)^(?:اسم المرسل|Sender Name)\s*+:?+\s*+(.+)$""", RegexOption.IGNORE_CASE),
            // "عبر: NATIONAL COMMERCIAL BANK, THE" - the bank an incoming transfer
            // came through, and the only handle those templates give. Not a
            // person, but enough to file every transfer from that account at once.
            Regex("""(?m)^(?:عبر|Via)\s*:\s*(.+)$""", RegexOption.IGNORE_CASE),
        ),
        cardPatterns = listOf(
            // "4399XXXXXXXX0891": the last four, not the BIN in front of the mask.
            Regex("""\d{4}X{4,}(\d{4})"""),
            // "بطاقة: ***5654; مدى" and "بطاقة:***5654;mada".
            Regex("""بطاقة\s*:?\s*\**\s*(\d{4})"""),
        ),
        ownWalletMerchants = OWN_WALLETS,
    )

    val ALL: List<BankProfile> = listOf(
        AL_RAJHI, SNB, D360, BARQ, EMIRATES_NBD, STC_PAY, SNB_CAPITAL, AL_JAZIRA, SAIB,
        URPAY, VISION_BANK, MEEM,
    )

    /** A registry covering every sender the app understands. */
    fun registry(): ParserRegistry = ParserRegistry(ALL.map(::BankMessageParser))
}
