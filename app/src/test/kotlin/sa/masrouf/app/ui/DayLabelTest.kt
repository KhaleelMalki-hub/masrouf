package sa.masrouf.app.ui

import kotlin.test.assertEquals
import org.junit.jupiter.api.Test
import sa.masrouf.core.dedup.Fingerprint
import sa.masrouf.core.model.Direction
import sa.masrouf.core.model.Source
import sa.masrouf.core.model.Status
import sa.masrouf.core.model.Transaction
import sa.masrouf.core.model.TransactionType
import sa.masrouf.core.money.Money
import java.time.Instant

class DayLabelTest {

    private fun at(instant: Instant) = Transaction(
        id = "t",
        amount = Money.ofMajor("10.00"),
        direction = Direction.DEBIT,
        type = TransactionType.PURCHASE,
        occurredAt = instant,
        accountId = null,
        categoryId = null,
        merchantRaw = null,
        merchantKey = null,
        note = null,
        source = Source.MANUAL,
        status = Status.CONFIRMED,
        fingerprint = Fingerprint.forManual("t"),
        rawText = null,
    )

    @Test
    fun `the day is formatted with ascii digits, as the amounts are`() {
        // A localised formatter would print ٢٨/٠٨/٢٦ in Arabic beside an amount
        // printed 125.00. The pattern has no text fields, so it cannot vary.
        assertEquals("28/08/26", at(Instant.parse("2026-08-28T09:00:00Z")).dayLabel())
    }

    @Test
    fun `a purchase after midnight in riyadh belongs to the riyadh day`() {
        // 22:30 UTC is 01:30 the next morning in Riyadh. Formatting the instant in
        // UTC would file this under the previous day, and the row would contradict
        // the monthly total, which is computed in Riyadh.
        val lateNight = Instant.parse("2026-08-27T22:30:00Z")

        assertEquals("28/08/26", at(lateNight).dayLabel())
    }

    @Test
    fun `just before midnight riyadh is still the same day`() {
        // 20:59 UTC is 23:59 in Riyadh - the last minute of the 27th.
        assertEquals("27/08/26", at(Instant.parse("2026-08-27T20:59:00Z")).dayLabel())
    }
}
