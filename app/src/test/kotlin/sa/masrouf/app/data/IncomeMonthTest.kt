package sa.masrouf.app.data

import org.junit.jupiter.api.Test
import sa.masrouf.core.money.Money
import java.time.YearMonth
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * A month of income, read out of the aggregate query.
 *
 * The screen this feeds exists because salary and bonuses were summed into one
 * figure the owner could not take apart, so the thing worth guarding is that they
 * stay apart all the way from SQL to the bar.
 */
class IncomeMonthTest {

    @Test
    fun `salary and bonus stay separate and still add up`() {
        val row = IncomeMonthRow(month = "2026-06", salaryHalalas = 19_491_00L, bonusHalalas = 26_899_03L)

        val model = row.toModel()!!

        assertEquals(YearMonth.of(2026, 6), model.month)
        assertEquals(Money.ofMajor("19491.00"), model.salary)
        assertEquals(Money.ofMajor("26899.03"), model.bonus)
        assertEquals(Money.ofMajor("46390.03"), model.total)
    }

    @Test
    fun `a month with no bonus reports zero, not the salary twice`() {
        val model = IncomeMonthRow("2026-08", salaryHalalas = 19_491_00L, bonusHalalas = 0L).toModel()!!

        assertEquals(Money.ZERO, model.bonus)
        assertEquals(model.salary, model.total)
    }

    /**
     * The query groups on a `strftime` key, so a malformed one is possible in
     * principle. It skips the row rather than throwing - one bad key must not take
     * the whole screen down - and rather than substituting a month, which would put
     * real money in the wrong year.
     */
    @Test
    fun `a month key that is not a month is skipped, not guessed`() {
        assertNull(IncomeMonthRow("not-a-month", 1L, 0L).toModel())
        assertNull(IncomeMonthRow("", 1L, 0L).toModel())
    }
}
