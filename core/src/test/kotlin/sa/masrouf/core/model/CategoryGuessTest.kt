package sa.masrouf.core.model

import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
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

    /**
     * Short keywords, as substrings, filed real merchants under categories nothing
     * about them suggested. Every name here is from the user's own history.
     */
    @Test
    fun `a short keyword does not match inside a longer word`() {
        // "HM", for H&M.
        assertNull(CategoryGuess.forMerchant("TAHA AHMED"))
        assertNull(CategoryGuess.forMerchant("TAREQ MOHAMMED AHMED A"))
        // "SEC", for the electricity company. Asserted as "not a utility bill"
        // rather than "unfiled": both of these have since earned rules of their
        // own, and what must never come back is the electricity company reaching
        // into their names.
        assertNotEquals(SaudiCategories.BILLS, CategoryGuess.forMerchant("CHEESECAKE FACTORY"))
        assertNotEquals(SaudiCategories.BILLS, CategoryGuess.forMerchant("WOMEN SECRET"))
        // "DR", for a doctor. FIRST DROP CAFE is not here because it is genuinely
        // food by its own word, which is the answer either way and proves nothing.
        assertNull(CategoryGuess.forMerchant("Dropelmagara"))
        // "LAB", for a laboratory.
        assertNull(CategoryGuess.forMerchant("LABEYLAA"))
    }

    @Test
    fun `a short keyword still matches the word it was written for`() {
        assertEquals(SaudiCategories.SHOPPING, CategoryGuess.forMerchant("H&M"))
        assertEquals(SaudiCategories.HEALTH, CategoryGuess.forMerchant("DR AHMED CLINIC"))
        assertEquals(SaudiCategories.FOOD, CategoryGuess.forMerchant("KFC Al Zahra"))
        assertEquals(SaudiCategories.SHOPPING, CategoryGuess.forMerchant("SPL"))
    }

    /**
     * Arabic attaches its definite article, so the article is stripped from the
     * merchant's words before comparing. Without that, a keyword would have to be
     * listed twice, once with ال and once without, and the two copies would drift.
     */
    @Test
    fun `the arabic definite article does not hide a word`() {
        val medical = "\u0637\u0628\u064A"          // طبي
        val theMedicalCentre = "\u0627\u0644\u0645\u0631\u0643\u0632 \u0627\u0644\u0637\u0628\u064A"
        val parking = "\u0645\u0648\u0642\u0641 \u0633\u064A\u0627\u0631\u0627\u062A"   // موقف سيارات

        assertEquals(SaudiCategories.HEALTH, CategoryGuess.forMerchant(medical))
        assertEquals(SaudiCategories.HEALTH, CategoryGuess.forMerchant(theMedicalCentre))
        // وقف is an endowment; موقف is a car park, and merely contains those letters.
        assertNull(CategoryGuess.forMerchant(parking))
    }

    /** Named by the user off their own history; neither is a purchase. */
    @Test
    fun `a wallet and a cash transfer are transfers`() {
        assertEquals(SaudiCategories.TRANSFERS, CategoryGuess.forMerchant("D360"))
        assertEquals(SaudiCategories.TRANSFERS, CategoryGuess.forMerchant("CASH TRANSFER"))
    }

    /**
     * One delivery app, five spellings, one category.
     *
     * The card network truncates at a different point each time, so the keyword
     * the rule was written for is not always present: "www.anani" carries no
     * "NINJA" at all.
     */
    @Test
    fun `every spelling of the delivery app files the same way`() {
        listOf("www.anani", "ANANINJA", "ANA NINJA", "WWW ANANINJA COM", "NINJA FOOD COMPANY")
            .forEach { spelling ->
                assertEquals(
                    SaudiCategories.GROCERIES,
                    CategoryGuess.forMerchant(spelling),
                    spelling,
                )
            }
    }

    /**
     * Two shops whose names begin the same way, filed apart.
     *
     * A rule keyed on "AL NOOR" would have filed a laundry as healthcare. The
     * keyword carries the letter the card network cuts at, which is the only thing
     * in the message that distinguishes them.
     */
    @Test
    fun `al noor the pharmacy is not al noor the laundry`() {
        assertEquals(SaudiCategories.HEALTH, CategoryGuess.forMerchant("Al Noor T"))
        assertNotEquals(SaudiCategories.HEALTH, CategoryGuess.forMerchant("Noor AlMa"))
    }

    /**
     * One laundry filed under two categories, because the card network truncated
     * its name and only the long spelling matched.
     *
     * "AL QIMMA LAUNDRY" matched the laundry rule; "AL QIMMA LAUNDR" matched
     * nothing there and fell through to a grocery rule further down. Fifty records
     * in one category and one in another, for the same shop.
     */
    @Test
    fun `a truncated laundry is still a laundry`() {
        assertEquals(SaudiCategories.SERVICES, CategoryGuess.forMerchant("AL QIMMA LAUNDRY"))
        assertEquals(SaudiCategories.SERVICES, CategoryGuess.forMerchant("AL QIMMA LAUNDR"))
    }

    /** A salon is personal care. It was filed as healthcare, which it is not. */
    @Test
    fun `laundries and salons are personal care, not the residue category`() {
        listOf("MGHASL ZKIEAH", "Noor AlMaabadi Laundry", "SALOON ENAYATI", "LE SALON")
            .forEach { name ->
                assertEquals(SaudiCategories.SERVICES, CategoryGuess.forMerchant(name), name)
            }
    }
}
