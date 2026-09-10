package sa.masrouf.app.data

import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import sa.masrouf.core.capture.AccountOwner
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
class MisreadDirectionTest {

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
        type: TransactionType = TransactionType.TRANSFER_OUT,
    ) = Transaction(
        id = id,
        amount = Money.ofMajor("640.00"),
        // As the old classifier read it: money leaving.
        direction = Direction.DEBIT,
        type = type,
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

    private fun row(id: String) = dao.rows.single { it.id == id }

    @Test
    fun `a reversed card purchase becomes money coming back`() = runTest {
        repository.recordCaptured(stored("reversal", reversalBody))

        val moved = repository.retypeMisreadDirections()

        assertEquals(1, moved)
        assertEquals(Direction.CREDIT.name, row("reversal").direction)
        assertEquals(TransactionType.REFUND.name, row("reversal").type)
    }

    @Test
    fun `a row the pass agrees with is left alone`() = runTest {
        repository.recordCaptured(
            stored("ordinary", "شراء\nمبلغ 640.00 SAR\nلدى A SHOP\nبطاقة *0000"),
        )

        assertEquals(0, repository.retypeMisreadDirections())
    }

    @Test
    fun `a record the user typed is never rewritten`() = runTest {
        // Rule 9: they saw the transaction and meant it. A pass correcting the app's
        // own reading has no business touching a number a person entered.
        repository.recordCaptured(stored("mine", reversalBody, source = Source.MANUAL))

        repository.retypeMisreadDirections()

        assertEquals(Direction.DEBIT.name, row("mine").direction)
    }
    @Test
    fun `an incoming transfer between his own accounts arrives rather than leaves`() = runTest {
        // "واردة" is the whole word: money reaching the account that got the message.
        // Both own-transfer rules called it money leaving, so the same wording was
        // stored one way or the other depending on which pass had read it.
        repository.recordCaptured(
            stored("own", "حوالة واردة بين حساباتك\nمبلغ 640 ريال\nحساب0000*\nفي 06/09/26 08:00"),
        )

        assertEquals(1, repository.retypeMisreadDirections())

        assertEquals(Direction.CREDIT.name, row("own").direction)
        assertEquals(TransactionType.OWN_TRANSFER.name, row("own").type)
    }

    /**
     * The prefilter is a second truth, and it can silently switch the pass off.
     *
     * `retypeMisreadDirections` skips any body without one of a handful of words,
     * for speed. A classifier fix whose family is not in that list produces a pass
     * that runs, reports success, and rewrites nothing - the shape this repo has
     * been bitten by twice. So the wage transfer goes through the repository, not
     * through the classifier, and this test fails if the word is ever dropped.
     */
    @Test
    fun `a wage sent abroad reaches the pass and stops being his own money`() = runTest {
        AccountOwner.configure("OWNER|NAME")
        repository.recordCaptured(
            stored(
                "wage",
                type = TransactionType.OWN_TRANSFER,
                body = """
                حوالة دولية صادرة
                المبلغ: 640 ر.س
                الرسوم: 0 ر.س
                من: OWNER NAME
                الى: RECIPIENT NAME
                شركة الحوالات: WesternUnion
                بتاريخ: 7/23/2026 ,8:19:34 PM
                """.trimIndent(),
            ),
        )

        assertEquals(1, repository.retypeMisreadDirections())

        assertEquals(TransactionType.TRANSFER_OUT.name, row("wage").type)
        assertEquals(Direction.DEBIT.name, row("wage").direction)
    }
}
