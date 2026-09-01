package sa.masrouf.core.capture

import sa.masrouf.core.text.ArabicText

/**
 * Decides whether a message may reach a parser at all.
 *
 * This runs before every parser and is the most important safety layer in the
 * capture pipeline, because of one property of Saudi bank messaging that is not
 * obvious until you look at real messages:
 *
 * **A one-time-password message carries the full amount and merchant of the
 * purchase it is authorising.**
 *
 *     الرقم السري لمره واحده لعملية الشراء عبر الإنترنت
 *     9861
 *     مبلغ SAR 35.0
 *     لدى barq
 *
 * It is, to a parser, indistinguishable from a purchase confirmation - and it
 * arrives seconds *before* the real confirmation for the same purchase. Without
 * this gate every online purchase is recorded twice, and the resulting monthly
 * total is quietly, plausibly, roughly double. There is no error and nothing to
 * notice.
 *
 * The second case is a declined transaction. `عملية مرفوضة رصيد غير كافي` has the
 * same shape as a successful purchase, but no money moved.
 *
 * Both are rejected on keywords rather than on structure, because structure is
 * exactly what they share with the messages we want.
 */
object MessageGate {

    enum class Rejection {
        /** Contains a one-time password. Also means the body must never be stored. */
        ONE_TIME_PASSWORD,

        /** The transaction did not go through. No money moved. */
        DECLINED,

        /** Marketing, service notices, login alerts - nothing financial to record. */
        NOT_FINANCIAL,
    }

    sealed interface Decision {
        data object Allow : Decision
        data class Reject(val reason: Rejection, val matched: String) : Decision
    }

    /**
     * Phrases that mark a message as carrying an authentication code.
     *
     * Matched against folded text, so spelling variants ("لمره" / "لمرة") and
     * missing spaces do not matter. Deliberately broad: a false reject loses one
     * transaction that the confirmation message will supply moments later, while a
     * false accept silently doubles a purchase and leaks a credential to disk.
     */
    private val OTP_MARKERS = listOf(
        // "رمز مؤقت:2345 / لـ :تحويل - الراجحي اعمال / المبلغ:25000 SAR" - a code
        // issued to AUTHORISE a transfer, carrying that transfer's full amount. It
        // was stored as a confirmed 25,000-riyal transfer out, so the month held a
        // movement that never happened and the code sat on disk beside it.
        //
        // "مؤقت" alone would be too broad; the pair is the wording the banks use.
        "رمز مؤقت",
        "رمزمؤقت",
        // "Requested an activation code 2470 for One Time Bill Payment / Amount:
        // 500 SAR" - the same thing in English, from a different template. Three of
        // these were stored as confirmed bill payments. The gate already knew the
        // Arabic "رمز التفعيل" and the English "ONE TIME PASSWORD", which is what
        // made this look covered: neither phrase appears in this body.
        "ACTIVATION CODE",
        "ONE TIME BILL PAYMENT",
        // "9399 is your password for the Alahli Credit Card transaction of SR
        // 334.95." - a one-time password in English, carrying the full amount of
        // the purchase it authorises. Twenty-five of them were stored as
        // transactions, each keeping a credential on disk.
        "IS YOUR PASSWORD",
        "YOUR PASSWORD FOR",
        "رمز التحقق",
        "رمزالتحقق",
        "الرقم السري لمره واحده",
        "الرقم السري لمرة واحدة",
        // Found in a real corpus of 5,074 AlRajhi messages: 88 of them carried a
        // live code under this wording and the gate did not stop a single one.
        // They were saved only by no parser understanding them yet - which is luck,
        // not a control, and the luck runs out the moment the parser improves.
        "كلمة مرور لمرة واحدة",
        "كلمة مرور صالحة لمرة واحدة",
        "كلمه مرور لمره واحده",
        "لا تفصح عن كلمة المرور",
        "لا تفصح عن كلمه المرور",
        "رمز التفعيل",
        "لا تشارك",
        "لاتشارك",
        "ننصح بعدم مشاركة",
        "عدم مشاركة الرمز",
        "ONE TIME PASSWORD",
        "OTP",
        // English templates. "Your secure code is 6659 / For internet purchase
        // SAR155.81 / Card ending 2887" carries the amount and the card of the
        // purchase it authorises and arrives seconds before it: 58 of these were
        // stored as confirmed purchases, each one doubling a real one and each one
        // keeping a credential in the database. Found in a real history, not
        // imagined.
        "SECURE CODE",
        "VERIFICATION CODE",
        "ONE TIME PASSWORD",
        "ONE-TIME PASSWORD",
        "DO NOT SHARE",
        "VERIFICATION CODE",
    )

    /**
     * Phrases that mark a message as an advertisement rather than a transaction.
     *
     * The bank's own marketing arrives from the bank's own sender, so a parser
     * claims it and then reads whatever it can. One real example, captured on the
     * owner's phone: a prize-draw advert became a cash withdrawal, because Arabic
     * tokens are matched as stems and `السحب` (the draw) contains `سحب` while
     * `النقدية` (the cash prize) contains `نقدي` - both tokens of the ATM rule,
     * present in a sentence about a raffle.
     *
     * [Rejection.NOT_FINANCIAL] existed from the beginning with nothing behind it.
     * This is what it was for.
     */
    private val MARKETING_MARKERS = listOf(
        // A card's limit being changed. "تم تغيير الحد اليومي للشراء عبر الانترنت
        // لبطاقة رقم ***907 الى SAR 200000" carries an amount and was stored as a
        // 200,000-riyal purchase, which is what the month total then said.
        "تغيير الحد",
        "الحد اليومي",
        "تحديث الحد",
        "الجائزة",
        "جائزة",
        "للفوز",
        "بالفوز",
        "فرصك",
        "اربح",
        "السحب الأسبوعي",
        "السحب الاسبوعي",
        "عرض خاص",
        "استبيان",
        "شاركنا",
        "خصم يصل",

        // The monthly credit-card statement. It announces what is owed, and the
        // parsers read that figure as an amount and stored it as a payment - nine
        // notices became 166,926 riyals of spending that never happened. The
        // settlement, when the user actually pays, arrives as its own message.
        //
        // One marker, not four: every one of these notices in a twelve-year corpus
        // says كشف حساب, and no completed transaction in that corpus says it.
        "كشف حساب",
    )

    /** Phrases that mark a transaction as not completed. */
    private val DECLINED_MARKERS = listOf(
        "عملية مرفوضة",
        // The active voice, which the passive markers below do not reach: "تم رفض
        // العملية: البطاقة غير نشطة". It stored 97 riyals of spending on a card
        // that had been cancelled, against a purchase that never happened.
        "رفض العملية",
        "عمليه مرفوضه",
        "مرفوضة",
        "رصيد غير كافي",
        "غير كافي",
        "فشلت العملية",
        "لم تتم العملية",
        "DECLINED",
        "INSUFFICIENT",
        "FAILED",
    )

    /**
     * Evaluates a message.
     *
     * @return [Decision.Allow] only when no rejection marker is present. Callers
     *   must honour [Rejection.ONE_TIME_PASSWORD] by discarding the body entirely
     *   rather than storing it as `rawText`.
     */
    fun evaluate(message: RawMessage): Decision {
        val folded = ArabicText.foldForMatching(message.fullText)

        OTP_MARKERS.firstOrNull { folded.contains(ArabicText.foldForMatching(it)) }
            ?.let { return Decision.Reject(Rejection.ONE_TIME_PASSWORD, it) }

        DECLINED_MARKERS.firstOrNull { folded.contains(ArabicText.foldForMatching(it)) }
            ?.let { return Decision.Reject(Rejection.DECLINED, it) }

        // Last, because a real transaction never contains these and a rejection
        // here must not shadow the two above, which are about safety rather than
        // about noise.
        MARKETING_MARKERS.firstOrNull { folded.contains(ArabicText.foldForMatching(it)) }
            ?.let { return Decision.Reject(Rejection.NOT_FINANCIAL, it) }

        return Decision.Allow
    }

    /** True when the message may be handed to a parser. */
    fun allows(message: RawMessage): Boolean = evaluate(message) is Decision.Allow

    /**
     * True when the message body must never be persisted.
     *
     * Kept separate from the reject decision because the app records rejected
     * messages for diagnostics ("we saw 40 messages, understood 31") - and an OTP
     * body is the one thing that must not survive that.
     */
    fun mustNotPersistBody(message: RawMessage): Boolean =
        (evaluate(message) as? Decision.Reject)?.reason == Rejection.ONE_TIME_PASSWORD
}
