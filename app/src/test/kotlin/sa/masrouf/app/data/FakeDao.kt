package sa.masrouf.app.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import sa.masrouf.core.model.Status

/**
 * In-memory stand-in for the real DAO.
 *
 * It reproduces exactly two behaviours of the schema, because they are the two the
 * repository's correctness rests on: the fingerprint is unique, and the confirm and
 * dismiss writes only touch rows that are still pending.
 */
class FakeDao : TransactionDao {

    private val state = MutableStateFlow<List<TransactionEntity>>(emptyList())

    val rows: List<TransactionEntity> get() = state.value

    override suspend fun insert(transaction: TransactionEntity): Long {
        if (state.value.any { it.fingerprint == transaction.fingerprint }) return -1L
        state.value = state.value + transaction
        return state.value.size.toLong()
    }

    override suspend fun neighbours(fromMillis: Long, untilMillis: Long) =
        state.value.filter { it.occurredAtMillis in fromMillis..untilMillis }

    override fun observeRecent(limit: Int): Flow<List<TransactionEntity>> =
        state.map { rows -> rows.sortedByDescending { it.occurredAtMillis }.take(limit) }

    override fun observeBetween(fromMillis: Long, untilMillis: Long) =
        state.map { rows -> rows.filter { it.occurredAtMillis in fromMillis until untilMillis } }

    override fun observePending(): Flow<List<TransactionEntity>> =
        state.map { rows -> rows.filter { it.status == Status.PENDING.name } }

    override suspend fun confirm(id: String): Int = update(id) { row ->
        row.copy(status = Status.CONFIRMED.name)
    }

    override suspend fun confirmAllPending(): Int {
        val pending = state.value.filter { it.status == Status.PENDING.name }
        state.value = state.value.map {
            if (it.status == Status.PENDING.name) it.copy(status = Status.CONFIRMED.name) else it
        }
        return pending.size
    }

    override suspend fun dismiss(id: String): Int {
        val target = state.value.firstOrNull { it.id == id && it.status == Status.PENDING.name }
            ?: return 0
        state.value = state.value - target
        return 1
    }

    override suspend fun setCategory(id: String, categoryId: String?, source: String?): Int {
        val target = state.value.firstOrNull { it.id == id } ?: return 0
        state.value = state.value.map {
            if (it.id == target.id) it.copy(categoryId = categoryId, categorySource = source) else it
        }
        return 1
    }

    override fun observeEarliest(): Flow<Long?> =
        state.map { rows -> rows.minOfOrNull { it.occurredAtMillis } }

    override fun observeMonthsWithData(): Flow<List<String>> = state.map { rows ->
        rows.map {
            java.time.Instant.ofEpochMilli(it.occurredAtMillis)
                .atZone(java.time.ZoneId.of("Asia/Riyadh"))
                .toLocalDate()
                .withDayOfMonth(1)
                .toString()
                .substring(0, 7)
        }.distinct().sortedDescending()
    }

    override suspend fun setCategoryForMerchant(
        merchantKey: String,
        categoryId: String?,
        source: String?,
    ): Int {
        val hits = state.value.filter { it.merchantKey == merchantKey }
        state.value = state.value.map {
            if (it.merchantKey == merchantKey) {
                it.copy(categoryId = categoryId, categorySource = source)
            } else {
                it
            }
        }
        return hits.size
    }

    override suspend fun clearAutomaticCategories(): Int {
        val hits = state.value.filter {
            it.categoryId != null && it.categorySource == CategorySource.AUTOMATIC.name
        }
        state.value = state.value.map {
            if (it in hits) it.copy(categoryId = null, categorySource = null) else it
        }
        return hits.size
    }

    override suspend fun uncategorised(): List<TransactionEntity> =
        state.value.filter { it.categoryId == null }

    override suspend fun delete(id: String): Int {
        val target = state.value.firstOrNull { it.id == id } ?: return 0
        state.value = state.value - target
        return 1
    }

    private fun update(id: String, change: (TransactionEntity) -> TransactionEntity): Int {
        val target = state.value.firstOrNull { it.id == id && it.status == Status.PENDING.name }
            ?: return 0
        state.value = state.value.map { if (it.id == target.id) change(it) else it }
        return 1
    }
}
