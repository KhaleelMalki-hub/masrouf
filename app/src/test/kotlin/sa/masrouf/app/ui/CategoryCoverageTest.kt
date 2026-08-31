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
