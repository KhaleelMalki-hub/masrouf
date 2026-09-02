package sa.masrouf.core.fixtures

/**
 * Real message bodies captured from Saudi bank senders, kept verbatim in shape.
 *
 * ## Redaction
 *
 * These are transcribed from a real inbox, so before being committed:
 *
 *  - every personal name was replaced with a placeholder
 *  - every one-time password was replaced with `000000`
 *  - every account balance was replaced with an invented figure
 *  - every bill, invoice and SADAD reference was replaced with zeroes. They are
 *    account identifiers by another name: a SADAD number is what someone else
 *    would need to pay - or query - the owner's bill. Their LENGTH is kept,
 *    because a parser that reads a field's shape is tested by it.
 *
 * What is preserved exactly, because the parsers are tested against it: message
 * structure, label wording and spelling, the presence or absence of spaces around
 * labels and currency tokens, transaction amounts, and the last four digits of
 * cards. Card fragments are the only identifiers kept, and four digits identify no
 * one on their own.
 *
 * ## Why these are transcriptions and not raw captures
 *
 * They were read off screenshots of an RTL conversation view, where the display
 * order of a Latin run inside Arabic text is not its order in the string. Anything
 * in these fixtures whose *character order* matters - dates above all - is
 * therefore suspect, and no parser should be built to depend on it. See
 * [sa.masrouf.core.capture.RawMessage.receivedAt] for what the app trusts instead.
 */
object RealMessages {

    // ---- AlAhli, older template family --------------------------------------
    //
    // NCB/AlAhli wrote a different vocabulary from the SNB templates elsewhere in
    // this file, and 87% of a real 3,361-message corpus matched no rule. Captured
    // from that corpus; balances invented, account masks kept as the bank writes
    // them, merchants kept because they are what the merchant patterns are tested
    // against.

    /** A card purchase, despite opening with سحب. The card and merchant identify it. */
    const val ALAHLI_CARD_PURCHASE = """سحب مبلغ 299.25 SAR
بطاقة 9552*
من SHBABIK RESTAURANT
في 01/15 12:18
الصرف المتبقي 10000.00 SAR"""

    /** Settling the credit card. Not spending. */
    const val ALAHLI_CARD_SETTLEMENT = """مدفوعات بطاقة ائتمانية
مبلغ 4900.51 ريال
حساب 104*010
في  01/15 12:34
الرصيد 10000.00"""

    /** Money going onto a card. Credit. */
    const val ALAHLI_CARD_TOPUP = """إيداع في بطاقة 4007*
مبلغ 4900.51
في 01/15
الصرف المتبقي 10000.00 SAR"""

    /** Money leaving the account, no card and no merchant named. */
    const val ALAHLI_ACCOUNT_WITHDRAWAL =
        """سحب من حساب104*010 مبلغSAR1500 في2026/07/27 08:56 الرصيد المتاح 10000.00"""

    /**
     * A foreign-currency purchase that also quotes a SAR balance.
     *
     * The case the parser must REFUSE. Before the currency guard the extractor
     * returned 6127.16 - the remaining balance - as the amount spent, turning a
     * 1058.66 AED purchase into a fabricated four-figure riyal transaction. 159
     * messages of this shape in one inbox.
     */
    const val ALAHLI_FOREIGN_PURCHASE = """سحب مبلغ 1058.66 AED
بطاقة 6000*
من MUMZWORLD FZ LLC
في 11/22 21:02
الصرف المتبقي 6127.16 SAR"""

    // ---- Emirates NBD ------------------------------------------------------
    //
    // Captured from the owner's inbox. Names replaced, balances invented, card
    // fragments kept because they are what the card patterns are tested against.
    // The card is masked "XX9994" rather than with an asterisk, which is why this
    // bank needs its own patterns despite wording that resembles SNB's.

    /** Point of sale, Arabic. */
    const val ENBD_POS_PURCHASE = """شراء بطاقة نقاط بيع (أثير)
بطاقة: فيزا الائتمانية XX9994
مبلغ: SAR 99.00
لدى: Aldrees 1437
في SAUDI ARABIA
رصيد: 10,000.00 ريال
في: 2026-08-11 02:29:51"""

    /** Online purchase. Note the thousands separator in the amount. */
    const val ENBD_ONLINE_PURCHASE = """شراء إنترنت
بطاقة: فيزا الائتمانية XX9994
مبلغ: SAR 15,000.00
لدى: SADAD payment
رصيد: 10,000.00 ريال
في: 2026-08-22 17:05:27"""

    /** Incoming transfer, English template. */
    const val ENBD_INCOMING_TRANSFER = """Incoming Fund Transfer Credited
to Account: XX8101
Amount: 585.00 SAR
From: RECIPIENT NAME ARAB NATIONAL BANK
IBAN: XX0018
at: 2026-08-23 12:32:24"""

    /** Paying off the credit card. Not spending. */
    const val ENBD_CARD_PAYMENT = """بطاقة إئتمانية: تأكيد السداد
بطاقة: XX9994;إئتمانية
مبلغ: 599.00  SAR
رصيد: 10,000.00 SAR
في: 23-08-2026"""

    // ---- AlRajhiBank -------------------------------------------------------

    /**
     * A prize-draw advertisement from the bank's own sender.
     *
     * Captured on the owner's phone, where it was stored as a 0.00 SAR cash
     * withdrawal: `السحب` contains `سحب` and `النقدية` contains `نقدي`, which are
     * both tokens of the ATM rule. Kept as the case the gate must refuse.
     */
    const val RAJHI_PRIZE_DRAW_ADVERT = """باقي على السحب الأسبوعي ثلاث أيام
ضاعف ادخاراتك في حساب المليون الادخاري لزيادة فرصك للفوز بالجائزة النقدية 10،000 ريال."""


    // The English templates. Found by running 5,074 real AlRajhi messages through
    // the pipeline: 182 transactions were being missed because only the Arabic
    // wordings were known. Names replaced, balances invented, amounts and card
    // fragments kept.

    /** Point of sale, English. The merchant follows "At:" and the card "By:". */
    const val RAJHI_POS_ENGLISH = """PoS
By:1335 ;Visa
Amount:SAR 339
At:MERCHANT NAME
Balance:SAR 10000.00
Date:23-9-15 23:19"""

    /** A reversal - the bank undoing its own entry, so the money comes back. */
    const val RAJHI_REVERSAL = """عكس عملية
بطاقة:2383;فيزا
مبلغ:1 SAR
لدى:MYSR*Jahe
رصيد:10000.00 SAR
17/4/26 10:31"""

    /** Between the user's own accounts. Not spending. */
    const val RAJHI_INTERNAL_TRANSFER = """Internal Transfer
Amount:SAR 27.23
To:3016
From:RECIPIENT NAME
Date:23-11-25 16:28"""

    /** Out to someone else, with a separate fee line. */
    const val RAJHI_LOCAL_TRANSFER = """Local Transfer
Bank:SNB
From:3016
Amount:SAR 20000
To:RECIPIENT NAME
Fees:SAR 1.15
Ref:0000000000000000
Date:23-11-28 18:20"""

    /**
     * Paying off the card. Not spending - the purchases that built the balance
     * were counted when they happened.
     */
    const val RAJHI_CREDIT_CARD_PAYMENT = """Credit Card:Payment
Card:1335 ;Visa
Amount:SAR 700
Balance:SAR 10000.00
Date:23-11-30 12:22"""

    /** Monthly profit on a savings account. Income. */
    const val RAJHI_SAVINGS_PROFIT = """ايداع:الأرباح الشهرية لحساب الادخار
مبلغ:.04 SAR
إلى:0111
1/2/26 05:47"""

    /**
     * An international purchase, priced in a foreign currency.
     *
     * Kept as a fixture for the case the parser must REFUSE. The amount is USD and
     * this app stores integer halalas of SAR; reading "1" as one riyal would be a
     * fabricated number, and the balance line would be worse. It stays
     * NotUnderstood until the model can hold a currency.
     */
    const val RAJHI_INTERNATIONAL_PURCHASE = """شراء دولي
بطاقة:7404 ;فيزا
مبلغ:USD 1
لدى:GOOGLE*CH
دولة:USA
رصيد:SAR 10000.00
في:25-6-4 20:33"""


    /** Online card purchase. Note the balance line carrying a much larger amount. */
    const val RAJHI_ONLINE_PURCHASE = """شراء إنترنت بـSR 931.64
عبر2383;فيزا
لIHERB ARA
رصيد:SR 10000.00
16:59 22/8/26"""

    /** Point-of-sale purchase, short template. Amount label is glued: "بـSR". */
    const val RAJHI_POS_SHORT = """شراء PoS
عبر5763;مدى-جوجل باي
بـSR 8.28
لASIAN POLYCLINI
26/8/27 15:09"""

    /** Point-of-sale purchase, long template with explicit labels. */
    const val RAJHI_POS_LONG = """شراء عبر نقاط البيع
بطاقة:2383-فيزا ;Google Pay
لدى:Fourth fr
مبلغ:SAR 320
رصيد:SAR 10000.00
22:52 26/8/26"""

    /** Incoming local transfer. */
    const val RAJHI_TRANSFER_IN = """حوالة محلية واردة بـSR 2000
لـ3016
من0018;RECIPIENT NAME
26/8/27 08:05"""

    /** Credit card refund - money coming back, not going out. */
    const val RAJHI_CARD_REFUND = """بطاقة ائتمانية استرداد مبلغ
بطاقة: 2383; فيزا
مبلغ: SAR 1138.71
التاجر: Amazon SA
في: 24/8/26 10:34"""

    /**
     * The same settlement, with the card field's halves reversed.
     *
     * AlRajhi began sending "عبر:فيزا;8134" in April 2026 where it had always sent
     * "عبر8134;فيزا". Ten of these arrived with no card attached: the amount and
     * the balance were read correctly and belonged to nothing, so the tile for a
     * card paid off in full kept showing what was left before it was paid.
     */
    const val RAJHI_SETTLEMENT_CARD_LAST = """بطاقة فيزا:سداد بـSR 3000 
عبر:فيزا;8134
رصيد:41000 SR
؜2/5/26 22:46"""

    /** Credit card settlement payment. */
    const val RAJHI_CARD_SETTLEMENT = """بطاقة فيزا:سداد بـSR 10000
عبر2383;فيزا
رصيد:SR 10000.00
17:04 22/8/26"""

    /**
     * A code issued to authorise a transfer, carrying that transfer's full amount.
     *
     * Captured from the owner's own inbox, where it had been stored as a confirmed
     * 25,000-riyal transfer out - a movement that never happened, with the code
     * itself kept on disk beside it. The code is redacted; the wording is exact.
     */
    const val RAJHI_TEMPORARY_CODE = """رمز مؤقت:000000
لـ :تحويل - الراجحي اعمال
المبلغ:25000 SAR"""

    /**
     * The same thing in English, from AlAhli's bill-payment template.
     *
     * The gate already knew the Arabic "رمز التفعيل" and the English "ONE TIME
     * PASSWORD", which is exactly what made this look covered - neither phrase is
     * in this body. Three of them were stored as confirmed bill payments.
     */
    const val SNB_BILL_ACTIVATION_CODE = """Requested an activation code 000000 for One Time Bill 
Payment
Biller: 054
Amount: 500 SAR
Date: 30/11/2023 08:44:37"""

    /**
     * The monthly statement notice. Announces what is owed; nothing has moved. The
     * figure in it was stored as a payment nine times over.
     */
    const val RAJHI_CARD_STATEMENT_NOTICE = """بطاقة ائتمانية: كشف حساب شهر July
البطاقة: 2383
إجمالي المبلغ المستحق:SAR 16608.05
المبلغ الأدنى المستحق:SAR 830.4
كما يمكنك سداد مستحقات البطاقة عن طريق رقم سداد: 000000000
تاريخ الاستحقاق: 25-08-2026"""

    /** The older AlAhli wording for the same notice. */
    const val SNB_CARD_STATEMENT_NOTICE = """عزيزي العميل
تم اصدار كشف حساب بطاقتك الائتمانية.
الحد الأدنى المستحق للبطاقة الائتمانية المنتهية ب XX9994 هو: 250.00 ريال سعودي
المبلغ الإجمالي: 5,000.00 ريال سعودي
تاريخ الاستحقاق: 01-08-2026"""

    /** The English settlement template. A card is named; no biller is. */
    const val RAJHI_CARD_SETTLEMENT_EN = """Bill Payment
Card:1335 ;Visa
Amount:SAR 442.75
Balance:SAR 33557.25
Date:23-9-7 11:03"""

    /**
     * A SADAD payment whose biller is the user's own AlRajhi credit card. Every
     * word says bill; only the number 255 says the money stayed with the owner.
     */
    const val SNB_SADAD_TO_OWN_CARD = """سداد فاتورة
مبلغ 15653.70 SAR
من 104*010
مفوتر 255
فاتورة 0000000000000000
في 24/06/24 13:29"""

    /**
     * The funding leg: the credit card being charged to settle another credit card.
     * Reads as an ordinary online purchase, and names no destination at all.
     */
    const val ENBD_CARD_SETTLES_OTHER_CARD = """شراء إنترنت
بطاقة: فيزا الائتمانية XX9994 
مبلغ: SAR 15,000.00
لدى: SADAD payment 
رصيد: 64,901.00 ريال 
في: 2026-08-22 17:05:27"""

    /**
     * A genuine utility bill paid by card, on the older template. Same rail, same
     * merchant, and it must keep counting as spending - which is why the rule for
     * the message above cannot simply be "SADAD".
     */
    const val SNB_SADAD_UTILITY_BY_CARD = """سحب مبلغ 234.28 SAR 
بطاقة 2887* 
من SADAD PAYMENT 
في 06/26 21:51 
الصرف المتبقي 23339.92 SAR"""

    /** A machine withdrawal that names the card it was made with. */
    const val SNB_ATM_WITHDRAWAL_BY_CARD = """سحب نقدي بالريال - صراف الأهلي
مبلغ SAR 450
بطاقة مدى *2907
حساب 104*010
موقع K.FAHAD RES COMPLEX"""

    /** A cash advance taken against a credit card. Cash out, not a purchase. */
    const val CASH_ADVANCE_ON_CREDIT_CARD = """سحب النقدي
مبلغ SAR 69.20
من بطاقة إئتمانية **8887
تاريخ 28/11/24 10:02
حد الصرف المتبقي SAR 6500.00"""

    /**
     * A shop purchase written with the same verb and the same card field. The only
     * thing that separates it from the two above is that it says neither نقدي nor
     * صراف, and it names where the money went.
     */
    const val CARD_PURCHASE_WRITTEN_AS_SAHB = """سحب مبلغ 159.00 SAR
بطاقة **0926*
من Monsoon Accessorize
في 01/10/2022 11:43"""

    /** An ordinary purchase on the same credit card. Spending, and must stay so. */
    const val ENBD_ORDINARY_PURCHASE = """شراء بطاقة نقاط بيع (أثير)
بطاقة: فيزا الائتمانية XX9994 
مبلغ: SAR 99.00 
لدى: Aldrees 1437 
في SAUDI ARABIA"""

    /** A SADAD payment to a real utility, for contrast with the biller above. */
    const val SNB_SADAD_ELECTRICITY = """سداد فاتورة
مبلغ 152.46 SAR
من 104*010
مفوتر 001
فاتورة 00000000000
في 26/03/24 13:29"""

    /** An outgoing transfer to the owner's own account at another bank. */
    const val BARQ_TRANSFER_TO_SELF = """حوالة صادرة محلية
مبلغ2850.00SAR
رسوم0.00SAR
الى OWNER NAME
بنكD360 BANK
لحساب2207
2026-08-27 08:03"""

    /** The same, with the surname masked mid-word by a different bank. */
    const val D360_TRANSFER_TO_SELF = """حوالة مالية صادرة مقبولة
خصمت من حساب: ****2207
القيمة: SAR 4,600.00
إلى: مالك الحساب اسم****
رقم الحساب: ****8101"""

    /**
     * An outgoing transfer to a relative who shares the surname. Real spending, and
     * the reason the owner is matched on two names rather than one.
     *
     * Both names are placeholders. What this fixture tests is that a recipient who
     * shares the owner's SURNAME but not his given name stays a transfer out, so
     * the tokens the test configures are placeholders too - the shape is the
     * subject, never the name.
     */
    const val SNB_TRANSFER_TO_RELATIVE = """حوالة صادرة داخلية
مبلغ:20 SAR
إلى:RECIPIENT NAME اسم
إلى:100*013
في:10/06/24 16:47"""

    /**
     * One-time password. Carries the full amount and merchant of the purchase it
     * authorises, and arrives seconds before the real confirmation for that same
     * purchase. Must never be parsed and must never be stored.
     */
    /**
     * A second one-time-password wording, found only by running a real corpus
     * through the gate. The code is redacted; the wording is exact.
     */
    const val RAJHI_OTP_PASSWORD_WORDING = """كلمة مرور لمرة واحدة
رمز: 000000
لـ :سداد الفواتير -تطبيق المباشر"""

    const val RAJHI_OTP = """ننصح بعدم مشاركة الرمز لحمايتك من الاحتيال
الرمز:000000
بطاقة:*2383
مبلغ:SAR 931.64
لدى:iHerb Arabia Co.
في:26/08/22 16:58"""

    // ---- SNB-AlAhli --------------------------------------------------------

    /** Online purchase. Amount label glued as "بـSAR", amount has no decimals. */
    const val SNB_ONLINE_PURCHASE = """شراء انترنت
بـSAR 35
من *0104
من barq
مدى *1887
في 21/08/26 14:41"""

    const val SNB_TRANSFER_IN = """حوالة واردة داخلية بSAR 35
من1007* RECIPIENT NAME
18/08/26 02:00"""

    const val SNB_TRANSFER_OUT = """حوالة صادرة داخلية بSAR 2000
ل0106* RECIPIENT NAME
20/08/26 22:08"""

    const val SNB_ATM_DEPOSIT = """ايداع صراف آلي
مبلغ SAR 2000
حساب 010*104
في 20/08/26 21:57"""

    /** One-time password. Same hazard as [RAJHI_OTP]. */
    const val SNB_OTP = """الرقم السري لمره واحده لعملية الشراء عبر الإنترنت
000000
مبلغ SAR 35.0
لدى barq
لبطاقة *1887
تاريخ 21/08/2026 14:40"""

    /** Activation code for an in-bank transfer. Also an OTP. */
    const val SNB_ACTIVATION_CODE = """لا تشارك رمز التفعيل 000000
تحويل داخل البنك
مبلغ SAR 2000"""

    // ---- D360 Bank ---------------------------------------------------------

    const val D360_TRANSFER_IN = """حوالة واردة: ARAB NATIONAL BANK
مبلغ: SAR 2,850.00
من: ****RECIPIENT NAME
حساب: 2207****
في: 27-08-2026 08:04"""

    const val D360_TRANSFER_OUT = """حوالة محلية صادرة
من : 2207****
مبلغ: SAR 350.00
إلى: RECIPIENT NAME****
الحساب: AlRajhi Bank - 0450****
في: 27-08-2026 08:06"""

    /**
     * Transfer between two accounts the user owns.
     *
     * Not spending. Counting it would inflate the monthly total by the full amount
     * while the money never left the user's control. Note the currency token sits
     * *after* the number and is the Arabic word, not "SAR".
     */
    const val D360_OWN_ACCOUNTS_TRANSFER = """تحويل بين حساباتك
من: *2207
المبلغ: 2,500.00 ريال
إلى: *3280
في: 27-08-2026 08:07"""

    const val D360_OTP = """<#> رمز التحقق: 000000
المبلغ: SAR 350
الخدمة: حوالة محلية 3Vu5reeDdfj"""

    // ---- barq wallet -------------------------------------------------------

    /**
     * Outgoing transfer. The amount label, number and currency are glued together
     * with no separators, and a second amount (fees) follows immediately.
     */
    const val BARQ_TRANSFER_OUT = """حوالة صادرة محلية
مبلغ2000.00SAR
رسوم0.00SAR
الى RECIPIENT NAME
بنكRAJHI BANK
لحساب3016
2026-08-27 08:04"""

    /** Wallet top-up, sent in English. */
    const val BARQ_TOPUP_EN = """Money Added to your Barq wallet
amount: 5000.0 SAR
via: Card
card number: **1887, mada
2026-08-27 08:51"""

    /** Online purchase. Balance is glued to its label with no separator. */
    const val BARQ_ONLINE_PURCHASE = """شراء إنترنت
فيزا SAR 1.00
رصيد15.18
لدىNoon
2026-08-26 16:43"""

    /**
     * A declined transaction.
     *
     * Structurally identical to a successful purchase: card, amount, merchant,
     * timestamp. No money moved. Recording it invents an expense.
     */
    const val BARQ_DECLINED = """عملية مرفوضة رصيد غير كافي
بطاقة**2166
مبلغ SAR 49.99
لدى Google YouTubePremium
2026-08-26 21:56"""

    const val BARQ_OTP = """رمز التحقق لعملية الشراءعبرالإنترنت
الرمز000000
مبلغ1.0SAR
لدى Noon One Subscription
2026-08-26 16:42:55"""

    // ---- Senders that do not use newlines -----------------------------------
    //
    // Four templates that stored an amount and no merchant at all, each for the
    // same reason: every field pattern in this app is anchored to the start of a
    // line, and these senders end a field with something else. 588 records in a
    // real corpus carried no party; these are what most of them looked like.

    /**
     * Emirates NBD's أثير purchases, whose fields are separated by a carriage
     * return alone. Written as a code point, per the rule against invisible
     * characters in source - it is invisible in an editor and in a diff.
     *
     * Balance invented.
     */
    val ENBD_ATHIR_PURCHASE = listOf(
        "شراء بطاقة نقاط بيع (أثير)",
        "بطاقة: فيزا الائتمانية XX9994 ",
        "مبلغ: SAR 99.00 ",
        "لدى: Aldrees 1437 ",
        "في SAUDI ARABIA ",
        "رصيد: 11,111.00 ريال ",
        "في: 2026-08-11 02:29:51 ",
    ).joinToString(Char(0x0D).toString())

    /**
     * The same message as it reaches storage on this owner's phone: the carriage
     * return already written out in caret notation, two ordinary characters. 68
     * stored bodies carry it. No bank writes `^M` on purpose, so whatever produced
     * it sits above this app - and the app has to read it either way.
     */
    val ENBD_ATHIR_PURCHASE_CARET =
        ENBD_ATHIR_PURCHASE.replace(Char(0x0D).toString(), "^M")

    /** SNB's mada Pay template: one line, fields divided by pipes, merchant labelled. */
    const val SNB_MADA_PAY_PIPES =
        "شراء عبر نقاط البيع (مدى Pay) | مبلغ 25 SAR | اسم المتجر FOWL AL TAKHSSY | " +
            "بطاقة مدى *2907 | حساب 104*010 | في 17/06/2023 09:30"

    /**
     * The same family with a terminal id in front of the merchant. The `من` guard
     * refuses a value that starts with digits, because that is also how an account
     * number appears; the name is read from after them.
     */
    const val SNB_POS_TERMINAL_ID =
        "شراء عبر نقاط البيع (Mada Pay)\nمبلغ 236.00 SAR\nبطاقة ائتمانية ***0926 \n" +
            "من 21140 CENTERPOINT -DOM\nالتاريخ 04/02/2023 21:02\nالصرف المتبقي 2,222.00 SAR"

    /**
     * AlRajhi's 2015-2019 template: the whole transaction on one line, the merchant
     * unlabelled between "في" and the date, the columns padded with spaces. Its
     * "سحب" is a purchase at a terminal, not a machine withdrawal. 335 records.
     *
     * Balance invented; the padding is kept exactly, because it is the boundary.
     */
    const val RAJHI_FLAT_POS =
        "سحب مبلغ 289.00 SAR بطاقة 1004* في EXTRA                    MAKKAH       SA " +
            "2015/09/02 19:42 الصرف المتبقي 33,333.00 SAR"

    /** A purchase the bank refused. No money moved, so nothing may be stored. */
    const val CARD_DECLINED_AS_INACTIVE =
        "تم رفض العملية: البطاقة غير نشطة\nالعملية: شراء\nالبطاقة: 7404\n" +
            "المبلغ: SAR 97.31\nالحساب: Mrsool\nالتاريخ: 8/3/26 2:07"

    // ---- STC Pay, the wallet that became STC Bank ---------------------------
    //
    // 4,446 messages spanning 2019-2026, none of them ever read: no profile
    // claimed the sender. Names and account numbers replaced, balances invented,
    // codes zeroed; wording, labels and structure exactly as sent.

    /** The commonest purchase template. The merchant follows "في:", as does a date. */
    const val STC_ONLINE_PURCHASE = """مشتريات إنترنت
البطاقة: ***8611؛ VISA
المبلغ: 10 SAR 
في: Health Endowment Fund
من حساب رقم: ***0000
بتاريخ: 2024/09/10 08:34:51"""

    /** A purchase at a terminal, paid with the wallet's card through Apple Pay. */
    const val STC_POS_PURCHASE = """مشتريات داخلية
البطاقة: ***4735؛ Apple Pay
المبلغ: 20 SAR 
في: SPEED TRACK3
بتاريخ: 2024/09/06 21:49:36"""

    /**
     * The terse template, which writes the card and the merchant under the same
     * label and tells them apart only by the asterisk.
     */
    const val STC_CARD_PURCHASE = """شراء VISA
من:*7667
بـ:72.59 SAR
من:AL DRE
في: 26/06/26 01:58"""

    /** His own money entering his own wallet. Never spending, never income. */
    const val STC_WALLET_TOPUP = """تغذية محفظة stc pay
بـ  :  100.00 ر.س 
البطاقة: 2907*، مدى
في: 22/01/25 - 21:01"""

    /** The same, in the wallet's newer wording. */
    const val STC_ADD_MONEY = """إضافة أموال لحسابك
بـ:1000.00 ر.س
عبر:*5763
في:2026-05-16 11:55"""

    /**
     * Wages sent abroad. Filed by the channel; the recipient is a domestic worker
     * and her name is stored as the note, never matched against a shipped rule.
     */
    const val STC_WESTERN_UNION = """خصم عبر حوالة دولية
المبلغ : 1,482.75 ر.س
الرسوم: 17.25 ر.س
اسم المرسل: OWNER NAME
MTCN: 0000000000
اسم المستلم: RECIPIENT NAME
حساب المستلم: استلام عبر ويسترين يونيون
دولة المستلم: Philippines
شركة الحوالات: ويسترين يونيون
بتاريخ: 2024-06-02 10:12:00"""

    /** The short form of the same transfer, which names no channel but says WU. */
    const val STC_WU_SHORT = """حوالة WU
مبلغ:982.75 ر.س
رسوم: 17.25 ر.س
MTCN:0000000000
إلى: RECIPIENT NAME
حساب:000*
 دولة:أندونيسيا
 في:16/05/26 11:55"""

    /**
     * The wallet's most common message of all - 889 of them - and the reason the
     * gate had to learn a new wording before this sender got a parser. It carries a
     * live code and the full amount of the purchase it authorises.
     */
    const val STC_SECURITY_CODE =
        "رمز الأمان هو: 000000 لدفع مبلغ SAR 120.00 للتاجر: Mrsool"

    /** A purchase the wallet refused for want of balance. */
    const val STC_DECLINED = """رصيد غير كافي
لا يمكن إتمام العملية
المبلغ: 250.00 ر.س
بتاريخ: 2024/03/02 19:00:00"""

    /**
     * An incoming transfer whose sender is on the heading line, which is how SNB
     * wrote it until 2021. The employer's name is what makes this an allowance
     * rather than an ordinary transfer, and it was being thrown away.
     *
     * The employer is an organisation, not a person, so it stays as sent.
     */
    const val SNB_INCOMING_SENDER_INLINE = """حوالة محلية واردة من امانة العاصمة المقدسة
مبلغ SAR 3664.10
حساب 104*010
عبر 
في 26/11/2020 10:33"""

    /**
     * A share dividend the company pays out "بصيغة إيداع راتب". The bank message is
     * word for word a salary deposit; only the company's own SMS, sent the same
     * day, says what it is. Kept because it is what makes the dashboard's salary
     * reading a MAX over three rather than the newest row.
     */
    const val DIVIDEND_PAID_AS_SALARY = """ايداع رواتب
مبلغ SAR 50
حساب0104*
في 21/07/25 13:53"""

    // ---- SNB Capital, the brokerage -----------------------------------------
    //
    // 1,136 messages skipped as an unknown sender: "SNB-Capital" contains none of
    // the bank's own sender ids. Account numbers zeroed, amounts kept.

    /** Money leaving the current account for the investment account. His own. */
    const val CAPITAL_TO_INVESTMENT =
        "تم تحويل مبلغ 5,000.00 ر.س من الحساب الجاري (104**010) الى الحساب الاستثماري " +
            "00000000000000 بتاريخ 09:06:30 2025-06-17. رمز العملية 00000000000000"

    /** And the same movement back. */
    const val CAPITAL_TO_CURRENT =
        "تم تحويل مبلغ 826.56 ر.س من الحساب الاستثماري 00000000000000 الى الحساب الجاري " +
            "(104**010) بتاريخ 09:06:30 2025-06-17. مجموع الرصيد الاستثماري المتاح هو 0 ر.س"

    /** A share dividend. Money earned, arriving in the investment account. */
    const val CAPITAL_DIVIDEND =
        "تم إيداع 19.26 ر.س أرباح شركة (أرباح ارامكو الربع الثالث 2024 EXTN00000000FNRO) " +
            "في الحساب الاستثماري رقم 00000000000000"

    /**
     * A limit, not a purchase. Stored as a 200,000-riyal purchase the moment the
     * inbox was re-read - the largest single figure in the whole history, and no
     * money moved at all.
     */
    /**
     * A share order filled. No total in it - a unit price and a count - so the
     * extractor read the order number and stored 74 halalas. The cash that moved is
     * reported separately as a movement of the investment account.
     */
    const val CAPITAL_ORDER_FILLED =
        "تم تنفيذ أمر شراء رقم .000000000000000 للرمز1180 (بتاريخ 10:44:17 2020-11-03، " +
            "الكمية المنفذة 12، سعر التنفيذ 39.650000 ريال)"

    /** A transfer the brokerage could not complete. No money moved. */
    const val CAPITAL_TRANSFER_FAILED =
        "لقد تعذر اتمام عملية تحويل بمبلغ 500.00 ر.س الى حسابك الجاري 00000000000000 " +
            "بتاريخ 08:25:38 2021-01-27، الرجاء المحاولة لاحقاً"

    const val CARD_LIMIT_RAISED =
        "Your Debit Card 5358XXXXXXXX2907 daily POS limit has been increased to SR 200000"

    // ---- Bank AlJazira and SAIB, added when the owner named their cards -----
    //
    // AlJazira writes in English; SAIB masks accounts with an X-run. Names
    // replaced, accounts zeroed, structure exact.

    /** AlJazira: the card under "By:", the ACCOUNT under "From:", the shop at "At:". */
    const val JAZIRA_ONLINE_PURCHASE = """Online Purchase
By:3761;mada
From: 0001
Amount: SAR 3,000.00
At: barq
Date: 2026-06-29 12:24"""

    const val JAZIRA_POS_PURCHASE = """POS Purchase (Google Pay)
at: tarwah alarabyh est
of: 1.00 SAR
on: 2026-05-10 21:10
Mada card: 3761"""

    const val JAZIRA_INCOMING_TRANSFER = """Incoming Fund Transfer 
Credited to Account: 0001
Amount: 3,992.36 SAR
From: SENDER NAME**
[Arab National Bank]
Debit from account: 0018
at: 2026-06-16 08:46
Ref: 0B00000000000000"""

    /** A reminder that a payment is LATE - the سداد rules must never read it as one. */
    const val JAZIRA_PAYMENT_OVERDUE =
        "نود التنبيه بأنك تجاوزت موعد سداد بطاقتك رقم2650 ، الرجاء سداد المبلغ المستحق في أقرب وقت ممكن."

    /** SAIB: the account masked as an X-run, under the same word as a person. */
    const val SAIB_ONLINE_PURCHASE = """شراء انترنت
بطاقة: XXX9097 مدى 
من: XXX1001 
مبلغ: SAR 10.00
لدى: Health Endowment Fund R 
في: 04-07 14:42"""

    const val SAIB_INCOMING_TRANSFER = """حوالة واردة: داخلية
مبلغ: SAR 56.00
الى: XXX1001 
من: SENDER NAME XXX2001 
في: 12-30 19:25"""

    // ---- urpay, Vision Bank and meem, added 2026-09-02 -----------------------
    //
    // Three senders the owner confirmed as his on 2026-09-01, none of which any
    // profile had claimed: urpay (179 messages, 2022-2024), Vision Bank (115,
    // 2025-2026) and meem under three sender ids (659, 2015-2024). Names replaced,
    // references and phone numbers zeroed to their length, balances invented,
    // structure exact.

    /** urpay, newer template: the card bare, the merchant under "من:". */
    const val URPAY_ONLINE_PURCHASE = """شراء إنترنت
بطاقة:4322
مبلغ:SAR 61
من:NETFLIX...
في:1-11-2023 15:59"""

    /** urpay, older template: the wallet's name inside the card field, the merchant under "لدى:". */
    const val URPAY_ONLINE_PURCHASE_OLD = """شراء إنترنت
بطاقة:  urpay بطاقة ***4322 
مبلغ: SAR 76.61
لدى: ALNAHDI MEDICAL CO
رصيد: 40.00
في: 09-10-2022 12:28"""

    /** The card written digits-first: "4322***;urpay بطاقة". */
    const val URPAY_POS_PURCHASE = """شراء عبر نقاط البيع
بطاقة: 4322***;urpay بطاقة
مبلغ: SAR 90.0
لدى: SASCO ELZAIDI STATION
في: 30-12-2022 21:10
رصيدك الحالي: 50"""

    /** Phone credit bought from the wallet. No shop; the operator is the party. */
    const val URPAY_PHONE_CREDIT = """خصم من المحفظة لـ (شحن خطوط الاتصال)

المبلغ: 115 ريال
مزوّد الخدمة: STC
رقم الجوال: +966500000000

الرصيد الحالي: 60.00 ريال"""

    /** Reward points paid out into the wallet. Money back, not money moved. */
    const val URPAY_REWARD_POINTS = """اضافة اموال عن طريق نقاط مكافأة
المبلغ: 342.5 ريال
التاريخ: 20-04-2023 03:07"""

    const val URPAY_CASHBACK = """استرداد نقدي
بطاقة:  4322***;urpay بطاقة
مبلغ: SAR 3.88
في: 06-08-2022 16:31"""

    /** Money arriving from another wallet. "وصلتك" is the only word that says so. */
    const val URPAY_WALLET_TRANSFER_IN = """مرحبًا OWNER
وصلتك حوالة عن طريق محفظة أخرى بقيمة 85.0 ريال إلى حسابك في 23-02-2023 14:53
رقم العملية 000000000

حدك الشهري المتبقي هو 15000.0 ريال."""

    /** An advert with a riyal figure in it. Was captured as a 15-riyal transfer. */
    const val URPAY_FEE_FREE_ADVERT = """حول لأي دولة في العالم بدون رسوم تحويل ولك حتى 15 ريال كاش باك عن طريق محفظة urpay

لمعلومات أكثر:
www.urpay.sa/ar/rewards"""

    /** The bank's side of a wallet top-up: a "purchase" at a shop called URPAY. */
    const val SNB_URPAY_TOPUP = """شراء إنترنت 
مبلغ 125.23 SAR
بطاقة ائتمانية ***0926 
من URPAY
التاريخ 16/04/24 04:59
الصرف المتبقي 1496.38 SAR"""

    /** Vision Bank: the card and the account both masked, under labels that name them. */
    const val VISION_POS_PURCHASE = """POS Local Purchase
From: MGHASL ZKIEAH ALGHAMDI
Amount: 12.00 SAR
Card Type: mada
Card Number: ****2455
Account Number: ****6000
30/05/2025 20:04:34
MAKKAH, SAU"""

    const val VISION_POS_PURCHASE_AR = """شراء عبر نقاط البيع (محلي - دولي) - أثير
من: MGHASL ZKIEAH ALGHAMDI
بمبلغ: 12.00 SAR
نوع البطاقة: مدى
رقم البطاقة: ****2455
رقم حساب البطاقة:  ****6000
التاريخ: 02/04/2026 19:10:21
الموقع: SAU, MAKKAH"""

    /** Money arriving. "Sender:" names the person, "From:" only the bank it came through. */
    const val VISION_CREDIT_TRANSFER = """Local Credit Transfer:
Reference:000000
Amount:160.00SAR
To Account Number:*6000
From:ARAB NATIONAL BANK
Sender:SENDER NAME
Account Number:*0018
25-05-2025 18:20"""

    /** Between his own Vision accounts; 5001 is the savings account the same sender announced. */
    const val VISION_OWN_ACCOUNTS = """اكتمل تحويل الأموال
From: ***6000
To: ***5001
Amount: SAR 10.00
Date: 13.03.2025 01:38"""

    const val VISION_PINCODE = "Your Pincode is 000000"

    /** meem, labelled template: the card under بطاقة, the account under من, the shop under لدى. */
    const val MEEM_POS_PURCHASE = """شراء عبر نقاط البيع
بطاقة: ***5654; مدى
من: ***2207
مبلغ: SAR 38.50
لدى: Express Food Company WM 9, MAKKAH, SA
في: 15/11/2019 13:27"""

    /** meem, 2017 prose: the shop sits between the amount and "على بطاقتك". */
    const val MEEM_PROSE_PURCHASE = """مرحبا،

تمت عملية شراء من نقطة بيع بمبلغ: SAR 400 من: Nesma على بطاقتك الإئتمانية المنتهية برقم: 4399XXXXXXXX0891 في: 21:02:14 15/02/2017. رصيدك المتوفر هو: 600 ريال سعودي"""

    /** meem, 2019 prose: "دفع عبر نقاط البيع", no word for شراء, the shop before "في". */
    const val MEEM_POS_PAYMENT = """عزيزي العميل،
تمت عملية دفع عبر نقاط البيع من حسابك المنتهي برقم: 209  بمبلغ 9.00 SAR من DUNKIN DONUTS 20059, MAKKAH, SA في 14/01/2019 07:15:49"""

    /** Money arriving, and nothing in it says وارد. */
    const val MEEM_INCOMING_INTERNAL = """عزيزي العميل،

تم إستلام حوالة داخلية بمبلغ 10,000.00 SAR على حسابك  بتاريخ 17/07/18 الساعة 09:04"""

    /** Money arriving, with only the bank it came through for a party. */
    const val MEEM_INCOMING_LOCAL = """حوالة واردة: محلية
عبر: NATIONAL COMMERCIAL BANK, THE
مبلغ: SAR 1,000.00
إلى: ***2207
في: 30/05/2019 10:22"""

    const val MEEM_CREDIT_TRANSFER_EN = """Credit transfer: Local
Via: NATIONAL COMMERCIAL BANK, THE
Amount: SAR 1,000.00
To: ***2207
Date: 09/07/2019 09:14"""

    /** Between his own meem accounts, with the noun حوالة rather than تحويل. */
    const val MEEM_OWN_ACCOUNTS_IN = """حوالة واردة: بين حساباتك
إلى: ***2209
مبلغ: SAR 1.00
من: ***2207
في: 13/05/2019 00:03"""

    /** The credit card thanking him for a payment, without the word سداد. */
    const val MEEM_CARD_PAYMENT_RECEIVED = """هلا ميمير،
مشكور استلمنا مبلغ 450.07 SAR  في 03/03/2016  لبطاقتك الإئتمانية رقم 4399XXXXXXXX0883 رصيدك الحالي هو 900.
معوض إن شاء الله"""

    /** The card written in full around an X-run: the last four are the card, the first four the BIN. */
    const val MEEM_CARD_SETTLEMENT = """بطاقة إئتمانية: تأكيد السداد
بطاقة: 4399XXXXXXXX0891; إئتمانية
مبلغ: SAR 287.5
رصيد: SAR 700
في: 29/12/2020 20:37:05"""

    const val MEEM_ATM_DEPOSIT =
        "هلا ميمر..   تم إيداع كاش في حسابك 207*** بمبلغ SAR 500 من ATM  في 05-JAN-16 21:17:20.. تتهنى فيها إن شاء الله"

    /** An outgoing transfer whose beneficiary is the owner. */
    const val MEEM_OUTGOING_TO_SELF = """حوالة صادرة محلية
مبلغ: SAR 30,005.75
اسم المستفيد: OWNER NAME
رقم حساب المستفيد: SA0000000000000000000000 
الرسوم  SAR 5.75
في: 03/03/2021 21:52"""

    /** A fee notice. Was captured as a refund of 2.25 riyals. */
    const val MEEM_FEE_NOTICE = """عزيزي العميل،

سيتم تحديث رسوم الصرف الدولي لبطاقات الصرف الآلي من 2.25% إلى 2.75% 
ابتداءً من تاريخ (8/12/2023) و سوف ينعكس ذلك أيضًا على الشروط والأحكام."""

    /** A template the bank sent unfilled, with an amount written to three decimals. */
    const val MEEM_PLACEHOLDER_TEMPLATE =
        "هلا ميمر! تمت عملية ناجحة بمبلغ: SAR 00000371.530 من: @MerchantName على بطاقتك الإئتمانية اللي تنتهي بـ: 4399XXXXXXXX0891 في: 22:17:28 23/01/2016. رصيدك الباقي هو : +00000500.00. معوض إن شاءالله."

    /** An offer. Was captured as ten thousand riyals of income. */
    const val MEEM_OFFER = """هلا OWNER
 
جبنا لك اليوم عرض رائع من عروض م
 
الكل يقدر يستمتع بأفضل نسبة ربح كل يوم مع وديعة المرابحة من م
بشكل يومي راح نراقب نسب السوق المصرفي علشان نضمن لك أفضل نسبة ربح. علشان تستفيد من العرض كل اللي عليك تسويه هو إنك تكمل اجراءات فتح حسابك في م بزيارة أحد مراكزنا.
وتودع مبلغ مايقل عن 10,000 ريال سعودي في وديعة المرابحة،
هذا العرض متاح لفترة محدودة فقط*"""

    const val MEEM_LOGIN_CODE = "الرمز السري المؤقت لتسجيل الدخول: 000000"

    const val MEEM_FAILED_TRANSFER = """عملية تحويل: حوالة محلية
الحالة: فاشلة
التاريخ: 09/03/2022 06:47:55"""

    /**
     * barq telling him the RECIPIENT has received a transfer he sent. Money
     * leaving, though it says استلام: the phrase rules must not read "استلام
     * حوالتك" as "استلام حوالة".
     */
    const val BARQ_TRANSFER_RECEIVED_ABROAD =
        "عزيزي العميل، تم استلام حوالتك الدولية بمبلغ 900 SAR. رقم المعاملة RMT000000-000000000."

    /** SNB, 2020: between his own accounts, with the noun حوالة. Stored 45 times as income. */
    const val SNB_INCOMING_OWN_ACCOUNTS = """حوالة واردة بين حساباتك
مبلغ 100SAR 
حساب205*880
في27/01/2021 08:21"""

    /** AlRajhi: the same movement, stored 53 times as money leaving. */
    const val RAJHI_OWN_ACCOUNTS = """حوالة بين حساباتك
مبلغ: SAR 5000
الى: 3016
في: 25-8-2 13:48"""

    /** SNB, 2015: one line, the shop after فى (alef maksura) and before the date. */
    const val SNB_APPROVED_2015 =
        "تمت الموافقة لسحب مبلغ 7248.00 SAR من بطاقة 1004** فى JARIR BOOK STORE         MAKKAH       SA بتاريخ 2015/01/19 الساعة 18:02 حد الصرف المتبقي 40000.00 SAR"

    /** SNB advert. Was stored as an 8,000-riyal purchase, twice. */
    const val SNB_CASHBACK_ADVERT =
        "استخدم بطاقة مدى من البنك الأهلي داخل أو خارج المملكة واسترجع حتى 8,000 ريال من قيمة مشترياتك. لابد أن يحالفك الحظ. تطبق الشروط والأحكام"

    /** AlJazira instalment advert. Stored as a 1,000-riyal purchase seven times over. */
    const val JAZIRA_INSTALMENT_ADVERT = """استخدم بطاقة بنك الجزيرة الائتمانية لدى ساكو أو عالم ساكو وقسط مشترياتك بسعر الكاش وبهامش ربح 0% مع برنامج لا تأجل عند الشراء بمبلغ 1000 ريال أو أكثر
لمزيد من المعلومات
www.baj.com.sa/epp"""

    /** A genuine SNB refund that ends the way an advert might. Must NOT be refused. */
    const val SNB_REFUND_WITH_FOOTER =
        "تم استرجاع مبلغ 10288.75 لبطاقتك الإئتمانية **2754. للمزيد من المعلومات الرجاء زيارة الأهلي موبايل"

    /** AlRajhi's loyalty ladder. Stored as a refund of 1,874 riyals. */
    const val RAJHI_POINTS_ADVERT =
        "متبقي لك 1874.12 ريال وتوصل للمستوى 4. استمر في استعمال بطاقتك الائتمانية لكسب نقاط مكافأة أكثر من الراجحي"

    // ---- Sender identities -------------------------------------------------

    /** SMS sender ids exactly as they appear on the device. */
    const val SENDER_RAJHI = "AlRajhiBank"
    const val SENDER_SNB = "SNB-AlAhli"
    const val SENDER_D360 = "D360 Bank"
    const val SENDER_BARQ = "barq app"
    const val SENDER_STC_PAY = "STCPAY"
    const val SENDER_URPAY = "urpay"
    const val SENDER_VISION = "Vision Bank"
    const val SENDER_MEEM = "meemKSA"

    data class Sample(val sender: String, val body: String)

    /** Completed transactions paired with the sender that actually sent them. */
    val COMPLETED_SAMPLES: List<Sample> = listOf(
        Sample(SENDER_RAJHI, RAJHI_ONLINE_PURCHASE),
        Sample(SENDER_RAJHI, RAJHI_POS_SHORT),
        Sample(SENDER_RAJHI, RAJHI_POS_LONG),
        Sample(SENDER_RAJHI, RAJHI_TRANSFER_IN),
        Sample(SENDER_RAJHI, RAJHI_CARD_REFUND),
        Sample(SENDER_RAJHI, RAJHI_CARD_SETTLEMENT),
        Sample(SENDER_SNB, SNB_ONLINE_PURCHASE),
        Sample(SENDER_SNB, SNB_TRANSFER_IN),
        Sample(SENDER_SNB, SNB_TRANSFER_OUT),
        Sample(SENDER_SNB, SNB_ATM_DEPOSIT),
        Sample(SENDER_D360, D360_TRANSFER_IN),
        Sample(SENDER_D360, D360_TRANSFER_OUT),
        Sample(SENDER_D360, D360_OWN_ACCOUNTS_TRANSFER),
        Sample(SENDER_BARQ, BARQ_TRANSFER_OUT),
        Sample(SENDER_BARQ, BARQ_TOPUP_EN),
        Sample(SENDER_BARQ, BARQ_ONLINE_PURCHASE),
        Sample(SENDER_STC_PAY, STC_ONLINE_PURCHASE),
        Sample(SENDER_STC_PAY, STC_POS_PURCHASE),
        Sample(SENDER_STC_PAY, STC_CARD_PURCHASE),
        Sample(SENDER_STC_PAY, STC_WESTERN_UNION),
        Sample(SENDER_URPAY, URPAY_ONLINE_PURCHASE),
        Sample(SENDER_URPAY, URPAY_ONLINE_PURCHASE_OLD),
        Sample(SENDER_URPAY, URPAY_POS_PURCHASE),
        Sample(SENDER_URPAY, URPAY_PHONE_CREDIT),
        Sample(SENDER_URPAY, URPAY_WALLET_TRANSFER_IN),
        Sample(SENDER_VISION, VISION_POS_PURCHASE),
        Sample(SENDER_VISION, VISION_POS_PURCHASE_AR),
        Sample(SENDER_VISION, VISION_CREDIT_TRANSFER),
        Sample(SENDER_VISION, VISION_OWN_ACCOUNTS),
        Sample(SENDER_MEEM, MEEM_POS_PURCHASE),
        Sample(SENDER_MEEM, MEEM_PROSE_PURCHASE),
        Sample(SENDER_MEEM, MEEM_POS_PAYMENT),
        Sample(SENDER_MEEM, MEEM_INCOMING_INTERNAL),
        Sample(SENDER_MEEM, MEEM_INCOMING_LOCAL),
        Sample(SENDER_MEEM, MEEM_CREDIT_TRANSFER_EN),
        Sample(SENDER_MEEM, MEEM_OWN_ACCOUNTS_IN),
        Sample(SENDER_MEEM, MEEM_CARD_SETTLEMENT),
        Sample(SENDER_MEEM, MEEM_ATM_DEPOSIT),
    )

    /** Rejectable messages paired with their sender. */
    val REJECTABLE_SAMPLES: List<Sample> = listOf(
        Sample(SENDER_RAJHI, RAJHI_OTP),
        Sample(SENDER_SNB, SNB_OTP),
        Sample(SENDER_SNB, SNB_ACTIVATION_CODE),
        Sample(SENDER_D360, D360_OTP),
        Sample(SENDER_BARQ, BARQ_OTP),
        Sample(SENDER_STC_PAY, STC_SECURITY_CODE),
        Sample(SENDER_STC_PAY, STC_DECLINED),
        Sample(SENDER_BARQ, BARQ_DECLINED),
        Sample(SENDER_URPAY, URPAY_FEE_FREE_ADVERT),
        Sample(SENDER_VISION, VISION_PINCODE),
        Sample(SENDER_MEEM, MEEM_FEE_NOTICE),
        Sample(SENDER_MEEM, MEEM_PLACEHOLDER_TEMPLATE),
        Sample(SENDER_MEEM, MEEM_OFFER),
        Sample(SENDER_MEEM, MEEM_LOGIN_CODE),
        Sample(SENDER_MEEM, MEEM_FAILED_TRANSFER),
        Sample(SENDER_SNB, SNB_CASHBACK_ADVERT),
        Sample("AlJaziraSMS", JAZIRA_INSTALMENT_ADVERT),
        Sample(SENDER_RAJHI, RAJHI_POINTS_ADVERT),
    )

    /** Every message that represents a real, completed movement of money. */
    val COMPLETED_TRANSACTIONS = listOf(
        RAJHI_ONLINE_PURCHASE, RAJHI_POS_SHORT, RAJHI_POS_LONG, RAJHI_TRANSFER_IN,
        RAJHI_CARD_REFUND, RAJHI_CARD_SETTLEMENT, RAJHI_CARD_SETTLEMENT_EN,
        SNB_SADAD_TO_OWN_CARD, SNB_SADAD_ELECTRICITY,
        BARQ_TRANSFER_TO_SELF, D360_TRANSFER_TO_SELF, SNB_TRANSFER_TO_RELATIVE,
        ENBD_CARD_SETTLES_OTHER_CARD, SNB_SADAD_UTILITY_BY_CARD, ENBD_ORDINARY_PURCHASE,
        SNB_ATM_WITHDRAWAL_BY_CARD, CASH_ADVANCE_ON_CREDIT_CARD, CARD_PURCHASE_WRITTEN_AS_SAHB,
        SNB_ONLINE_PURCHASE, SNB_TRANSFER_IN, SNB_TRANSFER_OUT, SNB_ATM_DEPOSIT,
        D360_TRANSFER_IN, D360_TRANSFER_OUT, D360_OWN_ACCOUNTS_TRANSFER,
        BARQ_TRANSFER_OUT, BARQ_TOPUP_EN, BARQ_ONLINE_PURCHASE,
        ENBD_ATHIR_PURCHASE, ENBD_ATHIR_PURCHASE_CARET, SNB_MADA_PAY_PIPES,
        SNB_POS_TERMINAL_ID, RAJHI_FLAT_POS,
        STC_ONLINE_PURCHASE, STC_POS_PURCHASE, STC_CARD_PURCHASE, STC_WALLET_TOPUP,
        STC_ADD_MONEY, STC_WESTERN_UNION, STC_WU_SHORT,
        CAPITAL_TO_INVESTMENT, CAPITAL_TO_CURRENT, CAPITAL_DIVIDEND,
        SNB_INCOMING_SENDER_INLINE, DIVIDEND_PAID_AS_SALARY,
        JAZIRA_ONLINE_PURCHASE, JAZIRA_POS_PURCHASE, JAZIRA_INCOMING_TRANSFER,
        SAIB_ONLINE_PURCHASE, SAIB_INCOMING_TRANSFER,
    )

    /** Every message that must never become a transaction. */
    val MUST_BE_REJECTED = listOf(
        RAJHI_OTP, SNB_OTP, SNB_ACTIVATION_CODE, D360_OTP, BARQ_OTP, BARQ_DECLINED,
        RAJHI_CARD_STATEMENT_NOTICE, SNB_CARD_STATEMENT_NOTICE,
        RAJHI_TEMPORARY_CODE, SNB_BILL_ACTIVATION_CODE, CARD_DECLINED_AS_INACTIVE,
        STC_SECURITY_CODE, STC_DECLINED, CARD_LIMIT_RAISED,
        CAPITAL_ORDER_FILLED, CAPITAL_TRANSFER_FAILED, JAZIRA_PAYMENT_OVERDUE,
    )
}
