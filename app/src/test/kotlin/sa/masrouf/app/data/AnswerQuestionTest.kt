package sa.masrouf.app.data

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import sa.masrouf.core.ask.Flow
import sa.masrouf.core.ask.Measure
import sa.masrouf.core.ask.PeriodLabel
import sa.masrouf.core.ask.Subject
import sa.masrouf.core.ask.Topic
import sa.masrouf.core.model.SaudiCategories
import sa.masrouf.core.money.Money
import sa.masrouf.core.time.RiyadhTime
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A typed question, all the way to a figure.
 *
 * The parser has its own tests and so does the arithmetic; this is the seam - that
 * the date bounds handed to SQL are the ones the period meant, in Riyadh time, and
 * that the "have I ever" question is answered by the index rather than assumed.
 */
class AnswerQuestionTest {

    private val dao = FakeDao()
    private val repository = TransactionRepository(dao)
    private val today = LocalDate.of(2026, 9, 10)

    private var next = 0

    private fun stored(
        riyals: String,
        on: LocalDate,
        category: String? = null,
        merchant: String? = null,
        type: String = "PURCHASE",
    ) = TransactionEntity(
        id = "t${next++}",
        amountHalalas = Money.ofMajor(riyals).halalas,
        direction = "DEBIT",
        type = type,
        occurredAtMillis = on.atTime(12, 0).atZone(RiyadhTime.ZONE).toInstant().toEpochMilli(),
        accountId = null,
        accountLast4 = null,
        categoryId = category,
        categorySource = "AUTOMATIC",
        merchantRaw = merchant,
        merchantKey = merchant?.uppercase(),
        note = null,
        source = "SMS",
        status = "CONFIRMED",
        fingerprint = "fp$next",
        rawText = null,
        currency = "SAR",
    )

    @Test
    fun `a month question sees that month and not the day either side of it`() = runTest {
        dao.replaceAll(
            listOf(
                // the last hour of August and the first of October, in Riyadh time
                stored("500.00", LocalDate.of(2026, 8, 31)),
                stored("100.00", LocalDate.of(2026, 9, 1)),
                stored("60.00", LocalDate.of(2026, 9, 30)),
                stored("900.00", LocalDate.of(2026, 10, 1)),
            ),
        )

        val answer = repository.answer("كم صرفت هذا الشهر", today)!!

        assertEquals(Money.ofMajor("160.00"), answer.total)
        assertEquals(PeriodLabel.THIS_MONTH, answer.query.period.label)
    }

    @Test
    fun `petrol is answered with the stations and not with the transport category`() = runTest {
        dao.replaceAll(
            listOf(
                stored("120.00", today, SaudiCategories.TRANSPORT.id, "ALDREES 1"),
                stored("80.00", today, SaudiCategories.TRANSPORT.id, "SASCO STA"),
                stored("650.00", today, SaudiCategories.TRANSPORT.id, "Fourth frame EST"),
            ),
        )

        val petrol = repository.answer("كم صرفت بنزين اليوم", today)!!
        assertEquals(Subject.OfTopic(Topic.FUEL), petrol.query.subject)
        assertEquals(Money.ofMajor("200.00"), petrol.total)

        val transport = repository.answer("كم صرفت على المواصلات اليوم", today)!!
        assertEquals(Money.ofMajor("850.00"), transport.total)
    }

    @Test
    fun `a merchant this history has never seen is reported as unknown, not as zero`() = runTest {
        dao.replaceAll(listOf(stored("40.00", today, merchant = "Amazon SA")))

        val known = repository.answer("كم صرفت في امازون الشهر الماضي", today)!!
        assertTrue(known.isEmpty, "nothing last month")
        assertTrue(known.subjectSeenEver, "but amazon is certainly in the history")

        val unknown = repository.answer("كم صرفت في زابلونيا", today)!!
        assertTrue(unknown.isEmpty)
        assertFalse(unknown.subjectSeenEver)
    }

    @Test
    fun `a question with no period reaches the whole history`() = runTest {
        dao.replaceAll(
            listOf(
                stored("10.00", LocalDate.of(2015, 1, 1), merchant = "ALDREES 1"),
                stored("20.00", today, merchant = "SASCO"),
            ),
        )

        val answer = repository.answer("كم صرفت بنزين", today)!!
        assertEquals(Money.ofMajor("30.00"), answer.total)
        assertEquals(PeriodLabel.ALL_TIME, answer.query.period.label)
    }

    @Test
    fun `a question it cannot read is refused rather than answered`() = runTest {
        dao.replaceAll(listOf(stored("40.00", today)))
        assertNull(repository.answer("كيف الطقس اليوم", today))
        assertNull(repository.answer("   ", today))
    }

    @Test
    fun `counting is not totalling`() = runTest {
        dao.replaceAll(
            listOf(
                stored("120.00", today, merchant = "ALDREES 1"),
                stored("80.00", today, merchant = "SASCO"),
            ),
        )

        val answer = repository.answer("كم مرة صرفت بنزين اليوم", today)!!
        assertEquals(Measure.COUNT, answer.query.measure)
        assertEquals(2, answer.count)
        // The total is still carried, because a count question shows both.
        assertEquals(Money.ofMajor("200.00"), answer.total)
    }

    @Test
    fun `income is asked of the income categories`() = runTest {
        dao.replaceAll(
            listOf(
                stored("19000.00", today, SaudiCategories.INCOME.id, type = "SALARY"),
                stored("300.00", today, SaudiCategories.FOOD.id),
            ),
        )

        val answer = repository.answer("كم دخلي هذا الشهر", today)!!
        assertEquals(Flow.INCOME, answer.query.flow)
        assertEquals(Money.ofMajor("19000.00"), answer.total)
    }
}
