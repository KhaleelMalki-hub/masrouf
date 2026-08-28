package sa.masrouf.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
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
private val LightBands = mapOf(
    SaudiCategories.FOOD.id to Color(0xFFB3261E),
    SaudiCategories.GROCERIES.id to Color(0xFF2E6B4F),
    SaudiCategories.TRANSPORT.id to Color(0xFF2B5CA8),
    SaudiCategories.BILLS.id to Color(0xFF8A5A00),
    SaudiCategories.HEALTH.id to Color(0xFF6C4E9C),
    SaudiCategories.SHOPPING.id to Color(0xFF9A4A2F),
    SaudiCategories.ENTERTAINMENT.id to Color(0xFF7A3E8F),
    SaudiCategories.CHARITY.id to Color(0xFF1F7A6B),
    SaudiCategories.CASH.id to Color(0xFF6B5B2E),
    SaudiCategories.INCOME.id to Color(0xFF2F7A3A),
    SaudiCategories.TRANSFERS.id to Color(0xFF4A6572),
    SaudiCategories.OTHER.id to Color(0xFF6E6E76),
)

private val DarkBands = mapOf(
    SaudiCategories.FOOD.id to Color(0xFFFF897D),
    SaudiCategories.GROCERIES.id to Color(0xFF7DDBA8),
    SaudiCategories.TRANSPORT.id to Color(0xFFAEC6FF),
    SaudiCategories.BILLS.id to Color(0xFFF5C264),
    SaudiCategories.HEALTH.id to Color(0xFFCDB4F5),
    SaudiCategories.SHOPPING.id to Color(0xFFFFB59B),
    SaudiCategories.ENTERTAINMENT.id to Color(0xFFE0AEF5),
    SaudiCategories.CHARITY.id to Color(0xFF7FD8C6),
    SaudiCategories.CASH.id to Color(0xFFDCC98A),
    SaudiCategories.INCOME.id to Color(0xFF8FD99B),
    SaudiCategories.TRANSFERS.id to Color(0xFFA8C8D8),
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
 * Spending with no category yet.
 *
 * Deliberately the dimmest thing in the palette and closest to the surface: it
 * should read as an absence the user can act on, not as a ninth category they
 * chose.
 */
@Composable
@ReadOnlyComposable
fun uncategorisedColour(): Color = MaterialTheme.colorScheme.outlineVariant
