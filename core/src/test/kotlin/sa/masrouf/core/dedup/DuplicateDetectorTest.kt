package sa.masrouf.core.dedup

import org.junit.jupiter.api.Test
import sa.masrouf.core.model.Direction
import sa.masrouf.core.model.Source
import sa.masrouf.core.money.Money
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DuplicateDetectorTest {

    private val detector = DuplicateDetector()

    /** 2026-08-27, Riyadh local time. */
    private fun at(time: String): Instant = Instant.parse("2026-08-27T${time}:00Z")

    private fun sig(
        amount: String,
        source: Source,
        time: String = "05:02",
        last4: String? = "1887",
        merchant: String? = "barq",
        direction: Direction = Direction.DEBIT,
    ) = EventSignature.of(
        amount = Money.ofMajor(amount),
        direction = direction,
        occurredAt = at(time),
        source = source,
        last4 = last4,
        merchantRaw = merchant,
    )

    // ---- The case the design exists for ------------------------------------

    /**
     * Two real wallet top-ups, 49 minutes apart, identical in amount, day, card and
     * merchant. Merging them would destroy 5,000 SAR and leave a record that looks
     * completely correct.
     */
    @Test
    fun `two identical top-ups on the same day stay separate`() {
        val first = sig("5000", Source.SMS, time = "05:02")
        val second = sig("5000", Source.SMS, time = "05:51")

        val result = detector.reconcile(existing = listOf(first), incoming = listOf(second))

        assertTrue(result.matches.isEmpty(), "the two top-ups were wrongly merged")
        assertEquals(listOf(0), result.newIncoming)
    }

    @Test
    fun `a redelivered message within the redelivery window is one event`() {
        val original = sig("5000", Source.SMS, time = "05:02")
        val redelivered = sig("5000", Source.NOTIFICATION, time = "05:03")

        val result = detector.reconcile(listOf(original), listOf(redelivered))

        assertEquals(1, result.matches.size)
        assertTrue(result.newIncoming.isEmpty())
    }

    // ---- Cross-source merging ----------------------------------------------

    @Test
    fun `a statement row merges with the notification for the same purchase`() {
        val notified = sig("931.64", Source.NOTIFICATION, time = "13:59", last4 = "2383", merchant = "IHERB ARA")
        val statementRow = sig("931.64", Source.STATEMENT, time = "00:00", last4 = "2383", merchant = "IHERB ARA 884213")

        val result = detector.reconcile(listOf(notified), listOf(statementRow))

        assertEquals(1, result.matches.size)
        assertTrue(result.matches.single().merchantAgrees, "the reference tail should not break the merchant key")
        assertTrue(result.newIncoming.isEmpty())
    }

    /**
     * A late-night purchase commonly posts to the statement on the following date.
     *
     * Note the instants: 20:18Z is 23:18 in Riyadh on the 26th, and the statement's
     * 00:00Z is 03:00 in Riyadh on the 27th. One day apart in Riyadh - which is the
     * only calendar this app counts in, and the reason day arithmetic lives behind
     * RiyadhTime rather than being done inline against UTC.
     */
    @Test
    fun `a statement row one day later still merges`() {
        val notified = EventSignature.of(
            Money.ofMajor("44.29"), Direction.DEBIT,
            Instant.parse("2026-08-26T20:18:00Z"), Source.NOTIFICATION, "2383", "Ninja Foo",
        )
        val statementRow = EventSignature.of(
            Money.ofMajor("44.29"), Direction.DEBIT,
            Instant.parse("2026-08-27T00:00:00Z"), Source.STATEMENT, "2383", "Ninja Foo",
        )

        assertEquals(java.time.LocalDate.of(2026, 8, 26), notified.day)
        assertEquals(java.time.LocalDate.of(2026, 8, 27), statementRow.day)

        val result = detector.reconcile(listOf(notified), listOf(statementRow))
        assertEquals(1, result.matches.size)
    }

    /**
     * The trap the test above walked into on its first writing: an instant late in
     * the UTC day is already the next day in Riyadh. A detector doing its day
     * arithmetic in UTC would put these two three days apart and refuse a merge
     * that should happen.
     */
    @Test
    fun `days are counted in Riyadh, not UTC`() {
        val lateNightRiyadh = EventSignature.of(
            Money.ofMajor("44.29"), Direction.DEBIT,
            Instant.parse("2026-08-26T22:00:00Z"), Source.NOTIFICATION, "2383", "Ninja Foo",
        )
        assertEquals(
            java.time.LocalDate.of(2026, 8, 27),
            lateNightRiyadh.day,
            "22:00 UTC is 01:00 the next day in Riyadh",
        )
    }

    @Test
    fun `a statement row three days later is a different transaction`() {
        val notified = EventSignature.of(
            Money.ofMajor("44.29"), Direction.DEBIT,
            Instant.parse("2026-08-22T20:18:00Z"), Source.NOTIFICATION, "2383", "Ninja Foo",
        )
        val later = EventSignature.of(
            Money.ofMajor("44.29"), Direction.DEBIT,
            Instant.parse("2026-08-26T20:18:00Z"), Source.STATEMENT, "2383", "Ninja Foo",
        )

        val result = detector.reconcile(listOf(notified), listOf(later))
        assertTrue(result.matches.isEmpty())
        assertEquals(listOf(0), result.newIncoming)
    }

    // ---- Counting, not just keying -----------------------------------------

    /**
     * The two 5,000 top-ups, later imported from a statement that lists both.
     * The answer is two merges - not one merge and one duplicate, and not four
     * records.
     */
    @Test
    fun `two identical transactions merge with two identical statement rows`() {
        val existing = listOf(
            sig("5000", Source.SMS, time = "05:02"),
            sig("5000", Source.SMS, time = "05:51"),
        )
        val incoming = listOf(
            sig("5000", Source.STATEMENT, time = "00:00"),
            sig("5000", Source.STATEMENT, time = "00:00"),
        )

        val result = detector.reconcile(existing, incoming)

        assertEquals(2, result.matches.size)
        assertTrue(result.newIncoming.isEmpty())
        assertEquals(
            setOf(0, 1),
            result.matches.map { it.existingIndex }.toSet(),
            "each stored record must absorb exactly one row",
        )
    }

    @Test
    fun `a surplus statement row beyond what was captured is new`() {
        val existing = listOf(sig("5000", Source.SMS, time = "05:02"))
        val incoming = listOf(
            sig("5000", Source.STATEMENT, time = "00:00"),
            sig("5000", Source.STATEMENT, time = "00:00"),
            sig("5000", Source.STATEMENT, time = "00:00"),
        )

        val result = detector.reconcile(existing, incoming)

        assertEquals(1, result.matches.size)
        assertEquals(2, result.newIncoming.size, "the two unseen top-ups must be recorded")
    }

    @Test
    fun `re-importing an overlapping statement adds nothing`() {
        val august = listOf(
            sig("931.64", Source.STATEMENT, time = "00:00", last4 = "2383", merchant = "IHERB ARA"),
            sig("8.28", Source.STATEMENT, time = "00:00", last4 = "5763", merchant = "ASIAN POLYCLINI"),
        )
        val julyToAugust = august

        val result = detector.reconcile(august, julyToAugust)

        assertEquals(2, result.matches.size)
        assertTrue(result.newIncoming.isEmpty())
    }

    // ---- Things that must never merge --------------------------------------

    @Test
    fun `different amounts never merge`() {
        val result = detector.reconcile(
            listOf(sig("8.28", Source.NOTIFICATION)),
            listOf(sig("4.42", Source.STATEMENT)),
        )
        assertTrue(result.matches.isEmpty())
    }

    @Test
    fun `opposite directions never merge`() {
        val spend = sig("2000", Source.NOTIFICATION, direction = Direction.DEBIT)
        val receive = sig("2000", Source.STATEMENT, direction = Direction.CREDIT)

        assertTrue(detector.reconcile(listOf(spend), listOf(receive)).matches.isEmpty())
    }

    @Test
    fun `different cards never merge`() {
        val onVisa = sig("320", Source.NOTIFICATION, last4 = "2383")
        val onMada = sig("320", Source.STATEMENT, last4 = "5763")

        assertTrue(detector.reconcile(listOf(onVisa), listOf(onMada)).matches.isEmpty())
    }

    /**
     * Transfers name no card at all. Treating an absent card as a mismatch would
     * leave every transfer permanently un-mergeable across sources.
     */
    @Test
    fun `a missing card on one side does not block a merge`() {
        val transfer = sig("350.00", Source.NOTIFICATION, last4 = null, merchant = "RECIPIENT NAME")
        val statementRow = sig("350.00", Source.STATEMENT, last4 = "2207", merchant = "RECIPIENT NAME")

        assertEquals(1, detector.reconcile(listOf(transfer), listOf(statementRow)).matches.size)
    }

    /**
     * A hand-entered record is the only one in the system a human vouched for.
     * Folding it into an imported row silently discards that.
     */
    @Test
    fun `a manually entered transaction is never merged away`() {
        val typed = sig("87.50", Source.MANUAL, merchant = "قهوة")
        val imported = sig("87.50", Source.STATEMENT, merchant = "قهوة")

        val result = detector.reconcile(listOf(typed), listOf(imported))
        assertTrue(result.matches.isEmpty())
        assertEquals(listOf(0), result.newIncoming)
    }

    @Test
    fun `an empty history means everything incoming is new`() {
        val result = detector.reconcile(emptyList(), listOf(sig("35", Source.SMS)))
        assertEquals(listOf(0), result.newIncoming)
        assertTrue(result.matches.isEmpty())
    }

    @Test
    fun `nothing incoming produces nothing`() {
        val result = detector.reconcile(listOf(sig("35", Source.SMS)), emptyList())
        assertTrue(result.matches.isEmpty())
        assertTrue(result.newIncoming.isEmpty())
    }

    @Test
    fun `no stored record is ever consumed twice`() {
        val existing = listOf(sig("5000", Source.SMS, time = "05:02"))
        val incoming = List(4) { sig("5000", Source.STATEMENT, time = "00:00") }

        val result = detector.reconcile(existing, incoming)

        assertEquals(
            result.matches.map { it.existingIndex }.size,
            result.matches.map { it.existingIndex }.distinct().size,
        )
        assertEquals(incoming.size, result.matches.size + result.newIncoming.size)
    }
}
