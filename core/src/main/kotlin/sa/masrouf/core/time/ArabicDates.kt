package sa.masrouf.core.time

import sa.masrouf.core.text.ArabicText
import java.time.LocalDate

/**
 * Parses the date formats Saudi bank statements actually use.
 *
 * Four banks, four conventions - and two of them are ambiguous with each other,
 * which is why each layout names its parser explicitly instead of a single
 * best-effort function guessing:
 *
 *     SNB       03/07/2026        day first
 *     AlRajhi   2026/05/26        year first
 *     D360      28 يوليو 2026     Arabic month name
 *     barq      27 Aug 2026       English month abbreviation
 *
 * `03/07/2026` is 3 July under SNB's convention and would be 7 March under a
 * month-first reading. Nothing in the row says which, so a shared "smart" parser
 * would silently mis-date a third of the year. The layout knows; the parser does
 * not have to.
 */
object ArabicDates {

    /**
     * Gregorian month names as banks write them, including the spelling variants.
     *
     * Keys are folded via [ArabicText.foldForMatching], so hamza and teh-marbuta
     * differences collapse and each name needs only one entry per real variant.
     */
    private val ARABIC_MONTHS: Map<String, Int> = buildMap {
        fun put(month: Int, vararg names: String) {
            names.forEach { put(ArabicText.foldForMatching(it), month) }
        }
        put(1, "يناير", "كانون الثاني")
        put(2, "فبراير", "شباط")
        put(3, "مارس", "آذار")
        put(4, "أبريل", "ابريل", "إبريل", "نيسان")
        put(5, "مايو", "أيار")
        put(6, "يونيو", "يونية", "حزيران")
        put(7, "يوليو", "يولية", "تموز")
        put(8, "أغسطس", "اغسطس", "آب")
        put(9, "سبتمبر", "أيلول")
        put(10, "أكتوبر", "اكتوبر", "تشرين الأول")
        put(11, "نوفمبر", "تشرين الثاني")
        put(12, "ديسمبر", "كانون الأول")
    }

    private val ENGLISH_MONTHS: Map<String, Int> = buildMap {
        val names = listOf(
            "jan", "feb", "mar", "apr", "may", "jun",
            "jul", "aug", "sep", "oct", "nov", "dec",
        )
        names.forEachIndexed { index, name -> put(name, index + 1) }
    }

    private val DAY_FIRST = Regex("""(\d{1,2})[/\-.](\d{1,2})[/\-.](\d{4})""")
    private val YEAR_FIRST = Regex("""(\d{4})[/\-.](\d{1,2})[/\-.](\d{1,2})""")
    private val NAMED_MONTH = Regex("""(\d{1,2})\s+(\p{L}[\p{L} ]*?)\s+(\d{4})""")

    /** `03/07/2026` - day, month, year. Used by SNB. */
    fun dayFirst(text: String): LocalDate? =
        DAY_FIRST.find(prepare(text))?.let { match ->
            build(
                year = match.groupValues[3].toInt(),
                month = match.groupValues[2].toInt(),
                day = match.groupValues[1].toInt(),
            )
        }

    /** `2026/05/26` - year, month, day. Used by AlRajhi. */
    fun yearFirst(text: String): LocalDate? =
        YEAR_FIRST.find(prepare(text))?.let { match ->
            build(
                year = match.groupValues[1].toInt(),
                month = match.groupValues[2].toInt(),
                day = match.groupValues[3].toInt(),
            )
        }

    /** `28 يوليو 2026` or `27 Aug 2026`. Used by D360 and barq. */
    fun namedMonth(text: String): LocalDate? {
        val prepared = prepare(text)
        val match = NAMED_MONTH.find(prepared) ?: return null
        val month = monthNumber(match.groupValues[2]) ?: return null
        return build(
            year = match.groupValues[3].toInt(),
            month = month,
            day = match.groupValues[1].toInt(),
        )
    }

    private fun monthNumber(name: String): Int? {
        val folded = ArabicText.foldForMatching(name)
        ARABIC_MONTHS[folded]?.let { return it }
        val latin = folded.lowercase()
        ENGLISH_MONTHS[latin.take(3)]?.let { return it }
        // Arabic names arrive with a leading conjunction or a stray particle often
        // enough to be worth one containment pass before giving up.
        return ARABIC_MONTHS.entries.firstOrNull { folded.contains(it.key) }?.value
    }

    /**
     * Rejects impossible dates rather than rolling them over.
     *
     * `LocalDate.of` throws on 31 February; a lenient parser would quietly return
     * 3 March instead, which is how a statement row ends up filed in the wrong
     * month with nothing to show for it.
     */
    private fun build(year: Int, month: Int, day: Int): LocalDate? = try {
        LocalDate.of(year, month, day)
    } catch (_: java.time.DateTimeException) {
        null
    }

    /** Statement cells carry Arabic-Indic digits and stray bidi marks. */
    private fun prepare(text: String): String = ArabicText.normalize(text)
}
