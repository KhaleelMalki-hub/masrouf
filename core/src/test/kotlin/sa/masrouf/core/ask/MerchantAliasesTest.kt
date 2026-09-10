package sa.masrouf.core.ask

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * The Arabic a person types, against the Latin a terminal writes.
 *
 * The second test is the one worth having: an alias table is a list of substrings,
 * and substrings reach further than anyone intends.
 */
class MerchantAliasesTest {

    @Test
    fun `the arabic spellings reach the latin names in this history`() {
        assertEquals("AMAZON", MerchantAliases.resolve("امازون"))
        assertEquals("HUNGERSTA", MerchantAliases.resolve("هنقرستيشن"))
        assertEquals("HUNGERSTA", MerchantAliases.resolve("هنجرستيشن"))
        assertEquals("NAHDI", MerchantAliases.resolve("النهدي"))
        assertEquals("ALDREES", MerchantAliases.resolve("الدريس"))
    }

    /** A single letter is inside almost every name in the table. */
    @Test
    fun `a fragment too short to mean anything resolves to nothing`() {
        assertEquals("ن", MerchantAliases.resolve("ن"))
        assertEquals("ال", MerchantAliases.resolve("ال"))
    }

    @Test
    fun `a name the table does not know is left exactly as typed`() {
        assertEquals("زابلونيا", MerchantAliases.resolve("زابلونيا"))
        assertEquals("Ounass", MerchantAliases.resolve("Ounass"))
    }

    /**
     * The failure that made this a whole-word table: "ابل" (Apple) sits inside
     * "زابلونيا", and the first version answered a question about an invented shop
     * with Apple's purchases.
     */
    @Test
    fun `a three letter alias does not reach inside a longer word`() {
        assertEquals("زابلونيا", MerchantAliases.resolve("زابلونيا"))
        assertEquals("مبرقش", MerchantAliases.resolve("مبرقش"))
    }

    /** A shop named inside a sentence is still the shop. */
    @Test
    fun `an alias is found among the other words of the question`() {
        assertEquals("AMAZON", MerchantAliases.resolve("من امازون"))
    }

    /** Four characters that begin a name are a truncation, not a coincidence. */
    @Test
    fun `a typed abbreviation still reaches the name`() {
        assertEquals("AMAZON", MerchantAliases.resolve("امازو"))
    }
}
