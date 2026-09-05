package sa.masrouf.app.data

import kotlin.test.assertEquals
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
 * The pass that turns money which came back into money which came back.
 *
 * AlAhli writes a card refund as حوالة عكسية, and the classifier knew only the other
 * wording, so the word حوالة carried it into the outgoing-transfer rules: the row was
 * stored as money leaving, counted as spending, on top of the purchase it refunds.
 *
 * Amounts and merchants here are invented; the template is the thing under test.
 */
class RetypeReversalsTest {

    private val dao = FakeDao()
    private val repository = TransactionRepository(dao)
    private val at = Instant.parse("2026-09-04T09:00:00Z")

    private val reversalBody = """
        حوالة عكسية
        بطاقة ائتمانية **0000
        مبلغ 640.00 SAR
        لدى A SHOP
        في 20/09/24 18:38
    """.trimIndent()

    private fun stored(
        id: String,
        body: String,
        source: Source = Source.SMS,
    ) = Transaction(
        id = id,
        amount = Money.ofMajor("640.00"),
        // As the old classifier read it: money leaving.
        direction = Direction.DEBIT,
        type = TransactionType.TRANSFER_OUT,
        occurredAt = at,
        accountId = null,
        categoryId = null,
        merchantRaw = null,
        merchantKey = null,
        note = null,
        source = source,
        status = Status.CONFIRMED,
        fingerprint = Fingerprint.forMessage(
            source, at, Money.ofMajor("640.00"), Direction.DEBIT, null, id,
        ),
        rawText = body,
    )

    private suspend fun row(id: String) = dao.allWithBody().single { it.id == id }

    @Test
    fun `a reversed card purchase becomes money coming back`() = runTest {
        repository.recordCaptured(stored("reversal", reversalBody))

        val moved = repository.retypeReversals()

        assertEquals(1, moved)
        assertEquals(Direction.CREDIT.name, row("reversal").direction)
        assertEquals(TransactionType.REFUND.name, row("reversal").type)
    }

    @Test
    fun `a row the pass agrees with is left alone`() = runTest {
        repository.recordCaptured(
            stored("ordinary", "شراء\nمبلغ 640.00 SAR\nلدى A SHOP\nبطاقة *0000"),
        )

        assertEquals(0, repository.retypeReversals())
    }

    @Test
    fun `a record the user typed is never rewritten`() = runTest {
        // Rule 9: they saw the transaction and meant it. A pass correcting the app's
        // own reading has no business touching a number a person entered.
        repository.recordCaptured(stored("mine", reversalBody, source = Source.MANUAL))

        repository.retypeReversals()

        assertEquals(Direction.DEBIT.name, row("mine").direction)
    }
}
