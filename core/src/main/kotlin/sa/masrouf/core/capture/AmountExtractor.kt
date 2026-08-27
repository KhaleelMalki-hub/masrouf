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

    /** `1,234.56` / `1234.5` / `87` - thousands separators optional, up to 2 decimals. */
    private const val NUMBER_PATTERN = "\\d{1,3}(?:,\\d{3})+(?:\\.\\d{1,2})?|\\d+(?:\\.\\d{1,2})?"

    private val CURRENCY_BEFORE = Regex(
        "(?<currency>$CURRENCY_PATTERN)\\s*(?<number>$NUMBER_PATTERN)",
        RegexOption.IGNORE_CASE,
    )

    private val CURRENCY_AFTER = Regex(
        "(?<number>$NUMBER_PATTERN)\\s*(?<currency>$CURRENCY_PATTERN)",
        RegexOption.IGNORE_CASE,
    )

    private val BARE_DECIMAL = Regex("(?<![\\d.,])(?<number>\\d{1,3}(?:,\\d{3})*\\.\\d{2})(?![\\d.,])")

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
        "BALANCE", "AVAIL",
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
            if (found.keys.any { it.overlaps(match.range) }) continue
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

    private fun isDisqualified(text: String, startIndex: Int): Boolean {
        val from = (startIndex - PREFIX_LOOKBACK).coerceAtLeast(0)
        val prefix = ArabicText.foldForMatching(text.substring(from, startIndex))
        return DISQUALIFYING_PREFIXES.any { prefix.endsWith(it) || prefix.endsWith("$it :") || prefix.endsWith("$it ") }
    }

    private fun IntRange.overlaps(other: IntRange): Boolean =
        first <= other.last && other.first <= last
}
