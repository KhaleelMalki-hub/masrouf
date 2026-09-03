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

    /** The third wave: twenty answers out of ninety strings. */
    @Test
    fun `the third wave files under what the owner agreed`() {
        val expected = mapOf(
            "ADDIDAS KIDS YASM" to SaudiCategories.SHOPPING,
            "DEER FOOT" to SaudiCategories.SHOPPING,
            // Chocolate and wedding carts; his record is a cart.
            "LAZWARD EST" to SaudiCategories.SHOPPING,
            "SPEED TRACK3" to SaudiCategories.TRANSPORT,
            "ROUTE" to SaudiCategories.FOOD,
            "ALZAWAQA" to SaudiCategories.FOOD,
            "BONON COM" to SaudiCategories.FOOD,
            "MEDIUM WE" to SaudiCategories.FOOD,
            "TAKKA EXPRESS" to SaudiCategories.FOOD,
            "SANAR" to SaudiCategories.HEALTH,
            "HALA YALA LOCAL" to SaudiCategories.ENTERTAINMENT,
            "KL TOWER" to SaudiCategories.ENTERTAINMENT,
            "RED SEA BEACH" to SaudiCategories.ENTERTAINMENT,
            "ALAIZDIHA" to SaudiCategories.SERVICES,
        )
        for ((merchant, category) in expected) {
            assertEquals(category, CategoryGuess.forMerchant(merchant), merchant)
        }
    }

    /**
     * Names read out of the one-time-password messages, which spell in full what
     * the confirmation truncates - and which the app must never store.
     */
    @Test
    fun `names recovered from the inbox file under what the full name says`() {
        val expected = mapOf(
            "AL RASHED" to SaudiCategories.TRANSPORT,
            "TECHNICAL" to SaudiCategories.TRANSPORT,
            "TECHNICAL INDSPECTION" to SaudiCategories.TRANSPORT,
            "Bader Ch.." to SaudiCategories.CHARITY,
            "Saudi Arabian" to SaudiCategories.TRAVEL,
            "SAEED ALI MORSH" to SaudiCategories.CHARITY,
            "AL Ahlia" to SaudiCategories.FOOD,
            "Ahmed Ara" to SaudiCategories.GROCERIES,
            "TAP TAIBA" to SaudiCategories.SHOPPING,
            "CITY WINDOW" to SaudiCategories.SHOPPING,
            "TermAppISO" to SaudiCategories.TRAVEL,
            // Named by their own code messages; "fatoora" is a gateway that sends
            // only the shop's initial, so each letter is a different shop.
            "fatoora*A" to SaudiCategories.SHOPPING,
            "fatoora*L" to SaudiCategories.SHOPPING,
            "fatoora*D" to SaudiCategories.GROCERIES,
            "fatoora*C" to SaudiCategories.TRANSPORT,
            "MF CARBOOST" to SaudiCategories.TRANSPORT,
            "NAWAL MELEH TO DECORAT" to SaudiCategories.SHOPPING,
        )
        for ((merchant, category) in expected) {
            assertEquals(category, CategoryGuess.forMerchant(merchant), merchant)
        }
    }

    /**
     * أجواد الكرم stays a grocery.
     *
     * A bare "KARAM" keyword would have claimed it. The keyword was nearly added
     * because a longer string in the inbox began with the truncation "Karam" -
     * but that purchase's own code message named SALLA APP, and a prefix is not
     * an identification.
     */
    @Test
    fun `the grocery named Karam is still a grocery`() {
        assertEquals(SaudiCategories.GROCERIES, CategoryGuess.forMerchant("AJWAD AL KARAM CO"))
        assertEquals(SaudiCategories.GROCERIES, CategoryGuess.forMerchant("AJWAD ALKRM COM"))
    }
}
