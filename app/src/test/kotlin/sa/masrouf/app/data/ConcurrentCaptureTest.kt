package sa.masrouf.app.data

import kotlin.test.assertEquals
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.yield
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import sa.masrouf.core.dedup.Fingerprint
import sa.masrouf.core.model.Direction
import sa.masrouf.core.model.Source
import sa.masrouf.core.model.Status
import sa.masrouf.core.model.Transaction
import sa.masrouf.core.model.TransactionType
import sa.masrouf.core.money.Money
import java.time.Instant

/**
 * The two capture paths run on different coroutines, and the arrival pattern the
 * whole feature exists for - a bank's SMS and that bank's own push, seconds apart -
 * is exactly the one that interleaves them.
 *
 * [CrossSourceDedupTest] calls the repository sequentially, so it proves the
 * matching algorithm and nothing about the plumbing around it. This models the
 * real schedule instead: a database read takes its snapshot when the query runs
 * and then suspends for I/O, so a second caller can read the same stale window
 * before the first has inserted. That is the interleaving that doubled the month.
 *
 * Note the DAO must NOT block until two readers arrive. That was the first shape
 * of this test and it deadlocked once the lock existed - the lock's whole job is
 * to make "both inside the read at once" impossible, so a test demanding it can
 * only ever hang. Yielding models the suspension without requiring the overlap.
 */
class ConcurrentCaptureTest {

    /** A DAO whose read takes its snapshot, then suspends the way real I/O does. */
    private class SuspendingDao : TransactionDao {
        private val state = MutableStateFlow<List<TransactionEntity>>(emptyList())

        val rows: List<TransactionEntity> get() = state.value

        override suspend fun neighbours(fromMillis: Long, untilMillis: Long): List<TransactionEntity> {
            val snapshot = state.value.filter { it.occurredAtMillis in fromMillis..untilMillis }
            // The query has run; these are the suspension points a real read has,
            // during which another coroutine may reach this same method.
            repeat(4) { yield() }
            return snapshot
        }

        override suspend fun insert(transaction: TransactionEntity): Long {
            if (state.value.any { it.fingerprint == transaction.fingerprint }) return -1L
            state.value = state.value + transaction
            return state.value.size.toLong()
        }

        override fun observeRecent(limit: Int): Flow<List<TransactionEntity>> = state
        override fun observeBetween(fromMillis: Long, untilMillis: Long) = state
        override fun observePending(): Flow<List<TransactionEntity>> =
            state.map { rows -> rows.filter { it.status == Status.PENDING.name } }
        override suspend fun confirm(id: String) = 0
        override suspend fun confirmAllPending() = 0
        override suspend fun dismiss(id: String) = 0
        override suspend fun setCategory(id: String, categoryId: String?, source: String?) = 0
        override suspend fun setCategoryForMerchant(
            merchantKey: String,
            categoryId: String?,
            source: String?,
        ) = 0
        override suspend fun clearAutomaticCategories() = 0
        override suspend fun setCategoryForMerchantAtBank(merchantKey: String, bankId: String, categoryId: String?, source: String?) = 0
        override suspend fun stampBank(fingerprint: String, bankId: String) = 0
        override fun observeCardBalances(): Flow<List<CardBalance>> = MutableStateFlow(emptyList())
        override suspend fun withoutBalance() = emptyList<TransactionEntity>()
        override suspend fun withMissingParty() = emptyList<TransactionEntity>()
        override fun observeConfirmedDebits(): Flow<List<TransactionEntity>> = MutableStateFlow(emptyList())
        override suspend fun retypeSalaryDeposits() = 0
        override suspend fun latestSmsMillis(): Long? = null
        override fun observeLatestSalary(): Flow<Long?> = MutableStateFlow(null)
        override suspend fun fillParty(id: String, merchantRaw: String?, merchantKey: String?, last4: String?) = 0
        override suspend fun allWithBody() = emptyList<TransactionEntity>()
        override suspend fun clearNumericParties() = 0
        override fun observeIncomeByMonth(): Flow<List<IncomeMonthRow>> = MutableStateFlow(emptyList())
        override fun observeIncomeRows(): Flow<List<TransactionEntity>> = MutableStateFlow(emptyList())
        override suspend fun setAmount(id: String, halalas: Long) = 0
        override suspend fun withBodyOfType(spendingTypes: List<String>) = emptyList<TransactionEntity>()
        override suspend fun retype(id: String, type: String, categoryId: String?) = 0
        override suspend fun deleteAll(ids: List<String>) = 0
        override suspend fun setBalance(id: String, halalas: Long?, kind: String) = 0
        override fun observeCardBanks(): Flow<List<CardBank>> = MutableStateFlow(emptyList())
        override fun observeEarliest(): Flow<Long?> = MutableStateFlow(null)
        override fun observeMonthsWithData(): Flow<List<String>> = MutableStateFlow(emptyList())
        override suspend fun uncategorised() = emptyList<TransactionEntity>()
        override suspend fun uncategorisedOrMerchant(merchantKey: String) =
            emptyList<TransactionEntity>()
        override suspend fun delete(id: String) = 0
    }

    private fun captured(source: Source, at: Instant) = Transaction(
        id = "id-${source.name}",
        amount = Money.ofMajor("5000.00"),
        direction = Direction.DEBIT,
        type = TransactionType.PURCHASE,
        occurredAt = at,
        accountId = null,
        categoryId = null,
        merchantRaw = "barq",
        merchantKey = "barq",
        note = null,
        source = source,
        status = Status.PENDING,
        fingerprint = Fingerprint.forMessage(
            source, at, Money.ofMajor("5000.00"), Direction.DEBIT, "1887", "barq",
        ),
        rawText = null,
    )

    @Test
    fun `one purchase arriving as sms and notification at once is still stored once`() = runTest {
        val noon = Instant.parse("2026-08-28T09:00:00Z")
        val dao = SuspendingDao()
        val repository = TransactionRepository(dao)

        // Both reach the neighbour query before either inserts - the schedule the
        // lock exists to make impossible. Without it both see an empty window,
        // both decide they are new, and the month doubles.
        listOf(
            async { repository.recordCaptured(captured(Source.SMS, noon), "1887") },
            async { repository.recordCaptured(captured(Source.NOTIFICATION, noon.plusSeconds(8)), "1887") },
        ).awaitAll()

        assertEquals(1, dao.rows.size, "one purchase was stored twice")
    }
}
