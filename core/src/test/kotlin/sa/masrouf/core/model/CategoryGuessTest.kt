package sa.masrouf.core.model

import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.Test

class CategoryGuessTest {

    @Test
    fun `merchants seen in this user's own messages are recognised`() {
        // Every one of these appears in a captured message, not in an invented list.
        assertEquals(SaudiCategories.GROCERIES, CategoryGuess.forMerchant("TAMIMI MARKETS"))
        assertEquals(SaudiCategories.SHOPPING, CategoryGuess.forMerchant("IHERB ARA"))
        assertEquals(SaudiCategories.SHOPPING, CategoryGuess.forMerchant("Amazon Now"))
        assertEquals(SaudiCategories.HEALTH, CategoryGuess.forMerchant("ASIAN POLYCLINI"))
        assertEquals(SaudiCategories.BILLS, CategoryGuess.forMerchant("Google YouTubePremium"))
    }

    @Test
    fun `the same merchant matches through the padding card networks add`() {
        // A notification and a statement row name one merchant differently. The
        // folding that makes deduplication work has to make this work too.
        assertEquals(
            SaudiCategories.GROCERIES,
            CategoryGuess.forMerchant("TAMIMI MARKETS RIYADH SA 1234"),
        )
    }

    @Test
    fun `an arabic merchant name matches an arabic rule`() {
        assertEquals(SaudiCategories.HEALTH, CategoryGuess.forMerchant("صيدلية النهدي"))
        assertEquals(SaudiCategories.FOOD, CategoryGuess.forMerchant("مطعم البيك"))
    }

    @Test
    fun `an unknown merchant stays unfiled rather than becoming other`() {
        // "I have not decided" and "I decided it was other" must stay
        // distinguishable, or the strip cannot show what is still unexamined.
        assertNull(CategoryGuess.forMerchant("SOME SHOP NOBODY LISTED"))
        assertNull(CategoryGuess.forMerchant(null))
        assertNull(CategoryGuess.forMerchant("   "))
    }

    @Test
    fun `a type decides when the merchant cannot`() {
        assertEquals(SaudiCategories.BILLS, CategoryGuess.suggest(null, TransactionType.BILL_PAYMENT))
        assertEquals(
            SaudiCategories.TRANSFERS,
            CategoryGuess.suggest(null, TransactionType.TRANSFER_OUT),
        )
    }

    @Test
    fun `the merchant outranks the type`() {
        // A bill payment to a named pharmacy is health, not "bills" - the merchant
        // is the more specific evidence and it wins.
        assertEquals(
            SaudiCategories.HEALTH,
            CategoryGuess.suggest("NAHDI PHARMACY", TransactionType.BILL_PAYMENT),
        )
    }

    @Test
    fun `an ordinary purchase with an unknown merchant suggests nothing`() {
        assertNull(CategoryGuess.suggest("UNLISTED TRADER", TransactionType.PURCHASE))
    }
}
