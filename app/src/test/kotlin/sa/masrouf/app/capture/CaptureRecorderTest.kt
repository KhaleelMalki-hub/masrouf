package sa.masrouf.app.capture

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import sa.masrouf.core.capture.RawMessage
import sa.masrouf.core.fixtures.RealMessages
import sa.masrouf.core.model.Source
import sa.masrouf.core.model.Status
import java.time.Instant

/**
 * Written against the captured messages in `RealMessages`, never against an
 * invented body. The fixtures are shared from `:core` rather than retyped here,
 * because a second copy drifts and a test against a drifted copy proves nothing.
 */
class CaptureRecorderTest {

    private val recorder = CaptureRecorder()
    private val receivedAt = Instant.parse("2026-08-27T12:00:00Z")

    private fun notification(body: String, pkg: String) = RawMessage(
        body = body,
        receivedAt = receivedAt,
        packageName = pkg,
    )

    @Test
    fun `a purchase notification becomes a pending transaction`() {
        val decision = recorder.decide(notification(RealMessages.RAJHI_POS_SHORT, "com.alrajhibank.activity"), id = "t-1", Source.NOTIFICATION)

        val stored = assertIs<CaptureRecorder.Decision.Store>(decision)
        assertEquals(Status.PENDING, stored.transaction.status)
        assertEquals(Source.NOTIFICATION, stored.transaction.source)
    }

    @Test
    fun `nothing captured is ever confirmed automatically`() {
        // The rule holds whatever the parser's confidence was. Lowering the
        // threshold is a per-parser decision taken after measuring that parser
        // against real messages, not one this class may take from a float.
        val bodies = listOf(
            RealMessages.RAJHI_POS_SHORT to "com.alrajhibank.activity",
            RealMessages.RAJHI_POS_LONG to "com.alrajhibank.activity",
            RealMessages.RAJHI_ONLINE_PURCHASE to "com.alrajhibank.activity",
        )

        val statuses = bodies.mapNotNull { (body, pkg) ->
            (recorder.decide(notification(body, pkg), "t", Source.NOTIFICATION) as? CaptureRecorder.Decision.Store)
                ?.transaction
                ?.status
        }

        assertTrue(statuses.isNotEmpty(), "no fixture parsed; the test proves nothing")
        assertTrue(statuses.all { it == Status.PENDING })
    }

    @Test
    fun `a one-time password is never stored and is marked sensitive`() {
        // The OTP carries the same amount and merchant as the purchase it
        // authorises and arrives seconds earlier. Stored, it doubles the purchase
        // and writes a credential to disk.
        val decision = recorder.decide(notification(RealMessages.SNB_OTP, "com.snb.alahli"), id = "t-otp", Source.NOTIFICATION)

        val skipped = assertIs<CaptureRecorder.Decision.Skip>(decision)
        assertTrue(skipped.bodyWasSensitive)
        assertEquals("ONE_TIME_PASSWORD", skipped.reason)
    }

    @Test
    fun `every otp fixture is refused, whatever the bank`() {
        val otps = mapOf(
            RealMessages.RAJHI_OTP to "com.alrajhibank.activity",
            RealMessages.SNB_OTP to "com.snb.alahli",
            RealMessages.D360_OTP to "com.d360.bank",
            RealMessages.BARQ_OTP to "sa.barq.app",
        )

        otps.forEach { (body, pkg) ->
            val decision = recorder.decide(notification(body, pkg), "t", Source.NOTIFICATION)
            val skipped = assertIs<CaptureRecorder.Decision.Skip>(decision, "stored an OTP from $pkg")
            assertTrue(skipped.bodyWasSensitive, "OTP from $pkg not marked sensitive")
        }
    }

    @Test
    fun `a declined transaction is not recorded as spending`() {
        // Same shape as a successful purchase, but no money moved.
        val decision = recorder.decide(notification(RealMessages.BARQ_DECLINED, "sa.barq.app"), id = "t-declined", Source.NOTIFICATION)

        val skipped = assertIs<CaptureRecorder.Decision.Skip>(decision)
        assertEquals("DECLINED", skipped.reason)
        assertFalse(skipped.bodyWasSensitive)
    }

    @Test
    fun `a notification from a non-bank app is ignored`() {
        val decision = recorder.decide(notification("Your order has shipped", "com.example.shopping"), id = "t-other", Source.NOTIFICATION)

        assertEquals("UNKNOWN_SENDER", assertIs<CaptureRecorder.Decision.Skip>(decision).reason)
    }

    @Test
    fun `the same notification posted twice produces the same fingerprint`() {
        // Android reposts a notification when it is updated. That must collapse to
        // one transaction, and the unique index on fingerprint is what enforces it -
        // so the fingerprint has to be stable across two separate decisions.
        val message = notification(RealMessages.RAJHI_POS_SHORT, "com.alrajhibank.activity")

        val first = assertIs<CaptureRecorder.Decision.Store>(recorder.decide(message, "id-a", Source.NOTIFICATION))
        val second = assertIs<CaptureRecorder.Decision.Store>(recorder.decide(message, "id-b", Source.NOTIFICATION))

        assertEquals(first.transaction.fingerprint, second.transaction.fingerprint)
        // The ids differ, so the match is the fingerprint's doing and not an artefact
        // of the two records being identical in every field.
        assertNotEquals(first.transaction.id, second.transaction.id)
    }

    @Test
    fun `two separate purchases minutes apart stay two transactions`() {
        // From real captured data: two 5,000 top-ups 49 minutes apart on one
        // morning, identical in amount, day, card and merchant. A day-granularity
        // fingerprint merges them and silently destroys 5,000 SAR.
        val body = RealMessages.RAJHI_POS_SHORT
        val early = RawMessage(body, receivedAt, packageName = "com.alrajhibank.activity")
        val later = RawMessage(
            body,
            receivedAt.plusSeconds(49 * 60),
            packageName = "com.alrajhibank.activity",
        )

        val a = assertIs<CaptureRecorder.Decision.Store>(recorder.decide(early, "id-a", Source.NOTIFICATION))
        val b = assertIs<CaptureRecorder.Decision.Store>(recorder.decide(later, "id-b", Source.NOTIFICATION))

        assertNotEquals(a.transaction.fingerprint, b.transaction.fingerprint)
    }

    @Test
    fun `the message body is kept so a parsing bug can be replayed against it`() {
        val decision = recorder.decide(notification(RealMessages.RAJHI_POS_SHORT, "com.alrajhibank.activity"), id = "t-raw", Source.NOTIFICATION)

        val stored = assertIs<CaptureRecorder.Decision.Store>(decision)
        assertTrue(stored.transaction.rawText!!.contains("8.28"))
    }
}
