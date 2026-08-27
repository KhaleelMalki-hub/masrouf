package sa.masrouf.app.capture

import kotlin.test.assertEquals
import kotlin.test.assertIs
import org.junit.jupiter.api.Test
import sa.masrouf.core.capture.BankMessageParser
import sa.masrouf.core.capture.RawMessage
import sa.masrouf.core.capture.SaudiBanks
import sa.masrouf.core.fixtures.RealMessages
import java.time.Instant

/**
 * The notification packages actually installed on the owner's phone.
 *
 * Read from `pm list packages` on the device on 2026-08-28, not guessed. That
 * distinction is the whole point of this file: a parser claims a message by
 * matching its posting package, so an invented package name produces a capture
 * pipeline that looks healthy and silently never fires.
 *
 * When a bank app is renamed or a new one is installed, this is the file that
 * should fail.
 */
class ObservedBankPackagesTest {

    private val receivedAt = Instant.parse("2026-08-28T09:00:00Z")

    private fun from(pkg: String, body: String) =
        RawMessage(body = body, receivedAt = receivedAt, packageName = pkg)

    private fun parserIdFor(pkg: String, body: String): String? =
        SaudiBanks.ALL
            .map(::BankMessageParser)
            .firstOrNull { it.canParse(from(pkg, body)) }
            ?.id

    @Test
    fun `the installed alrajhi banking app is claimed by the alrajhi parser`() {
        assertEquals("alrajhi", parserIdFor("com.alrajhiretailapp", RealMessages.RAJHI_POS_SHORT))
    }

    @Test
    fun `both installed snb apps are claimed by the snb parser`() {
        assertEquals("snb", parserIdFor("com.snb.alahlimobile", RealMessages.SNB_ONLINE_PURCHASE))
        assertEquals("snb", parserIdFor("com.snb.neo", RealMessages.SNB_ONLINE_PURCHASE))
    }

    @Test
    fun `a captured alrajhi message from the real package becomes a transaction`() {
        // canParse matching is necessary but not sufficient - this asserts the whole
        // path, so a profile that claims the package but cannot read its messages
        // still fails here.
        val decision = CaptureRecorder().decide(
            from("com.alrajhiretailapp", RealMessages.RAJHI_POS_SHORT),
            id = "t-real-pkg",
        )

        assertIs<CaptureRecorder.Decision.Store>(decision)
    }

    @Test
    fun `no parser claims a non-bank package`() {
        assertEquals(null, parserIdFor("com.example.shopping", RealMessages.RAJHI_POS_SHORT))
    }
}
