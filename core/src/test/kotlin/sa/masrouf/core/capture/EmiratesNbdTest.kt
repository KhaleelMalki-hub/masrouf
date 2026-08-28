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
 * Emirates NBD had no profile at all, and its messages were being parsed anyway.
 *
 * `canParse` matches sender ids as substrings of the folded origin, and "SNB" is a
 * substring of "EMIRATESNBD" - so all 499 of this bank's messages were claimed by
 * the SNB profile. It looked like success: 248 of them captured. They were filed
 * under the wrong bank, and the card fragment was dropped because Emirates NBD
 * masks it "XX9994" where SNB uses an asterisk.
 *
 * Two things are pinned here: that this bank is matched by its own profile, and
 * that the profile actually reads its templates.
 */
class EmiratesNbdTest {

    private val registry = SaudiBanks.registry()
    private val pipeline = CapturePipeline()

    private fun message(body: String) =
        RawMessage(body, Instant.parse("2026-08-28T09:00:00Z"), sender = "EmiratesNBD")

    private fun parserFor(body: String): String? = SaudiBanks.ALL
        .map(::BankMessageParser)
        .firstOrNull { it.canParse(message(body)) }
        ?.id

    private fun captured(body: String) =
        assertIs<CapturePipeline.Outcome.Captured>(pipeline.process(message(body))).draft

    @Test
    fun `emirates nbd is claimed by its own profile, not by SNB`() {
        // The whole point. Before this profile existed the answer was "snb".
        assertEquals("enbd", parserFor(RealMessages.ENBD_POS_PURCHASE))
        assertEquals("enbd", parserFor(RealMessages.ENBD_INCOMING_TRANSFER))
    }

    @Test
    fun `the SNB profile no longer claims a bank whose name merely contains its own`() {
        val snbClaims = BankMessageParser(SaudiBanks.SNB).canParse(message(RealMessages.ENBD_POS_PURCHASE))

        assertEquals(false, snbClaims)
    }

    @Test
    fun `SNB's own senders still match`() {
        listOf("SNB-AlAhli", "AlahliSMS", "AlAhliSMS").forEach { sender ->
            val claimed = BankMessageParser(SaudiBanks.SNB).canParse(
                RawMessage(RealMessages.SNB_ONLINE_PURCHASE, Instant.now(), sender = sender)
            )
            assertEquals(true, claimed, "SNB stopped recognising $sender")
        }
    }

    @Test
    fun `a point of sale purchase is read with its card and merchant`() {
        val draft = captured(RealMessages.ENBD_POS_PURCHASE)

        assertEquals(TransactionType.PURCHASE, draft.type)
        assertEquals(Money.ofMajor("99.00"), draft.amount)
        // The XX mask, which the other banks' asterisk patterns cannot reach.
        assertEquals("9994", draft.accountLast4)
    }

    @Test
    fun `a thousands separator in the amount is read, not truncated`() {
        val draft = captured(RealMessages.ENBD_ONLINE_PURCHASE)

        assertEquals(Money.ofMajor("15000.00"), draft.amount)
    }

    @Test
    fun `an incoming transfer is credit and is not spending`() {
        val draft = captured(RealMessages.ENBD_INCOMING_TRANSFER)

        assertEquals(Direction.CREDIT, draft.direction)
        assertEquals(false, draft.type.countsAsSpending)
        assertEquals(Money.ofMajor("585.00"), draft.amount)
    }

    @Test
    fun `paying off the card is not spending`() {
        val draft = captured(RealMessages.ENBD_CARD_PAYMENT)

        assertEquals(false, draft.type.countsAsSpending)
        assertEquals(Money.ofMajor("599.00"), draft.amount)
    }
}
