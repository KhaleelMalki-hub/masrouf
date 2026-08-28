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

    /**
     * The type rules, which are what covers a merchant-less record.
     *
     * 9,301 records in a real 22,084-record history carry no merchant name at all:
     * transfers to a person, machine withdrawals, salary. No list of shops however
     * long can reach any of them, so these five mappings are worth more coverage
     * than every merchant rule in the file put together.
     */
    @Test
    fun `a movement with no merchant is filed by what kind of movement it is`() {
        assertEquals(SaudiCategories.TRANSFERS, CategoryGuess.suggest(null, TransactionType.TRANSFER_OUT))
        assertEquals(SaudiCategories.TRANSFERS, CategoryGuess.suggest(null, TransactionType.TRANSFER_IN))
        assertEquals(SaudiCategories.TRANSFERS, CategoryGuess.suggest(null, TransactionType.OWN_TRANSFER))
        assertEquals(SaudiCategories.CASH, CategoryGuess.suggest(null, TransactionType.ATM_WITHDRAWAL))
        assertEquals(SaudiCategories.CASH, CategoryGuess.suggest(null, TransactionType.ATM_DEPOSIT))
        assertEquals(SaudiCategories.INCOME, CategoryGuess.suggest(null, TransactionType.SALARY))
        assertEquals(SaudiCategories.BILLS, CategoryGuess.suggest(null, TransactionType.BILL_PAYMENT))
    }

    /**
     * A purchase with an unknown merchant stays unfiled, on purpose.
     *
     * The type says money left by card and nothing else. Filing it as "other" would
     * make a guess look like a decision, and would remove it from the worklist the
     * user works through to file the merchants only they can name.
     */
    @Test
    fun `an unrecognised purchase is left for the user`() {
        assertNull(CategoryGuess.suggest("SOME LOCAL SHOP EST", TransactionType.PURCHASE))
        assertNull(CategoryGuess.suggest(null, TransactionType.PURCHASE))
        assertNull(CategoryGuess.suggest(null, TransactionType.REFUND))
    }
}
