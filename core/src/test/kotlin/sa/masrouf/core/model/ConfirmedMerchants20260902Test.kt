package sa.masrouf.core.model

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/** The 2026-09-02 web-identified merchants the owner confirmed, against the strings the terminals send. */
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
}
