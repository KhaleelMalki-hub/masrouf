package sa.masrouf.app.ui

import org.junit.jupiter.api.Test
import sa.masrouf.app.data.CardBalance
import kotlin.test.assertEquals

/**
 * The order the card tiles appear in.
 *
 * The row is read left to right in English and right to left in Arabic, and in
 * both the first element of this list is the one the owner sees first. He named
 * the order himself - الراجحي, then الأهلي, then D360, then برق - and before it
 * was asserted the tiles came back in whatever order the balance query produced,
 * which changed as messages arrived.
 */
class CardOrderTest {

    private fun card(last4: String, bankId: String? = null) =
        CardBalance(last4 = last4, halalas = 100L, kind = null, atMillis = 0L, bankId = bankId)

    @Test
    fun `banks come in the order the owner named them`() {
        val ordered = orderedCards(
            listOf(card("7285"), card("8202"), card("1887"), card("2383")),
        )

        assertEquals(listOf("2383", "1887", "8202", "7285"), ordered.map { it.last4 })
    }

    @Test
    fun `cards of one bank are ordered by number, so no tile moves between launches`() {
        val ordered = orderedCards(listOf(card("8134"), card("2383"), card("5763")))

        assertEquals(listOf("2383", "5763", "8134"), ordered.map { it.last4 })
    }

    /**
     * The owner's statement outranks the stamp on the message, here as everywhere:
     * 1887 is his AlAhli card, and a barq top-up naming it as the funding card
     * stamps that row `barq`. Sorting on the stamp would file it under برق.
     */
    @Test
    fun `a known card is placed by its issuer, not by the stamp on its last message`() {
        val ordered = orderedCards(listOf(card("7285", bankId = "barq"), card("1887", bankId = "barq")))

        assertEquals(listOf("1887", "7285"), ordered.map { it.last4 })
    }

    /** An issuer the app cannot name is the least interesting tile, so it is last. */
    @Test
    fun `a card of no known bank sorts after every card of a known one`() {
        val ordered = orderedCards(listOf(card("7536"), card("7285"), card("2383")))

        assertEquals(listOf("2383", "7285", "7536"), ordered.map { it.last4 })
    }

    @Test
    fun `a card the owner has not said is open is not shown at all`() {
        val ordered = orderedCards(listOf(card("7404"), card("2383")))

        assertEquals(listOf("2383"), ordered.map { it.last4 })
    }
}
