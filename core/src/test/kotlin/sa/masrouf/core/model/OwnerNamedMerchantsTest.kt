package sa.masrouf.core.model

import org.junit.jupiter.api.Test
import sa.masrouf.core.capture.BankMessageParser
import sa.masrouf.core.capture.ParseResult
import sa.masrouf.core.capture.RawMessage
import sa.masrouf.core.capture.SaudiBanks
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Shops only the owner can name.
 *
 * Nothing in "Time-race" says car parts and nothing in "ONTIME PL" says watches;
 * no shipped list of merchants could hold either. Each of these was sitting unfiled
 * until the owner said what it was, and each is asserted here against the exact
 * string the card network sent - truncation, gateway prefix and all - because that
 * string is what the rule has to match, not the shop's real name.
 */
class OwnerNamedMerchantsTest {

    @Test
    fun `the merchants the owner named are filed`() {
        val named = mapOf(
            // Car parts, Haval in particular. Both the shop and its payment gateway.
            "Time-race" to SaudiCategories.TRANSPORT,
            "tap*Time" to SaudiCategories.TRANSPORT,
            // Bathrobes, towels, pillows, a mattress. Arrives truncated too.
            "Reefi Store" to SaudiCategories.SHOPPING,
            "reefi" to SaudiCategories.SHOPPING,
            // A watch shop. "ONTIME PL" reads as a delivery service and is not one.
            "ONTIME PL" to SaudiCategories.SHOPPING,
            // Found unfiled beside the tyre shop.
            "AUTOMOTIVE DISTRIBUTION" to SaudiCategories.TRANSPORT,
            "SAUDI AUTOMOTIVE SERVI" to SaudiCategories.TRANSPORT,
            // Approved by the owner in the same pass.
            "TORY BURC" to SaudiCategories.SHOPPING,
            "ATHLOCITY" to SaudiCategories.SHOPPING,
            "TAILOR SH" to SaudiCategories.SHOPPING,
            "Bcare" to SaudiCategories.HEALTH,
            "KHALEEL MALKI" to SaudiCategories.TRANSFERS,
        )

        for ((merchant, expected) in named) {
            assertEquals(expected, CategoryGuess.forMerchant(merchant), "wrong category for $merchant")
        }
    }

    /**
     * Every airline and every hotel in twelve years of history, by the two stems
     * that carry them. 51,289 riyals of these were filed as transport, beside the
     * petrol, which is what a category of their own is for.
     */
    @Test
    fun `flights and hotels are travel`() {
        val travel = listOf(
            "Saudia Airlines", "Saudia Airlines - MOTO", "SAUDI AIRLINES",
            "SAUDI ARABIAN AIRLINES", "ETHIOPIAN AIRLINES",
            "FLYNAS CO", "FlyNas", "COM FLYAKEED", "COM FLYAK",
            "Hotel at B", "THE RUMA HOTEL-FO", "ELAF HOTEL", "Three bees hotel co",
            "AL ARABI PALACE HOTEL", "SUNWAY RESORT HOTEL-FO", "Hotel on Booking.com",
        )

        for (merchant in travel) {
            assertEquals(SaudiCategories.TRAVEL, CategoryGuess.forMerchant(merchant), merchant)
        }
    }

    /**
     * The stems are short, so this is the guard that keeps them honest. A petrol
     * station and a car workshop are transport and must not follow the flights.
     */
    @Test
    fun `driving is still transport`() {
        val transport = listOf("Fourth frame EST", "MS.21535", "TIRE SERV", "Time-race")

        for (merchant in transport) {
            assertEquals(SaudiCategories.TRANSPORT, CategoryGuess.forMerchant(merchant), merchant)
        }
    }

    /**
     * Tiqmo is a wallet the owner topped up, not a shop he bought from. The money
     * is still his until he spends it from there, so the top-up is not spending -
     * the same treatment barq already had, reached the same way.
     */
    @Test
    fun `a top-up of the owner's own wallet is not spending`() {
        val body = "شراء انترنت\nبـ1250 SAR\nمن 0104*\nمن Tiqmo\nمدى*1887\nفي 12/03/25 23:53"

        val draft = (BankMessageParser(SaudiBanks.SNB).parse(RawMessage(body, Instant.EPOCH))
            as ParseResult.Parsed).draft

        assertEquals(TransactionType.OWN_TRANSFER, draft.type)
        assertFalse(draft.type.countsAsSpending)
    }

    /** A real shop must not be swept up by the wallet list. */
    @Test
    fun `an ordinary online purchase is still a purchase`() {
        val body = "شراء انترنت\nبـ35 SAR\nمن 0104*\nمن NINJA RETAIL\nمدى*1887\nفي 12/03/25 23:53"

        val draft = (BankMessageParser(SaudiBanks.SNB).parse(RawMessage(body, Instant.EPOCH))
            as ParseResult.Parsed).draft

        assertEquals(TransactionType.PURCHASE, draft.type)
    }
}
