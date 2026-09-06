package sa.masrouf.app.ui

import androidx.lifecycle.SavedStateHandle
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import sa.masrouf.app.data.FakeDao
import sa.masrouf.app.data.TransactionRepository
import sa.masrouf.core.model.SaudiCategories
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Where the user was, after Android has taken the process away.
 *
 * A backgrounded app is killed freely, and everything about where they were lived in
 * memory: the month they had paged to, the category they were filtering by, the text
 * in the search box. Coming back put them on this month with no filter - which reads
 * as the app having forgotten, not as the system having reclaimed memory. The tab
 * they were on was already saved, so the mismatch was visible: the right tab, the
 * wrong month.
 */
class SavedStateTest {

    private val dispatcher = StandardTestDispatcher()
    private val dao = FakeDao()
    private val repository = TransactionRepository(dao, computation = dispatcher)
    private val clock = Clock.fixed(Instant.parse("2026-09-06T09:00:00Z"), ZoneOffset.UTC)

    @BeforeEach fun setUp() = Dispatchers.setMain(dispatcher)
    @AfterEach fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(state: SavedStateHandle) = AddExpenseViewModel(
        repository = repository,
        clock = clock,
        background = dispatcher,
        savedState = state,
    )

    @Test
    fun `the month a user paged to comes back with them`() = runTest {
        val state = SavedStateHandle()
        viewModel(state).showMonth(LocalDate.of(2025, 3, 14))

        // Same saved state, new view model: what the system hands back after a kill.
        assertEquals(LocalDate.of(2025, 3, 1), viewModel(state).selectedMonth.value)
    }

    @Test
    fun `so does the category they were filtering by`() = runTest {
        val state = SavedStateHandle()
        viewModel(state).toggleCategoryFilter(SaudiCategories.GROCERIES)

        assertEquals(
            HistoryFilter.OfCategory(SaudiCategories.GROCERIES),
            viewModel(state).categoryFilter.value,
        )
    }

    @Test
    fun `and the unfiled filter, which is not a missing category`() = runTest {
        val state = SavedStateHandle()
        viewModel(state).toggleCategoryFilter(null)

        assertEquals(HistoryFilter.Unfiled, viewModel(state).categoryFilter.value)
    }

    @Test
    fun `and what they had typed`() = runTest {
        val state = SavedStateHandle()
        viewModel(state).onQueryChanged("نقلي")

        assertEquals("نقلي", viewModel(state).query.value)
    }

    @Test
    fun `clearing the filters clears what was saved`() = runTest {
        val state = SavedStateHandle()
        val first = viewModel(state)
        first.toggleCategoryFilter(SaudiCategories.FOOD)
        first.onQueryChanged("كافيه")

        first.clearFilters()

        val second = viewModel(state)
        assertEquals(null, second.categoryFilter.value)
        assertEquals("", second.query.value)
    }
}
