package sa.masrouf.core.capture

import kotlin.test.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * The card fragment, read out of the shapes a real history actually contains.
 *
 * Found by re-parsing 14,379 stored bodies whose card had never been read. The
 * digits were there in most of them; the word between "بطاقة" and the stars was
 * what the old pattern could not cross.
 */
class CardFragmentTest {

    private fun last4(profile: BankProfile, body: String): String? {
        val result = BankMessageParser(profile).parse(RawMessage(body, Instant.EPOCH))
        return (result as? ParseResult.Parsed)?.draft?.accountLast4
    }

    @Test
    fun `a credit card with its kind between the word and the digits`() {
        val body = "مدفوعات بطاقة ائتمانية\nبطاقة ائتمانية ***2887\nمبلغ SAR 500.00\nفي 12/14"
        assertEquals("2887", last4(SaudiBanks.SNB, body))
    }

    @Test
    fun `a visa with a colon`() {
        val body = "شراء إنترنت\nبطاقة فيزا: **2166\nمبلغ 12.99 SAR\nلدى APPLE COM BILL\nحساب **8982"
        assertEquals("2166", last4(SaudiBanks.SNB, body))
    }

    @Test
    fun `the hamza is spelt both ways`() {
        val body = "إسترداد مبلغ\nمبلغ SAR 1400.00\nمن بطاقة إئتمانية **3396\nحد الصرف المتبقي SAR 1875.50"
        assertEquals("3396", last4(SaudiBanks.SNB, body))
    }

    @Test
    fun `al rajhi with a colon after عبر`() {
        val body = "شراء\nعبر:5763;مدى-جوجل باي\nبـSR 1\nلـtarwah alarabyh\n26/6/2 22:04"
        assertEquals("5763", last4(SaudiBanks.AL_RAJHI, body))
    }
}
