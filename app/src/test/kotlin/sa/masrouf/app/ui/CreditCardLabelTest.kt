package sa.masrouf.app.ui

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A credit card's remaining limit is not money the user has.
 *
 * `BalanceReader` decides from the keyword, which is all one message gives it, and
 * AlRajhi writes `رصيد` for a credit card exactly as it does for a current account.
 * So the tile put a credit card's remaining allowance under "الرصيد" - money its
 * owner does not have, which `BalanceReader`'s own documentation calls the worst
 * thing an expense app can say.
 *
 * Being a credit card is a property of the card, not of whichever message arrived
 * last, so it is recorded per card. The figures are the owner's own and live in
 * `local.properties`, out of this public repository; what is asserted here is the
 * PARSER that reads them, against values invented for the test.
 */
class CreditCardLabelTest {

    @Test
    fun `a well-formed spec is read`() {
        CreditCards.configure("1111:1230000 ; 2222:4560000")

        assertEquals(1_230_000L, CreditCards.limitHalalas["1111"])
        assertEquals(4_560_000L, CreditCards.limitHalalas["2222"])
    }

    /**
     * The default, and the state on anyone else's clone. A card with no limit shows
     * its remaining figure without a ceiling - less information, nothing invented.
     */
    @Test
    fun `an absent spec leaves every card without a limit`() {
        CreditCards.configure("")

        assertTrue(CreditCards.limitHalalas.isEmpty())
    }

    /**
     * A mistyped limit is dropped, never guessed at. Understating a ceiling would
     * overstate what has been spent against it, which is the direction that misleads.
     */
    @Test
    fun `a malformed entry is dropped rather than repaired`() {
        CreditCards.configure("1111:1230000 ; 333:100 ; 4444:notanumber ; 5555: ; 6666:0 ; 7777:-5 ; junk")

        assertEquals(mapOf("1111" to 1_230_000L), CreditCards.limitHalalas)
    }

    @Test
    fun `a card that is not four digits is not a card`() {
        CreditCards.configure("12a4:100 ; 12345:100 ; 1234:100")

        assertEquals(setOf("1234"), CreditCards.limitHalalas.keys)
    }

    /**
     * The tile treats a card as credit when it has a limit OR when its last message
     * called the figure a spending limit. This asserts the first half, which is the
     * half the owner's own configuration drives.
     */
    @Test
    fun `a card with no configured limit is not treated as a credit card by that route`() {
        CreditCards.configure("1111:1230000")

        assertNull(CreditCards.limitHalalas["9999"])
    }

    @Test
    fun `no card is given an issuer the app cannot label`() {
        val labelled = setOf("alrajhi", "snb", "barq", "d360", "enbd")

        assertEquals(emptySet(), CardIssuers.BANK_ID.values.toSet() - labelled)
    }

    /**
     * A card the app knows the bank of, but does not list as open, is a card whose
     * tile never appears - the knowledge is recorded and then wasted.
     */
    @Test
    fun `every card with a known issuer is one the owner says is open`() {
        assertEquals(emptySet(), CardIssuers.BANK_ID.keys - ActiveCards.LAST4)
    }
}
