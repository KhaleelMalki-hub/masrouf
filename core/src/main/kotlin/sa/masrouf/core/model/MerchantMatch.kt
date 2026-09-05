package sa.masrouf.core.model

import sa.masrouf.core.text.ArabicText

/**
 * Matching a merchant name against a list of keywords.
 *
 * Extracted from [CategoryGuess] when a second list needed the same rules. There
 * is one hard-won behaviour here and it must not be reimplemented per list: card
 * networks truncate a merchant name at a different length every time, and every
 * defect this file exists to prevent came from a keyword that did not survive that.
 */
object MerchantMatch {

    /**
     * A rule list with its keywords folded once.
     *
     * Folding a keyword costs a normalisation pass, and the first version folded
     * all 260 of them on every call: the launch-time filing of 2,300 records ran
     * 600,000 normalisations and took the dashboard's first reading with it.
     */
    class Rules<T>(pairs: List<Pair<String, T>>) {
        internal val entries: List<Entry<T>> = pairs.map { (keyword, value) ->
            val folded = ArabicText.normalizeMerchant(keyword)
            Entry(folded, folded.replace(" ", ""), value)
        }
    }

    internal class Entry<T>(val folded: String, val glued: String, val value: T)

    /**
     * @return the value of the first rule whose keyword matches, or null.
     *
     * An exact match wins over a partial one whatever the order of the list.
     * Without that, "Amazon SA" - which normalises to exactly "AMAZON" - was caught
     * by an "AMAZON NO" rule through the truncation rule below, because "AMAZONNO"
     * does start with "AMAZON". Six hundred records went to groceries. Reordering
     * cannot fix it: whichever of the two rules comes first swallows the other's
     * merchant.
     *
     * Otherwise the list order decides, so the specific must sit above the general.
     */
    fun <T> firstMatch(merchantRaw: String?, rules: Rules<T>): T? {
        val folded = merchantRaw?.let(ArabicText::normalizeMerchant)?.takeIf { it.isNotBlank() }
            ?: return null
        val glued = folded.replace(" ", "")

        rules.entries.firstOrNull { it.glued == glued }?.let { return it.value }
        return rules.entries.firstOrNull { matches(folded, glued, it) }?.value
    }

    /**
     * The glued forms are passed in, never rebuilt here.
     *
     * This ran `replace(" ", "")` on BOTH sides for every entry it tested - so one
     * lookup against the merchant list allocated a couple of hundred strings, and the
     * name shown on a history row is one such lookup per row. The keyword's glued
     * form is computed once when the list is built, and the merchant's once per
     * lookup; this only compares them.
     */
    private fun matches(foldedMerchant: String, gluedMerchant: String, entry: Entry<*>): Boolean {
        if (entry.folded.length < MIN_SUBSTRING_LENGTH) {
            return matchesWholeWord(foldedMerchant, entry.folded)
        }
        if (gluedMerchant.contains(entry.glued)) return true
        // A truncated name is accepted when the keyword starts with it, which is
        // what truncation means: "HUNGERSTA" is HUNGERSTATION with the end cut off.
        return gluedMerchant.length >= MIN_TRUNCATED_LENGTH && entry.glued.startsWith(gluedMerchant)
    }

    /**
     * A short keyword has to be a whole word, never a fragment of one.
     *
     * Measured on a real 22,084-record history: as substrings, "HM" filed TAHA
     * AHMED and TAREQ MOHAMMED as shopping, "SEC" filed the Cheesecake Factory and
     * Victoria's Secret as a utility bill, "DR" filed a cafe called Dropelmagara as
     * healthcare. Each was a rule for a real thing - H&M, the electricity company,
     * a doctor - reaching into words that have nothing to do with it.
     *
     * The Arabic definite article is stripped from the merchant's words before
     * comparing, because Arabic attaches it: "المركز الطبي" carries the word طبي and
     * should match, while "موقف" merely contains those letters and must not.
     */
    private fun matchesWholeWord(foldedMerchant: String, foldedKeyword: String): Boolean {
        val keyword = foldedKeyword.split(" ")
        return foldedMerchant
            .split(" ")
            .map { it.removePrefix(DEFINITE_ARTICLE) }
            .windowed(keyword.size)
            .any { it == keyword }
    }

    private const val MIN_TRUNCATED_LENGTH = 6

    /** Below this, a keyword matches a whole word only. See [matchesWholeWord]. */
    private const val MIN_SUBSTRING_LENGTH = 4

    private const val DEFINITE_ARTICLE = "ال"
}
