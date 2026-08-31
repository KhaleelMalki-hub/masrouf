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
import kotlin.test.assertTrue

class CapturePipelineTest {

    private val pipeline = CapturePipeline()
    private val received = Instant.parse("2026-08-27T05:04:00Z")

    private fun capture(sender: String, body: String): CapturePipeline.Outcome.Captured {
        val outcome = pipeline.process(RawMessage(body = body, receivedAt = received, sender = sender))
        assertIs<CapturePipeline.Outcome.Captured>(
            outcome,
            "expected a captured transaction from $sender but got $outcome\n---\n$body",
        )
        return outcome
    }

    private fun assertCaptured(
        sender: String,
        body: String,
        amount: String,
        type: TransactionType,
        direction: Direction,
        party: String? = null,
        cardLast4: String? = null,
    ) {
        val draft = capture(sender, body).draft
        assertEquals(Money.ofMajor(amount), draft.amount, "amount\n$body")
        assertEquals(type, draft.type, "type\n$body")
        assertEquals(direction, draft.direction, "direction\n$body")
        if (party != null) assertEquals(party, draft.merchantRaw, "party\n$body")
        if (cardLast4 != null) assertEquals(cardLast4, draft.accountLast4, "card\n$body")
    }

    // ---- AlRajhi -----------------------------------------------------------

    @Test
    fun `alrajhi online purchase`() = assertCaptured(
        RealMessages.SENDER_RAJHI, RealMessages.RAJHI_ONLINE_PURCHASE,
        amount = "931.64", type = TransactionType.PURCHASE, direction = Direction.DEBIT,
        party = "IHERB ARA", cardLast4 = "2383",
    )

    @Test
    fun `alrajhi point of sale, short template`() = assertCaptured(
        RealMessages.SENDER_RAJHI, RealMessages.RAJHI_POS_SHORT,
        amount = "8.28", type = TransactionType.PURCHASE, direction = Direction.DEBIT,
        party = "ASIAN POLYCLINI", cardLast4 = "5763",
    )

    @Test
    fun `alrajhi point of sale, long template`() = assertCaptured(
        RealMessages.SENDER_RAJHI, RealMessages.RAJHI_POS_LONG,
        amount = "320", type = TransactionType.PURCHASE, direction = Direction.DEBIT,
        party = "Fourth fr", cardLast4 = "2383",
    )

    @Test
    fun `alrajhi incoming transfer`() = assertCaptured(
        RealMessages.SENDER_RAJHI, RealMessages.RAJHI_TRANSFER_IN,
        amount = "2000", type = TransactionType.TRANSFER_IN, direction = Direction.CREDIT,
        party = "RECIPIENT NAME",
    )

    @Test
    fun `alrajhi card refund is money coming back, not going out`() = assertCaptured(
        RealMessages.SENDER_RAJHI, RealMessages.RAJHI_CARD_REFUND,
        amount = "1138.71", type = TransactionType.REFUND, direction = Direction.CREDIT,
        party = "Amazon SA", cardLast4 = "2383",
    )

    /**
     * Settling the card, not paying a bill. This asserted BILL_PAYMENT for as long
     * as the fixture existed, which is how 43 settlements were counted as spending
     * on top of the purchases that had built the balance. See [OwnMoneyTest].
     */
    @Test
    fun `alrajhi credit card settlement`() = assertCaptured(
        RealMessages.SENDER_RAJHI, RealMessages.RAJHI_CARD_SETTLEMENT,
        amount = "10000", type = TransactionType.OWN_TRANSFER, direction = Direction.DEBIT,
        cardLast4 = "2383",
    )

    // ---- SNB ---------------------------------------------------------------

    /**
     * The funding account and the merchant are both introduced by "من"; only the
     * asterisk tells them apart ("من *0104" is the account, "من barq" is where the
     * money went).
     */
    @Test
    fun `snb tells the funding account apart from the merchant`() {
        val draft = capture(RealMessages.SENDER_SNB, RealMessages.SNB_ONLINE_PURCHASE).draft
        assertEquals("barq", draft.merchantRaw)
        assertEquals("1887", draft.accountLast4)
        assertEquals(Money.ofMajor("35"), draft.amount)
    }

    @Test
    fun `snb incoming internal transfer`() = assertCaptured(
        RealMessages.SENDER_SNB, RealMessages.SNB_TRANSFER_IN,
        amount = "35", type = TransactionType.TRANSFER_IN, direction = Direction.CREDIT,
        party = "RECIPIENT NAME",
    )

    @Test
    fun `snb outgoing internal transfer`() = assertCaptured(
        RealMessages.SENDER_SNB, RealMessages.SNB_TRANSFER_OUT,
        amount = "2000", type = TransactionType.TRANSFER_OUT, direction = Direction.DEBIT,
        party = "RECIPIENT NAME",
    )

    @Test
    fun `snb atm deposit is credit, not spending`() = assertCaptured(
        RealMessages.SENDER_SNB, RealMessages.SNB_ATM_DEPOSIT,
        amount = "2000", type = TransactionType.ATM_DEPOSIT, direction = Direction.CREDIT,
    )

    // ---- D360 --------------------------------------------------------------

    @Test
    fun `d360 incoming transfer with thousands separator`() = assertCaptured(
        RealMessages.SENDER_D360, RealMessages.D360_TRANSFER_IN,
        amount = "2850.00", type = TransactionType.TRANSFER_IN, direction = Direction.CREDIT,
        party = "RECIPIENT NAME", cardLast4 = "2207",
    )

    @Test
    fun `d360 outgoing transfer`() = assertCaptured(
        RealMessages.SENDER_D360, RealMessages.D360_TRANSFER_OUT,
        amount = "350.00", type = TransactionType.TRANSFER_OUT, direction = Direction.DEBIT,
        party = "RECIPIENT NAME",
    )

    @Test
    fun `d360 transfer between the users own accounts is not spending`() {
        val draft = capture(
            RealMessages.SENDER_D360,
            RealMessages.D360_OWN_ACCOUNTS_TRANSFER,
        ).draft
        assertEquals(TransactionType.OWN_TRANSFER, draft.type)
        assertEquals(Money.ofMajor("2500.00"), draft.amount)
        assertEquals(false, draft.type.countsAsSpending)
    }

    // ---- barq --------------------------------------------------------------

    @Test
    fun `barq outgoing transfer ignores the fees line`() = assertCaptured(
        RealMessages.SENDER_BARQ, RealMessages.BARQ_TRANSFER_OUT,
        amount = "2000.00", type = TransactionType.TRANSFER_OUT, direction = Direction.DEBIT,
        party = "RECIPIENT NAME",
    )

    @Test
    fun `barq english top-up notice`() = assertCaptured(
        RealMessages.SENDER_BARQ, RealMessages.BARQ_TOPUP_EN,
        amount = "5000.00", type = TransactionType.TRANSFER_IN, direction = Direction.CREDIT,
        cardLast4 = "1887",
    )

    @Test
    fun `barq online purchase ignores the glued balance`() = assertCaptured(
        RealMessages.SENDER_BARQ, RealMessages.BARQ_ONLINE_PURCHASE,
        amount = "1.00", type = TransactionType.PURCHASE, direction = Direction.DEBIT,
        party = "Noon", cardLast4 = null,
    )

    // ---- The wallet double-count -------------------------------------------

    /**
     * Topping up the barq wallet shows up on SNB as an ordinary online purchase at
     * a merchant called "barq". The same riyals are then reported a second time by
     * barq itself as they are spent.
     *
     * Left as a purchase, every top-up is counted twice - and because top-ups are
     * large round numbers, the inflated total is both significant and hard to trace
     * back to its cause.
     */
    @Test
    fun `a wallet top-up is reclassified out of spending`() {
        val draft = capture(RealMessages.SENDER_SNB, RealMessages.SNB_ONLINE_PURCHASE).draft
        assertEquals(TransactionType.OWN_TRANSFER, draft.type)
        assertEquals(false, draft.type.countsAsSpending)
    }

    @Test
    fun `an ordinary purchase still counts as spending`() {
        val draft = capture(RealMessages.SENDER_RAJHI, RealMessages.RAJHI_ONLINE_PURCHASE).draft
        assertTrue(draft.type.countsAsSpending)
    }

    // ---- Pipeline guarantees -----------------------------------------------

    @Test
    fun `every real completed transaction is understood by some parser`() {
        for ((sender, body) in RealMessages.COMPLETED_SAMPLES) {
            val outcome = pipeline.process(
                RawMessage(body = body, receivedAt = received, sender = sender)
            )
            assertIs<CapturePipeline.Outcome.Captured>(
                outcome,
                "sender=$sender produced $outcome\n---\n$body",
            )
        }
    }

    @Test
    fun `no OTP or declined message ever becomes a transaction`() {
        for ((sender, body) in RealMessages.REJECTABLE_SAMPLES) {
            val outcome = pipeline.process(
                RawMessage(body = body, receivedAt = received, sender = sender)
            )
            assertIs<CapturePipeline.Outcome.Rejected>(
                outcome,
                "sender=$sender produced $outcome\n---\n$body",
            )
        }
    }

    @Test
    fun `OTP bodies are flagged as unsafe to store`() {
        val outcome = pipeline.process(
            RawMessage(
                body = RealMessages.RAJHI_OTP,
                receivedAt = received,
                sender = RealMessages.SENDER_RAJHI,
            )
        )
        assertIs<CapturePipeline.Outcome.Rejected>(outcome)
        assertTrue(outcome.bodyIsSensitive)
    }

    @Test
    fun `an unknown sender is reported as such, not parsed`() {
        val outcome = pipeline.process(
            RawMessage(body = "خصم 50 ريال", receivedAt = received, sender = "Jarir")
        )
        assertEquals(CapturePipeline.Outcome.UnknownSender, outcome)
    }

    /** A template change at a known bank must be visible, not silently dropped. */
    @Test
    fun `a known sender with unreadable content is reported as not understood`() {
        val outcome = pipeline.process(
            RawMessage(
                body = "تم تحديث بياناتك بنجاح",
                receivedAt = received,
                sender = RealMessages.SENDER_RAJHI,
            )
        )
        assertIs<CapturePipeline.Outcome.NotUnderstood>(outcome)
        assertEquals("alrajhi", outcome.parserId)
    }

    // ---- Trust boundaries --------------------------------------------------

    /**
     * The draft's timestamp comes from the device, never from the message body.
     * See BankMessageParser.parse for why the in-body date is not trusted.
     */
    @Test
    fun `the device receipt time is used, not the date written in the message`() {
        val draft = capture(RealMessages.SENDER_RAJHI, RealMessages.RAJHI_ONLINE_PURCHASE).draft
        assertEquals(received, draft.occurredAt)
    }

    @Test
    fun `nothing is auto-confirmed`() {
        for ((sender, body) in RealMessages.COMPLETED_SAMPLES) {
            val captured = capture(sender, body)
            assertTrue(
                captured.confidence < ParserRegistry.CONFIRMATION_THRESHOLD,
                "sender=$sender would auto-confirm at ${captured.confidence}\n---\n$body",
            )
        }
    }

    @Test
    fun `an amount is never invented when the message has none`() {
        val outcome = pipeline.process(
            RawMessage(
                body = "شراء عبر نقاط البيع\nلدى:Fourth fr",
                receivedAt = received,
                sender = RealMessages.SENDER_RAJHI,
            )
        )
        assertIs<CapturePipeline.Outcome.NotUnderstood>(outcome)
        assertEquals("no amount found", outcome.reason)
    }

    @Test
    fun `a message with no sender is not attributed to a bank`() {
        val outcome = pipeline.process(
            RawMessage(body = RealMessages.RAJHI_ONLINE_PURCHASE, receivedAt = received)
        )
        assertEquals(CapturePipeline.Outcome.UnknownSender, outcome)
    }

    @Test
    fun `transfers carry no merchant, only a counterparty`() {
        val draft = capture(RealMessages.SENDER_SNB, RealMessages.SNB_TRANSFER_OUT).draft
        assertNull(draft.note, "counterparty belongs in merchantRaw, not duplicated into note")
    }
}
