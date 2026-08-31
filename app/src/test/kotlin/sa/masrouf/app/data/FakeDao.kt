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

    /** For tests that need a row in a shape the repository would never write. */
    fun replaceAll(rows: List<TransactionEntity>) { state.value = rows }

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

    override suspend fun setCategoryForMerchantAtBank(merchantKey: String, bankId: String, categoryId: String?, source: String?): Int {
        val hits = state.value.filter { it.merchantKey == merchantKey && it.bankId == bankId }
        state.value = state.value.map {
            if (it.merchantKey == merchantKey && it.bankId == bankId) it.copy(categoryId = categoryId, categorySource = source) else it
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

    override suspend fun stampBank(fingerprint: String, bankId: String): Int {
        val target = state.value.firstOrNull { it.fingerprint == fingerprint && it.bankId == null }
            ?: return 0
        state.value = state.value.map {
            if (it.id == target.id) it.copy(bankId = bankId) else it
        }
        return 1
    }

    override fun observeCardBanks(): Flow<List<CardBank>> = state.map { rows ->
        rows.mapNotNull { row ->
            val last4 = row.accountLast4 ?: return@mapNotNull null
            val bank = row.bankId ?: return@mapNotNull null
            CardBank(last4, bank)
        }.distinct()
    }

    override fun observeCardBalances(): Flow<List<CardBalance>> = state.map { rows ->
        rows.filter { it.accountLast4 != null }
            .groupBy { it.accountLast4!! }
            .map { (card, group) ->
                val newest = group.maxBy { it.occurredAtMillis }
                val figure = group.filter { it.balanceHalalas != null }.maxByOrNull { it.occurredAtMillis }
                CardBalance(
                    last4 = card,
                    halalas = figure?.balanceHalalas,
                    kind = figure?.balanceKind,
                    atMillis = newest.occurredAtMillis,
                    bankId = group.firstOrNull { it.bankId != null }?.bankId,
                )
            }
            .sortedByDescending { it.atMillis }
    }

    override suspend fun retypeSalaryDeposits(): Int = 0

    override suspend fun latestSmsMillis(): Long? =
        state.value.filter { it.source == "SMS" }.maxOfOrNull { it.occurredAtMillis }

    override fun observeLatestSalary(): Flow<Long?> = state.map { rows ->
        rows.filter { it.type == "SALARY" && it.direction == "CREDIT" }.maxByOrNull { it.occurredAtMillis }?.amountHalalas
    }

    override fun observeConfirmedDebits(): Flow<List<TransactionEntity>> = state.map { rows ->
        rows.filter { it.status == "CONFIRMED" && it.direction == "DEBIT" && it.merchantKey != null }
            .sortedBy { it.occurredAtMillis }
    }

    override suspend fun withMissingParty(): List<TransactionEntity> =
        state.value.filter { it.rawText != null && (it.merchantKey == null || it.accountLast4 == null) }

    override suspend fun fillParty(id: String, merchantRaw: String?, merchantKey: String?, last4: String?): Int {
        val target = state.value.firstOrNull { it.id == id } ?: return 0
        state.value = state.value.map {
            if (it.id == target.id) it.copy(
                merchantRaw = it.merchantRaw ?: merchantRaw,
                merchantKey = it.merchantKey ?: merchantKey,
                accountLast4 = it.accountLast4 ?: last4,
            ) else it
        }
        return 1
    }

    override suspend fun allWithBody(): List<TransactionEntity> = state.value.filter { it.rawText != null }

    override suspend fun withBodyOfType(spendingTypes: List<String>): List<TransactionEntity> =
        state.value.filter { it.rawText != null && it.type in spendingTypes }

    override suspend fun retype(id: String, type: String, categoryId: String?): Int {
        val target = state.value.firstOrNull { it.id == id } ?: return 0
        state.value = state.value.map {
            if (it.id != target.id) it
            // The real query leaves a hand-filed category alone. Reproduced here
            // because a pass that overwrites the user's own filing would otherwise
            // look correct in every test.
            else if (it.categorySource == "MANUAL") it.copy(type = type)
            else it.copy(type = type, categoryId = categoryId)
        }
        return 1
    }

    override suspend fun deleteAll(ids: List<String>): Int {
        val before = state.value.size
        state.value = state.value.filterNot { it.id in ids }
        return before - state.value.size
    }

    override suspend fun withoutBalance(): List<TransactionEntity> =
        state.value.filter { it.rawText != null && it.balanceKind == null }

    override suspend fun setBalance(id: String, halalas: Long?, kind: String): Int {
        val target = state.value.firstOrNull { it.id == id } ?: return 0
        state.value = state.value.map {
            if (it.id == target.id) it.copy(balanceHalalas = halalas, balanceKind = kind) else it
        }
        return 1
    }

    override suspend fun uncategorisedOrMerchant(merchantKey: String): List<TransactionEntity> =
        state.value.filter { it.merchantKey == merchantKey }

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
