package sa.masrouf.core.capture

import org.junit.jupiter.api.Test
import sa.masrouf.core.fixtures.RealMessages
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull

/**
 * The merchant, in the messages that do not put it on a line of its own.
 *
 * 588 stored records carried no party at all - 330,211 riyals of spending filed
 * against nobody, and unfileable, since a category is learned from a merchant. In
 * almost every one the name was sitting in the body untouched. Every field pattern
 * in this app is anchored to the start of a line, and these senders end a field
 * with something else: a carriage return that normalisation was folding into a
 * space, the two literal characters `^M`, a pipe, or a run of padding spaces.
 *
 * One fix upstream, in [sa.masrouf.core.text.ArabicText], plus the two labels
 * nothing had ever looked for. These are the four families, measured on the real
 * corpus at 337 records recovered.
 */
class UnlabelledPartyTest {

    private fun party(profile: BankProfile, body: String): String? {
        val result = BankMessageParser(profile).parse(RawMessage(body, Instant.EPOCH))
        return (result as? ParseResult.Parsed)?.draft?.merchantRaw
    }

    /** 68 records. The carriage return was in the space-like set. */
    @Test
    fun `a sender that ends every field with a carriage return`() {
        assertEquals(
            "Aldrees 1437",
            party(SaudiBanks.EMIRATES_NBD, RealMessages.ENBD_ATHIR_PURCHASE),
        )
    }

    /**
     * The same message as it actually reaches storage on the owner's phone, with
     * the carriage return already written out as two ordinary characters.
     */
    @Test
    fun `and the same sender with the carriage return spelt out as caret M`() {
        assertEquals(
            "Aldrees 1437",
            party(SaudiBanks.EMIRATES_NBD, RealMessages.ENBD_ATHIR_PURCHASE_CARET),
        )
    }

    /** 50 records. Nothing had ever looked for this label. */
    @Test
    fun `mada Pay names the merchant outright, on a line of pipes`() {
        assertEquals(
            "FOWL AL TAKHSSY",
            party(SaudiBanks.SNB, RealMessages.SNB_MADA_PAY_PIPES),
        )
    }

    /**
     * The guard that refuses a value beginning with digits is what keeps an account
     * number out of the party field, and 2,014 records paid for it. So the terminal
     * id is skipped rather than the guard loosened.
     */
    @Test
    fun `a terminal id in front of the merchant does not hide it`() {
        assertEquals(
            "CENTERPOINT -DOM",
            party(SaudiBanks.SNB, RealMessages.SNB_POS_TERMINAL_ID),
        )
    }

    /** 335 records, 255,631 riyals. One line, no label, columns padded with spaces. */
    @Test
    fun `the flat template names its merchant between the card and the date`() {
        assertEquals("EXTRA", party(SaudiBanks.AL_RAJHI, RealMessages.RAJHI_FLAT_POS))
    }

    /**
     * The padding is a field boundary, not part of the name, and only in that
     * template. An ordinary double space inside a merchant's name stays inside it.
     */
    @Test
    fun `a short run of spaces is part of the name, not a boundary`() {
        val body = "شراء إنترنت\nمبلغ 12.99 SAR\nلدى APPLE  COM BILL\nبطاقة **2166"

        assertEquals("APPLE COM BILL", party(SaudiBanks.SNB, body))
    }

    /**
     * The reason this matters at all: a party is what a category is learned from,
     * and every one of these records was unfileable without it.
     */
    @Test
    fun `every recovered family yields a party rather than nothing`() {
        val samples = listOf(
            SaudiBanks.EMIRATES_NBD to RealMessages.ENBD_ATHIR_PURCHASE,
            SaudiBanks.EMIRATES_NBD to RealMessages.ENBD_ATHIR_PURCHASE_CARET,
            SaudiBanks.SNB to RealMessages.SNB_MADA_PAY_PIPES,
            SaudiBanks.SNB to RealMessages.SNB_POS_TERMINAL_ID,
            SaudiBanks.AL_RAJHI to RealMessages.RAJHI_FLAT_POS,
        )

        for ((profile, body) in samples) {
            assertNotNull(party(profile, body), "no party read from a ${profile.id} message")
        }
    }

    /**
     * The card is not the shop.
     *
     * SNB writes two `من` lines on its 2017-2018 point-of-sale message - the card
     * it was paid with, then the shop - and the party pattern took whichever came
     * first. 179 purchases were stored with a card where a merchant belonged: not
     * fileable, since a category is learned from a merchant, and swept into
     * transfers by the fallback.
     */
    @Test
    fun `a card on the line above the shop is not the party`() {
        assertEquals(
            "دانكن دوناتس",
            party(SaudiBanks.SNB, RealMessages.SNB_POS_CARD_THEN_SHOP),
        )
    }

    /**
     * A body that names no shop must produce no party, not the nearest text.
     *
     * The fix above made the pattern walk past the card, and a walk that keeps
     * going finds something: on this template it found the bank's own link, and
     * seventeen purchases were filed as bills by a URL.
     */
    @Test
    fun `a footer link is not the party`() {
        assertNull(party(SaudiBanks.SNB, RealMessages.SNB_MADA_PAY_NO_SHOP))
    }

    /** An empty merchant field takes the line below it, and a date is never a shop. */
    @Test
    fun `a date is not the party`() {
        assertNull(party(SaudiBanks.BARQ, RealMessages.BARQ_REFUND_EMPTY_MERCHANT))
    }
}
