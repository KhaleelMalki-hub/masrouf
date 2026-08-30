package sa.masrouf.core.model

import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.Test

/**
 * Names shown, never names stored.
 *
 * The raw descriptor stays in the database: it is what the bank sent, it is what
 * the next rule will be written against, and replacing it would destroy the only
 * record of what actually arrived.
 */
class MerchantNamesTest {

    @Test
    fun `the descriptors a card network sends are shown as people say them`() {
        assertEquals("هنقرستيشن", MerchantNames.forMerchant("HUNGERSTA"))
        assertEquals("هنقرستيشن", MerchantNames.forMerchant("HUNGERSTATION LLC"))
        assertEquals("بنده", MerchantNames.forMerchant("AZIZIA PANDA UNITED P"))
        assertEquals("الدريس", MerchantNames.forMerchant("ALDREES 1"))
        assertEquals("نينجا", MerchantNames.forMerchant("www.anani"))
    }

    /**
     * Amazon Now and Amazon are two names, and the exact-match rule that keeps
     * their categories apart keeps their names apart too.
     */
    @Test
    fun `amazon now is named apart from amazon`() {
        assertEquals("أمازون ناو", MerchantNames.forMerchant("Amazon No"))
        assertEquals("أمازون", MerchantNames.forMerchant("Amazon SA"))
    }

    /** A shop seen twice reads perfectly well as itself. */
    @Test
    fun `an unlisted merchant keeps the name the bank sent`() {
        assertNull(MerchantNames.forMerchant("MEZAB TRADING EST"))
        assertNull(MerchantNames.forMerchant(null))
    }

    /**
     * Every name has to be reachable, which is not free: the list is ordered and
     * matched exactly as the category rules are, so a keyword can be swallowed by
     * a shorter one above it.
     */
    @Test
    fun `every listed merchant resolves to its own name`() {
        listOf(
            "STC Pay" to "اس تي سي باي",
            "Saudi Telecom Company" to "الاتصالات السعودية",
            "Health Endowment Fund" to "الوقف الصحي",
            // One word from the endowment fund, and only order keeps them apart -
            // the same collision the category rules hit with this exact word.
            "Healthy pie bakery" to "هيلثي باي",
            "Health" to "الوقف الصحي",
        ).forEach { (raw, expected) ->
            assertEquals(expected, MerchantNames.forMerchant(raw), raw)
        }
    }
}
