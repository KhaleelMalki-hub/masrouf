package sa.masrouf.app.data

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import sa.masrouf.core.dedup.Fingerprint
import sa.masrouf.core.model.Direction
import sa.masrouf.core.model.Source
import sa.masrouf.core.model.Status
import sa.masrouf.core.model.Transaction
import sa.masrouf.core.model.TransactionType
import sa.masrouf.core.money.Money
import java.time.Instant

/**
 * One purchase can reach this app as a bank SMS and as that bank's own push
 * notification, seconds apart. They are two different records - different source,
 * different text, different fingerprint - describing one movement of money, and no
 * database constraint can see it. Counting both silently doubles the month.
 *
 * The opposite error costs just as much: two genuinely separate payments that look
 * alike must stay two records, or money disappears without anything looking wrong.
 */
class CrossSourceDedupTest {

    private val dao = FakeDao()
    private val repository = TransactionRepository(dao)

    private val noon = Instant.parse("2026-08-28T09:00:00Z")

    private fun captured(
        source: Source,
        at: Instant = noon,
        riyals: String = "5000.00",
        merchant: String? = "barq",
    ) = Transaction(
        id = "id-${source.name}-${at.epochSecond}-$riyals",
        amount = Money.ofMajor(riyals),
        direction = Direction.DEBIT,
        type = TransactionType.PURCHASE,
        occurredAt = at,
        accountId = null,
        categoryId = null,
        merchantRaw = merchant,
        merchantKey = merchant,
        note = null,
        source = source,
        status = Status.PENDING,
        fingerprint = Fingerprint.forMessage(
            source, at, Money.ofMajor(riyals), Direction.DEBIT, null, merchant,
        ),
        rawText = null,
    )

    @Test
    fun `the same purchase seen as sms and as a notification is stored once`() = runTest {
        val last4 = "1887"

        assertTrue(repository.recordCaptured(captured(Source.SMS), last4))
        // The bank's own app posts its notification eight seconds later.
        assertFalse(
            repository.recordCaptured(captured(Source.NOTIFICATION, noon.plusSeconds(8)), last4),
            "one purchase was recorded twice",
        )

        assertEquals(1, dao.rows.size)
    }

    @Test
    fun `two real purchases 49 minutes apart both survive`() = runTest {
        // From real captured data: two 5,000 top-ups on one morning, identical in
        // amount, card and merchant. Merging them destroys 5,000 SAR and leaves a
        // record that looks entirely correct.
        val last4 = "1887"

        assertTrue(repository.recordCaptured(captured(Source.NOTIFICATION), last4))
        assertTrue(
            repository.recordCaptured(
                captured(Source.NOTIFICATION, noon.plusSeconds(49 * 60)),
                last4,
            ),
            "a second genuine purchase was swallowed as a duplicate",
        )

        assertEquals(2, dao.rows.size)
    }

    @Test
    fun `the same amount on two different cards stays two transactions`() = runTest {
        // This is what the stored card fragment buys. A missing last4 is treated as
        // compatible with every other, so without the column these two would merge.
        assertTrue(repository.recordCaptured(captured(Source.SMS), "1887"))
        assertTrue(
            repository.recordCaptured(captured(Source.NOTIFICATION, noon.plusSeconds(8)), "2383"),
            "two different cards were merged into one transaction",
        )

        assertEquals(2, dao.rows.size)
    }

    @Test
    fun `an identical redelivery of the same record is refused by the fingerprint`() = runTest {
        assertTrue(repository.recordCaptured(captured(Source.SMS), "1887"))
        assertFalse(repository.recordCaptured(captured(Source.SMS), "1887"))

        assertEquals(1, dao.rows.size)
    }
}
