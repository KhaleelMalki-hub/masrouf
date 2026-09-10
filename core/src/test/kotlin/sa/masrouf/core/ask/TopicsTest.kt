package sa.masrouf.core.ask

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What each topic must reach, and - the half that matters - what it must not.
 *
 * Every name in the second test is a real merchant from the owner's history that a
 * plausible version of these rules would have swallowed. They are the reason the
 * fuel topic is a list of brands rather than "anything with STATION in it".
 */
class TopicsTest {

    @Test
    fun `fuel reaches the filling stations, under every spelling the terminals send`() {
        val stations = listOf(
            "ALDREES 1", "AL DREES", "AL DREES1", "ALDREES P", "ALDREES 7",
            "SASCO", "SASCO STA", "SASCO ELZAIDI STATION", "SASCO AL ZAIDI",
            "NAFT SERV", "NAFT SERVICES COMPANY",
            "Total ene", "ALZAIDI STATION MECCA", "MAKKAH 12 ALZAIDI",
        )
        val missed = stations.filterNot(Topic.FUEL::claims)
        assertEquals(emptyList(), missed, "fuel does not reach these stations")
    }

    /**
     * The four names that make this file worth having.
     *
     * HUNGERSTATION is a food-delivery app with a thousand records and ninety-eight
     * thousand riyals on this history; a fuel rule matching "STATION" takes all of
     * it. BREW 92 ALZAIDI is a coffee shop inside a filling station and MS 21535
     * KUDAY ALZAIDY is the garage next door, so "ALZAIDI" alone is not a fuel
     * keyword either. JUICES STATION sells juice.
     */
    @Test
    fun `fuel reaches nothing that merely sounds like a station`() {
        for (name in listOf(
            "HUNGERSTATION", "HUNGERSTATION LLC", "HUNGERSTATION INTERNET",
            "JUICES STATION", "BREW 92 ALZAIDI", "MS 21535 KUDAY ALZAIDY",
        )) {
            assertTrue(!Topic.FUEL.claims(name), "fuel wrongly claims $name")
        }
    }

    @Test
    fun `delivery and coffee do not claim each other's shops`() {
        assertTrue(Topic.DELIVERY.claims("HUNGERSTATION"))
        assertTrue(Topic.COFFEE.claims("BREW 92 ALZAIDI"))
        assertTrue(!Topic.COFFEE.claims("HUNGERSTATION"))
        assertTrue(!Topic.DELIVERY.claims("BREW 92 A"))
    }

    @Test
    fun `a pharmacy is reached under the acronym as well as the name`() {
        assertTrue(Topic.PHARMACY.claims("Nahdi"))
        assertTrue(Topic.PHARMACY.claims("Al Nahdi Pharmacy 2082"))
        assertTrue(Topic.PHARMACY.claims("NMC2075"))
        assertTrue(Topic.PHARMACY.claims("NMC8121"))
    }

    @Test
    fun `an unrelated shop belongs to no topic at all`() {
        assertNull(Topic.claiming("AL MUASHA"))
        assertNull(Topic.claiming("Amazon SA"))
        assertNull(Topic.claiming(null))
    }
}
