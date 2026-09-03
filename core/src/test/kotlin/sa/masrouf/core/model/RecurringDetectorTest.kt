package sa.masrouf.core.model

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import sa.masrouf.core.money.Money
import java.time.Duration
import java.time.Instant

/**
 * What counts as a payment on a rhythm.
 *
 * Every threshold here was set against a real 12,525-record history, where the
 * first cut called three iHerb orders in a fortnight "weekly", called a daily
 * Amazon habit a standing order, and missed Netflix because its descriptor
 * changed from "NETFLIX COM" to "NETFLIX" in June.
 */
class RecurringDetectorTest {

    private val now = Instant.parse("2026-08-30T12:00:00Z")

    private fun tx(
        merchant: String,
        daysAgo: Long,
        riyals: String,
        type: TransactionType = TransactionType.PURCHASE,
        categoryId: String? = null,
    ) = Transaction(
        id = "$merchant-$daysAgo",
        amount = Money.ofMajor(riyals),
        direction = Direction.DEBIT,
        type = type,
        occurredAt = now.minus(Duration.ofDays(daysAgo)),
        accountId = null,
        categoryId = categoryId,
        merchantRaw = merchant,
        merchantKey = merchant.uppercase(),
        note = null,
        source = Source.SMS,
        status = Status.CONFIRMED,
        fingerprint = "$merchant-$daysAgo",
        rawText = null,
    )

    @Test
    fun `the same amount every thirty days is a monthly subscription`() {
        val rows = listOf(2L, 32, 61, 92, 122).map { tx("NETFLIX COM", it, "71.00") }

        val found = RecurringDetector.detect(rows, now)

        val netflix = found.single()
        assertEquals(RecurringDetector.Cadence.MONTHLY, netflix.cadence)
        assertEquals(Money.ofMajor("71.00"), netflix.typicalAmount)
        assertEquals(now.minus(Duration.ofDays(2)).plus(Duration.ofDays(30)), netflix.nextExpected)
    }

    @Test
    fun `three orders in a fortnight are shopping, not a subscription`() {
        val rows = listOf(1L, 8, 15).map { tx("IHERB", it, "801.11") }

        assertTrue(RecurringDetector.detect(rows, now).isEmpty())
    }

    @Test
    fun `a bill whose amount moves with usage is still a bill`() {
        val rows = listOf("92.00", "92.00", "203.00", "92.00", "58.00", "65.00")
            .mapIndexed { i, amount -> tx("MOBILY", 3L + i * 30, amount) }

        assertEquals(RecurringDetector.Cadence.MONTHLY, RecurringDetector.detect(rows, now).single().cadence)
    }

    @Test
    fun `a shop visited daily is not a standing order unless the amount never moves`() {
        val shopping = (0L until 10).map { tx("AMAZON SA", it, listOf("73.90", "120.00", "45.50", "88.00")[it.toInt() % 4]) }
        val donation = (0L until 10).map { tx("HEALTH ENDOWMENT FUND", it, "10.00") }

        val found = RecurringDetector.detect(shopping + donation, now)

        assertEquals(listOf("HEALTH ENDOWMENT FUND"), found.map { it.merchantRaw })
        assertEquals(RecurringDetector.Cadence.DAILY, found.single().cadence)
    }

    @Test
    fun `a rhythm that stopped is history, not a commitment`() {
        val rows = listOf(100L, 130, 160, 190, 220).map { tx("GOOGLE YOUTUBEPREMIUM", it, "49.99") }

        assertTrue(RecurringDetector.detect(rows, now).isEmpty())
    }

    /** "NETFLIX COM" for eighteen months, then "NETFLIX": one subscription. */
    @Test
    fun `a merchant whose descriptor changes is judged as one merchant`() {
        val rows = listOf(62L, 92, 122, 152).map { tx("NETFLIX COM", it, "71.00") } +
            listOf(1L, 31).map { tx("NETFLIX", it, "71.00") }

        val found = RecurringDetector.detect(rows, now)

        assertEquals(1, found.size)
        assertEquals(6, found.single().occurrences)
    }

    @Test
    fun `only the recent rhythm is judged`() {
        // Irregular two years ago, monthly for half a year since: a subscription
        // now. The window is the last eight events, so the old gaps are outvoted.
        val old = listOf(400L, 470, 520, 700).map { tx("SPOTIFY", it, "21.99") }
        val recent = listOf(5L, 35, 65, 95, 125, 155).map { tx("SPOTIFY", it, "21.99") }

        assertEquals(1, RecurringDetector.detect(old + recent, now).size)
    }

    /**
     * Two bills under one descriptor: STC charges the phone line on the 2nd and
     * the home internet on the 28th, both as STCPOSTPA. As one stream the gaps
     * alternate 4 and 26 days and nothing fits; as two amount clusters both are
     * monthly.
     */
    @Test
    fun `two bills under one merchant are found as two rhythms`() {
        val phone = listOf(2L, 32, 63, 93, 124).map { tx("STCPOSTPA", it, "1041.00") }
        val internet = listOf(6L, 37, 67, 98, 128).map { tx("STCPOSTPA", it, "286.00") }

        val found = RecurringDetector.detect(phone + internet, now)

        assertEquals(2, found.size)
        assertEquals(listOf(Money.ofMajor("1041.00"), Money.ofMajor("286.00")), found.map { it.typicalAmount })
        assertTrue(found.all { it.cadence == RecurringDetector.Cadence.MONTHLY })
    }

    /**
     * A movement is not a payment.
     *
     * The owner transfers money to his own AlRajhi account most weeks - 186 of
     * them - and the panel read that as a recurring payment, announcing on his
     * home screen that he pays out 102,890 riyals a month. The filter is
     * [countsAsSpending], the app's single decision about which debits are money
     * leaving, so this panel and the month's total can never disagree again.
     */
    @Test
    fun `a standing transfer to the owner's own account is not a payment`() {
        val rows = listOf(2L, 32, 61, 92, 122)
            .map { tx("KHALEEL MALKI", it, "3500.00", type = TransactionType.OWN_TRANSFER) }

        assertTrue(RecurringDetector.detect(rows, now).isEmpty())
    }

    /** A monthly deposit at the investment house is money he still has. */
    @Test
    fun `a standing investment deposit is not a payment`() {
        val rows = listOf(2L, 32, 61, 92, 122).map {
            tx("TAMRA CAPITAL", it, "2000.00", categoryId = SaudiCategories.INVESTMENT.id)
        }

        assertTrue(RecurringDetector.detect(rows, now).isEmpty())
    }

    /** But a standing transfer to someone else is money that left. */
    @Test
    fun `a standing transfer to another person is still a payment`() {
        val rows = listOf(2L, 32, 61, 92, 122)
            .map { tx("HANEEN", it, "1200.00", type = TransactionType.TRANSFER_OUT) }

        assertEquals(1, RecurringDetector.detect(rows, now).size)
    }
}
