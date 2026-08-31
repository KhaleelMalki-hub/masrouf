package sa.masrouf.core.capture

import sa.masrouf.core.money.Money
import sa.masrouf.core.text.ArabicText

/**
 * Pulls monetary amounts out of a bank message.
 *
 * This is the highest-risk function in the application. A misread amount does not
 * crash anything and does not look wrong: it produces a plausible number that
 * flows into monthly totals and budget decisions. So the design bias throughout is
 * to return *nothing* rather than a guess.
 *
 * The hard part is that a bank message is full of numbers that are not amounts:
 *
 *   - card and account fragments      "بطاقة: مدى 1234"
 *   - dates and times                 "2026-08-25 19:42"
 *   - reference and approval numbers  "مرجع: 884213"
 *   - the remaining balance           "الرصيد المتاح: 4,210.00"
 *
 * Two signals separate a real amount from those: an adjacent currency token, and a
 * two-decimal fraction. Candidates are ranked by how many of those they carry, and
 * a candidate with neither is never returned.
 */
object AmountExtractor {

    /** Currency tokens seen in Saudi bank messages, in both scripts. */
    private val CURRENCY_ALTERNATIVES = listOf(
        "SAR", "SR", "SAUDI RIYAL",
        "ريال",       // riyal
        "ر.س",             // r.s
        "رس",                   // rs
    )

    private val CURRENCY_PATTERN =
        CURRENCY_ALTERNATIVES.joinToString("|") { Regex.escape(it) }

    /**
     * `1,234.56` / `1234.5` / `87` / `.04` - thousands separators optional, up to 2
     * decimals, and a leading decimal point with no integer part. The last of those
     * is how AlRajhi writes a savings profit of four halalas ("مبلغ:.04 SAR"), which
     * was read as four riyals - the same number, wrong by a hundred.
     *
     * The leading-dot form is listed first so it wins the alternation and captures
     * the point; leave it last and the engine matches the digits alone.
     */
    private const val NUMBER_PATTERN =
        "\\.\\d{1,2}|\\d{1,3}(?:,\\d{3})+(?:\\.\\d{1,2})?|\\d+(?:\\.\\d{1,2})?"

    /**
     * A number must begin and end at a number boundary.
     *
     * Without this the engine is free to start a match *inside* another number to
     * make the rest of the pattern fit, and one real message made it do exactly
     * that. A bank sent its own floating-point artifact:
     *
     *     إيداع في بطاقة 9552* مبلغ 8315.08 الصرف المتبقي 21684.91999999999999 SAR
     *
     * "21684.91" cannot be followed by SAR, so the match slid to the digits after
     * the decimal point, where "91999999999999 SAR" fits perfectly. An 8,315-riyal
     * deposit was stored as 91,999,999,999,999 - ninety-two trillion riyals, which
     * is every incoming total this app will ever show, wrong, from one message.
     *
     * With the boundaries the balance line yields no candidate at all, which is the
     * right answer: this file returns nothing rather than a guess.
     */
    private const val NUMBER_START = "(?<![\\d,])(?<!\\d\\.)"

    /**
     * A digit, or a decimal point with a digit behind it, means the match stopped
     * inside a longer number. A bare full stop does not: an English message ends
     * its sentence right after the amount ("...transaction of SR 334.95.").
     */
    private const val NUMBER_END = "(?![\\d,])(?!\\.\\d)"

    private val CURRENCY_BEFORE = Regex(
        "(?<currency>$CURRENCY_PATTERN)\\s*$NUMBER_START(?<number>$NUMBER_PATTERN)$NUMBER_END",
        RegexOption.IGNORE_CASE,
    )

    private val CURRENCY_AFTER = Regex(
        "$NUMBER_START(?<number>$NUMBER_PATTERN)$NUMBER_END\\s*(?<currency>$CURRENCY_PATTERN)",
        RegexOption.IGNORE_CASE,
    )

    /**
     * A message that labels its own amount.
     *
     * The strongest signal there is, and it was not being used. "إيداع في بطاقة
     * 2887* / مبلغ 8500 / الصرف المتبقي 32167.58 SAR" carries the amount with no
     * currency beside it and the balance with one, so the balance won: 439 records
     * stored a balance where an amount belonged, across nine years.
     *
     * Scored above an adjacent currency token, because a bank naming its own figure
     * outranks a bank putting a currency near one.
     */
    private val AMOUNT_LABEL = Regex(
        "(?:بمبلغ|مبلغ|AMOUNT)\\s*:?\\s*(?:$CURRENCY_PATTERN)?\\s*$NUMBER_START(?<number>$NUMBER_PATTERN)$NUMBER_END",
        RegexOption.IGNORE_CASE,
    )

    /**
     * A decimal amount standing on its own, with no label and no currency.
     *
     * The integer part is any length. It used to be capped at three digits unless
     * thousands separators were present, which made every four-figure amount
     * written without a comma invisible - and those are exactly the messages whose
     * balance was picked up instead.
     */
    private val BARE_DECIMAL = Regex(
        "$NUMBER_START(?<number>(?:\\d{1,3}(?:,\\d{3})+|\\d+)\\.\\d{2}|\\.\\d{2})$NUMBER_END"
    )

    /**
     * Fragments that mark the number after them as something other than the amount
     * charged. Matched against the folded text preceding a candidate.
     */
    private val DISQUALIFYING_PREFIXES = listOf(
        // Balance. Present in the same message as the charge, and larger than it,
        // so getting this wrong turns a 931.64 coffee run into a 12,711.32 one.
        // Both the definite and bare forms appear, and AlRajhi/barq glue the label
        // straight onto the number with no separator at all ("رصيد15.18").
        "الرصيد",
        "رصيد",
        "المتاح",
        // "الصرف المتبقي" - what a credit card will still let through. A balance by
        // another name, larger than the charge beside it, and the only one of the
        // three the list did not already know.
        "المتبقي",
        "BALANCE", "AVAIL", "REMAIN",
        // Fees, sent as a second amount line right under the transfer amount
        // ("مبلغ2000.00SAR" then "رسوم0.00SAR").
        "رسوم",
        "FEE", "FEES",
        "مرجع",
        "REF", "AUTH",
        "بطاقة",
        "CARD",
        "حساب",
        "IBAN",
    )

    /** How much of the text before a candidate is inspected for a disqualifying prefix. */
    private const val PREFIX_LOOKBACK = 24

    data class Candidate(
        val money: Money,
        val currency: String?,
        val matchedText: String,
        val range: IntRange,
        val score: Int,
    )

    /**
     * All plausible amounts in [rawText], best first.
     *
     * @param rawText message text; normalised internally, so raw input is fine.
     */
    fun candidates(rawText: String): List<Candidate> {
        val text = ArabicText.normalize(rawText)
        val found = LinkedHashMap<IntRange, Candidate>()

        fun consider(range: IntRange, numberToken: String, currency: String?, baseScore: Int) {
            val money = Money.parseOrNull(numberToken) ?: return
            if (isDisqualified(text, range.first)) return
            val score = baseScore + if (numberToken.contains('.')) 1 else 0
            val existing = found[range]
            if (existing == null || existing.score < score) {
                found[range] = Candidate(
                    money = money,
                    currency = currency?.uppercase(),
                    matchedText = text.substring(range),
                    range = range,
                    score = score,
                )
            }
        }

        // Highest first: a labelled amount beats a number that merely sits beside a
        // currency token, which beats one standing on its own.
        for (match in AMOUNT_LABEL.findAll(text)) {
            val number = match.groups["number"] ?: continue
            consider(number.range, number.value, currency = null, baseScore = 3)
        }
        for (match in CURRENCY_BEFORE.findAll(text)) {
            val number = match.groups["number"] ?: continue
            consider(match.range, number.value, match.groups["currency"]?.value, baseScore = 2)
        }
        for (match in CURRENCY_AFTER.findAll(text)) {
            val number = match.groups["number"] ?: continue
            consider(match.range, number.value, match.groups["currency"]?.value, baseScore = 2)
        }
        for (match in BARE_DECIMAL.findAll(text)) {
            val number = match.groups["number"] ?: continue
            if (found.keys.any { it.overlaps(number.range) }) continue
            consider(match.range, number.value, currency = null, baseScore = 0)
        }

        return found.values.sortedWith(
            compareByDescending<Candidate> { it.score }.thenBy { it.range.first }
        )
    }

    /**
     * The single most likely charged amount, or null when the message carries none.
     *
     * Returning null is a normal outcome, not an error: plenty of bank messages
     * (OTP codes, marketing, login alerts) contain no amount at all.
     */
    fun extractOrNull(rawText: String): Candidate? = candidates(rawText).firstOrNull()

    /**
     * The labels, folded once.
     *
     * They were compared unfolded against folded text, so "بطاقة" could never match
     * anything - folding maps ة to ه, and the list held the spelling nobody would
     * be comparing against. A guard that cannot fire is worse than an absent one:
     * it is read as protection.
     */
    private val DISQUALIFYING_FOLDED = DISQUALIFYING_PREFIXES.map(ArabicText::foldForMatching)

    /**
     * Whether the text just before a candidate marks it as something other than the
     * amount charged.
     *
     * Two things are stripped before the comparison, and both are why the guard
     * used to leak. `foldForMatching` ends in `trim()`, so a trailing space can
     * never survive to be matched - the two `endsWith("$it ")` forms this replaced
     * were unreachable. And a currency token between the label and the number
     * ("الرصيد المتاح SAR 4210.00") put SAR at the end of the lookback, so the
     * label was no longer there to be found and a balance was read as a purchase.
     */
    private fun isDisqualified(text: String, startIndex: Int): Boolean {
        val from = (startIndex - PREFIX_LOOKBACK).coerceAtLeast(0)
        val prefix = ArabicText.foldForMatching(text.substring(from, startIndex))
            .let(::withoutTrailingCurrency)
        return DISQUALIFYING_FOLDED.any { prefix.endsWith(it) }
    }

    /** Drops a currency token sitting between a label and its number. */
    private fun withoutTrailingCurrency(prefix: String): String =
        CURRENCY_FOLDED.firstOrNull { prefix.endsWith(it) }
            ?.let { prefix.dropLast(it.length).trimEnd() }
            ?: prefix

    private val CURRENCY_FOLDED = CURRENCY_ALTERNATIVES
        .map(ArabicText::foldForMatching)
        .sortedByDescending(String::length)

    private fun IntRange.overlaps(other: IntRange): Boolean =
        first <= other.last && other.first <= last
}
