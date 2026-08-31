package sa.masrouf.core.model

import org.junit.jupiter.api.Test
import sa.masrouf.core.model.RecurringDetector.spread
import sa.masrouf.core.money.Money
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A median shown on its own reads as a promise.
 *
 * The panel put "357.42" beside the word "monthly" for an electricity bill that
 * has ranged from 299 to 554, and the owner read it as a figure the app expected
 * him to pay. He was right to object: nothing on the row said the number was a
 * median of six months rather than the amount of the next one.
 *
 * The bill still belongs on the panel - it is one of his largest standing
 * commitments, and dropping it to avoid quoting a number would hide it. What the
 * detector now carries is the range it actually saw, so the row can say "about"
 * and show it.
 */
class RecurringSpreadTest {

    private val now: Instant = Instant.parse("2026-09-01T00:00:00Z")

    private var nextId = 0

    private fun bill(riyals: String, monthsAgo: Long, merchant: String) = Transaction(
        id = "t-${nextId++}",
        amount = Money.ofMajor(riyals),
        direction = Direction.DEBIT,
        type = TransactionType.PURCHASE,
        occurredAt = now.minus(Duration.ofDays(monthsAgo * 30)),
        accountId = null,
        categoryId = SaudiCategories.BILLS.id,
        merchantRaw = merchant,
        merchantKey = merchant,
        note = null,
        source = Source.SMS,
        status = Status.CONFIRMED,
        fingerprint = "fp-$nextId",
        rawText = null,
    )

    private fun detect(vararg riyals: String, merchant: String = "SAUDI ELECTRICITY") =
        RecurringDetector
            .detect(riyals.mapIndexed { i, r -> bill(r, riyals.size - 1L - i, merchant) }, now)
            .single()

    /**
     * The owner's own last six electricity bills. The median is the honest single
     * figure and the range is what keeps it from being read as the next one.
     */
    @Test
    fun `a bill that moves reports the range it was judged on`() {
        val found = detect("299", "384", "554", "302", "311", "463")

        // The upper of the two middle values: the detector takes index size/2, which
        // for an even count is the higher one. Asserted as it is rather than as it
        // "should" be - this test is about the range, and quietly changing how the
        // typical figure is chosen would move every row on the panel.
        assertEquals(Money.ofMajor("384"), found.typicalAmount)
        assertEquals(Money.ofMajor("299"), found.lowAmount)
        assertEquals(Money.ofMajor("554"), found.highAmount)
    }

    @Test
    fun `a bill that moves is flagged as varying`() {
        val found = detect("299", "384", "554", "302", "311", "463")

        assertTrue(found.spread() > RecurringDetector.VARIES_ABOVE, "spread was ${found.spread()}")
    }

    /**
     * The other half. A fixed subscription must not acquire a range and an "about"
     * it does not need - Netflix has been exactly 71.00 every month.
     */
    @Test
    fun `a fixed subscription is not flagged as varying`() {
        val found = detect("71", "71", "71", "71", "71", "71", merchant = "NETFLIX COM")

        assertEquals(Money.ofMajor("71"), found.typicalAmount)
        assertEquals(found.lowAmount, found.highAmount)
        assertTrue(found.spread() <= RecurringDetector.VARIES_ABOVE, "spread was ${found.spread()}")
    }

    /**
     * The owner also questioned these two. Measured over his own history they
     * barely move at all, so they must keep reading as the fixed figures they are -
     * the objection was to the electricity, and the fix must not spread from it.
     */
    @Test
    fun `a bill that barely moves is not flagged as varying`() {
        val phone = detect("58", "58", "65", "58", "67", "62", merchant = "MOBILY")

        assertTrue(phone.spread() <= RecurringDetector.VARIES_ABOVE, "spread was ${phone.spread()}")
    }
}
