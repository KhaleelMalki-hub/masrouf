package sa.masrouf.app.data

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
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
 * A captured record is a parser's reading of a bank message. It stays out of the
 * user's totals until they say it is right, and confirming is the only thing that
 * lets it in. These tests pin both halves of that: the withholding, and the letting
 * in - because a fix for one that breaks the other is indistinguishable from
 * dropping captured spending entirely.
 */
class PendingConfirmationTest {

    private val dao = FakeDao()
    private val repository = TransactionRepository(dao)
    private val at = Instant.parse("2026-08-28T09:00:00Z")

    private fun captured(riyals: String = "931.64", at: Instant = this.at) = Transaction(
        id = "cap-${at.epochSecond}-$riyals",
        amount = Money.ofMajor(riyals),
        direction = Direction.DEBIT,
        type = TransactionType.PURCHASE,
        occurredAt = at,
        accountId = null,
        categoryId = null,
        merchantRaw = "IHERB ARA",
        merchantKey = "IHERB ARA",
        note = null,
        source = Source.NOTIFICATION,
        status = Status.PENDING,
        fingerprint = Fingerprint.forMessage(
            Source.NOTIFICATION, at, Money.ofMajor(riyals), Direction.DEBIT, "2383", "IHERB ARA",
        ),
        rawText = "raw",
    )

    private suspend fun monthTotal() =
        repository.observeMonth(java.time.LocalDate.of(2026, 8, 15)).first().spendingTotal()

    @Test
    fun `a captured record is listed as pending and is not in the total`() = runTest {
        repository.recordCaptured(captured(), "2383")

        assertEquals(1, repository.observePending().first().size)
        assertEquals(Money.ZERO, monthTotal())
    }

    @Test
    fun `confirming moves it into the total and off the pending list`() = runTest {
        val record = captured()
        repository.recordCaptured(record, "2383")

        assertTrue(repository.confirm(record.id))

        assertEquals(emptyList(), repository.observePending().first())
        assertEquals(Money.ofMajor("931.64"), monthTotal())
    }

    @Test
    fun `dismissing removes it and it never reaches the total`() = runTest {
        val record = captured()
        repository.recordCaptured(record, "2383")

        assertTrue(repository.dismiss(record.id))

        assertEquals(emptyList(), repository.observePending().first())
        assertEquals(Money.ZERO, monthTotal())
        assertEquals(0, dao.rows.size)
    }

    @Test
    fun `confirming twice does not happen silently`() = runTest {
        // A stale id from a screen that has not caught up must not report success,
        // or the second tap looks like it did something.
        val record = captured()
        repository.recordCaptured(record, "2383")

        assertTrue(repository.confirm(record.id))
        assertFalse(repository.confirm(record.id))

        assertEquals(Money.ofMajor("931.64"), monthTotal())
    }

    @Test
    fun `a confirmed record cannot be dismissed by a stale screen`() = runTest {
        val record = captured()
        repository.recordCaptured(record, "2383")
        repository.confirm(record.id)

        assertFalse(repository.dismiss(record.id))

        assertEquals(1, dao.rows.size)
        assertEquals(Money.ofMajor("931.64"), monthTotal())
    }

    @Test
    fun `deleting removes a confirmed record and takes it out of the total`() = runTest {
        // Unlike dismiss, this is not guarded on status: it is the user removing
        // something they can see, which includes records they already confirmed.
        val record = captured()
        repository.recordCaptured(record, "2383")
        repository.confirm(record.id)
        assertEquals(Money.ofMajor("931.64"), monthTotal())

        assertTrue(repository.delete(record.id))

        assertEquals(Money.ZERO, monthTotal())
        assertEquals(0, dao.rows.size)
    }

    @Test
    fun `acting on an id that was never stored reports failure`() = runTest {
        assertFalse(repository.confirm("no-such-id"))
        assertFalse(repository.dismiss("no-such-id"))
        assertFalse(repository.delete("no-such-id"))
    }
}
