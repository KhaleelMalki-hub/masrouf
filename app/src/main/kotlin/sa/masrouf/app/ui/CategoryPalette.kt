package sa.masrouf.app.ui

import androidx.compose.ui.graphics.Color
import sa.masrouf.core.model.Category
import sa.masrouf.core.model.SaudiCategories

/**
 * One dyed band per category.
 *
 * Assigned by hand rather than generated from a hue wheel: a woven band has a
 * finite dye lot, and eight colours chosen to sit together read as one cloth,
 * where eight evenly-spaced hues read as a chart legend. Adjacent categories in
 * [SaudiCategories.ALL] are also kept far apart in colour, because they will sit
 * next to each other in the strip.
 */
val Category.band: Color
    get() = when (id) {
        SaudiCategories.FOOD.id -> Sadu.Madder
        SaudiCategories.GROCERIES.id -> Sadu.Palm
        SaudiCategories.TRANSPORT.id -> Sadu.Indigo
        SaudiCategories.BILLS.id -> Sadu.Saffron
        SaudiCategories.HEALTH.id -> Sadu.Dusk
        SaudiCategories.SHOPPING.id -> Sadu.Clay
        SaudiCategories.TRANSFERS.id -> Sadu.Sand
        else -> Sadu.Ash
    }

/** Spending with no category yet. Deliberately the colour of unwoven ground. */
val UncategorisedBand: Color = Sadu.Loom
