package sa.masrouf.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import sa.masrouf.core.model.Category
import sa.masrouf.core.model.SaudiCategories

/**
 * One colour per category.
 *
 * These are data, not decoration. The month strip is unreadable if two categories
 * are hard to tell apart, so they are chosen for mutual distinguishability first
 * and harmony second, and adjacent entries in display order are kept far apart in
 * hue because they sit next to each other in the strip.
 *
 * Specified per theme rather than shared. A colour with enough contrast against
 * `#121318` is usually washed out against `#FBF8FF`, so the light set is darker
 * and more saturated and the dark set is lighter and softer. Sharing one set is
 * how a chart ends up legible in one theme and mush in the other.
 */
internal val LightBands = mapOf(
    SaudiCategories.FOOD.id to Color(0xFFB3261E),
    SaudiCategories.GROCERIES.id to Color(0xFF3A6A47),
    SaudiCategories.TRANSPORT.id to Color(0xFF125EA9),
    SaudiCategories.BILLS.id to Color(0xFF8A5A00),
    SaudiCategories.HEALTH.id to Color(0xFF6C4E9C),
    SaudiCategories.SHOPPING.id to Color(0xFF9A4A2F),
    SaudiCategories.HOUSING.id to Color(0xFF1F5F8B),
    SaudiCategories.EDUCATION.id to Color(0xFF4A6C27),
    SaudiCategories.SERVICES.id to Color(0xFF006971),
    // Blue-violet, not the teal it started as: teal put it 5.9 from charity and
    // 5.8 from groceries in the same strip. Measured against every other light
    // band, its nearest neighbour is now investment at 15.7.
    SaudiCategories.TRAVEL.id to Color(0xFF2F4FB0),
    // Gold. It shipped as a byte-identical copy of INCOME - 0xFF2F7A3A twice -
    // so in the light theme a month's salary and its bonus drew as one solid
    // block, and the income screen's whole premise ("never summed into one bar")
    // was false in one of the two themes it ships with. Nearest other band is now
    // bills at 20.3.
    SaudiCategories.BONUS.id to Color(0xFFB5891C),
    SaudiCategories.ENTERTAINMENT.id to Color(0xFF833A88),
    SaudiCategories.FEES.id to Color(0xFF8C4A6B),
    SaudiCategories.CHARITY.id to Color(0xFF1F7A6B),
    SaudiCategories.CASH.id to Color(0xFF6B5B2E),
    SaudiCategories.INVESTMENT.id to Color(0xFF5B5E99),
    SaudiCategories.INCOME.id to Color(0xFF2B7A3C),
    SaudiCategories.TRANSFERS.id to Color(0xFF456576),
    SaudiCategories.OTHER.id to Color(0xFF736D75),
)

internal val DarkBands = mapOf(
    SaudiCategories.FOOD.id to Color(0xFFFF897D),
    SaudiCategories.GROCERIES.id to Color(0xFF6FDCB2),
    SaudiCategories.TRANSPORT.id to Color(0xFFA1C9FF),
    SaudiCategories.BILLS.id to Color(0xFFF6C264),
    SaudiCategories.HEALTH.id to Color(0xFFD5B2F1),
    SaudiCategories.SHOPPING.id to Color(0xFFFFB59B),
    SaudiCategories.HOUSING.id to Color(0xFF83CBEE),
    SaudiCategories.EDUCATION.id to Color(0xFFB8D98F),
    SaudiCategories.SERVICES.id to Color(0xFF6ED4DD),
    // Periwinkle, for the reason its light twin changed: the teal was 2.1 from
    // charity, which is invisible. Nearest other dark band is investment at 9.7.
    SaudiCategories.TRAVEL.id to Color(0xFFAFB8FF),
    // Gold, matching its light twin. The old pale green cleared INCOME by 11.9,
    // which is a difference you can measure and not one you can see at 3dp - a
    // deposit chip is a 3x12dp sliver. Nearest other dark band is bills at 19.9.
    SaudiCategories.BONUS.id to Color(0xFFDAC75F),
    SaudiCategories.ENTERTAINMENT.id to Color(0xFFF4A8E4),
    SaudiCategories.FEES.id to Color(0xFFF3A8C8),
    SaudiCategories.CHARITY.id to Color(0xFF7FD8C6),
    SaudiCategories.CASH.id to Color(0xFFDCC98A),
    SaudiCategories.INVESTMENT.id to Color(0xFFB9B5E1),
    SaudiCategories.INCOME.id to Color(0xFF91D999),
    SaudiCategories.TRANSFERS.id to Color(0xFFA8C8D7),
    SaudiCategories.OTHER.id to Color(0xFFA8A8B2),
)

/** True when the current scheme is the dark one. Compared on a role that differs most. */
@Composable
@ReadOnlyComposable
private fun isDarkScheme(): Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5f

private fun Color.luminance(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue

/** The dye for a category in whichever theme is showing. */
@Composable
@ReadOnlyComposable
fun bandColour(category: Category?): Color {
    val bands = if (isDarkScheme()) DarkBands else LightBands
    return category?.let { bands[it.id] } ?: uncategorisedColour()
}

/**
 * What to write ON a band: whichever of black or white the eye can actually read.
 *
 * A band is a colour of this app's own choosing, not a scheme role, so no `on-`
 * token corresponds to it - and the one that was used, `surface`, is the right
 * answer only by coincidence. The coincidence held in the dark theme, where every
 * band clears 7:1 against a near-black surface, and failed in the light one: the
 * gold of BONUS reached 3.04 against near-white, under the 4.5 that WCAG asks for
 * body text, so the word on the chip the user had just chosen was the least
 * legible thing on the screen.
 *
 * Computed rather than tabulated. A hand-written second palette of label colours
 * drifts from the first the moment one band is retuned, and the drift is invisible
 * until someone measures it - which is how the coincidence lasted this long.
 */
fun onBandColour(band: Color): Color =
    if (contrastRatio(band, Color.White) >= contrastRatio(band, Color.Black)) {
        Color.White
    } else {
        Color.Black
    }

/**
 * WCAG 2.1 contrast, `(lighter + 0.05) / (darker + 0.05)`.
 *
 * Both colours are taken as opaque. Every band is, and a ratio computed against a
 * translucent colour would describe a colour nobody sees.
 */
fun contrastRatio(a: Color, b: Color): Float {
    val first = a.luminance()
    val second = b.luminance()
    return (maxOf(first, second) + 0.05f) / (minOf(first, second) + 0.05f)
}

/**
 * Spending with no category yet.
 *
 * Deliberately the dimmest thing in the palette and closest to the surface: it
 * should read as an absence the user can act on, not as a ninth category they
 * chose.
 */
@Composable
@ReadOnlyComposable
fun uncategorisedColour(): Color = MaterialTheme.colorScheme.outlineVariant

/**
 * The two band maps, for the guard that asserts every category has a colour of its
 * own in each theme. Exposed rather than duplicated: a test holding its own copy
 * of the palette proves that two of my lists agree, which is not the question.
 */
internal val BandsByTheme: Map<String, Map<String, androidx.compose.ui.graphics.Color>> =
    mapOf("light" to LightBands, "dark" to DarkBands)

/**
 * How strongly a category's colour tints a container behind text.
 *
 * One value, because it was three - 0.16 on the history disc, 0.14 on the card chip,
 * 0.22 on the legend fill - with nothing saying why the same idea differed by site.
 * The legend keeps its own, heavier value: it is a proportion bar as well as a tint,
 * and it has to read as a length.
 */
const val TINT_ALPHA = 0.16f
