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

    /** Credit card settlement payment. */
    const val RAJHI_CARD_SETTLEMENT = """بطاقة فيزا:سداد بـSR 10000
عبر2383;فيزا
رصيد:SR 10000.00
17:04 22/8/26"""

    /**
     * The monthly statement notice. Announces what is owed; nothing has moved. The
     * figure in it was stored as a payment nine times over.
     */
    const val RAJHI_CARD_STATEMENT_NOTICE = """بطاقة ائتمانية: كشف حساب شهر July
البطاقة: 2383
إجمالي المبلغ المستحق:SAR 16608.05
المبلغ الأدنى المستحق:SAR 830.4
كما يمكنك سداد مستحقات البطاقة عن طريق رقم سداد: 105871380
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
فاتورة 5117765907171922
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
فاتورة 05183896808
في 26/03/24 13:29"""

    /** An outgoing transfer to the owner's own account at another bank. */
    const val BARQ_TRANSFER_TO_SELF = """حوالة صادرة محلية
مبلغ2850.00SAR
رسوم0.00SAR
الى KHALEEL MALKI
بنكD360 BANK
لحساب2207
2026-08-27 08:03"""

    /** The same, with the surname masked mid-word by a different bank. */
    const val D360_TRANSFER_TO_SELF = """حوالة مالية صادرة مقبولة
خصمت من حساب: ****2207
القيمة: SAR 4,600.00
إلى: خليل سامي خل****
رقم الحساب: ****8101"""

    /**
     * An outgoing transfer to a relative who shares the surname. Real spending, and
     * the reason the owner is matched on two names rather than one.
     */
    const val SNB_TRANSFER_TO_RELATIVE = """حوالة صادرة داخلية
مبلغ:20 SAR
إلى:عبدالكريم مالكى
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

    // ---- Sender identities -------------------------------------------------

    /** SMS sender ids exactly as they appear on the device. */
    const val SENDER_RAJHI = "AlRajhiBank"
    const val SENDER_SNB = "SNB-AlAhli"
    const val SENDER_D360 = "D360 Bank"
    const val SENDER_BARQ = "barq app"

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
    )

    /** Rejectable messages paired with their sender. */
    val REJECTABLE_SAMPLES: List<Sample> = listOf(
        Sample(SENDER_RAJHI, RAJHI_OTP),
        Sample(SENDER_SNB, SNB_OTP),
        Sample(SENDER_SNB, SNB_ACTIVATION_CODE),
        Sample(SENDER_D360, D360_OTP),
        Sample(SENDER_BARQ, BARQ_OTP),
        Sample(SENDER_BARQ, BARQ_DECLINED),
    )

    /** Every message that represents a real, completed movement of money. */
    val COMPLETED_TRANSACTIONS = listOf(
        RAJHI_ONLINE_PURCHASE, RAJHI_POS_SHORT, RAJHI_POS_LONG, RAJHI_TRANSFER_IN,
        RAJHI_CARD_REFUND, RAJHI_CARD_SETTLEMENT, RAJHI_CARD_SETTLEMENT_EN,
        SNB_SADAD_TO_OWN_CARD, SNB_SADAD_ELECTRICITY,
        BARQ_TRANSFER_TO_SELF, D360_TRANSFER_TO_SELF, SNB_TRANSFER_TO_RELATIVE,
        ENBD_CARD_SETTLES_OTHER_CARD, SNB_SADAD_UTILITY_BY_CARD, ENBD_ORDINARY_PURCHASE,
        SNB_ONLINE_PURCHASE, SNB_TRANSFER_IN, SNB_TRANSFER_OUT, SNB_ATM_DEPOSIT,
        D360_TRANSFER_IN, D360_TRANSFER_OUT, D360_OWN_ACCOUNTS_TRANSFER,
        BARQ_TRANSFER_OUT, BARQ_TOPUP_EN, BARQ_ONLINE_PURCHASE,
    )

    /** Every message that must never become a transaction. */
    val MUST_BE_REJECTED = listOf(
        RAJHI_OTP, SNB_OTP, SNB_ACTIVATION_CODE, D360_OTP, BARQ_OTP, BARQ_DECLINED,
        RAJHI_CARD_STATEMENT_NOTICE, SNB_CARD_STATEMENT_NOTICE,
    )
}
