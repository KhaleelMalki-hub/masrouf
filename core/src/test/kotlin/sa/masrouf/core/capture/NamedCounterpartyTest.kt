package sa.masrouf.core.capture

import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals

/**
 * The person, not the account they used.
 *
 * 2,014 records carried an account number as their merchant - "104*010", "3016",
 * "106*011" - while the party that actually sent or received the money was two
 * lines away in the same message under مرسل or مستفيد. Nothing can be filed
 * against a number, so all 2,014 sat unfiled or under a category derived from
 * their type alone, and the history showed the user their own account number where
 * a name belonged.
 *
 * Personal names are placeholders, as everywhere else in these tests: what is
 * being asserted is which line the parser reads, not who was on it.
 */
class NamedCounterpartyTest {

    private fun partyOf(body: String): String? =
        (BankMessageParser(SaudiBanks.SNB).parse(RawMessage(body, Instant.EPOCH))
            as? ParseResult.Parsed)?.draft?.merchantRaw

    @Test
    fun `an incoming transfer names its sender, not the account`() {
        val body = "حوالة واردة داخلية\nمبلغ:3400 SAR\nمرسل:SENDER NAME\nمن:106*011\nفي:03/12/23 11:49"

        assertEquals("SENDER NAME", partyOf(body))
    }

    @Test
    fun `an outgoing transfer names its beneficiary, not the account`() {
        val body = "حوالة صادرة داخلية\nمبلغ 600 SAR\nمن 104*010\nمستفيد BENEFICIARY NAME\nإلى 508*111\nفي 26/08/23 22:26"

        assertEquals("BENEFICIARY NAME", partyOf(body))
    }

    /** The employer's transfers, which is where this was noticed. */
    @Test
    fun `the sender is read from مرسل من as well as from مرسل`() {
        val body = "حوالة واردة محلية\nمبلغ SAR 26899.03\nمرسل من امانة العاصمة المقدسة\nمن *\nإلى 0104*\nعبر البنك المركزي السعودي"

        assertEquals("امانة العاصمة المقدسة", partyOf(body))
    }

    /**
     * Every profile, not just the one whose message this is.
     *
     * reparseStoredBodies tries all of them and keeps whichever reads the most, so
     * one unguarded pattern anywhere claims every bank's bodies. D360's two lines
     * and Emirates NBD's were left without the guard SNB got, and 710 rows came
     * back from the repair pass still carrying an account number as their party -
     * with the real name one line below it.
     */
    @Test
    fun `no profile reads an account line as the party`() {
        val body = "حوالة محلية واردة\nعبر:ANB\nمبلغ:SAR 1000\nالى:3016\nمن:SENDER NAME\nمن:0018"

        for (profile in SaudiBanks.ALL) {
            val party = (BankMessageParser(profile).parse(RawMessage(body, Instant.EPOCH))
                as? ParseResult.Parsed)?.draft?.merchantRaw
            assertEquals(
                null,
                party?.takeIf { it.any(Char::isDigit) },
                "${profile.id} read an account as the party: $party",
            )
        }
    }

    /**
     * The guard. An account line must never become the party, whichever word
     * introduces it - which is what the negative lookaheads are for.
     */
    @Test
    fun `an account number is never the party`() {
        val body = "سداد فاتورة\nمبلغ 500 SAR\nمن 104*010\nمفوتر 123\nفاتورة 01250603000666"

        val party = partyOf(body)

        assertEquals(null, party?.takeIf { it.any(Char::isDigit) }, "an account became the party: $party")
    }
}
