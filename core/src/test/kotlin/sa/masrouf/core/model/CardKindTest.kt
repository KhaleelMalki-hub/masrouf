package sa.masrouf.core.model

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** What a bank message says about the kind of card it names. */
class CardKindTest {

    @Test
    fun `a mada card is read from either spelling`() {
        assertEquals(CardKind.MADA, CardKinds.of("سحب نقدي بالريال - صراف الأهلي | بطاقة مدى *2907"))
        assertEquals(CardKind.MADA, CardKinds.of("By:3761;mada"))
    }

    /** Both spellings of the hamza arrive, and folding is what makes one rule reach both. */
    @Test
    fun `a credit card is read through both spellings of the hamza`() {
        assertEquals(CardKind.CREDIT, CardKinds.of("بطاقة ائتمانية ***2887"))
        assertEquals(CardKind.CREDIT, CardKinds.of("بطاقة إئتمانية **3396"))
        assertEquals(CardKind.CREDIT, CardKinds.of("Credit Card Payment"))
    }

    /**
     * The network says how the money travelled, not whether the card borrows. The
     * owner's 7536 is a MasterCard drawn on his account.
     */
    @Test
    fun `the card network is not evidence of credit`() {
        assertNull(CardKinds.of("عبر:فيزا;8134"))
        assertNull(CardKinds.of("شراء VISA / من:*7667"))
    }

    /** A settlement names the card being paid and the card paying it. */
    @Test
    fun `a body naming both kinds says nothing about either`() {
        assertNull(CardKinds.of("سداد بطاقة ائتمانية من بطاقة مدى"))
    }

    @Test
    fun `the verdict follows the majority of the card's own messages`() {
        val bodies = listOf("بطاقة مدى *2907", "بطاقة مدى *2907", "بطاقة ائتمانية ***2907")

        assertEquals(CardKind.MADA, CardKinds.verdict(bodies))
    }

    @Test
    fun `a card its messages never describe is left unlabelled`() {
        assertNull(CardKinds.verdict(listOf("شراء إنترنت", null)))
        assertNull(CardKinds.verdict(emptyList()))
    }

    /** A tie is not a decision. */
    @Test
    fun `an even split says nothing`() {
        assertNull(CardKinds.verdict(listOf("بطاقة مدى *1", "بطاقة ائتمانية *1")))
    }
}
