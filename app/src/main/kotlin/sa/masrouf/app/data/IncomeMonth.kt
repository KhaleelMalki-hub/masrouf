package sa.masrouf.app.data

import sa.masrouf.core.money.Money
import java.time.YearMonth

/** One month's income, straight out of the aggregate query. */
data class IncomeMonthRow(
    val month: String,
    val salaryHalalas: Long,
    val bonusHalalas: Long,
)

/**
 * One month of income, in the terms the screen speaks.
 *
 * Salary and bonuses kept apart rather than summed, because that separation is the
 * whole reason this screen exists: the owner wanted to read the two series over the
 * years, and a month that carried both would otherwise show one figure he could not
 * take apart.
 */
data class IncomeMonth(
    val month: YearMonth,
    val salary: Money,
    val bonus: Money,
) {
    val total: Money get() = salary + bonus
}

/**
 * @return null when the stored month is not a month.
 *
 * A row is skipped rather than substituted. `YearMonth.parse` throwing here would
 * take the whole screen down over one malformed key, and a substituted month would
 * put real money in the wrong year.
 */
fun IncomeMonthRow.toModel(): IncomeMonth? = runCatching {
    IncomeMonth(
        month = YearMonth.parse(month),
        salary = Money.ofHalalas(salaryHalalas),
        bonus = Money.ofHalalas(bonusHalalas),
    )
}.getOrNull()
