package sa.masrouf.core.ask

import org.junit.jupiter.api.Test
import sa.masrouf.core.model.Direction
import sa.masrouf.core.model.SaudiCategories
import sa.masrouf.core.model.Source
import sa.masrouf.core.model.Status
import sa.masrouf.core.model.Transaction
import sa.masrouf.core.model.TransactionType
import sa.masrouf.core.money.Money
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What a question comes to, given rows.
 *
 * The arithmetic is here rather than in SQL so that it is proven on any machine
 * with a JDK, and so that it goes through the same `countsAsSpending` the month
 * total goes through. The tests that matter most are the ones asserting what is
 * LEFT OUT: a pending slip, a transfer to himself, and a deposit at the brokerage
 * are all money leaving an account and none of them is spending.
 */
class AskAnswerTest {

    private var next = 0

    private fun row(
        riyals: String,
        category: String? = null,
        merchant: String? = null,
        type: TransactionType = TransactionType.PURCHASE,
        direction: Direction = Direction.DEBIT,
        status: Status = Status.CONFIRMED,
    ) = Transaction(
        id = "t${next++}",
        amount = Money.ofMajor(riyals),
        direction = direction,
        type = type,
        occurredAt = Instant.EPOCH.plusSeconds(next.toLong()),
        accountId = null,
        categoryId = category,
        merchantRaw = merchant,
        merchantKey = merchant,
        note = null,
        source = Source.SMS,
        status = status,
        fingerprint = "fp$next",
        rawText = null,
        currency = "SAR",
    )

    private fun query(
        subject: Subject = Subject.Everything,
        measure: Measure = Measure.TOTAL,
        flow: Flow = Flow.SPENDING,
    ) = AskQuery(measure, subject, Period.Always, flow)

    @Test
    fun `a total is the sum of what counts as spending, and nothing else`() {
        val answer = query().answeredFrom(
            listOf(
                row("100.00"),
                row("50.50"),
                // none of these is spending, and each is a different reason
                row("900.00", type = TransactionType.OWN_TRANSFER),
                row("800.00", category = SaudiCategories.INVESTMENT.id),
                row("700.00", direction = Direction.CREDIT, type = TransactionType.REFUND),
                row("600.00", status = Status.PENDING),
            ),
        )

        assertEquals(Money.ofMajor("150.50"), answer.total)
        assertEquals(2, answer.count)
    }

    @Test
    fun `a topic selects its merchants and leaves the rest of the category alone`() {
        val rows = listOf(
            row("100.00", category = SaudiCategories.TRANSPORT.id, merchant = "ALDREES 1"),
            row("60.00", category = SaudiCategories.TRANSPORT.id, merchant = "SASCO STA"),
            // same category, and emphatically not petrol
            row("900.00", category = SaudiCategories.TRANSPORT.id, merchant = "Fourth frame EST"),
            row("500.00", category = SaudiCategories.TRANSPORT.id, merchant = "MS 21535 KUDAY"),
        )

        val fuel = query(Subject.OfTopic(Topic.FUEL)).answeredFrom(rows)
        assertEquals(Money.ofMajor("160.00"), fuel.total)

        val transport = query(Subject.OfCategory(SaudiCategories.TRANSPORT)).answeredFrom(rows)
        assertEquals(Money.ofMajor("1560.00"), transport.total)
    }

    @Test
    fun `a merchant is matched however the person spells the part they remember`() {
        val rows = listOf(
            row("30.00", merchant = "Amazon SA"),
            row("20.00", merchant = "amazon no"),
            row("99.00", merchant = "Noon"),
        )
        assertEquals(Money.ofMajor("50.00"), query(Subject.AtMerchant("amazon")).answeredFrom(rows).total)
    }

    @Test
    fun `the largest is the largest, and the count is all of them`() {
        val answer = query(measure = Measure.LARGEST)
            .answeredFrom(listOf(row("10.00"), row("400.00"), row("70.00")))

        assertEquals(Money.ofMajor("400.00"), answer.largest?.amount)
        assertEquals(3, answer.count)
    }

    @Test
    fun `income is asked of the income categories, not of the direction`() {
        val rows = listOf(
            row("19000.00", category = SaudiCategories.INCOME.id, direction = Direction.CREDIT, type = TransactionType.SALARY),
            row("2000.00", category = SaudiCategories.BONUS.id, direction = Direction.CREDIT, type = TransactionType.TRANSFER_IN),
            // arriving, and not income: money coming back from his own brokerage
            row("5000.00", category = SaudiCategories.INVESTMENT.id, direction = Direction.CREDIT, type = TransactionType.TRANSFER_IN),
        )

        assertEquals(Money.ofMajor("21000.00"), query(flow = Flow.INCOME).answeredFrom(rows).total)
    }

    /**
     * The cap is on what is SHOWN. A total that quietly covered the first two
     * hundred rows would be wrong in the direction nobody checks.
     */
    @Test
    fun `the rows are capped and the total is not`() {
        val answer = query().answeredFrom(List(250) { row("1.00") })

        assertEquals(250, answer.count)
        assertEquals(Money.ofMajor("250.00"), answer.total)
        assertEquals(200, answer.rows.size)
        assertEquals(50, answer.moreRows)
    }

    /**
     * "Nothing this month" and "no such shop" are different sentences, and the
     * screen must not print the first when it means the second.
     */
    @Test
    fun `a merchant never seen is carried differently from a merchant with a quiet month`() {
        val quiet = query(Subject.AtMerchant("amazon"))
            .answeredFrom(emptyList(), subjectSeenEver = true)
        assertTrue(quiet.isEmpty)
        assertTrue(quiet.subjectSeenEver, "amazon is in the history; the period is just empty")

        val never = query(Subject.AtMerchant("الفضاء"))
            .answeredFrom(emptyList(), subjectSeenEver = false)
        assertTrue(never.isEmpty)
        assertFalse(never.subjectSeenEver, "nothing by that name has ever been bought")
    }

    /**
     * An unfiled row is selected by the ABSENCE of a category, and a row filed as
     * anything at all - including the ones a category makes non-spending - is not
     * in the answer.
     */
    @Test
    fun `unfiled selects what has no category and nothing else`() {
        val answer = query(Subject.Unfiled).answeredFrom(
            listOf(
                row("40.00"),
                row("60.00"),
                row("900.00", category = SaudiCategories.FOOD.id),
            ),
        )

        assertEquals(Money.ofMajor("100.00"), answer.total)
        assertEquals(2, answer.count)
    }

    @Test
    fun `rows come back newest first`() {
        val answer = query().answeredFrom(listOf(row("1.00"), row("2.00"), row("3.00")))
        assertEquals(listOf("3.00", "2.00", "1.00"), answer.rows.map { it.amount.toPlainString() })
    }

    /**
     * The defect the owner found by using it: the cap is applied to the SORTED
     * list, so a list ordered by date and then capped can leave out the largest
     * record entirely - and he went looking for a 17,000-riyal row that was not on
     * the screen.
     */
    @Test
    fun `sorting happens before the cap, not after it`() {
        // 250 small recent rows, and one large old one that a date sort buries.
        val rows = List(250) { row("1.00") } + listOf(row("17033.00").copy(
            occurredAt = Instant.EPOCH,
        ))

        val byDate = query().answeredFrom(rows, sort = AskSort.NEWEST)
        assertEquals(200, byDate.rows.size)
        assertTrue(byDate.rows.none { it.amount == Money.ofMajor("17033.00") })

        val bySize = query().answeredFrom(rows, sort = AskSort.LARGEST)
        assertEquals(Money.ofMajor("17033.00"), bySize.rows.first().amount)
        // and the figure is the same either way, because the cap is on what is SHOWN
        assertEquals(byDate.total, bySize.total)
    }

    /**
     * A worklist is about value, not recency: one tap on a seventeen-thousand-riyal
     * merchant is worth a hundred taps on coffees.
     */
    @Test
    fun `unfiled defaults to largest first and everything else to newest`() {
        assertEquals(AskSort.LARGEST, AskSort.forSubject(Subject.Unfiled))
        assertEquals(AskSort.NEWEST, AskSort.forSubject(Subject.Everything))
        assertEquals(AskSort.NEWEST, AskSort.forSubject(Subject.OfTopic(Topic.FUEL)))
    }
}
