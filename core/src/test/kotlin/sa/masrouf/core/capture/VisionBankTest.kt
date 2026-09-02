package sa.masrouf.core.capture

import org.junit.jupiter.api.Test
import sa.masrouf.core.fixtures.RealMessages
import sa.masrouf.core.model.TransactionType
import sa.masrouf.core.money.Money
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/** Vision Bank, added 2026-09-02. Card 2455. */
class VisionBankTest {

    private fun parsed(body: String) =
        (BankMessageParser(SaudiBanks.VISION_BANK).parse(RawMessage(body, Instant.EPOCH))
            as? ParseResult.Parsed)?.draft ?: error("not parsed")

    /** The inbox also holds a marketing sender called "Vision 2030". */
    @Test
    fun `the bank is claimed and the marketing sender of a similar name is not`() {
        val parser = BankMessageParser(SaudiBanks.VISION_BANK)

        assertEquals(true, parser.canParse(RawMessage("x", Instant.EPOCH, sender = "Vision Bank")))
        assertEquals(false, parser.canParse(RawMessage("x", Instant.EPOCH, sender = "Vision 2030")))
    }

    /** "Card Number: ****2455" is the card; "Account Number: ****6000" two lines down is not. */
    @Test
    fun `a pos purchase reads the card and never the account`() {
        val draft = parsed(RealMessages.VISION_POS_PURCHASE)

        assertEquals(TransactionType.PURCHASE, draft.type)
        assertEquals(Money.ofMajor("12").halalas, draft.amount.halalas)
        assertEquals("2455", draft.accountLast4)
        assertEquals("MGHASL ZKIEAH ALGHAMDI", draft.merchantRaw)
    }

    @Test
    fun `the arabic twin of the pos template reads the same`() {
        val draft = parsed(RealMessages.VISION_POS_PURCHASE_AR)

        assertEquals(TransactionType.PURCHASE, draft.type)
        assertEquals("2455", draft.accountLast4)
        assertEquals("MGHASL ZKIEAH ALGHAMDI", draft.merchantRaw)
    }

    /** "Local Credit Transfer" is money ARRIVING, and "Sender:" is who sent it. */
    @Test
    fun `a credit transfer is money arriving from the named sender`() {
        val draft = parsed(RealMessages.VISION_CREDIT_TRANSFER)

        assertEquals(TransactionType.TRANSFER_IN, draft.type)
        assertEquals(Money.ofMajor("160").halalas, draft.amount.halalas)
        assertEquals("SENDER NAME", draft.merchantRaw)
    }

    /**
     * "From: ***5001" is an account. With a plain `\s*` before the guard the engine
     * gave back the space, the guard passed on " ***5001", and the tile row showed
     * a party called 5001.
     */
    @Test
    fun `a transfer between his own accounts has no party and is not spending`() {
        val draft = parsed(RealMessages.VISION_OWN_ACCOUNTS)

        assertEquals(TransactionType.OWN_TRANSFER, draft.type)
        assertNull(draft.merchantRaw)
    }

    @Test
    fun `a pincode is refused and its body must not be kept`() {
        val message = RawMessage(RealMessages.VISION_PINCODE, Instant.EPOCH, sender = "Vision Bank")

        assertIs<MessageGate.Decision.Reject>(MessageGate.evaluate(message))
        assertEquals(true, MessageGate.mustNotPersistBody(message))
    }
}
