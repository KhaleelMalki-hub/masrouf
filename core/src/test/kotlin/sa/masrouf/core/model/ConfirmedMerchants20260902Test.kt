package sa.masrouf.core.model

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * The 2026-09-02 web-identified merchants the owner confirmed, against the exact
 * strings the terminals send.
 *
 * The two he corrected are asserted below with the rest, because a search that was
 * wrong twice out of forty-eight is exactly why nothing enters this file until he
 * has read it.
 */
class ConfirmedMerchants20260902Test {

    @Test
    fun `confirmed merchants file under what the owner agreed`() {
        val expected = mapOf(
            "SCOPEER" to SaudiCategories.INVESTMENT,
            "BEST SHIE" to SaudiCategories.TRANSPORT,
            "HALA BOSTANI TRADING E" to SaudiCategories.GROCERIES,
            "MOHAMMED ALI SAAD ALDEN C" to SaudiCategories.GROCERIES,
            "STARS SMI" to SaudiCategories.HEALTH,
            "EMIRATISBAIT MAKKAH" to SaudiCategories.SHOPPING,
            "TAP WEFT" to SaudiCategories.SHOPPING,
            "IBRAHIM AL KORASHI SHO" to SaudiCategories.SHOPPING,
            "LA VIE AMP ROSE" to SaudiCategories.SHOPPING,
            "F F" to SaudiCategories.SHOPPING,
            "THEBUTCHERSA" to SaudiCategories.FOOD,
            "SHOBAK" to SaudiCategories.FOOD,
            "ERWAA" to SaudiCategories.CHARITY,
            "GAZ ALTAHASOSI" to SaudiCategories.BILLS,
            "DAINTREE WORLD" to SaudiCategories.ENTERTAINMENT,
        )
        for ((merchant, category) in expected) {
            assertEquals(category, CategoryGuess.forMerchant(merchant), merchant)
        }
    }

    /** The second wave, confirmed the same evening. */
    @Test
    fun `the second wave files under what the owner agreed`() {
        val expected = mapOf(
            "SHRIMP ZO" to SaudiCategories.FOOD,
            "SHRIMP ZONE" to SaudiCategories.FOOD,
            "SHRIMP AREA" to SaudiCategories.FOOD,
            "ROKON ALJAMBARY EST" to SaudiCategories.FOOD,
            "ROKON ALJ" to SaudiCategories.FOOD,
            "OVER JAR COMPANY LIMIT" to SaudiCategories.FOOD,
            "C HUB" to SaudiCategories.FOOD,
            "NEURON CORPORATION" to SaudiCategories.FOOD,
            "ALGHARBIS" to SaudiCategories.GROCERIES,
            "ABDALHADI OMAR BAFART" to SaudiCategories.GROCERIES,
            "PTB TALAH AL JOOD" to SaudiCategories.GROCERIES,
            "NATWAN MAKKAH" to SaudiCategories.GROCERIES,
            "JOODESKAN" to SaudiCategories.CHARITY,
            "IRQAHORG AD" to SaudiCategories.CHARITY,
            "HAMAD M ALRUGAI" to SaudiCategories.SHOPPING,
            "RINA HAIFA MALL JEDDAH" to SaudiCategories.SHOPPING,
            "MOHAMMED KABLI TRADING ES" to SaudiCategories.TRANSPORT,
            "RWAEA ALMARAH EST" to SaudiCategories.ENTERTAINMENT,
            // His two corrections: تكوة is a restaurant, and the mall charge is
            // parking. The search had both as shops.
            "TAKWAH" to SaudiCategories.FOOD,
            "MALL OF ARABIA" to SaudiCategories.TRANSPORT,
        )
        for ((merchant, category) in expected) {
            assertEquals(category, CategoryGuess.forMerchant(merchant), merchant)
        }
    }
}
