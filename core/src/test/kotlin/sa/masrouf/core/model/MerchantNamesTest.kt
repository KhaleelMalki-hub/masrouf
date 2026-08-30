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
        assertEquals("هنقرستيشن", MerchantNames.forMerchant("HUNGERSTA")?.ar)
        assertEquals("هنقرستيشن", MerchantNames.forMerchant("HUNGERSTATION LLC")?.ar)
        assertEquals("بنده", MerchantNames.forMerchant("AZIZIA PANDA UNITED P")?.ar)
        assertEquals("الدريس", MerchantNames.forMerchant("ALDREES 1")?.ar)
        assertEquals("نينجا", MerchantNames.forMerchant("www.anani")?.ar)
    }

    /**
     * Amazon Now and Amazon are two names, and the exact-match rule that keeps
     * their categories apart keeps their names apart too.
     */
    @Test
    fun `amazon now is named apart from amazon`() {
        assertEquals("أمازون ناو", MerchantNames.forMerchant("Amazon No")?.ar)
        assertEquals("أمازون", MerchantNames.forMerchant("Amazon SA")?.ar)
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
            "SUREPay SNB" to "مغسلة ربوة التميز",
        ).forEach { (raw, expected) ->
            assertEquals(expected, MerchantNames.forMerchant(raw)?.ar, raw)
        }
    }

    /**
     * Both languages, because the app switches between them.
     *
     * A merchant list that stayed Arabic under an English interface would be worse
     * than the descriptors it replaced. The English name is not the descriptor
     * either: "AZIZIA PANDA UNITED P" reads as Panda in either language.
     */
    @Test
    fun `every name is given in both languages`() {
        val panda = MerchantNames.forMerchant("AZIZIA PANDA UNITED P")
        assertEquals("بنده", panda?.ar)
        assertEquals("Panda", panda?.en)

        val station = MerchantNames.forMerchant("HUNGERSTA")
        assertEquals("هنقرستيشن", station?.ar)
        assertEquals("HungerStation", station?.en)
    }
}
