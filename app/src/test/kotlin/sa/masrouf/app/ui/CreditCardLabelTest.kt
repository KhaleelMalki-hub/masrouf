package sa.masrouf.app.ui

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A credit card's remaining limit is not money the user has.
 *
 * `BalanceReader` decides from the keyword, which is all one message gives it, and
 * AlRajhi writes `رصيد` for a credit card exactly as it does for a current account.
 * So the tile for card 2383 said "الرصيد 31,837.17" while the card carried a 38,500
 * limit - it told its owner he held 31,837 riyals that do not exist, which
 * `BalanceReader`'s own documentation calls the worst thing an expense app can say.
 *
 * The fix records what a card *is* per card rather than per message. These tests
 * guard the two halves of that: the list is right, and it is corroborated.
 */
class CreditCardLabelTest {

    @Test
    fun `the cards the owner named as credit cards carry a limit`() {
        for (last4 in listOf("2383", "8134", "9994")) {
            assertNotNull(CreditCards.LIMIT_HALALAS[last4], "no limit recorded for $last4")
        }
    }

    /**
     * The highest balance a card's messages ever reported, from the owner's own
     * history. A remaining limit can approach the ceiling but never pass it, so a
     * limit below one of these figures would be a typo - and a typo here understates
     * the ceiling and overstates what has been spent.
     */
    @Test
    fun `each limit is at least the highest figure that card ever reported`() {
        val highestEverSeen = mapOf(
            "2383" to 37_754_59L,
            "8134" to 41_010_00L,
            "9994" to 97_000_00L,
        )

        for ((last4, seen) in highestEverSeen) {
            val limit = CreditCards.LIMIT_HALALAS.getValue(last4)
            assertTrue(
                limit >= seen - TOLERANCE_HALALAS,
                "card $last4 has limit $limit but once reported $seen",
            )
        }
    }

    /**
     * A debit card must not acquire a limit by accident: labelling a real account
     * balance "المتبقي من الحد" is the same lie in the other direction.
     */
    @Test
    fun `the mada debit cards have no limit`() {
        assertNull(CreditCards.LIMIT_HALALAS["5763"])
        assertNull(CreditCards.LIMIT_HALALAS["8202"])
    }

    /**
     * A card the app knows the bank or the limit of, but does not list as open, is
     * a card whose tile never appears - the knowledge is recorded and then wasted.
     * That was true of 1887 and 9994 until the owner confirmed both.
     */
    @Test
    fun `every card the app knows something about is one the owner says is open`() {
        val named = CreditCards.LIMIT_HALALAS.keys + CardIssuers.BANK_ID.keys

        assertEquals(emptySet(), named - ActiveCards.LAST4)
    }

    @Test
    fun `no card is given an issuer the app cannot label`() {
        val labelled = setOf("alrajhi", "snb", "barq", "d360", "enbd")

        assertEquals(emptySet(), CardIssuers.BANK_ID.values.toSet() - labelled)
    }

    private companion object {
        /** A statement's closing figure can round a halala against a stated limit. */
        const val TOLERANCE_HALALAS = 100_00L
    }
}
