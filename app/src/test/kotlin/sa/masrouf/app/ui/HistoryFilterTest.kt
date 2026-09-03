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
import java.time.ZoneOffset

/**
 * The filing worklist.
 *
 * The shipped merchant list plus the type rules file about 84% of a real history.
 * The rest is a few thousand rows over more than a thousand local shops, and the
 * only way it ever gets filed is the user working through it. That is a filter, so
 * "no filter" and "show me what is unfiled" have to be different states - and they
 * were not, because both were expressed as a null category.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HistoryFilterTest {

    private val dispatcher = StandardTestDispatcher()
    private val dao = FakeDao()
    private val repository = TransactionRepository(dao)
    private val clock = Clock.fixed(Instant.parse("2026-08-15T09:00:00Z"), ZoneOffset.UTC)

    @BeforeEach fun setUp() = Dispatchers.setMain(dispatcher)
    @AfterEach fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(): AddExpenseViewModel {
        val vm = AddExpenseViewModel(repository, clock, background = dispatcher)
        listOf(vm.monthTransactions, vm.monthShares)
            .forEach { flow -> backgroundScope.launch { flow.collect {} } }
        return vm
    }

    private suspend fun store(id: String, merchant: String, minute: Long, category: String?) {
        val at = Instant.parse("2026-08-10T09:00:00Z").plusSeconds(minute * 60)
        repository.recordCaptured(
            Transaction(
                id = id,
                amount = Money.ofMajor("50.00"),
                direction = Direction.DEBIT,
                type = TransactionType.PURCHASE,
                occurredAt = at,
                accountId = null,
                categoryId = category,
                merchantRaw = merchant,
                merchantKey = merchant,
                note = null,
                source = Source.SMS,
                status = Status.CONFIRMED,
                fingerprint = Fingerprint.forMessage(
                    Source.SMS, at, Money.ofMajor("50.00"), Direction.DEBIT, merchant, id,
                ),
                rawText = null,
            )
        )
    }

    private suspend fun threeRows() {
        store("filed", "TAMIMI", 0, SaudiCategories.GROCERIES.id)
        // A name no shipped rule reaches. AL QIMMA used to be here and earned a
        // rule since; the view model files what it can at launch, so an unfiled
        // fixture has to be unfileable.
        store("unfiled-a", "MEZAB TRADING EST", 30, null)
        store("unfiled-b", "SOME LOCAL SHOP", 60, null)
    }

    @Test
    fun `selecting the uncategorised band shows only what is still to be filed`() = runTest(dispatcher) {
        threeRows()
        val vm = viewModel()
        testScheduler.advanceUntilIdle()
        assertEquals(3, vm.monthTransactions.value.size)

        // Null is what the legend's uncategorised band carries: it has no category.
        vm.toggleCategoryFilter(null)
        testScheduler.advanceUntilIdle()

        // A set, not a list: ordering is the real DAO's `ORDER BY`, and asserting
        // it here would only pin the in-memory fake's insertion order.
        assertEquals(
            setOf("unfiled-a", "unfiled-b"),
            vm.monthTransactions.value.map { it.id }.toSet(),
        )
        assertEquals(HistoryFilter.Unfiled, vm.categoryFilter.value)
    }

    @Test
    fun `tapping the same band twice clears the filter`() = runTest(dispatcher) {
        threeRows()
        val vm = viewModel()
        testScheduler.advanceUntilIdle()

        vm.toggleCategoryFilter(null)
        vm.toggleCategoryFilter(null)
        testScheduler.advanceUntilIdle()

        assertEquals(null, vm.categoryFilter.value)
        assertEquals(3, vm.monthTransactions.value.size)
    }

    @Test
    fun `a category band shows only that category`() = runTest(dispatcher) {
        threeRows()
        val vm = viewModel()
        testScheduler.advanceUntilIdle()

        vm.toggleCategoryFilter(SaudiCategories.GROCERIES)
        testScheduler.advanceUntilIdle()

        assertEquals(listOf("filed"), vm.monthTransactions.value.map { it.id })
    }

    /**
     * A stored id this build cannot name is not filed.
     *
     * It reads as filed if the filter compares ids, and then it can never be
     * reached to be corrected: it belongs to no band in the legend and matches no
     * category filter. Going through [SaudiCategories.byId], as the legend does,
     * puts it back in the worklist.
     */
    @Test
    fun `a category this build does not know counts as unfiled`() = runTest(dispatcher) {
        store("stale", "SOMEWHERE", 0, "a-category-from-a-later-version")
        val vm = viewModel()
        testScheduler.advanceUntilIdle()

        vm.toggleCategoryFilter(null)
        testScheduler.advanceUntilIdle()

        assertEquals(listOf("stale"), vm.monthTransactions.value.map { it.id })
    }
}
