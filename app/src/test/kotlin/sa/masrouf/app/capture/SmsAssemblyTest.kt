package sa.masrouf.app.capture

import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import sa.masrouf.core.fixtures.RealMessages
import java.time.Instant

class SmsAssemblyTest {

    private val receivedAt = Instant.parse("2026-08-28T09:00:00Z")

    @Test
    fun `a multipart message is rebuilt before anything tries to read it`() {
        // An Arabic SMS holds 70 characters per part, so a bank purchase message is
        // always split. Read part by part, the part with the amount and the part
        // with the merchant are different messages.
        val whole = RealMessages.RAJHI_POS_SHORT
        val split = whole.chunked(40).map { SmsPart("AlRajhiBank", it) }
        assertTrue(split.size > 1, "fixture did not split; the test would prove nothing")

        val message = SmsAssembly.assemble(split, receivedAt)

        assertEquals(whole, message?.body)
    }

    @Test
    fun `a rebuilt multipart message still parses into a transaction`() {
        val split = RealMessages.RAJHI_POS_SHORT.chunked(40).map { SmsPart("AlRajhiBank", it) }
        val message = SmsAssembly.assemble(split, receivedAt)!!

        val decision = CaptureRecorder().decide(message, "t-sms")

        assertIs<CaptureRecorder.Decision.Store>(decision)
    }

    @Test
    fun `the sender is carried so a parser can claim the message`() {
        val message = SmsAssembly.assemble(
            listOf(SmsPart("AlRajhiBank", RealMessages.RAJHI_POS_SHORT)),
            receivedAt,
        )

        assertEquals("AlRajhiBank", message?.sender)
        // SMS has no posting package, and the parsers match on either.
        assertNull(message?.packageName)
    }

    @Test
    fun `two banks delivered together are never spliced into one message`() {
        // Joining these blindly would graft one bank's amount onto another's
        // merchant, and the result would parse - which is what makes it dangerous.
        val parts = listOf(
            SmsPart("AlRajhiBank", RealMessages.RAJHI_POS_SHORT),
            SmsPart("SNB ALAHLI", RealMessages.SNB_ONLINE_PURCHASE),
        )

        val messages = SmsAssembly.assembleBySender(parts, receivedAt)

        assertEquals(2, messages.size)
        assertEquals(setOf("AlRajhiBank", "SNB ALAHLI"), messages.mapNotNull { it.sender }.toSet())
    }

    @Test
    fun `an empty or blank delivery yields nothing rather than an empty message`() {
        assertNull(SmsAssembly.assemble(emptyList(), receivedAt))
        assertNull(SmsAssembly.assemble(listOf(SmsPart("X", "   ")), receivedAt))
    }

    @Test
    fun `an otp arriving by sms is refused exactly as it is from a notification`() {
        val message = SmsAssembly.assemble(
            listOf(SmsPart("SNB ALAHLI", RealMessages.SNB_OTP)),
            receivedAt,
        )!!

        val decision = CaptureRecorder().decide(message, "t-otp-sms")

        val skipped = assertIs<CaptureRecorder.Decision.Skip>(decision)
        assertTrue(skipped.bodyWasSensitive)
    }
}
