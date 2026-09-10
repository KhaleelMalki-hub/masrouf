package sa.masrouf.core.model

import org.junit.jupiter.api.Test
import sa.masrouf.core.capture.BankMessageParser
import sa.masrouf.core.capture.ParseResult
import sa.masrouf.core.capture.RawMessage
import sa.masrouf.core.capture.SaudiBanks
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotEquals
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
            // الخزائن المبتكرة, fitted cabinets.
            "Maan Hama" to SaudiCategories.SHOPPING,
            // A perfume shop.
            "LAURE" to SaudiCategories.SHOPPING,
            // Named on 2026-09-01, off the list left unfiled after the party fix.
            // المطلق للأثاث والمفروشات - furniture, and a family name besides.
            "ALMUTLAQ" to SaudiCategories.SHOPPING,
            // Named on 2026-09-02, off the filing worksheet.
            "LA CALLE" to SaudiCategories.FOOD,
            "KARAZ LIN" to SaudiCategories.SHOPPING,
            "QUTOUF AND HALA MAKKAH" to SaudiCategories.GROCERIES,
            "QOTOF AND HALA" to SaudiCategories.GROCERIES,
            "QUTOOF HALA EST" to SaudiCategories.GROCERIES,
            "AJWAD AL KARAM CO MAKKAH" to SaudiCategories.GROCERIES,
            "AJWAD ALKRM COM" to SaudiCategories.GROCERIES,
            "ABDULMOHSEN AL HOKAIR" to SaudiCategories.ENTERTAINMENT,
            "ABDULMOHS" to SaudiCategories.ENTERTAINMENT,
            "ATLOBHA" to SaudiCategories.TRANSPORT,
            "MYAZU" to SaudiCategories.FOOD,
            "DAR ZIED" to SaudiCategories.FOOD,
            "AFAQEMAAR" to SaudiCategories.SHOPPING,
            "RIDAA ALMISK CO" to SaudiCategories.SHOPPING,
            "RIDAA ALM" to SaudiCategories.SHOPPING,
            // هوم بوكس. The branch number the terminal appends must not hide it.
            "HOMEBOX 2" to SaudiCategories.SHOPPING,
            // كورو, Japanese, in Jeddah. The city is glued to the name.
            "Kuuru Jed" to SaudiCategories.FOOD,
            // شانيل in Jeddah, billed under its operator's name.
            "AL NOUJAI" to SaudiCategories.SHOPPING,
            // ريفي, household goods, as the terminal sends it: the bare word.
            "Reefi" to SaudiCategories.SHOPPING,
            "reefi" to SaudiCategories.SHOPPING,
            // الرقيب للأثاث, under every spelling its terminals have sent.
            "HAMAD M ALRUGAIB AND S" to SaudiCategories.SHOPPING,
            "HAMAD ALRUGAIB and SO" to SaudiCategories.SHOPPING,
            "www.alrugaibfurni" to SaudiCategories.SHOPPING,
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
     * The guard a five-letter keyword needed and did not get.
     *
     * "REEFI" was added for a linens shop. MerchantMatch takes any keyword of four
     * characters or more as a substring, so it also matched "Al Saj Al Reefi
     * Restau" - 29 rows of a restaurant, moved to shopping by the refile pass
     * before anyone looked at them. الريفي is an ordinary Arabic word; no stem of
     * it can be safe in a list matched this way.
     *
     * Same shape, same session: "FLYIN" for flyin.com also matched Flying Tiger
     * Copenhagen, a stationery chain.
     */
    @Test
    fun `a short keyword does not swallow the merchants that contain it`() {
        assertEquals(SaudiCategories.SHOPPING, CategoryGuess.forMerchant("Reefi Store"))
        assertEquals(SaudiCategories.FOOD, CategoryGuess.forMerchant("Al Saj Al Reefi Restau"))
        assertEquals(SaudiCategories.FOOD, CategoryGuess.forMerchant("ALSAAJ ALREEFI"))

        assertEquals(SaudiCategories.TRAVEL, CategoryGuess.forMerchant("Flyin"))
        // This read `null` until 2026-09-01, when the owner named the shop
        // (reefi.me, household goods) and the bare word got a rule of its own. It
        // is reached by MerchantMatch's EXACT pass, which runs before any partial
        // one, so the restaurant two lines above - a different string entirely - is
        // unaffected. The assertions above are what proves that, and they are why
        // the rule sits last in the list.
        assertEquals(SaudiCategories.SHOPPING, CategoryGuess.forMerchant("reefi"))
        assertEquals(SaudiCategories.SHOPPING, CategoryGuess.forMerchant("FLYING TI"))
        assertEquals(SaudiCategories.SHOPPING, CategoryGuess.forMerchant("FLYING TIGER COPENHAGE"))
    }

    /**
     * A name cut at the FRONT cannot be reached by a keyword.
     *
     * The domestic-labour recruiter arrives from one terminal as "NTERNATIO", and
     * MerchantMatch's truncation rule only forgives a missing tail. The tempting
     * fix - a keyword of "NTERNATIO" - is a substring of every "INTERNATIONAL ..."
     * in this history, and adding it took a creative agency, a regions firm and
     * Alshaya to fees. This test records the trade rather than the fix: the
     * spellings that keep their first letter are filed, the one that lost it is
     * left for the user to file once in the app.
     */
    @Test
    fun `a front-truncated name is left alone rather than caught by a broad keyword`() {
        assertEquals(SaudiCategories.FEES, CategoryGuess.forMerchant("INTERNATIONAL RECRUI"))

        // What the two ambiguous spellings resolve to today, asserted as it is
        // rather than as it should be. "INTERNATI" is a prefix of both the bakery
        // and the recruiter, and the bakery's rule is reached first; "NTERNATIO"
        // reaches neither. The owner has said both rows are the recruiter, and the
        // way to record that is his own filing, not a keyword.
        assertEquals(SaudiCategories.FOOD, CategoryGuess.forMerchant("Internati"))
        assertEquals(null, CategoryGuess.forMerchant("NTERNATIO"))

        // The companies a broad keyword would have taken with it.
        assertEquals(SaudiCategories.FOOD, CategoryGuess.forMerchant("INTERNATIONAL OVEN CO."))
        assertEquals(null, CategoryGuess.forMerchant("International Regions"))
    }

    /**
     * The rule that pays for the bare "REEFI" being safe, and the one that would
     * go red first if it were moved up the list. الساج الريفي is a restaurant whose
     * name contains the shop's whole name; 29 meals were filed as shopping the last
     * time a keyword reached into it.
     */
    @Test
    fun `the household shop does not swallow the restaurant that shares its name`() {
        assertEquals(
            SaudiCategories.FOOD,
            CategoryGuess.forMerchant("Al Saj Al Reefi Restau"),
        )
        assertEquals(SaudiCategories.FOOD, CategoryGuess.forMerchant("ALSAAJ ALREEFI"))
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

    /**
     * The shop the SMS could never name, reached through the truncation it made.
     *
     * Al Rajhi cuts the merchant to nine characters, so the stored name is
     * "AL MUASHA" and no search of that string finds anything. His card statement
     * carries the untruncated "AL MUASHAH TRADINJ C", which reaches a furniture
     * company in Jeddah, and he confirmed it. The keyword is the LONGER form:
     * MerchantMatch accepts a truncation when the keyword starts with the stored
     * name, so the rule has to be at least as long as the shop's real name and the
     * stored fragment reaches it, not the other way round.
     */
    @Test
    fun `the truncated furniture shop reaches its keyword`() {
        assertEquals(SaudiCategories.SHOPPING, CategoryGuess.forMerchant("AL MUASHA"))
    }

    /**
     * And the keyword is long enough not to reach anything else. Written because a
     * four-letter keyword took three unrelated companies once before.
     */
    @Test
    fun `the furniture keyword does not reach an unrelated name`() {
        assertNotEquals(SaudiCategories.SHOPPING, CategoryGuess.forMerchant("AL MUSBAH"))
        assertNotEquals(SaudiCategories.SHOPPING, CategoryGuess.forMerchant("ALMU"))
    }

    // ---- Named by the card statement, 2026-09-10 ---------------------------

    /**
     * Each of these is stored under the nine characters the SMS allowed, and the
     * keyword is the full name the statement gave. The assertion is on the STORED
     * form, because that is the string the app will actually be asked about.
     */
    @Test
    fun `the shops the statement named reach their categories`() {
        val expected = mapOf(
            // the owner corrected this one: a tyre and car-service shop, not a
            // government-transactions office as the search had it
            "AL ENJAZ A" to SaudiCategories.TRANSPORT,
            "SALT U WA" to SaudiCategories.FOOD,
            "THE BLAK" to SaudiCategories.FOOD,
            "Address C" to SaudiCategories.FOOD,
            "Miraqe Ga" to SaudiCategories.FOOD,
            "MYSR*Amma" to SaudiCategories.FOOD,
            "American" to SaudiCategories.SHOPPING,
            "SAIF EL D" to SaudiCategories.SHOPPING,
            "NEWMAX" to SaudiCategories.SHOPPING,
            "Binat-alh" to SaudiCategories.SHOPPING,
            "MYSR*Easi" to SaudiCategories.SHOPPING,
            "ALNABEA A" to SaudiCategories.SHOPPING,
            "Arwan for" to SaudiCategories.SHOPPING,
            "BILLY BEE" to SaudiCategories.ENTERTAINMENT,
            "Future Pa" to SaudiCategories.ENTERTAINMENT,
            "COMPANY A" to SaudiCategories.ENTERTAINMENT,
            "Ban Holdi" to SaudiCategories.ENTERTAINMENT,
            "WOQOOF CO" to SaudiCategories.TRANSPORT,
            "Safari Te" to SaudiCategories.BILLS,
            "ARABIAN G" to SaudiCategories.GROCERIES,
            "MECCA COM" to SaudiCategories.HEALTH,
            "ALESAYI H" to SaudiCategories.TRAVEL,
            // the deep-search three: two placed by the history around them, one
            // by the owner's memory of renting a car that week
            "Fadaa Ali" to SaudiCategories.SHOPPING,
            "FAWASEL A" to SaudiCategories.ENTERTAINMENT,
            "DURRAH AL" to SaudiCategories.TRANSPORT,
            // the second deep pass
            "Magma Fyo" to SaudiCategories.HEALTH,
            "LABA LAMA" to SaudiCategories.SERVICES,
            "NMC2075" to SaudiCategories.HEALTH,
            "NMC2059" to SaudiCategories.HEALTH,
            "NMC8121" to SaudiCategories.HEALTH,
            "N2-sa" to SaudiCategories.ENTERTAINMENT,
            "Think Con" to SaudiCategories.SHOPPING,
        )

        val wrong = expected.mapNotNull { (stored, want) ->
            val got = CategoryGuess.forMerchant(stored)
            if (got == want) null else "$stored: wanted ${want.id}, got ${got?.id}"
        }

        assertEquals(emptyList(), wrong)
    }

    /**
     * And none of them reaches a shop it has no business filing.
     *
     * "COMPANY A" is the one to watch: it is a nine-character truncation of a
     * generic prefix, and it is only safe because the keyword behind it is long.
     * The day a second "COMPANY A..." appears in the history, this test is where it
     * will show up.
     */
    @Test
    fun `the statement keywords do not reach a different company`() {
        assertNull(CategoryGuess.forMerchant("COMPANY ALKHALEEJ"))
        // not "AMERICAN GARAGE": a shipped "GARAGE" rule files that as transport, and
        // correctly - the control has to be a name no OTHER rule claims either.
        assertNull(CategoryGuess.forMerchant("AMERICAN WIDGET CO"))
        assertNull(CategoryGuess.forMerchant("SALT LAKE SUPPLIES"))
        // "N2" is two characters, so it must match a whole word and never a
        // fragment. These are names that CONTAIN n2 without it being a word, and
        // they are the reason the length check in MerchantMatch is load-bearing.
        //
        // Both halves of each control were probed against the whole rule list
        // first, which is the point of the lesson of 2026-09-10: "STATION2 GRILL"
        // and "CARBON2 STORE" were the obvious choices and both are claimed - by
        // the STATION rule and by STORE - so they would have failed here while
        // saying nothing at all about "N2".
        assertNull(CategoryGuess.forMerchant("CARBON2 QLTX"))
        assertNull(CategoryGuess.forMerchant("ZEPHYR2 QLTX"))
    }
}
