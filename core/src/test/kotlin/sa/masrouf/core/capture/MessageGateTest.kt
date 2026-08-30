package sa.masrouf.core.capture

import org.junit.jupiter.api.Test
import sa.masrouf.core.fixtures.RealMessages
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MessageGateTest {

    private fun message(body: String) = RawMessage(body = body, receivedAt = Instant.EPOCH)

    @Test
    fun `every real completed transaction is allowed through`() {
        for (body in RealMessages.COMPLETED_TRANSACTIONS) {
            assertTrue(
                MessageGate.allows(message(body)),
                "gate wrongly rejected a real transaction:\n$body",
            )
        }
    }

    @Test
    fun `every one-time password and declined message is rejected`() {
        for (body in RealMessages.MUST_BE_REJECTED) {
            assertIs<MessageGate.Decision.Reject>(
                MessageGate.evaluate(message(body)),
                "gate wrongly allowed a message that must never become a transaction:\n$body",
            )
        }
    }

    @Test
    fun `one-time passwords are identified as such, not merely rejected`() {
        val otpBodies = listOf(
            RealMessages.RAJHI_OTP,
            RealMessages.SNB_OTP,
            RealMessages.SNB_ACTIVATION_CODE,
            RealMessages.D360_OTP,
            RealMessages.BARQ_OTP,
        )
        for (body in otpBodies) {
            val decision = MessageGate.evaluate(message(body))
            assertIs<MessageGate.Decision.Reject>(decision, "not rejected:\n$body")
            assertEquals(
                MessageGate.Rejection.ONE_TIME_PASSWORD,
                decision.reason,
                "wrong rejection reason for:\n$body",
            )
        }
    }

    @Test
    fun `a declined transaction is rejected as declined, not as an OTP`() {
        val decision = MessageGate.evaluate(message(RealMessages.BARQ_DECLINED))
        assertIs<MessageGate.Decision.Reject>(decision)
        assertEquals(MessageGate.Rejection.DECLINED, decision.reason)
    }

    @Test
    fun `an OTP body is marked as unsafe to persist`() {
        assertTrue(MessageGate.mustNotPersistBody(message(RealMessages.SNB_OTP)))
    }

    @Test
    fun `a declined message may be persisted for diagnostics`() {
        assertTrue(!MessageGate.mustNotPersistBody(message(RealMessages.BARQ_DECLINED)))
    }

    /**
     * The reason this gate exists at all.
     *
     * The OTP that authorises a purchase carries the same amount and the same
     * merchant as the confirmation that follows it moments later. Left ungated it
     * parses perfectly - and every online purchase is recorded twice.
     */
    @Test
    fun `an OTP would otherwise parse as a perfectly good purchase`() {
        val otpAmount = AmountExtractor.extractOrNull(RealMessages.RAJHI_OTP)
        val purchaseAmount = AmountExtractor.extractOrNull(RealMessages.RAJHI_ONLINE_PURCHASE)

        assertEquals(
            purchaseAmount?.money,
            otpAmount?.money,
            "the OTP and its purchase confirmation carry the same amount, which is " +
                "exactly why the gate must run before any parser",
        )
        assertTrue(!MessageGate.allows(message(RealMessages.RAJHI_OTP)))
    }

    @Test
    fun `the password wording is refused, not only the code wording`() {
        // From a real corpus: 88 messages used this wording and the gate passed
        // every one. Nothing stored them because no parser could read them yet -
        // and "no parser can read it" is not a control over a credential.
        val message = RawMessage(
            body = RealMessages.RAJHI_OTP_PASSWORD_WORDING,
            receivedAt = Instant.parse("2026-08-28T09:00:00Z"),
            sender = "AlRajhiBank",
        )

        val decision = MessageGate.evaluate(message)

        assertTrue(decision is MessageGate.Decision.Reject)
        assertEquals(
            MessageGate.Rejection.ONE_TIME_PASSWORD,
            (decision as MessageGate.Decision.Reject).reason,
        )
        assertTrue(MessageGate.mustNotPersistBody(message))
    }

    /**
     * An English one-time code, word for word as one bank sends it.
     *
     * Fifty-eight of these reached storage as confirmed purchases before this
     * marker existed. Each carried the amount and the card of the purchase it
     * authorised, so each doubled a real one, and each kept a credential in the
     * database. The body must be rejected AND flagged sensitive, so it is never
     * written anywhere.
     */
    @Test
    fun `an english secure code is a credential, not a purchase`() {
        val body = "Your secure code is 6659\nFor internet purchase SAR155.81\nCard ending 2887"

        val decision = MessageGate.evaluate(message(body))

        assertIs<MessageGate.Decision.Reject>(decision)
        assertEquals(MessageGate.Rejection.ONE_TIME_PASSWORD, decision.reason)
        assertTrue(MessageGate.mustNotPersistBody(message(body)))
    }

    /** A limit change carries an amount and is not a transaction. Stored once as 200,000 riyals spent. */
    @Test
    fun `a card limit change is not a purchase`() {
        val body = "تم تغيير الحد اليومي للشراء عبر الانترنت لبطاقة رقم ***907\nالى SAR 200000\nفي 24/12/2024 09:28"

        val decision = MessageGate.evaluate(message(body))

        assertIs<MessageGate.Decision.Reject>(decision)
        assertEquals(MessageGate.Rejection.NOT_FINANCIAL, decision.reason)
    }
}
