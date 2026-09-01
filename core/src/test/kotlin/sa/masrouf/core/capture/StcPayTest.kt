package sa.masrouf.core.capture

import org.junit.jupiter.api.Test
import sa.masrouf.core.fixtures.RealMessages
import sa.masrouf.core.model.CategoryGuess
import sa.masrouf.core.model.Direction
import sa.masrouf.core.model.SaudiCategories
import sa.masrouf.core.model.TransactionType
import sa.masrouf.core.money.Money
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The wallet nobody had read.
 *
 * 4,446 STC Pay messages sat in the owner's inbox from 2019 to 2026 and not one
 * was ever examined: no profile claimed the sender, so the wallet produced no
 * records, and a sender that produces no records is invisible to every query over
 * the database. What was missing was 1,845 purchases and 68 international
 * transfers; what was wrong was the other side of it - 670 top-ups of this wallet,
 * 650,280 riyals, stored as purchases at a shop called "STC Pay" and counted as
 * money spent.
 *
 * The wallet is closed now (it became STC Bank), so this parser reads history
 * rather than new messages. History is what the app is for.
 */
class StcPayTest {

    private fun parse(body: String) =
        BankMessageParser(SaudiBanks.STC_PAY).parse(RawMessage(body, Instant.EPOCH))

    private fun parsed(body: String) =
        (parse(body) as? ParseResult.Parsed)?.draft
            ?: error("not parsed: ${parse(body)}")

    @Test
    fun `the sender is claimed at all, which it never was`() {
        val claimed = BankMessageParser(SaudiBanks.STC_PAY)
            .canParse(RawMessage(RealMessages.STC_ONLINE_PURCHASE, Instant.EPOCH, sender = "STCPAY"))

        assertTrue(claimed)
    }

    @Test
    fun `an online purchase gives up its merchant, amount and card`() {
        val draft = parsed(RealMessages.STC_ONLINE_PURCHASE)

        assertEquals(TransactionType.PURCHASE, draft.type)
        assertEquals(Money.ofMajor("10").halalas, draft.amount.halalas)
        assertEquals("Health Endowment Fund", draft.merchantRaw)
        assertEquals("8611", draft.accountLast4)
    }

    @Test
    fun `a purchase at a terminal reads the same way`() {
        val draft = parsed(RealMessages.STC_POS_PURCHASE)

        assertEquals(TransactionType.PURCHASE, draft.type)
        assertEquals("SPEED TRACK3", draft.merchantRaw)
    }

    /**
     * The wallet writes the card and the merchant under one label, "من:", and the
     * asterisk is the only thing that separates them.
     */
    @Test
    fun `the card is not mistaken for the merchant when both say من`() {
        val draft = parsed(RealMessages.STC_CARD_PURCHASE)

        assertEquals("AL DRE", draft.merchantRaw)
        assertEquals("7667", draft.accountLast4)
    }

    /**
     * The date is written after the same word as the merchant. A parser that reads
     * it as a shop invents a merchant called "26/06/26".
     */
    @Test
    fun `a date after في is never read as a merchant`() {
        assertNotEquals("26/06/26 01:58", parsed(RealMessages.STC_CARD_PURCHASE).merchantRaw)
    }

    @Test
    fun `a top-up is the owner's own money, in both wordings`() {
        assertEquals(TransactionType.OWN_TRANSFER, parsed(RealMessages.STC_WALLET_TOPUP).type)
        assertEquals(TransactionType.OWN_TRANSFER, parsed(RealMessages.STC_ADD_MONEY).type)
    }

    /**
     * The other half of the same movement, seen from the bank whose card funded it.
     * This is what was counted as spending for seven years.
     */
    @Test
    fun `a bank purchase at the wallet is a transfer, not a purchase`() {
        val body = "شراء إنترنت\nبطاقة **0926\nمبلغ 10000.00 SAR\nلدى STC Pay\nفي 06/09/2022 14:18"

        val draft = (BankMessageParser(SaudiBanks.SNB).parse(RawMessage(body, Instant.EPOCH))
            as ParseResult.Parsed).draft

        assertEquals(TransactionType.OWN_TRANSFER, draft.type)
    }

    /**
     * Wages for domestic staff, sent through Western Union. Filed by the channel
     * rather than by the recipient: she is a person, and a person's name has no
     * place in a shipped category rule. The body keeps her name regardless.
     */
    @Test
    fun `an international transfer is filed by its channel, not its recipient`() {
        val draft = parsed(RealMessages.STC_WESTERN_UNION)

        assertEquals(TransactionType.TRANSFER_OUT, draft.type)
        assertEquals(Direction.DEBIT, draft.direction)
        assertEquals("ويسترين يونيون", draft.merchantRaw)
        assertEquals(SaudiCategories.FEES, CategoryGuess.forMerchant(draft.merchantRaw))
    }

    /**
     * The defect the channel patterns uncovered, and the more expensive of the two.
     * Every outgoing transfer names the owner - he is sending it - and the rule that
     * demotes a transfer to himself asked only whether his name appeared anywhere.
     * STC Pay writes "اسم المرسل" on all 68 of these, so 94,126 riyals of wages read
     * as the owner moving money to himself and left his spending entirely.
     */
    @Test
    fun `naming the owner as the sender does not make a transfer his own money`() {
        AccountOwner.configure("OWNER|NAME")
        try {
            assertEquals(TransactionType.TRANSFER_OUT, parsed(RealMessages.STC_WESTERN_UNION).type)
        } finally {
            AccountOwner.configure("")
        }
    }

    /** The short form of the same transfer names no channel, only "WU". */
    @Test
    fun `the short transfer template files the same way`() {
        val draft = parsed(RealMessages.STC_WU_SHORT)

        assertEquals("WU", draft.merchantRaw)
        assertEquals(SaudiCategories.FEES, CategoryGuess.forMerchant(draft.merchantRaw))
    }

    /**
     * Two characters, so MerchantMatch requires a whole word. Without that the rule
     * would file a shop called "WUJOOH" as wages.
     */
    @Test
    fun `the two-letter channel does not reach inside another name`() {
        assertNotEquals(SaudiCategories.FEES, CategoryGuess.forMerchant("WUJOOH MAKKAH MALL"))
    }

    /**
     * The wallet's most common message, 889 of them, each carrying a live code and
     * the amount of the purchase it authorises. Nothing stopped them before,
     * because nothing read this sender at all.
     */
    @Test
    fun `a security code is refused and never stored`() {
        val decision = MessageGate.evaluate(
            RawMessage(RealMessages.STC_SECURITY_CODE, Instant.EPOCH, sender = "STCPAY")
        )

        assertEquals(
            MessageGate.Rejection.ONE_TIME_PASSWORD,
            (decision as MessageGate.Decision.Reject).reason,
        )
    }

    @Test
    fun `a refusal for want of balance is not a transaction`() {
        val decision = MessageGate.evaluate(
            RawMessage(RealMessages.STC_DECLINED, Instant.EPOCH, sender = "STCPAY")
        )

        assertTrue(decision is MessageGate.Decision.Reject)
    }
}
