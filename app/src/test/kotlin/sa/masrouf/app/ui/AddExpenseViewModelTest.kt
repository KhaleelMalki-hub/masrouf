package sa.masrouf.app.ui

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import sa.masrouf.app.data.FakeDao
import sa.masrouf.app.data.TransactionRepository
import sa.masrouf.core.model.Source
import sa.masrouf.core.model.Status
import sa.masrouf.core.model.TransactionType
import sa.masrouf.core.money.Money
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * Manual entry is the feature this app is measured against - "record an expense in
 * about five seconds" - and it was the one path with no tests at all. It is also
 * the only path with no deduplication by design, so a defect here writes a wrong
 * number straight into the user's history with nothing downstream to catch it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AddExpenseViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val dao = FakeDao()
    private val repository = TransactionRepository(dao)
    private val clock = Clock.fixed(Instant.parse("2026-08-28T09:00:00Z"), ZoneOffset.UTC)

    private fun viewModel() = AddExpenseViewModel(repository, clock)

    @BeforeEach fun setUp() = Dispatchers.setMain(dispatcher)
    @AfterEach fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `a typed expense is stored as a confirmed manual record`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onAmountChanged("87.50")
        vm.onMerchantChanged("TAMIMI MARKETS")

        vm.save()
        testScheduler.advanceUntilIdle()

        assertEquals(1, dao.rows.size)
        val row = dao.rows.single()
        assertEquals(8750, row.amountHalalas)
        assertEquals(Source.MANUAL.name, row.source)
        // The user read the amount and typed it; asking them to confirm their own
        // keystrokes only teaches them to dismiss the step that will matter.
        assertEquals(Status.CONFIRMED.name, row.status)
    }

    @Test
    fun `saving twice in quick succession writes one record`() = runTest(dispatcher) {
        // The Save button is deliberately always enabled and the form is cleared only
        // after the write returns, so without an in-flight guard two taps a tenth of
        // a second apart both read the same form and both insert. Manual records are
        // UUID-fingerprinted, so nothing downstream would catch the duplicate.
        val vm = viewModel()
        vm.onAmountChanged("250.00")

        vm.save()
        vm.save()
        testScheduler.advanceUntilIdle()

        assertEquals(1, dao.rows.size, "one tap intent produced two records")
    }

    @Test
    fun `an empty amount is not scolded until the user tries to save`() = runTest(dispatcher) {
        val vm = viewModel()

        assertNull(vm.form.value.amountError)

        vm.save()
        testScheduler.advanceUntilIdle()

        assertEquals(AddExpenseState.AmountError.REQUIRED, vm.form.value.amountError)
        assertTrue(dao.rows.isEmpty())
    }

    @Test
    fun `an invalid amount is refused and nothing is written`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onAmountChanged("1.234")

        // Shown immediately - unlike an empty field, this one is already wrong.
        assertEquals(AddExpenseState.AmountError.INVALID, vm.form.value.amountError)

        vm.save()
        testScheduler.advanceUntilIdle()

        assertTrue(dao.rows.isEmpty())
    }

    @Test
    fun `the form clears after a save but keeps the chosen type`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onTypeChanged(TransactionType.BILL_PAYMENT)
        vm.onAmountChanged("40.00")
        vm.onNoteChanged("water")

        vm.save()
        testScheduler.advanceUntilIdle()

        assertEquals("", vm.form.value.typedAmount)
        assertEquals("", vm.form.value.note)
        // Kept: recording several of the same kind in a row is the common case.
        assertEquals(TransactionType.BILL_PAYMENT, vm.form.value.type)
    }

    @Test
    fun `a blank merchant and note are stored as null, not as empty strings`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onAmountChanged("10.00")
        vm.onMerchantChanged("   ")

        vm.save()
        testScheduler.advanceUntilIdle()

        val row = dao.rows.single()
        assertNull(row.merchantRaw)
        assertNull(row.merchantKey)
        assertNull(row.note)
    }

    @Test
    fun `the same amount entered twice deliberately is kept twice`() = runTest(dispatcher) {
        // Manual records are fingerprinted on a fresh UUID precisely so that a
        // person meaning to record two identical purchases can.
        val vm = viewModel()
        vm.onAmountChanged("15.00")
        vm.save()
        testScheduler.advanceUntilIdle()

        vm.onAmountChanged("15.00")
        vm.save()
        testScheduler.advanceUntilIdle()

        assertEquals(2, dao.rows.size)
        assertEquals(Money.ofMajor("30.00").halalas, dao.rows.sumOf { it.amountHalalas })
    }
}
