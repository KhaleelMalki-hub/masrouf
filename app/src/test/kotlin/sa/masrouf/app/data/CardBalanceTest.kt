package sa.masrouf.app.data

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import sa.masrouf.core.capture.BalanceReader
import sa.masrouf.core.dedup.Fingerprint
import sa.masrouf.core.model.Direction
import sa.masrouf.core.model.Source
import sa.masrouf.core.model.Status
import sa.masrouf.core.model.Transaction
import sa.masrouf.core.model.TransactionType
import sa.masrouf.core.money.Money
import java.time.Instant

/**
 * What a card is shown as holding.
 *
 * Read off the messages, so it is as current as the last message that carried a
 * figure - and only that one. The last message of all is often an OTP or a
 * declined attempt, which say nothing, and must not blank the balance.
 */
class CardBalanceTest {

    private val dao = FakeDao()
    private val repository = TransactionRepository(dao)
    private val at = Instant.parse("2026-08-20T09:00:00Z")

    private fun record(id: String, minute: Long, card: String) = Transaction(
        id = id,
        amount = Money.ofMajor("50.00"),
        direction = Direction.DEBIT,
        type = TransactionType.PURCHASE,
        occurredAt = at.plusSeconds(minute * 60),
        accountId = null,
        categoryId = null,
        merchantRaw = "SHOP $minute",
        merchantKey = "SHOP $minute",
        note = null,
        source = Source.SMS,
        status = Status.CONFIRMED,
        fingerprint = Fingerprint.forMessage(
            Source.SMS, at.plusSeconds(minute * 60), Money.ofMajor("50.00"), Direction.DEBIT, card, id,
        ),
        rawText = "شراء\nرصيد:100.00 SR",
    )

    private fun reading(amount: String, kind: BalanceReader.Kind = BalanceReader.Kind.ACCOUNT) =
        BalanceReader.Reading(Money.ofMajor(amount), kind)

    @Test
    fun `the balance is the latest message that carried one, not the latest message`() = runTest {
        repository.recordCaptured(record("a", 0, "2383"), "2383", reading("1000.00"))
        repository.recordCaptured(record("b", 30, "2383"), "2383", reading("900.00"))
        // A later message with no figure - the shape of an OTP or a decline.
        repository.recordCaptured(record("c", 60, "2383"), "2383", balance = null)

        val card = repository.observeCardBalances().first().single()

        assertEquals(90_000L, card.halalas)
        assertEquals("ACCOUNT", card.kind)
    }

    @Test
    fun `a credit limit keeps its kind all the way to the screen`() = runTest {
        repository.recordCaptured(
            record("a", 0, "0926"), "0926", reading("2902.84", BalanceReader.Kind.CREDIT_LIMIT),
        )

        assertEquals("CREDIT_LIMIT", repository.observeCardBalances().first().single().kind)
    }

    /** barq, Al Rajhi and D360 never put a figure in a message. The card still exists. */
    @Test
    fun `a card whose bank sends no figure is still a card`() = runTest {
        repository.recordCaptured(record("a", 0, "5763"), "5763", balance = null)

        val card = repository.observeCardBalances().first().single()

        assertEquals("5763", card.last4)
        assertNull(card.halalas)
    }

    @Test
    fun `the backfill reads stored bodies once and marks the ones that say nothing`() = runTest {
        repository.recordCaptured(record("a", 0, "2383"), "2383", balance = null)
        // A row from before the column existed: body kept, nothing read yet.
        dao.replaceAll(listOf(dao.rows.single().copy(balanceHalalas = null, balanceKind = null)))

        assertEquals(1, repository.backfillBalances())
        assertEquals(10_000L, dao.rows.single().balanceHalalas)
        // Second run touches nothing.
        assertEquals(0, repository.backfillBalances())
    }
}
