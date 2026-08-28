package sa.masrouf.core.capture

import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import org.junit.jupiter.api.Test
import sa.masrouf.core.fixtures.RealMessages
import sa.masrouf.core.model.Direction
import sa.masrouf.core.model.TransactionType
import sa.masrouf.core.money.Money
import java.time.Instant

/**
 * The templates found by running 5,074 real AlRajhi messages through the pipeline.
 *
 * Every one of these was silently reported as "no recognised intent" - 182
 * transactions the app knew it could not read and nobody was counting. They are
 * tested here against the captured wording, redacted, rather than against a guess
 * at what AlRajhi's English messages look like.
 */
class RajhiEnglishTemplatesTest {

    private val pipeline = CapturePipeline()

    private fun parse(body: String): CapturePipeline.Outcome = pipeline.process(
        RawMessage(body, Instant.parse("2026-08-28T09:00:00Z"), sender = "AlRajhiBank")
    )

    private fun captured(body: String) =
        assertIs<CapturePipeline.Outcome.Captured>(parse(body)).draft

    @Test
    fun `the english point of sale template is a purchase`() {
        val draft = captured(RealMessages.RAJHI_POS_ENGLISH)

        assertEquals(TransactionType.PURCHASE, draft.type)
        assertEquals(Direction.DEBIT, draft.direction)
        assertEquals(Money.ofMajor("339.00"), draft.amount)
        assertEquals("1335", draft.accountLast4)
        // The merchant follows "At:", and must not be the balance line beneath it.
        assertEquals("MERCHANT NAME", draft.merchantRaw)
    }

    @Test
    fun `a reversal is money coming back, not another purchase`() {
        val draft = captured(RealMessages.RAJHI_REVERSAL)

        assertEquals(TransactionType.REFUND, draft.type)
        assertEquals(Direction.CREDIT, draft.direction)
        assertEquals(Money.ofMajor("1.00"), draft.amount)
    }

    @Test
    fun `an internal transfer is between the user's own accounts and is not spending`() {
        val draft = captured(RealMessages.RAJHI_INTERNAL_TRANSFER)

        assertEquals(TransactionType.OWN_TRANSFER, draft.type)
        assertEquals(false, draft.type.countsAsSpending)
        assertEquals(Money.ofMajor("27.23"), draft.amount)
    }

    @Test
    fun `a local transfer leaves the user and counts as spending`() {
        val draft = captured(RealMessages.RAJHI_LOCAL_TRANSFER)

        assertEquals(TransactionType.TRANSFER_OUT, draft.type)
        assertEquals(true, draft.type.countsAsSpending)
        // The transfer amount, not the 1.15 fee line beneath it.
        assertEquals(Money.ofMajor("20000.00"), draft.amount)
    }

    @Test
    fun `paying off a credit card is not spending`() {
        // The purchases that built the balance were counted when they happened.
        // Counting the payment too charges the same riyals twice.
        val draft = captured(RealMessages.RAJHI_CREDIT_CARD_PAYMENT)

        assertEquals(TransactionType.OWN_TRANSFER, draft.type)
        assertEquals(false, draft.type.countsAsSpending)
        assertEquals(Money.ofMajor("700.00"), draft.amount)
    }

    @Test
    fun `savings profit is income`() {
        val draft = captured(RealMessages.RAJHI_SAVINGS_PROFIT)

        assertEquals(Direction.CREDIT, draft.direction)
        assertEquals(false, draft.type.countsAsSpending)
    }

    @Test
    fun `an international purchase is refused rather than read as riyals`() {
        // The amount is USD and this app stores halalas of SAR. Reading "1" as one
        // riyal invents a number; picking up the SAR balance line would be worse.
        // Refusing keeps it visible as a known gap instead of a wrong total.
        val outcome = parse(RealMessages.RAJHI_INTERNATIONAL_PURCHASE)

        assertIs<CapturePipeline.Outcome.NotUnderstood>(outcome)
    }

    @Test
    fun `a one-time password in the password wording never reaches a parser`() {
        // 88 of these were in the corpus and the gate passed every one; 25 were
        // stored as transactions with the code in the body.
        val outcome = parse(RealMessages.RAJHI_OTP_PASSWORD_WORDING)

        val rejected = assertIs<CapturePipeline.Outcome.Rejected>(outcome)
        assertEquals(MessageGate.Rejection.ONE_TIME_PASSWORD, rejected.reason)
        assertEquals(true, rejected.bodyIsSensitive)
    }

    @Test
    fun `a voucher code is not a transaction`() {
        assertNull((parse(RealMessages.RAJHI_POS_ENGLISH) as? CapturePipeline.Outcome.NotUnderstood))
    }

    @Test
    fun `a prize-draw advert is not a cash withdrawal`() {
        // Captured on a real phone, where it was stored as a 0.00 SAR withdrawal.
        // Arabic tokens match as stems, so "السحب" (the draw) contains "سحب" and
        // "النقدية" (the cash prize) contains "نقدي" - both tokens of the ATM
        // rule, in a sentence about a raffle.
        val outcome = parse(RealMessages.RAJHI_PRIZE_DRAW_ADVERT)

        val rejected = assertIs<CapturePipeline.Outcome.Rejected>(outcome)
        assertEquals(MessageGate.Rejection.NOT_FINANCIAL, rejected.reason)
        // Marketing is noise, not a credential: the body may still be logged.
        assertEquals(false, rejected.bodyIsSensitive)
    }
}
