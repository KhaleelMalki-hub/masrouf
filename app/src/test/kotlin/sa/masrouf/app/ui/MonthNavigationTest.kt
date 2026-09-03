package sa.masrouf.app.ui

import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import sa.masrouf.app.data.FakeDao
import sa.masrouf.app.data.TransactionRepository
import sa.masrouf.core.dedup.Fingerprint
import sa.masrouf.core.model.Direction
import sa.masrouf.core.model.SaudiCategories
import sa.masrouf.core.model.Source
import sa.masrouf.core.model.Status
import sa.masrouf.core.model.Transaction
import sa.masrouf.core.model.TransactionType
import sa.masrouf.core.money.Money
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Importing a history writes transactions into months the dashboard could not
 * show: everything was pinned to the current month, so years of imported records
 * were in the database and invisible. These pin the paging that fixed it, and in
 * particular its two ends - the future, which has no spending, and the month
 * before the first record, which is not the same as an empty month.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MonthNavigationTest {

    private val dispatcher = StandardTestDispatcher()
    private val dao = FakeDao()
    // The repository maps its flows off the main thread. Handing it the test's own
    // dispatcher is what lets `advanceUntilIdle()` mean what it says here.
    private val repository = TransactionRepository(dao, computation = dispatcher)

    /** A fixed "now" in the middle of August 2026, Riyadh. */
    private val clock = Clock.fixed(Instant.parse("2026-08-15T09:00:00Z"), ZoneOffset.UTC)
    private val august = LocalDate.of(2026, 8, 1)

    @BeforeEach fun setUp() = Dispatchers.setMain(dispatcher)
    @AfterEach fun tearDown() = Dispatchers.resetMain()

    /**
     * Builds the view model and starts collecting the flows under test.
     *
     * They are `stateIn(WhileSubscribed)`, so with no collector they sit at their
     * initial value for ever and every assertion reads zero. Compose subscribes for
     * real via collectAsStateWithLifecycle; a test has to say so explicitly.
     */
    private fun TestScope.viewModel(): AddExpenseViewModel {
        val vm = AddExpenseViewModel(repository, clock, background = dispatcher)
        listOf(vm.monthTotal, vm.monthShares, vm.monthTransactions, vm.earliestMonth)
            .forEach { flow -> backgroundScope.launch { flow.collect {} } }
        return vm
    }

    private suspend fun store(riyals: String, at: Instant, category: String? = null) {
        repository.recordCaptured(
            Transaction(
                id = "t-$at-$riyals",
                amount = Money.ofMajor(riyals),
                direction = Direction.DEBIT,
                type = TransactionType.PURCHASE,
                occurredAt = at,
                accountId = null,
                categoryId = category,
                merchantRaw = "SHOP",
                merchantKey = "SHOP",
                note = null,
                source = Source.SMS,
                status = Status.CONFIRMED,
                fingerprint = Fingerprint.forMessage(
                    Source.SMS, at, Money.ofMajor(riyals), Direction.DEBIT, null, "SHOP $riyals",
                ),
                rawText = null,
            )
        )
    }

    @Test
    fun `the app opens on the current month`() = runTest(dispatcher) {
        val vm = viewModel()
        testScheduler.advanceUntilIdle()

        assertEquals(august, vm.selectedMonth.value)
    }

    @Test
    fun `paging back shows the previous month's own total`() = runTest(dispatcher) {
        store("100.00", Instant.parse("2026-08-10T09:00:00Z"))
        store("250.00", Instant.parse("2026-07-10T09:00:00Z"))
        val vm = viewModel()
        testScheduler.advanceUntilIdle()

        assertEquals(Money.ofMajor("100.00"), vm.monthTotal.value)

        vm.showPreviousMonth()
        testScheduler.advanceUntilIdle()

        assertEquals(LocalDate.of(2026, 7, 1), vm.selectedMonth.value)
        // July's own spending, not a running total and not August's.
        assertEquals(Money.ofMajor("250.00"), vm.monthTotal.value)
    }

    @Test
    fun `the future is not reachable`() = runTest(dispatcher) {
        // There is no spending in a month that has not happened.
        store("100.00", Instant.parse("2026-08-10T09:00:00Z"))
        val vm = viewModel()
        testScheduler.advanceUntilIdle()

        vm.showNextMonth()
        testScheduler.advanceUntilIdle()

        assertEquals(august, vm.selectedMonth.value)
    }

    @Test
    fun `paging stops at the first month that has anything in it`() = runTest(dispatcher) {
        // Running backwards for ever through empty months reads as data loss
        // rather than as the end of the record.
        store("250.00", Instant.parse("2026-06-10T09:00:00Z"))
        val vm = viewModel()
        testScheduler.advanceUntilIdle()

        repeat(6) {
            vm.showPreviousMonth()
            testScheduler.advanceUntilIdle()
        }

        assertEquals(LocalDate.of(2026, 6, 1), vm.selectedMonth.value)
    }

    @Test
    fun `paging back and forward returns to where it started`() = runTest(dispatcher) {
        store("100.00", Instant.parse("2026-08-10T09:00:00Z"))
        store("250.00", Instant.parse("2026-07-10T09:00:00Z"))
        val vm = viewModel()
        testScheduler.advanceUntilIdle()

        vm.showPreviousMonth(); testScheduler.advanceUntilIdle()
        vm.showNextMonth(); testScheduler.advanceUntilIdle()

        assertEquals(august, vm.selectedMonth.value)
        assertEquals(Money.ofMajor("100.00"), vm.monthTotal.value)
    }

    @Test
    fun `the strip and the history follow the selected month too`() = runTest(dispatcher) {
        // The failure this guards is a page showing one month's total above another
        // month's transactions.
        store("100.00", Instant.parse("2026-08-10T09:00:00Z"), SaudiCategories.FOOD.id)
        store("250.00", Instant.parse("2026-07-10T09:00:00Z"), SaudiCategories.GROCERIES.id)
        val vm = viewModel()
        testScheduler.advanceUntilIdle()

        assertEquals(listOf(SaudiCategories.FOOD.id), vm.monthShares.value.map { it.first?.id })
        assertEquals(1, vm.monthTransactions.value.size)

        vm.showPreviousMonth()
        testScheduler.advanceUntilIdle()

        assertEquals(listOf(SaudiCategories.GROCERIES.id), vm.monthShares.value.map { it.first?.id })
        assertEquals(1, vm.monthTransactions.value.size)
        assertEquals(Money.ofMajor("250.00"), vm.monthTransactions.value.single().amount)
    }

    @Test
    fun `an empty database cannot page anywhere`() = runTest(dispatcher) {
        val vm = viewModel()
        testScheduler.advanceUntilIdle()

        vm.showPreviousMonth()
        testScheduler.advanceUntilIdle()

        assertEquals(august, vm.selectedMonth.value)
    }
}
