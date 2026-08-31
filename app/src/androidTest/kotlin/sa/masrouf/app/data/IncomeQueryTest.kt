package sa.masrouf.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import sa.masrouf.core.model.INCOME_CATEGORY_IDS
import sa.masrouf.core.model.SaudiCategories
import sa.masrouf.core.money.Money
import sa.masrouf.core.time.RiyadhTime
import java.time.LocalDateTime

/**
 * The income queries, against real SQLite.
 *
 * A review made this test necessary by proving nothing else reached them: it
 * replaced the bonus column of the aggregate with a literal zero, and the whole
 * suite stayed green - because `FakeDao` re-implements the query in Kotlin, so
 * every unit test proves the copy. Every figure on the income screen came from a
 * string that nothing read back.
 *
 * It runs on a device, against an in-memory database, and never touches the app's
 * own. What it asserts is the two things a Kotlin double cannot: that the SQL means
 * what the Kotlin thinks, and that the aggregate and the detail behind it agree.
 */
@RunWith(AndroidJUnit4::class)
class IncomeQueryTest {

    private lateinit var db: MasroufDatabase
    private lateinit var dao: TransactionDao

    private var nextId = 0

    @Before
    fun open() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MasroufDatabase::class.java,
        ).build()
        dao = db.transactions()
    }

    @After
    fun close() = db.close()

    private fun row(
        riyals: String,
        at: LocalDateTime,
        categoryId: String?,
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

    private suspend fun insert(vararg rows: TransactionEntity) = rows.forEach { dao.insert(it) }

    private suspend fun months() = dao.observeIncomeByMonth(
        salaryId = SaudiCategories.INCOME.id,
        bonusId = SaudiCategories.BONUS.id,
    ).first()

    /**
     * The mutation that went undetected. If the bonus column stops reading the
     * bonus category, this is the assertion that goes red.
     */
    @Test
    fun theAggregateSumsSalaryAndBonusIntoSeparateColumns() = runBlocking {
        insert(
            row("19491", LocalDateTime.of(2026, 6, 26, 2, 25), SaudiCategories.INCOME.id),
            row("26899.03", LocalDateTime.of(2026, 6, 23, 13, 24), SaudiCategories.BONUS.id),
            row("3810.95", LocalDateTime.of(2026, 6, 2, 12, 54), SaudiCategories.BONUS.id),
        )

        val june = months().single()

        assertEquals("2026-06", june.month)
        assertEquals(Money.ofMajor("19491").halalas, june.salaryHalalas)
        assertEquals(Money.ofMajor("30709.98").halalas, june.bonusHalalas)
    }

    /**
     * The class of bug that appears whenever an aggregate and its detail are
     * computed separately. They are computed by two different queries here, so the
     * agreement has to be asserted rather than assumed.
     */
    @Test
    fun theAggregateAndTheDepositsBehindItAgree() = runBlocking {
        insert(
            row("19491", LocalDateTime.of(2026, 6, 26, 2, 25), SaudiCategories.INCOME.id),
            row("26899.03", LocalDateTime.of(2026, 6, 23, 13, 24), SaudiCategories.BONUS.id),
            row("18808.50", LocalDateTime.of(2025, 12, 26, 2, 25), SaudiCategories.INCOME.id),
        )

        val aggregate = months().sumOf { it.salaryHalalas + it.bonusHalalas }
        val deposits = dao.observeIncomeRows(INCOME_CATEGORY_IDS).first().sumOf { it.amountHalalas }

        assertEquals(aggregate, deposits)
    }

    /**
     * A salary lands at 02:25 Riyadh on the 1st. Bucketed in UTC that is 23:25 on
     * the last day of the month before, and a year of salaries would each sit one
     * month early. The `+3 hours` in the SQL is what prevents that, and only a real
     * `strftime` can prove it.
     */
    @Test
    fun monthsAreBucketedInRiyadhNotUtc() = runBlocking {
        insert(row("19491", LocalDateTime.of(2026, 7, 1, 2, 25), SaudiCategories.INCOME.id))

        assertEquals("2026-07", months().single().month)
    }

    @Test
    fun aPendingRowIsNotCounted() = runBlocking {
        insert(
            row("19491", LocalDateTime.of(2026, 8, 26, 2, 25), SaudiCategories.INCOME.id),
            row("5000", LocalDateTime.of(2026, 8, 27, 2, 25), SaudiCategories.INCOME.id, status = "PENDING"),
        )

        assertEquals(Money.ofMajor("19491").halalas, months().single().salaryHalalas)
    }

    @Test
    fun aDebitIsNeverIncome() = runBlocking {
        insert(
            row("19491", LocalDateTime.of(2026, 8, 26, 2, 25), SaudiCategories.INCOME.id),
            row("400", LocalDateTime.of(2026, 8, 27, 2, 25), SaudiCategories.INCOME.id, direction = "DEBIT"),
        )

        assertEquals(Money.ofMajor("19491").halalas, months().single().salaryHalalas)
    }

    @Test
    fun aTransferIsNotIncomeHoweverLarge() = runBlocking {
        insert(row("100000", LocalDateTime.of(2026, 8, 26, 2, 25), SaudiCategories.TRANSFERS.id))

        assertTrue(months().isEmpty())
    }

    @Test
    fun monthsComeBackNewestFirst() = runBlocking {
        insert(
            row("1", LocalDateTime.of(2025, 12, 26, 2, 25), SaudiCategories.INCOME.id),
            row("1", LocalDateTime.of(2026, 8, 26, 2, 25), SaudiCategories.INCOME.id),
            row("1", LocalDateTime.of(2026, 3, 26, 2, 25), SaudiCategories.INCOME.id),
        )

        assertEquals(listOf("2026-08", "2026-03", "2025-12"), months().map { it.month })
    }
}
