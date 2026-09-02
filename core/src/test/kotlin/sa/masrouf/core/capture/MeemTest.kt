package sa.masrouf.core.capture

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
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
 * meem (Gulf International Bank), added 2026-09-02: three sender ids over nine
 * years, and the oldest templates written as prose.
 */
class MeemTest {

    @BeforeEach
    fun configureOwner() = AccountOwner.configure("OWNER|NAME")

    @AfterEach
    fun resetOwner() = AccountOwner.configure("")

    private fun parsed(body: String, profile: BankProfile = SaudiBanks.MEEM) =
        (BankMessageParser(profile).parse(RawMessage(body, Instant.EPOCH))
            as? ParseResult.Parsed)?.draft ?: error("not parsed")

    private fun gate(body: String) = MessageGate.evaluate(RawMessage(body, Instant.EPOCH, sender = "meemKSA"))

    @Test
    fun `every sender id the bank has used is claimed, and so is its app`() {
        val parser = BankMessageParser(SaudiBanks.MEEM)

        for (sender in listOf("MEEMSMS", "meemKSA", "meem", "meemSecure")) {
            assertEquals(true, parser.canParse(RawMessage("x", Instant.EPOCH, sender = sender)), sender)
        }
        assertEquals(true, parser.canParse(RawMessage("x", Instant.EPOCH, packageName = "com.veripark.GIB")))
    }

    @Test
    fun `a labelled purchase reads shop, card and amount`() {
        val draft = parsed(RealMessages.MEEM_POS_PURCHASE)

        assertEquals(TransactionType.PURCHASE, draft.type)
        assertEquals(Money.ofMajor("38.50").halalas, draft.amount.halalas)
        assertEquals("5654", draft.accountLast4)
        assertEquals("Express Food Company WM 9, MAKKAH, SA", draft.merchantRaw)
    }

    /** The shop sits inline between the amount and "على بطاقتك"; the card is the last four after the X-run. */
    @Test
    fun `a prose purchase reads the shop inline and the card past the mask`() {
        val draft = parsed(RealMessages.MEEM_PROSE_PURCHASE)

        assertEquals(TransactionType.PURCHASE, draft.type)
        assertEquals(Money.ofMajor("400").halalas, draft.amount.halalas)
        assertEquals("Nesma", draft.merchantRaw)
        assertEquals("0891", draft.accountLast4)
    }

    /** "دفع عبر نقاط البيع" - no word for شراء anywhere in it. */
    @Test
    fun `a point-of-sale payment is a purchase and the shop ends at the date`() {
        val draft = parsed(RealMessages.MEEM_POS_PAYMENT)

        assertEquals(TransactionType.PURCHASE, draft.type)
        assertEquals(Money.ofMajor("9").halalas, draft.amount.halalas)
        assertEquals("DUNKIN DONUTS 20059, MAKKAH, SA", draft.merchantRaw)
    }

    /** "تم إستلام حوالة داخلية" says nothing about وارد; the bare حوال rule read it as money leaving. */
    @Test
    fun `a received transfer is money arriving`() {
        val draft = parsed(RealMessages.MEEM_INCOMING_INTERNAL)

        assertEquals(TransactionType.TRANSFER_IN, draft.type)
        assertEquals(Direction.CREDIT, draft.direction)
        assertEquals(Money.ofMajor("10000").halalas, draft.amount.halalas)
    }

    @Test
    fun `an incoming local transfer keeps the bank it came through as its party`() {
        val draft = parsed(RealMessages.MEEM_INCOMING_LOCAL)

        assertEquals(TransactionType.TRANSFER_IN, draft.type)
        assertEquals("NATIONAL COMMERCIAL BANK, THE", draft.merchantRaw)
    }

    /** "Credit transfer: Local" would otherwise be claimed by LOCAL+TRANSFER, the outgoing kind. */
    @Test
    fun `an english credit transfer is money arriving`() {
        assertEquals(TransactionType.TRANSFER_IN, parsed(RealMessages.MEEM_CREDIT_TRANSFER_EN).type)
    }

    @Test
    fun `a transfer between his own accounts is neither income nor spending`() {
        assertEquals(TransactionType.OWN_TRANSFER, parsed(RealMessages.MEEM_OWN_ACCOUNTS_IN).type)
    }

    /** No سداد in it, but it is the card being paid. */
    @Test
    fun `the card thanking him for a payment is his own money`() {
        val draft = parsed(RealMessages.MEEM_CARD_PAYMENT_RECEIVED)

        assertEquals(TransactionType.OWN_TRANSFER, draft.type)
        assertEquals("0883", draft.accountLast4)
    }

    /** "4399XXXXXXXX0891": the last four, never the BIN in front. */
    @Test
    fun `a settlement reads the card from behind the mask`() {
        val draft = parsed(RealMessages.MEEM_CARD_SETTLEMENT)

        assertEquals(TransactionType.OWN_TRANSFER, draft.type)
        assertEquals("0891", draft.accountLast4)
        assertEquals(Money.ofMajor("287.5").halalas, draft.amount.halalas)
    }

    @Test
    fun `cash put in at a machine named in english is a deposit`() {
        val draft = parsed(RealMessages.MEEM_ATM_DEPOSIT)

        assertEquals(TransactionType.ATM_DEPOSIT, draft.type)
        assertEquals(Money.ofMajor("500").halalas, draft.amount.halalas)
    }

    @Test
    fun `an outgoing transfer to the owner is his own money, and names him`() {
        val draft = parsed(RealMessages.MEEM_OUTGOING_TO_SELF)

        assertEquals(TransactionType.OWN_TRANSFER, draft.type)
        assertEquals("OWNER NAME", draft.merchantRaw)
        assertEquals(Money.ofMajor("30005.75").halalas, draft.amount.halalas)
    }

    @Test
    fun `notices with figures in them are refused`() {
        assertIs<MessageGate.Decision.Reject>(gate(RealMessages.MEEM_FEE_NOTICE))
        assertIs<MessageGate.Decision.Reject>(gate(RealMessages.MEEM_OFFER))
        assertIs<MessageGate.Decision.Reject>(gate(RealMessages.MEEM_PLACEHOLDER_TEMPLATE))
    }

    @Test
    fun `a failed transfer is refused as declined`() {
        val decision = assertIs<MessageGate.Decision.Reject>(gate(RealMessages.MEEM_FAILED_TRANSFER))

        assertEquals(MessageGate.Rejection.DECLINED, decision.reason)
    }

    @Test
    fun `a login code is refused and its body must not be kept`() {
        val message = RawMessage(RealMessages.MEEM_LOGIN_CODE, Instant.EPOCH, sender = "MEEMSMS")

        assertIs<MessageGate.Decision.Reject>(MessageGate.evaluate(message))
        assertEquals(true, MessageGate.mustNotPersistBody(message))
    }

    // ---- What the new rules must NOT claim ---------------------------------

    /** "تم استلام حوالتك" is the recipient's receipt of money he SENT. */
    @Test
    fun `a transfer received abroad stays money leaving`() {
        val draft = parsed(RealMessages.BARQ_TRANSFER_RECEIVED_ABROAD, SaudiBanks.BARQ)

        assertEquals(TransactionType.TRANSFER_OUT, draft.type)
    }

    /** "حساب المستلم: استلام عبر ويسترين يونيون" sits inside an outgoing wage transfer. */
    @Test
    fun `a western union transfer with استلام in a field stays money leaving`() {
        val draft = parsed(RealMessages.STC_WESTERN_UNION, SaudiBanks.STC_PAY)

        assertEquals(TransactionType.TRANSFER_OUT, draft.type)
    }

    /** The same fix reaches the two big banks, whose own templates use the noun حوالة. */
    @Test
    fun `حوالة بين حساباتك at the other banks is the owner's own money`() {
        assertEquals(TransactionType.OWN_TRANSFER, parsed(RealMessages.SNB_INCOMING_OWN_ACCOUNTS, SaudiBanks.SNB).type)
        assertEquals(TransactionType.OWN_TRANSFER, parsed(RealMessages.RAJHI_OWN_ACCOUNTS, SaudiBanks.AL_RAJHI).type)
        assertNull(parsed(RealMessages.RAJHI_OWN_ACCOUNTS, SaudiBanks.AL_RAJHI).merchantRaw)
    }
}
