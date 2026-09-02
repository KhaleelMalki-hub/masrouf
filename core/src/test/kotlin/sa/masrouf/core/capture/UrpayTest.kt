package sa.masrouf.core.capture

import org.junit.jupiter.api.Test
import sa.masrouf.core.fixtures.RealMessages
import sa.masrouf.core.model.Direction
import sa.masrouf.core.model.TransactionType
import sa.masrouf.core.money.Money
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * urpay, added 2026-09-02. The owner confirmed the wallet as his; 179 messages
 * from it sat unread, and 26 bank-side top-ups of it were counted as spending.
 */
class UrpayTest {

    private fun parsed(body: String) =
        (BankMessageParser(SaudiBanks.URPAY).parse(RawMessage(body, Instant.EPOCH))
            as? ParseResult.Parsed)?.draft ?: error("not parsed")

    private fun gate(body: String) = MessageGate.evaluate(RawMessage(body, Instant.EPOCH, sender = "urpay"))

    @Test
    fun `the sender and the installed package are both claimed`() {
        val parser = BankMessageParser(SaudiBanks.URPAY)

        assertEquals(true, parser.canParse(RawMessage("x", Instant.EPOCH, sender = "urpay")))
        assertEquals(true, parser.canParse(RawMessage("x", Instant.EPOCH, packageName = "com.urpay.consumer")))
    }

    @Test
    fun `a purchase in the newer template reads card, amount and merchant`() {
        val draft = parsed(RealMessages.URPAY_ONLINE_PURCHASE)

        assertEquals(TransactionType.PURCHASE, draft.type)
        assertEquals(Money.ofMajor("61").halalas, draft.amount.halalas)
        assertEquals("4322", draft.accountLast4)
        assertEquals("NETFLIX...", draft.merchantRaw)
    }

    /** "بطاقة:  urpay بطاقة ***4322" - the wallet's name inside the card field. */
    @Test
    fun `the older template reads the card through the wallet's own name`() {
        val draft = parsed(RealMessages.URPAY_ONLINE_PURCHASE_OLD)

        assertEquals("4322", draft.accountLast4)
        assertEquals("ALNAHDI MEDICAL CO", draft.merchantRaw)
        assertEquals(Money.ofMajor("76.61").halalas, draft.amount.halalas)
    }

    /** "بطاقة: 4322***;urpay بطاقة" - digits first, mask after. */
    @Test
    fun `a card written digits-first is still the card`() {
        val draft = parsed(RealMessages.URPAY_POS_PURCHASE)

        assertEquals("4322", draft.accountLast4)
        assertEquals("SASCO ELZAIDI STATION", draft.merchantRaw)
    }

    @Test
    fun `phone credit is a bill paid to the operator`() {
        val draft = parsed(RealMessages.URPAY_PHONE_CREDIT)

        assertEquals(TransactionType.BILL_PAYMENT, draft.type)
        assertEquals(Money.ofMajor("115").halalas, draft.amount.halalas)
        assertEquals("STC", draft.merchantRaw)
    }

    @Test
    fun `reward points paid out are money back, not a top-up`() {
        val draft = parsed(RealMessages.URPAY_REWARD_POINTS)

        assertEquals(TransactionType.REFUND, draft.type)
        assertEquals(Direction.CREDIT, draft.direction)
        assertEquals(Money.ofMajor("342.5").halalas, draft.amount.halalas)
    }

    @Test
    fun `cashback is a refund on the card`() {
        val draft = parsed(RealMessages.URPAY_CASHBACK)

        assertEquals(TransactionType.REFUND, draft.type)
        assertEquals("4322", draft.accountLast4)
        assertNull(draft.merchantRaw)
    }

    /** "وصلتك حوالة": money arriving, in a template with no وارد in it. */
    @Test
    fun `a transfer from another wallet is money arriving`() {
        val draft = parsed(RealMessages.URPAY_WALLET_TRANSFER_IN)

        assertEquals(TransactionType.TRANSFER_IN, draft.type)
        assertEquals(Money.ofMajor("85").halalas, draft.amount.halalas)
    }

    @Test
    fun `an advert quoting a cashback figure is refused`() {
        assertIs<MessageGate.Decision.Reject>(gate(RealMessages.URPAY_FEE_FREE_ADVERT))
    }

    /** The other leg: the funding bank sees a purchase at a shop called URPAY. */
    @Test
    fun `a bank-side top-up of the wallet is the owner's own money`() {
        val draft = (BankMessageParser(SaudiBanks.SNB).parse(RawMessage(RealMessages.SNB_URPAY_TOPUP, Instant.EPOCH))
            as ParseResult.Parsed).draft

        assertEquals(TransactionType.OWN_TRANSFER, draft.type)
        assertEquals("URPAY", draft.merchantRaw)
    }
}
