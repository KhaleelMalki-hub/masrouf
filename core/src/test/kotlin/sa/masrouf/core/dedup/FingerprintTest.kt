package sa.masrouf.core.dedup

import org.junit.jupiter.api.Test
import sa.masrouf.core.model.Direction
import sa.masrouf.core.model.Source
import sa.masrouf.core.money.Money
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class FingerprintTest {

    private fun message(time: String, amount: String = "5000", merchant: String? = "barq") =
        Fingerprint.forMessage(
            source = Source.SMS,
            occurredAt = Instant.parse("2026-08-27T${time}:00Z"),
            amount = Money.ofMajor(amount),
            direction = Direction.DEBIT,
            last4 = "1887",
            merchantRaw = merchant,
        )

    @Test
    fun `the same message fingerprints the same every time`() {
        assertEquals(message("05:02"), message("05:02"))
    }

    /**
     * The two real 5,000 SAR top-ups, 49 minutes apart on one morning. A
     * day-granularity fingerprint collapses them and swallows 5,000 SAR.
     */
    @Test
    fun `two identical transactions minutes apart fingerprint differently`() {
        assertNotEquals(message("05:02"), message("05:51"))
    }

    @Test
    fun `a different amount changes the fingerprint`() {
        assertNotEquals(message("05:02", amount = "5000"), message("05:02", amount = "5000.01"))
    }

    /**
     * With a separator that can occur inside a field, ("TAMIMI", "MARKETS") and
     * ("TAMIMI MARKETS", "") build the same canonical string and collide. Merchant
     * names are full of spaces, so this is not hypothetical.
     */
    @Test
    fun `field boundaries cannot be shifted to force a collision`() {
        val split = Fingerprint.forMessage(
            Source.SMS, Instant.EPOCH, Money.ofMajor("10"), Direction.DEBIT,
            last4 = "1887", merchantRaw = "TAMIMI MARKETS",
        )
        val shifted = Fingerprint.forMessage(
            Source.SMS, Instant.EPOCH, Money.ofMajor("10"), Direction.DEBIT,
            last4 = "1887 TAMIMI", merchantRaw = "MARKETS",
        )
        assertNotEquals(split, shifted)
    }

    @Test
    fun `the merchant reference tail does not change the fingerprint`() {
        assertEquals(
            message("05:02", merchant = "IHERB ARA"),
            message("05:02", merchant = "IHERB ARA 884213"),
        )
    }

    // ---- Statement rows ----------------------------------------------------

    private fun row(statementId: String, index: Int, amount: String = "5000") =
        Fingerprint.forStatementRow(
            statementId = statementId,
            rowIndex = index,
            date = LocalDate.of(2026, 8, 27),
            amount = Money.ofMajor(amount),
            direction = Direction.DEBIT,
            last4 = "1887",
            merchantRaw = "barq",
        )

    @Test
    fun `re-importing the same file yields the same fingerprints`() {
        assertEquals(row("sha-of-august", 0), row("sha-of-august", 0))
    }

    /** Two identical rows inside one statement are two transactions. */
    @Test
    fun `identical rows at different positions fingerprint differently`() {
        assertNotEquals(row("sha-of-august", 3), row("sha-of-august", 7))
    }

    /**
     * An overlapping file gives the same transaction a different fingerprint, by
     * design. Catching that is DuplicateDetector's job, where dates and counts can
     * be weighed - a hash cannot settle it.
     */
    @Test
    fun `the same row from an overlapping file fingerprints differently`() {
        assertNotEquals(row("sha-of-august", 0), row("sha-of-july-august", 0))
    }

    @Test
    fun `a statement row never collides with a message`() {
        assertNotEquals(row("sha-of-august", 0), message("05:02"))
    }

    @Test
    fun `manual entries are unique per record`() {
        assertNotEquals(Fingerprint.forManual("id-a"), Fingerprint.forManual("id-b"))
        assertEquals(Fingerprint.forManual("id-a"), Fingerprint.forManual("id-a"))
    }

    @Test
    fun `fingerprints are lowercase hex of a sha-256`() {
        val value = message("05:02")
        assertEquals(64, value.length)
        assertEquals(true, value.all { it in "0123456789abcdef" })
    }
}
