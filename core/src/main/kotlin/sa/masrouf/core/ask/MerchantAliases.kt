package sa.masrouf.core.ask

import sa.masrouf.core.text.ArabicText

/**
 * The name a person says, against the name a card terminal writes.
 *
 * The owner types Arabic. The merchant field is almost always Latin, because that
 * is what the terminal sends: he thinks "امازون" and the row says "Amazon SA", he
 * thinks "هنقرستيشن" and the row says "HUNGERSTATION LLC". Without this, asking
 * about a shop by its Arabic name answers "you have never bought from that" about
 * six hundred purchases - which is not merely unhelpful, it is a confident false
 * statement about his own history.
 *
 * Found by a test rather than by use: the seam test asked about امازون and got
 * "never seen".
 *
 * ## What belongs here
 *
 * Only names that are actually in this history, and only where the two spellings
 * are the same shop beyond argument. This is not a transliteration engine - a
 * general one would map "بن" to BIN, BAN, BUN and BEN and reach half the merchant
 * list. Each entry is one Arabic spelling and the Latin fragment it should search
 * for, and the fragment is long enough not to reach a different shop.
 *
 * The spellings are the ones a Saudi types, including the ones that are wrong in
 * an editor and right in a search box: هنقرستيشن with a qaf, and هنجرستيشن with a
 * jeem, are both what people write.
 */
object MerchantAliases {

    /**
     * @return the Latin fragment to search for, or the typed name unchanged when
     *   nothing here recognises it. Never null: an unknown name is still a
     *   perfectly good search, it simply finds nothing.
     */
    fun resolve(typed: String): String {
        val folded = ArabicText.foldForMatching(typed)
        val words = folded.split(' ').filter(String::isNotBlank)
        return ALIASES.firstOrNull { (alias, _) -> matches(words, alias) }?.second ?: typed
    }

    /**
     * Whole words, never a bare substring.
     *
     * The first version compared substrings and resolved "زابلونيا" to APPLE,
     * because "ابل" sits inside it - the same three-letter trap that once filed a
     * café as healthcare through a "DR" rule and Victoria's Secret as a utility
     * bill through "SEC". A person naming a shop types the word; the word is what
     * is compared.
     *
     * The one concession is a typed abbreviation: four characters or more that
     * BEGIN an alias are accepted, which is the truncation rule the merchant list
     * already lives by, so "امازو" still reaches Amazon while "ن" reaches nothing.
     */
    private fun matches(words: List<String>, alias: String): Boolean {
        val aliasWords = alias.split(' ').filter(String::isNotBlank)
        if (aliasWords.size > 1) return alias in words.joinToString(" ")
        return words.any { it == alias || (it.length >= MIN_ABBREVIATION && alias.startsWith(it)) }
    }

    private const val MIN_ABBREVIATION = 4

    private val ALIASES: List<Pair<String, String>> = listOf(
        "امازون" to "AMAZON",
        "امزون" to "AMAZON",
        "نون" to "NOON",
        "هنقرستيشن" to "HUNGERSTA",
        "هنجرستيشن" to "HUNGERSTA",
        "مرسول" to "MRSOOL",
        "كيتا" to "KEETA",
        "جاهز" to "JAHEZ",
        "نينجا" to "NINJA",
        "دكان" to "DUKAN",
        "بنده" to "PANDA",
        "بندة" to "PANDA",
        "دانكن" to "DUNKIN",
        "ماكدونالدز" to "MCDONALDS",
        "ابل" to "APPLE",
        "قوقل" to "GOOGLE",
        "جوجل" to "GOOGLE",
        "نتفلكس" to "NETFLIX",
        "موبايلي" to "MOBILY",
        "الاتصالات السعوديه" to "SAUDI TELECOM",
        "اس تي سي" to "STC",
        "بايبال" to "PAYPAL",
        "الدريس" to "ALDREES",
        "ساسكو" to "SASCO",
        "النهدي" to "NAHDI",
        "جرير" to "JARIR",
        "اكسترا" to "EXTRA",
        "نمشي" to "NAMSHI",
        "شي ان" to "SHEIN",
        "ايكيا" to "IKEA",
        "اوناس" to "OUNASS",
        "سنتربوينت" to "CENTREPOINT",
        "برق" to "BARQ",
    ).map { (arabic, latin) -> ArabicText.foldForMatching(arabic) to latin }
}
