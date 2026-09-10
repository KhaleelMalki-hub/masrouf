package sa.masrouf.core.ask

import sa.masrouf.core.model.Category
import sa.masrouf.core.model.SaudiCategories
import sa.masrouf.core.text.ArabicText
import java.time.LocalDate

/**
 * Turns a typed question into an [AskQuery], or into nothing.
 *
 * ## Why this is a keyword parser and not a model
 *
 * The answer is a figure about someone's money, and this project's oldest rule is
 * that a parser refuses to guess: no recognised intent means no draft, never a
 * draft with a plausible value in it. A language model is the opposite trade - it
 * always produces something, it produces numbers, and there is no test that pins
 * it. So the vocabulary is closed and small, every question maps to a query a test
 * can assert, and a question outside the vocabulary comes back as null so the
 * screen can say it did not understand.
 *
 * The arithmetic never happens here. This produces a description of a question; the
 * repository answers it in SQL, through the same `countsAsSpending` rule every
 * other total goes through, so an answer here and a month total there can never
 * disagree.
 *
 * ## Reading Arabic
 *
 * Everything is compared after [ArabicText.foldForMatching], which collapses the
 * spellings that differ only by a hamza or a taa marbuta - "السنه" and "السنة",
 * "امس" and "أمس" - and uppercases the Latin. The keyword tables are folded once at
 * class-load rather than on every question.
 */
object AskParser {

    /**
     * @param question what the user typed
     * @param today the day to resolve "this month", "yesterday" and the rest
     *   against. Passed in rather than read from the clock so that a question asked
     *   a second before midnight cannot answer about a different month than the one
     *   its own label names, and so a test can ask about last month without waiting.
     * @return the query, or null when the question is not one this can answer.
     */
    fun parse(question: String, today: LocalDate): AskQuery? {
        val text = ArabicText.foldForMatching(question)
        if (text.isBlank()) return null

        val measureWord = MEASURES.firstOrNull { (word, _) -> word in text }
        val subject = subjectOf(text)

        // A period alone is not a question about money. "How is the weather today"
        // carries a period and nothing else, and answering it with a month's total
        // is the failure this guard exists for: the app would look confidently
        // wrong rather than honestly blank.
        if (measureWord == null && subject == Subject.Everything) return null

        return AskQuery(
            measure = measureWord?.second ?: Measure.TOTAL,
            subject = subject,
            period = periodOf(text, today),
            flow = if (INCOME_WORDS.any { it in text }) Flow.INCOME else Flow.SPENDING,
        )
    }

    // ---- what is being asked ------------------------------------------------

    /**
     * Ordered: the longer phrase wins. "كم مرة" has to be tested before "كم", or
     * every count question is answered as a total - a number of the right shape and
     * the wrong meaning, which is the worst kind.
     */
    private val MEASURES: List<Pair<String, Measure>> = listOf(
        "كم مره" to Measure.COUNT,
        "عدد" to Measure.COUNT,
        "اكبر" to Measure.LARGEST,
        "اعلي" to Measure.LARGEST,
        "اغلي" to Measure.LARGEST,
        "اعرض" to Measure.LIST,
        "وريني" to Measure.LIST,
        "قائمه" to Measure.LIST,
        "HOW MANY" to Measure.COUNT,
        "HOW MUCH" to Measure.TOTAL,
        "LARGEST" to Measure.LARGEST,
        "BIGGEST" to Measure.LARGEST,
        "SHOW" to Measure.LIST,
        "LIST" to Measure.LIST,
        "TOTAL" to Measure.TOTAL,
        "SPENT" to Measure.TOTAL,
        "كم" to Measure.TOTAL,
        "مجموع" to Measure.TOTAL,
        "اجمالي" to Measure.TOTAL,
        "صرفت" to Measure.TOTAL,
        "دفعت" to Measure.TOTAL,
        "مصروف" to Measure.TOTAL,
    ).map { (word, measure) -> ArabicText.foldForMatching(word) to measure }

    private val INCOME_WORDS = listOf("دخل", "دخلي", "راتب", "وصلني", "استلمت", "INCOME", "SALARY")
        .map(ArabicText::foldForMatching)

    /**
     * The rows with no category. See [Subject.Unfiled].
     *
     * Both with and without the definite article, because both are what a person
     * types: "وش غير مصنف" and "اعرض غير المصنف" are the same question, and a table
     * of substrings does not know that ال is an article rather than three letters
     * of the word. Cheaper and clearer than stripping it, which would have to know
     * which ال is an article - "الله" is not "له" with one.
     */
    private val UNFILED_WORDS = listOf(
        "غير مصنف", "غير المصنف", "غير مصنفه", "غير المصنفه",
        "UNFILED", "NOT FILED",
        "بلا تصنيف", "بدون تصنيف", "ما صنفت", "لم اصنف",
        "UNFILED", "UNCATEGORISED", "UNCATEGORIZED",
    ).map(ArabicText::foldForMatching)

    // ---- what it is about ---------------------------------------------------

    private fun subjectOf(text: String): Subject {
        // First, because it is an absence rather than a thing: "غير مصنف" contains
        // no category word, but a question that says it means only one thing.
        if (UNFILED_WORDS.any { it in text }) return Subject.Unfiled
        TOPIC_WORDS.firstOrNull { (word, _) -> word in text }
            ?.let { return Subject.OfTopic(it.second) }
        CATEGORY_WORDS.firstOrNull { (word, _) -> word in text }
            ?.let { return Subject.OfCategory(it.second) }
        merchantIn(text)?.let { return Subject.AtMerchant(it) }
        return Subject.Everything
    }

    /**
     * Topics come first, because a topic is the narrower reading and the one the
     * asker meant: "بنزين" must not become the transport category, which is three
     * times bigger. See [Topic].
     */
    private val TOPIC_WORDS: List<Pair<String, Topic>> = listOf(
        "PETROL" to Topic.FUEL,
        "FUEL" to Topic.FUEL,
        "COFFEE" to Topic.COFFEE,
        "DELIVERY" to Topic.DELIVERY,
        "PHARMACY" to Topic.PHARMACY,
        "بنزين" to Topic.FUEL,
        "وقود" to Topic.FUEL,
        "محطه" to Topic.FUEL,
        "قهوه" to Topic.COFFEE,
        "كافيه" to Topic.COFFEE,
        "توصيل" to Topic.DELIVERY,
        "طلبات" to Topic.DELIVERY,
        "صيدليه" to Topic.PHARMACY,
        "دواء" to Topic.PHARMACY,
    ).map { (word, topic) -> ArabicText.foldForMatching(word) to topic }

    /**
     * The categories, by the words a person uses for them rather than by their ids.
     * Several categories are deliberately absent: nobody asks "how much on other".
     */
    private val CATEGORY_WORDS: List<Pair<String, Category>> = listOf(
        "GROCERIES" to SaudiCategories.GROCERIES,
        "EATING OUT" to SaudiCategories.FOOD,
        "RESTAURANTS" to SaudiCategories.FOOD,
        "TRANSPORT" to SaudiCategories.TRANSPORT,
        "BILLS" to SaudiCategories.BILLS,
        "HEALTH" to SaudiCategories.HEALTH,
        "SHOPPING" to SaudiCategories.SHOPPING,
        "TRANSFERS" to SaudiCategories.TRANSFERS,
        "CHARITY" to SaudiCategories.CHARITY,
        "HOUSING" to SaudiCategories.HOUSING,
        "EDUCATION" to SaudiCategories.EDUCATION,
        "ENTERTAINMENT" to SaudiCategories.ENTERTAINMENT,
        "TRAVEL" to SaudiCategories.TRAVEL,
        "بقاله" to SaudiCategories.GROCERIES,
        "اغذيه" to SaudiCategories.GROCERIES,
        "مطاعم" to SaudiCategories.FOOD,
        "اكل" to SaudiCategories.FOOD,
        "مواصلات" to SaudiCategories.TRANSPORT,
        "سياره" to SaudiCategories.TRANSPORT,
        "فواتير" to SaudiCategories.BILLS,
        "اشتراكات" to SaudiCategories.BILLS,
        "صحه" to SaudiCategories.HEALTH,
        "علاج" to SaudiCategories.HEALTH,
        "تسوق" to SaudiCategories.SHOPPING,
        "ملابس" to SaudiCategories.SHOPPING,
        "تحويلات" to SaudiCategories.TRANSFERS,
        "صدقه" to SaudiCategories.CHARITY,
        "زكاه" to SaudiCategories.CHARITY,
        "سكن" to SaudiCategories.HOUSING,
        "ايجار" to SaudiCategories.HOUSING,
        "عنايه" to SaudiCategories.SERVICES,
        "مغسله" to SaudiCategories.SERVICES,
        "استثمار" to SaudiCategories.INVESTMENT,
        "تعليم" to SaudiCategories.EDUCATION,
        "مدارس" to SaudiCategories.EDUCATION,
        "رسوم" to SaudiCategories.FEES,
        "ترفيه" to SaudiCategories.ENTERTAINMENT,
        "سفر" to SaudiCategories.TRAVEL,
        "نقد" to SaudiCategories.CASH,
        "مكافات" to SaudiCategories.BONUS,
    ).map { (word, category) -> ArabicText.foldForMatching(word) to category }

    /**
     * A merchant is whatever follows "at", "from" or "on", when nothing better
     * matched.
     *
     * Only the name is taken, not the rest of the sentence: the period words that
     * may follow it are cut off, so "في امازون الشهر الماضي" asks about امازون and
     * not about a merchant called "امازون الشهر الماضي".
     *
     * An unrecognised noun becomes a merchant rather than a refusal, and that is
     * deliberate: "كم صرفت على الفضاء" is answerable - there are no purchases at
     * anything called that - and a lookup which finds nothing is honest in a way
     * that a shrug is not. The screen must then say "no merchant by that name"
     * rather than "0 riyals", because those are different statements and only one
     * of them is true.
     */
    private fun merchantIn(text: String): String? {
        val at = MERCHANT_LEAD.find(text) ?: return null
        var name = at.groupValues[1].trim()
        for (stop in PERIOD_STOPWORDS) {
            val cut = name.indexOf(stop)
            if (cut > 0) name = name.take(cut).trim()
        }
        return name.takeIf { it.length >= MIN_MERCHANT_LENGTH }
    }

    /**
     * `\b` is not used here and must not be. Java defines a word boundary over
     * [A-Za-z0-9_], so between a space and an Arabic letter there is no boundary at
     * all and the alternation would match nothing - the same defect that once made
     * a sender-line guard read as present and do nothing. The start of the string
     * or a space does the job in both scripts.
     */
    private val MERCHANT_LEAD =
        Regex("""(?:^|\s)(?:في|من|لدي|علي|AT)\s+(.+)$""", RegexOption.IGNORE_CASE)

    private val PERIOD_STOPWORDS = listOf(
        "هذا", "هذه", "هذي", "الماضي", "الماضيه", "اليوم", "امس", "اخر", "سنه", "شهر",
    ).map(ArabicText::foldForMatching)

    /** Below this a "merchant" is a preposition's leftovers, not a name. */
    private const val MIN_MERCHANT_LENGTH = 3

    // ---- when ---------------------------------------------------------------

    private fun periodOf(text: String, today: LocalDate): Period {
        lastNDays(text, today)?.let { return it }

        when {
            has(text, "اليوم") || has(text, "TODAY") -> return day(today, PeriodLabel.TODAY)
            has(text, "امس") || has(text, "YESTERDAY") ->
                return day(today.minusDays(1), PeriodLabel.YESTERDAY)
            has(text, "هذا الاسبوع") || has(text, "THIS WEEK") -> return Period.of(
                today.minusDays(6), today.plusDays(1), PeriodLabel.THIS_WEEK,
            )
            has(text, "الشهر الماضي") || has(text, "الشهر اللي راح") || has(text, "LAST MONTH") ->
                return month(today.minusMonths(1), PeriodLabel.LAST_MONTH)
            has(text, "هذا الشهر") || has(text, "هذي الشهر") || has(text, "الشهر الحالي") ||
                has(text, "THIS MONTH") ->
                return month(today, PeriodLabel.THIS_MONTH)
            has(text, "السنه الماضيه") || has(text, "العام الماضي") || has(text, "LAST YEAR") ->
                return year(today.year - 1, PeriodLabel.LAST_YEAR)
            has(text, "هذي السنه") || has(text, "هذا العام") || has(text, "السنه الحاليه") ||
                has(text, "THIS YEAR") ->
                return year(today.year, PeriodLabel.THIS_YEAR)
        }

        namedMonth(text, today)?.let { return it }
        namedYear(text)?.let { return it }
        return Period.Always
    }

    private fun has(text: String, phrase: String) = ArabicText.foldForMatching(phrase) in text

    private fun day(on: LocalDate, label: PeriodLabel) =
        Period.of(on, on.plusDays(1), label)

    private fun month(anyDayIn: LocalDate, label: PeriodLabel): Period {
        val first = anyDayIn.withDayOfMonth(1)
        return Period.of(first, first.plusMonths(1), label)
    }

    private fun year(year: Int, label: PeriodLabel) =
        Period.of(LocalDate.of(year, 1, 1), LocalDate.of(year + 1, 1, 1), label)

    /** "آخر 30 يوم". Inclusive of today, so the window ends tomorrow. */
    private fun lastNDays(text: String, today: LocalDate): Period? {
        val m = LAST_N_DAYS.find(text) ?: return null
        val days = m.groupValues[1].toIntOrNull()?.takeIf { it in 1..MAX_WINDOW_DAYS } ?: return null
        return Period.of(today.minusDays(days.toLong()), today.plusDays(1), PeriodLabel.LAST_N_DAYS)
    }

    private val LAST_N_DAYS = Regex("""${'آ'}?(?:خر|اخر)\s*(\d{1,4})\s*(?:يوم|ايام)""")

    /** Ten years back is a window; more is a way of saying "everything". */
    private const val MAX_WINDOW_DAYS = 3650

    /**
     * A month by name, in the year named beside it or else the current one.
     *
     * A month with no year means the one just gone rather than a date in the
     * future: asked in September, "in December" is last December, not the one that
     * has not happened.
     */
    private fun namedMonth(text: String, today: LocalDate): Period? {
        val month = MONTHS.firstOrNull { (word, _) -> word in text }?.second ?: return null
        val year = FOUR_DIGIT_YEAR.find(text)?.groupValues?.get(1)?.toIntOrNull()
            ?: today.year.let { if (month > today.monthValue) it - 1 else it }
        return month(LocalDate.of(year, month, 1), PeriodLabel.NAMED_MONTH)
    }

    private fun namedYear(text: String): Period? =
        FOUR_DIGIT_YEAR.find(text)?.groupValues?.get(1)?.toIntOrNull()
            ?.let { year(it, PeriodLabel.NAMED_YEAR) }

    private val FOUR_DIGIT_YEAR = Regex("""\b(20[0-3]\d)\b""")

    private val MONTHS: List<Pair<String, Int>> = listOf(
        listOf("يناير", "JANUARY", "كانون الثاني") to 1,
        listOf("فبراير", "FEBRUARY", "شباط") to 2,
        listOf("مارس", "MARCH", "اذار") to 3,
        listOf("ابريل", "APRIL", "نيسان") to 4,
        listOf("مايو", "MAY", "ايار") to 5,
        listOf("يونيو", "JUNE", "حزيران") to 6,
        listOf("يوليو", "JULY", "تموز") to 7,
        listOf("اغسطس", "AUGUST", "اب") to 8,
        listOf("سبتمبر", "SEPTEMBER", "ايلول") to 9,
        listOf("اكتوبر", "OCTOBER", "تشرين الاول") to 10,
        listOf("نوفمبر", "NOVEMBER", "تشرين الثاني") to 11,
        listOf("ديسمبر", "DECEMBER", "كانون الاول") to 12,
    ).flatMap { (words, number) -> words.map { ArabicText.foldForMatching(it) to number } }
}
