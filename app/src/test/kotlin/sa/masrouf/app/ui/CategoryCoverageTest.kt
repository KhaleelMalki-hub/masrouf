package sa.masrouf.app.ui

import org.junit.jupiter.api.Test
import androidx.compose.ui.graphics.Color
import sa.masrouf.core.model.SaudiCategories
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Every category has a colour, an icon and a label, in both themes.
 *
 * Three hand-maintained maps sit between `SaudiCategories.ALL` and the screen, and
 * nothing connected them. This session added two categories and each map had to be
 * remembered separately; a forgotten one is not a crash but a silent default - a
 * grey band, a question-mark glyph, an English id where a name belongs - on a
 * screen whose whole job is to be read at a glance.
 *
 * The palette is checked twice because it is two maps: a chart legible in one
 * theme and mush in the other is the failure the file was split for, and BONUS
 * shipped as a byte-identical copy of INCOME in the light theme.
 */
class CategoryCoverageTest {

    private val all = SaudiCategories.ALL

    /**
     * The name in `SaudiCategories` and the name on the screen are the same name.
     *
     * They are two sources of truth for one string and they drifted the day
     * groceries was renamed: `labelAr` became "بقالة وأغذية", the screen kept
     * saying "بقالة", and nothing failed - the interface reads the string
     * RESOURCE, and `labelAr` is read by nothing at all. That is worse than a
     * duplicate, because the copy that looks authoritative in the model is the one
     * that renders nowhere.
     *
     * Parsing the XML rather than resolving R: these are plain JVM tests, and the
     * file is the artefact that ships.
     */
    @Test
    fun `the arabic name in the model is the arabic name on the screen`() {
        val xml = java.io.File("src/main/res/values/strings.xml").readText()
        val strings = Regex("""<string name="([^"]+)">([^<]*)</string>""")
            .findAll(xml)
            .associate { it.groupValues[1] to it.groupValues[2] }

        val disagreeing = all.mapNotNull { category ->
            val onScreen = strings["category_${category.id}"]
            if (onScreen != null && onScreen != category.labelAr) {
                "${category.id}: model='${category.labelAr}' screen='$onScreen'"
            } else {
                null
            }
        }

        assertEquals(emptyList(), disagreeing, "the model and the screen disagree")
    }

    @Test
    fun `every category has a band colour of its own, in both themes`() {
        for ((theme, bands) in BandsByTheme) assertDistinctColours(theme, bands)
    }

    /**
     * Not merely present but DISTINGUISHABLE. Two categories sharing a colour are a
     * chart that cannot be read, which is worse than one with a gap in it.
     */
    private fun assertDistinctColours(theme: String, bands: Map<String, Color>) {
        val missing = all.map { it.id }.filter { it !in bands && it != SaudiCategories.OTHER.id }
        assertEquals(emptyList(), missing, "$theme: categories with no band colour")

        val byColour = all.filter { it.id in bands }.groupBy { bands.getValue(it.id) }
            .filterValues { it.size > 1 }
        assertTrue(
            byColour.isEmpty(),
            "$theme: categories sharing one colour: " +
                byColour.values.joinToString { group -> group.joinToString("/") { it.id } },
        )
    }

    @Test
    fun `every category has a chip label`() {
        val missing = all.filter { it.labelRes == 0 }.map { it.id }

        assertEquals(emptyList(), missing, "categories with no label resource")
    }

    @Test
    fun `every category has an icon`() {
        val fallback = SaudiCategories.byId("no-such-category").icon
        val missing = all.filter { it.icon == fallback }.map { it.id }

        assertEquals(emptyList(), missing, "categories falling through to the default glyph")
    }
}
