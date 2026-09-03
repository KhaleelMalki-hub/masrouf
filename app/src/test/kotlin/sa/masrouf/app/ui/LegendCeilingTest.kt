package sa.masrouf.app.ui

import androidx.compose.ui.graphics.Color
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import sa.masrouf.core.model.Category
import sa.masrouf.core.model.SaudiCategories
import sa.masrouf.core.money.Money

/**
 * The month card's legend has one row per category the month touched, and there
 * are sixteen categories. Unbounded, a varied month pushed the history the card
 * sits above off the screen entirely.
 */
class LegendCeilingTest {

    private fun bands(count: Int): List<Band> =
        SaudiCategories.ALL.take(count).mapIndexed { index, category ->
            band(category, 1000L - index)
        }

    private fun band(category: Category?, halalas: Long) = Band(
        category = category,
        label = category?.id ?: "uncategorised",
        amount = Money.ofHalalas(halalas),
        colour = Color.Unspecified,
    )

    @Test
    fun `a short legend is shown whole`() {
        val bands = bands(LEGEND_CEILING)

        assertEquals(bands, legendRows(bands, selected = null, showAll = false))
    }

    @Test
    fun `a long legend stops at the ceiling`() {
        val bands = bands(LEGEND_CEILING + 4)

        val shown = legendRows(bands, selected = null, showAll = false)

        assertEquals(LEGEND_CEILING, shown.size)
        assertEquals(bands.take(LEGEND_CEILING), shown)
    }

    @Test
    fun `asking for all of it shows all of it`() {
        val bands = bands(LEGEND_CEILING + 4)

        assertEquals(bands, legendRows(bands, selected = null, showAll = true))
    }

    @Test
    fun `a filter below the cut opens the legend`() {
        // Otherwise the history below is narrowed to a category with nothing on
        // screen naming it, and the only way out is a filter the user cannot see.
        val bands = bands(LEGEND_CEILING + 4)
        val buried = bands.last().category!!

        val shown = legendRows(bands, HistoryFilter.OfCategory(buried), showAll = false)

        assertEquals(bands, shown)
        assertTrue(shown.any { it.category?.id == buried.id })
    }

    @Test
    fun `a filter above the cut leaves the ceiling in place`() {
        val bands = bands(LEGEND_CEILING + 4)
        val visible = bands.first().category!!

        val shown = legendRows(bands, HistoryFilter.OfCategory(visible), showAll = false)

        assertEquals(LEGEND_CEILING, shown.size)
    }

    @Test
    fun `the unfiled filter opens a legend whose unfiled row is buried`() {
        val bands = bands(LEGEND_CEILING + 3) + band(category = null, halalas = 1L)

        val shown = legendRows(bands, HistoryFilter.Unfiled, showAll = false)

        assertEquals(bands, shown)
    }
}
