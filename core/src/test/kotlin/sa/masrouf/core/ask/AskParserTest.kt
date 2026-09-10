package sa.masrouf.core.ask

import org.junit.jupiter.api.Test
import sa.masrouf.core.model.SaudiCategories
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Questions, and the queries they must become.
 *
 * The parser answers or says nothing. There is no third outcome where it produces a
 * query it is unsure of, because the query becomes a figure about someone's money.
 * Half of this file is questions that must return null.
 */
class AskParserTest {

    /** A Wednesday, mid-month, mid-year - so every relative period is unambiguous. */
    private val today = LocalDate.of(2026, 9, 10)

    private fun ask(q: String) = AskParser.parse(q, today)

    // ---- the questions the owner actually asked for -------------------------

    @Test
    fun `how much on petrol in a named month`() {
        val query = ask("كم صرفت بنزين في مارس")!!
        assertEquals(Measure.TOTAL, query.measure)
        assertEquals(Subject.OfTopic(Topic.FUEL), query.subject)
        assertEquals(LocalDate.of(2026, 3, 1), query.period.from)
        assertEquals(LocalDate.of(2026, 4, 1), query.period.toExclusive)
        assertEquals(PeriodLabel.NAMED_MONTH, query.period.label)
    }

    @Test
    fun `how much on petrol on a given day`() {
        val query = ask("كم صرفت بنزين اليوم")!!
        assertEquals(Subject.OfTopic(Topic.FUEL), query.subject)
        assertEquals(today, query.period.from)
        assertEquals(today.plusDays(1), query.period.toExclusive)
    }

    @Test
    fun `how much on petrol in a year`() {
        val query = ask("كم صرفت بنزين 2024")!!
        assertEquals(Subject.OfTopic(Topic.FUEL), query.subject)
        assertEquals(LocalDate.of(2024, 1, 1), query.period.from)
        assertEquals(LocalDate.of(2025, 1, 1), query.period.toExclusive)
        assertEquals(PeriodLabel.NAMED_YEAR, query.period.label)
    }

    // ---- periods ------------------------------------------------------------

    @Test
    fun `this month and last month are the calendar months, not thirty days`() {
        assertEquals(LocalDate.of(2026, 9, 1), ask("كم صرفت هذا الشهر")!!.period.from)
        assertEquals(LocalDate.of(2026, 10, 1), ask("كم صرفت هذا الشهر")!!.period.toExclusive)

        val last = ask("كم صرفت الشهر الماضي")!!.period
        assertEquals(LocalDate.of(2026, 8, 1), last.from)
        assertEquals(LocalDate.of(2026, 9, 1), last.toExclusive)
        assertEquals(PeriodLabel.LAST_MONTH, last.label)
    }

    @Test
    fun `a year on its own, and last year`() {
        assertEquals(LocalDate.of(2025, 1, 1), ask("كم صرفت السنة الماضية")!!.period.from)
        assertEquals(LocalDate.of(2026, 1, 1), ask("كم صرفت هذي السنة")!!.period.from)
    }

    @Test
    fun `yesterday is one day and it is not today`() {
        val y = ask("كم صرفت امس")!!.period
        assertEquals(today.minusDays(1), y.from)
        assertEquals(today, y.toExclusive)
    }

    @Test
    fun `last thirty days is counted back from today`() {
        val p = ask("كم صرفت آخر 30 يوم")!!.period
        assertEquals(today.minusDays(30), p.from)
        assertEquals(today.plusDays(1), p.toExclusive)
    }

    /**
     * No period named means the whole history, and the label says so. An unstated
     * period must never be quietly assumed to be this month: the reader would have
     * no way to tell which question was answered.
     */
    @Test
    fun `no period at all means everything, and says so`() {
        val p = ask("كم صرفت على القهوة")!!.period
        assertNull(p.from)
        assertEquals(PeriodLabel.ALL_TIME, p.label)
    }

    // ---- subjects -----------------------------------------------------------

    @Test
    fun `a category is recognised by its own name`() {
        assertEquals(
            Subject.OfCategory(SaudiCategories.GROCERIES),
            ask("كم صرفت على البقالة الشهر الماضي")!!.subject,
        )
    }

    /**
     * The distinction this whole feature turns on. Petrol is a topic, not the
     * transport category - `transport` also holds the tyre shop, the garage, the
     * spare-part suppliers and the car wash.
     */
    @Test
    fun `petrol is not the transport category`() {
        assertEquals(Subject.OfTopic(Topic.FUEL), ask("كم صرفت بنزين")!!.subject)
        assertEquals(
            Subject.OfCategory(SaudiCategories.TRANSPORT),
            ask("كم صرفت على المواصلات")!!.subject,
        )
    }

    @Test
    fun `a merchant the user names is matched as a merchant`() {
        assertEquals(Subject.AtMerchant("امازون"), ask("كم صرفت في امازون")!!.subject)
    }

    // ---- measures -----------------------------------------------------------

    @Test
    fun `how many times is a count, not a total`() {
        assertEquals(Measure.COUNT, ask("كم مرة صرفت بنزين هذا الشهر")!!.measure)
    }

    @Test
    fun `the biggest one is a largest`() {
        assertEquals(Measure.LARGEST, ask("اكبر مبلغ صرفته هذا الشهر")!!.measure)
    }

    @Test
    fun `show me is a list`() {
        assertEquals(Measure.LIST, ask("اعرض مصروف القهوة الشهر الماضي")!!.measure)
    }

    @Test
    fun `income is asked for by its own words`() {
        assertEquals(Flow.INCOME, ask("كم دخلي هذا الشهر")!!.flow)
        assertEquals(Flow.SPENDING, ask("كم صرفت هذا الشهر")!!.flow)
    }

    // ---- and the questions it must refuse ------------------------------------

    /**
     * A question with a period in it but nothing to measure is NOT a spending
     * question, and must not be answered as though it were. "How is the weather
     * today" would otherwise come back as this month's total.
     */
    @Test
    fun `a question that is not about money returns nothing`() {
        assertNull(ask("كيف الطقس اليوم"))
        assertNull(ask("وش اخبارك"))
        assertNull(ask(""))
        assertNull(ask("2026"))
    }

    /**
     * An unrecognised noun becomes a merchant lookup, not a refusal and not a total
     * of everything. Answering "how much on <something I do not know>" with the
     * whole month would be a number of the right shape and the wrong meaning; the
     * merchant reading is answerable and comes back empty, which is true.
     */
    @Test
    fun `an unknown noun is looked up as a merchant, not answered as everything`() {
        assertEquals(Subject.AtMerchant("الفضاء"), ask("كم صرفت على الفضاء")!!.subject)
    }
}
