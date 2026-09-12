package sa.masrouf.app.ui

import org.junit.jupiter.api.Test
import androidx.compose.ui.graphics.Color
import sa.masrouf.core.model.SaudiCategories
import kotlin.test.assertEquals
import kotlin.math.cbrt
import kotlin.math.pow
import kotlin.math.sqrt
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

    /**
     * The floor two bands must clear to be told apart.
     *
     * 13 rather than a textbook 15: the light palette reaches 15.5 and the dark
     * 14.5, and the gap between the floor and what the palette achieves is the room
     * a future colour has to be tuned in. Raise it if a palette is ever retuned
     * higher; do not lower it to admit a colour.
     */
    private val MIN_BAND_DISTANCE = 13.0

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
     * Not merely present but DISTINGUISHABLE - and now actually measured.
     *
     * This said the same sentence for months and asserted byte equality: it caught
     * only the exact-duplicate case (bonus shipped as a copy of income once) and
     * passed everything else. Under it, transport and investment sat 5.8 apart in
     * the light theme and bills and bonus 4.2 apart in the dark - both of them
     * indistinguishable in a 12dp band, both of them shipped, both of them past a
     * guard whose own docstring promised to stop exactly that. A guard that asserts
     * IDENTITY while claiming to assert CONTENT is the shape this repository has
     * been bitten by more than once.
     *
     * CIE76 in CIE Lab, which is the metric the palette's own comments quote. The
     * floor is what the palette actually achieves with a little headroom: nineteen
     * colours confined to one lightness band (so each stays legible against its
     * theme's surface) cannot all be far apart, and a floor set where no palette
     * can reach it is a guard that gets deleted rather than obeyed.
     */
    private fun assertDistinctColours(theme: String, bands: Map<String, Color>) {
        val missing = all.map { it.id }.filter { it !in bands && it != SaudiCategories.OTHER.id }
        assertEquals(emptyList(), missing, "$theme: categories with no band colour")

        val present = all.filter { it.id in bands }
        val tooClose = present.indices.flatMap { i ->
            (i + 1 until present.size).mapNotNull { j ->
                val a = present[i]
                val b = present[j]
                val distance = deltaE(bands.getValue(a.id), bands.getValue(b.id))
                if (distance < MIN_BAND_DISTANCE) Triple(a.id, b.id, distance) else null
            }
        }

        assertTrue(
            tooClose.isEmpty(),
            "$theme: bands too close to tell apart (CIE76 floor $MIN_BAND_DISTANCE): " +
                tooClose.joinToString { (a, b, d) -> "$a/$b ${"%.1f".format(d)}" },
        )
    }

    /** CIE76. Enough to separate two flat fills; nobody is proofing print here. */
    private fun deltaE(one: Color, other: Color): Double {
        val (l1, a1, b1) = lab(one)
        val (l2, a2, b2) = lab(other)
        return sqrt((l1 - l2).pow(2) + (a1 - a2).pow(2) + (b1 - b2).pow(2))
    }

    private fun lab(colour: Color): Triple<Double, Double, Double> {
        fun linear(channel: Float): Double {
            val c = channel.toDouble()
            return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        val r = linear(colour.red)
        val g = linear(colour.green)
        val b = linear(colour.blue)
        // sRGB to CIE XYZ, D65.
        val x = (r * 0.4124564 + g * 0.3575761 + b * 0.1804375) / 0.95047
        val y = r * 0.2126729 + g * 0.7151522 + b * 0.0721750
        val z = (r * 0.0193339 + g * 0.1191920 + b * 0.9503041) / 1.08883
        fun f(t: Double) = if (t > 216.0 / 24389.0) cbrt(t) else (841.0 / 108.0) * t + 4.0 / 29.0
        val fx = f(x)
        val fy = f(y)
        val fz = f(z)
        return Triple(116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz))
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

    /**
     * No `%d` in any string resource, in either language.
     *
     * `Resources.getString(id, args)` formats with the CONFIGURATION locale, and
     * under `ar-SA` that renders Arabic-Indic digits - ٣٠ where every other number
     * in this app is 30. This app's one absolute typographic rule is Western
     * numerals in both languages, matching what Saudi banks print, and every count
     * elsewhere is passed pre-converted into a `%s` for exactly that reason.
     *
     * Two `%d`s shipped on the ask screen the day it was written, and neither the
     * strings nor the Kotlin looked wrong on its own: the defect lives in the seam.
     * This is the only place that can see both sides.
     */
    @Test
    fun `no string resource formats a number with the locale's own digits`() {
        val offenders = listOf("src/main/res/values/strings.xml", "src/main/res/values-en/strings.xml")
            .flatMap { path ->
                Regex("""<string name="([^"]+)">([^<]*)</string>""")
                    .findAll(java.io.File(path).readText())
                    .filter { Regex("""%\d+[$]d""").containsMatchIn(it.groupValues[2]) }
                    .map { "$path: ${it.groupValues[1]}" }
            }

        assertEquals(
            emptyList(),
            offenders,
            "these format a number with %d, which is Arabic-Indic under ar-SA; pass a String into %s",
        )
    }

    /**
     * Every band must be readable with the label [onBandColour] picks for it.
     *
     * The floor is WCAG AA for body text. It is a guard on the PALETTE, not on the
     * chooser: retuning one band towards its own label's colour is a change that
     * looks fine in a diff, ships, and is only visible to whoever happens to select
     * that category on that theme. The gold of BONUS spent a release at 3.04 that
     * way, while the dark theme's identical code cleared 7:1.
     */
    @Test
    fun `every band is readable with the label chosen for it`() {
        val floor = 4.5f
        listOf("light" to LightBands, "dark" to DarkBands).forEach { (theme, bands) ->
            val failing = bands.mapNotNull { (id, band) ->
                val ratio = contrastRatio(band, onBandColour(band))
                if (ratio < floor) "$id ${"%.2f".format(ratio)}" else null
            }
            assertTrue(
                failing.isEmpty(),
                "$theme: bands whose own label is unreadable on them (floor $floor): $failing",
            )
        }
    }
}
