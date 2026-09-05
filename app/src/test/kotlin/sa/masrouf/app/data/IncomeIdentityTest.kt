package sa.masrouf.app.data

import kotlin.test.assertEquals
import org.junit.jupiter.api.Test
import sa.masrouf.core.model.INCOME_CATEGORY_IDS
import sa.masrouf.core.model.SaudiCategories

/**
 * The two queries that answer "what came in" have to agree about what income is.
 *
 * They are shaped differently and cannot share a parameter: the month aggregate
 * splits salary from bonus into two SUM arms, because the screen draws them as two
 * segments of one bar, while the deposit list binds the whole list. Add a third
 * income category and the list would show deposits the header above it does not
 * count - the header says 45,000, the rows add to 30,000, and nothing anywhere
 * reports an error.
 *
 * Nothing in Kotlin decides this, so nothing in Kotlin can be made to enforce it.
 * This test is the enforcement: it fails the moment the list stops being exactly
 * the two ids `observeIncomeByMonth` names, which is the moment to go and give that
 * query a third arm.
 */
class IncomeIdentityTest {

    @Test
    fun `income is exactly the two categories the month aggregate splits`() {
        assertEquals(
            listOf(SaudiCategories.INCOME.id, SaudiCategories.BONUS.id),
            INCOME_CATEGORY_IDS,
        )
    }
}
