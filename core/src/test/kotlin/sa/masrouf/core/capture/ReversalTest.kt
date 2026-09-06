package sa.masrouf.core.capture

import kotlin.test.assertEquals
import kotlin.test.assertIs
import org.junit.jupiter.api.Test
import sa.masrouf.core.model.Direction
import sa.masrouf.core.model.TransactionType
import sa.masrouf.core.money.Money
import java.time.Instant

/**
 * A reversal is the bank undoing an entry, and which way the money goes depends
 * entirely on which entry it undid.
 *
 * The word is عكسية either way. Undo a card purchase and the money comes back; undo
 * an incoming transfer and the money leaves again. The classifier knew only the
 * card wording that says عملية عكسية, so every message that said حوالة عكسية fell
 * through to the outgoing-transfer rules and was stored as money LEAVING - a refund
 * counted as spending, on top of the purchase it was refunding.
 *
 * The bank's own arithmetic is what settles the direction, and it is in the message
 * pair: the card's remaining limit after a purchase, then that limit plus the same
 * amount after the reversal. The limit is restored, so the money came back.
 *
 * Amounts and names here are invented. The template is what is being tested, and a
 * real person's figures do not belong in tracked source.
 */
class ReversalTest {

    private val pipeline = CapturePipeline()

    private fun captured(body: String) = assertIs<CapturePipeline.Outcome.Captured>(
        pipeline.process(RawMessage(body, Instant.parse("2026-09-04T09:00:00Z"), sender = "SNB-AlAhli"))
    ).draft

    @Test
    fun `a reversed card purchase is money coming back`() {
        val draft = captured(
            """
            حوالة عكسية
            بطاقة ائتمانية **0000
            مبلغ 640.00 SAR
            لدى A SHOP
            في 20/09/24 18:38
            الرصيد المتبقي 1234.00 SAR
            """.trimIndent()
        )

        assertEquals(TransactionType.REFUND, draft.type)
        assertEquals(Direction.CREDIT, draft.direction)
        assertEquals(Money.ofMajor("640.00"), draft.amount)
        assertEquals(false, draft.type.countsAsSpending)
    }

    @Test
    fun `a reversed incoming transfer is money going back out`() {
        val draft = captured(
            """
            حوالة عكسية واردة داخلية
            مبلغ 640SAR
            حساب0000*
            04/09/26 17:24
            """.trimIndent()
        )

        assertEquals(Direction.DEBIT, draft.direction)
        assertEquals(TransactionType.TRANSFER_OUT, draft.type)
    }

    @Test
    fun `an ordinary incoming transfer is untouched`() {
        val draft = captured(
            """
            حوالة واردة داخلية ب640 SAR
            من0000* A PERSON
            04/09/26 17:24
            """.trimIndent()
        )

        assertEquals(Direction.CREDIT, draft.direction)
        assertEquals(TransactionType.TRANSFER_IN, draft.type)
    }
    /**
     * A named sender means the money is arriving.
     *
     * "تحويل من A PERSON / مبلغ N / حساب 0000*" was read as an ordinary outgoing
     * transfer - 567 records and 846,912 riyals counted as spending across twelve
     * years - because the classifier saw a transfer and no marker saying which way.
     *
     * The bank's own running balance settles it: of the rows in this family that
     * carry one, 332 show the balance rising by exactly the amount and not one shows
     * it falling.
     */
    @Test
    fun `a transfer from a named sender is money arriving`() {
        val draft = captured(
            """
            تحويل من A PERSON
            مبلغ 640 SAR
            حساب 0000*
            في 06/09/2026 08:00
            """.trimIndent()
        )

        assertEquals(Direction.CREDIT, draft.direction)
        assertEquals(TransactionType.TRANSFER_IN, draft.type)
        assertEquals(false, draft.type.countsAsSpending)
    }

    /** And an outgoing one still says so. */
    @Test
    fun `a transfer addressed to someone is money leaving`() {
        val draft = captured(
            """
            حوالة صادرة داخلية
            مبلغ:640 SAR
            إلى:A PERSON
            إلى:0000*
            في:06/09/2026 08:00
            """.trimIndent()
        )

        assertEquals(Direction.DEBIT, draft.direction)
        assertEquals(true, draft.type.countsAsSpending)
    }

}
