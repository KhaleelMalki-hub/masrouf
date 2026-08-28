package sa.masrouf.core.capture

import kotlin.test.assertEquals
import kotlin.test.assertIs
import org.junit.jupiter.api.Test
import sa.masrouf.core.fixtures.RealMessages
import sa.masrouf.core.model.Direction
import sa.masrouf.core.model.TransactionType
import sa.masrouf.core.money.Money
import java.time.Instant

/**
 * AlAhli's older template family, from a real 3,361-message corpus where 87% of
 * messages matched no rule at all. The parser had only ever been shown the newer
 * SNB wording.
 */
class AlAhliTemplatesTest {

    private val pipeline = CapturePipeline()

    private fun outcome(body: String) = pipeline.process(
        RawMessage(body, Instant.parse("2026-08-28T09:00:00Z"), sender = "AlahliSMS")
    )

    private fun captured(body: String) =
        assertIs<CapturePipeline.Outcome.Captured>(outcome(body)).draft

    @Test
    fun `a card purchase is a purchase, despite the word withdrawal`() {
        // "سحب" opens the message, but a card and a merchant are named, which is
        // what separates this from money simply leaving the account.
        val draft = captured(RealMessages.ALAHLI_CARD_PURCHASE)

        assertEquals(TransactionType.PURCHASE, draft.type)
        assertEquals(Direction.DEBIT, draft.direction)
        assertEquals(Money.ofMajor("299.25"), draft.amount)
        assertEquals(true, draft.type.countsAsSpending)
    }

    @Test
    fun `settling the credit card is not spending`() {
        // The purchases that built the balance were counted when they happened.
        val draft = captured(RealMessages.ALAHLI_CARD_SETTLEMENT)

        assertEquals(false, draft.type.countsAsSpending)
        assertEquals(Money.ofMajor("4900.51"), draft.amount)
    }

    @Test
    fun `money going onto a card is a credit`() {
        val draft = captured(RealMessages.ALAHLI_CARD_TOPUP)

        assertEquals(Direction.CREDIT, draft.direction)
        assertEquals(false, draft.type.countsAsSpending)
    }

    @Test
    fun `an account withdrawal with no merchant is read but not counted as spending`() {
        // Nothing in the message says what it was for, so it errs toward a total
        // that is too low rather than one that invents a purchase.
        val draft = captured(RealMessages.ALAHLI_ACCOUNT_WITHDRAWAL)

        assertEquals(Money.ofMajor("1500.00"), draft.amount)
        assertEquals(false, draft.type.countsAsSpending)
    }

    @Test
    fun `a foreign-currency purchase is refused, not read as the SAR balance`() {
        // The bug this guards: the extractor returned 6127.16, the remaining
        // balance, as the amount spent. A 1058.66 AED purchase became a fabricated
        // four-figure riyal transaction, and 159 messages in one inbox had this
        // shape.
        val result = outcome(RealMessages.ALAHLI_FOREIGN_PURCHASE)

        val refused = assertIs<CapturePipeline.Outcome.NotUnderstood>(result)
        assertEquals("amount in a foreign currency", refused.reason)
    }

    @Test
    fun `the foreign-currency guard does not refuse an ordinary riyal purchase`() {
        // The mirror-image failure: a guard that refuses everything is not a guard.
        assertIs<CapturePipeline.Outcome.Captured>(outcome(RealMessages.ALAHLI_CARD_PURCHASE))
        assertIs<CapturePipeline.Outcome.Captured>(outcome(RealMessages.SNB_ONLINE_PURCHASE))
    }
}
