package sa.masrouf.app.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import sa.masrouf.core.money.Money
import sa.masrouf.core.time.RiyadhTime
import java.time.LocalDateTime
import java.time.YearMonth
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The series behind the income screen.
 *
 * Everything here is a filter that, if it slipped, would put a number on that
 * screen the owner never earned - a pending row nobody agreed to, a debit read as
 * income, or a salary in the wrong month.
 */
class IncomeSeriesTest {

    private val dao = FakeDao()
    private val repository = TransactionRepository(dao)

    private var nextId = 0

    private fun row(
        riyals: String,
        at: LocalDateTime,
        categoryId: String,
        direction: String = "CREDIT",
        status: String = "CONFIRMED",
    ) = TransactionEntity(
        id = "t-${nextId++}",
        amountHalalas = Money.ofMajor(riyals).halalas,
        direction = direction,
        type = if (direction == "CREDIT") "TRANSFER_IN" else "PURCHASE",
        occurredAtMillis = RiyadhTime.toInstant(at).toEpochMilli(),
        accountId = null,
        accountLast4 = null,
        categoryId = categoryId,
        categorySource = "AUTOMATIC",
        merchantRaw = null,
        merchantKey = null,
        note = null,
        source = "SMS",
        status = status,
        fingerprint = "fp-$nextId",
        rawText = null,
        currency = "SAR",
    )

    private suspend fun series() = repository.observeIncomeByMonth().first()

    @Test
    fun `a month reports its salary and its bonus apart`() = runTest {
        dao.replaceAll(
            listOf(
                row("19491", LocalDateTime.of(2026, 6, 26, 2, 25), "income"),
                row("26899.03", LocalDateTime.of(2026, 6, 23, 13, 24), "bonus"),
            )
        )

        val june = series().single()

        assertEquals(YearMonth.of(2026, 6), june.month)
        assertEquals(Money.ofMajor("19491"), june.salary)
        assertEquals(Money.ofMajor("26899.03"), june.bonus)
    }

    /**
     * The salary lands at 02:25 Riyadh on the 26th. Bucketed in UTC that is 23:25
     * on the 25th - the same month here, but the first of a month it would move
     * into the previous one, and a year of salaries would each sit one month early.
     */
    @Test
    fun `months are bucketed in Riyadh, not UTC`() = runTest {
        dao.replaceAll(listOf(row("19491", LocalDateTime.of(2026, 7, 1, 2, 25), "income")))

        assertEquals(YearMonth.of(2026, 7), series().single().month)
    }

    @Test
    fun `a pending row is not counted`() = runTest {
        dao.replaceAll(
            listOf(
                row("19491", LocalDateTime.of(2026, 8, 26, 2, 25), "income"),
                row("5000", LocalDateTime.of(2026, 8, 27, 2, 25), "income", status = "PENDING"),
            )
        )

        assertEquals(Money.ofMajor("19491"), series().single().salary)
    }

    /** Spending filed under a category this screen reads must never appear as income. */
    @Test
    fun `a debit is not income`() = runTest {
        dao.replaceAll(
            listOf(
                row("19491", LocalDateTime.of(2026, 8, 26, 2, 25), "income"),
                row("400", LocalDateTime.of(2026, 8, 27, 2, 25), "income", direction = "DEBIT"),
            )
        )

        assertEquals(Money.ofMajor("19491"), series().single().total)
    }

    @Test
    fun `a transfer is not income, however large`() = runTest {
        dao.replaceAll(listOf(row("100000", LocalDateTime.of(2026, 8, 26, 2, 25), "transfers")))

        assertTrue(series().isEmpty())
    }

    @Test
    fun `months come back newest first`() = runTest {
        dao.replaceAll(
            listOf(
                row("19491", LocalDateTime.of(2025, 12, 26, 2, 25), "income"),
                row("19491", LocalDateTime.of(2026, 8, 26, 2, 25), "income"),
                row("19491", LocalDateTime.of(2026, 3, 26, 2, 25), "income"),
            )
        )

        assertEquals(
            listOf(YearMonth.of(2026, 8), YearMonth.of(2026, 3), YearMonth.of(2025, 12)),
            series().map { it.month },
        )
    }

    /**
     * A month with neither is absent, not zero. The series is what arrived, and
     * inventing empty months would draw years of nothing that never happened.
     */
    @Test
    fun `a month with no income at all is absent`() = runTest {
        dao.replaceAll(listOf(row("19491", LocalDateTime.of(2026, 8, 26, 2, 25), "income")))

        assertEquals(1, series().size)
    }
}
