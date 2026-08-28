package sa.masrouf.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    /**
     * @return the new row id, or -1 when the fingerprint was already stored.
     *
     * Ignoring the conflict rather than replacing it is the point: a re-read of the
     * same notification, or a second import of the same statement file, must not
     * overwrite a record the user has since confirmed or re-categorised.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(transaction: TransactionEntity): Long

    /**
     * Confirmed history, newest first.
     *
     * Pending records are excluded because they have their own section on screen.
     * Without this filter each captured row rendered twice - once awaiting
     * confirmation and once in the history beneath it - offering two different
     * destructive actions for one transaction.
     */
    @Query(
        """
        SELECT * FROM transactions
        WHERE status = 'CONFIRMED'
        ORDER BY occurred_at_millis DESC
        LIMIT :limit
        """
    )
    fun observeRecent(limit: Int): Flow<List<TransactionEntity>>

    /**
     * Everything in a half-open instant range, newest first.
     *
     * The range is passed in already resolved rather than computed in SQL, because
     * SQLite would have to be told about Riyadh's offset to do it and that is
     * `RiyadhTime`'s single job.
     */
    @Query(
        """
        SELECT * FROM transactions
        WHERE occurred_at_millis >= :fromMillis AND occurred_at_millis < :untilMillis
        ORDER BY occurred_at_millis DESC
        """
    )
    fun observeBetween(fromMillis: Long, untilMillis: Long): Flow<List<TransactionEntity>>

    /**
     * Everything close enough in time to be the same real-world event, for
     * reconciliation. One-shot rather than a Flow: this answers a question asked at
     * the moment of writing, and observing it would invite deciding twice.
     */
    @Query(
        """
        SELECT * FROM transactions
        WHERE occurred_at_millis >= :fromMillis AND occurred_at_millis <= :untilMillis
        """
    )
    suspend fun neighbours(fromMillis: Long, untilMillis: Long): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE status = 'PENDING' ORDER BY occurred_at_millis DESC")
    fun observePending(): Flow<List<TransactionEntity>>

    /**
     * @return rows changed: 1 on success, 0 when the record was already confirmed
     *   or no longer exists.
     *
     * The `status = 'PENDING'` guard is the point. Without it a stale id from a
     * screen that has not caught up would re-confirm a record, or confirm one the
     * user had already dismissed, and the write would look successful either way.
     */
    @Query("UPDATE transactions SET status = 'CONFIRMED' WHERE id = :id AND status = 'PENDING'")
    suspend fun confirm(id: String): Int

    /**
     * Confirms every pending record at once.
     *
     * @return how many were confirmed.
     *
     * The per-record rule stands: nothing auto-confirms. This is not the app
     * deciding, it is the user vouching for a batch in one action, which is the
     * only way a bulk import is usable - 1,664 records reviewed one at a time is a
     * pile nobody works through, and a pile nobody works through protects nothing.
     */
    @Query("UPDATE transactions SET status = 'CONFIRMED' WHERE status = 'PENDING'")
    suspend fun confirmAllPending(): Int

    /**
     * Deletes a captured record the user rejected.
     *
     * Guarded to PENDING so this can never remove something already confirmed.
     *
     * ponytail: deleting frees the fingerprint, so an identical message redelivered
     * later reappears. That needs the same second on the device clock, so it is rare
     * and costs a second dismissal rather than wrong money. A REJECTED status would
     * close it properly, and that means a new value on the core Status enum.
     */
    @Query("DELETE FROM transactions WHERE id = :id AND status = 'PENDING'")
    suspend fun dismiss(id: String): Int

    /**
     * Deletes a record outright, whatever its status.
     *
     * Unguarded, unlike [dismiss], because this is the user deliberately removing
     * something they can see on screen rather than a background path acting on an
     * id it was handed.
     */
    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun delete(id: String): Int

    /**
     * Files a record under a category.
     *
     * Unguarded on status: a pending record is categorised as it is confirmed, and
     * a confirmed one can be refiled later when the first guess turns out wrong.
     */
    @Query(
        """
        UPDATE transactions SET category_id = :categoryId, category_source = :source
        WHERE id = :id
        """
    )
    suspend fun setCategory(id: String, categoryId: String?, source: String?): Int

    /**
     * When the earliest stored transaction happened, or null on an empty database.
     *
     * Bounds how far back the month navigation goes. Without it a user can page
     * backwards for ever through months that never had anything in them, which
     * looks like data loss rather than like the end of the record.
     */
    @Query("SELECT MIN(occurred_at_millis) FROM transactions")
    fun observeEarliest(): Flow<Long?>

    /**
     * Every Riyadh month that has at least one record, newest first.
     *
     * A real history spans 146 months, so the picker cannot offer a flat range and
     * hope: it has to show which months are worth opening. Grouped in SQL rather
     * than by loading 22,000 rows to find out.
     */
    @Query(
        """
        SELECT DISTINCT strftime('%Y-%m', occurred_at_millis/1000, 'unixepoch', '+3 hours') AS month
        FROM transactions
        ORDER BY month DESC
        """
    )
    fun observeMonthsWithData(): Flow<List<String>>

    /**
     * Files every transaction from one merchant at once.
     *
     * @return how many were refiled.
     */
    @Query(
        """
        UPDATE transactions SET category_id = :categoryId, category_source = :source
        WHERE merchant_key = :merchantKey
        """
    )
    suspend fun setCategoryForMerchant(
        merchantKey: String,
        categoryId: String?,
        source: String?,
    ): Int

    /** Everything with no category yet, for a one-off backfill over the history. */
    @Query("SELECT * FROM transactions WHERE category_id IS NULL")
    suspend fun uncategorised(): List<TransactionEntity>

    /**
     * Clears every category the app itself filed, leaving the user's alone.
     *
     * The first half of re-filing. A row the user chose is identified by its
     * `category_source`, not by whether the current rules would agree with it:
     * agreeing with a guess is still a decision, and re-deriving it would throw the
     * decision away.
     *
     * @return how many were cleared.
     */
    @Query(
        """
        UPDATE transactions SET category_id = NULL, category_source = NULL
        WHERE category_id IS NOT NULL AND category_source = 'AUTOMATIC'
        """
    )
    suspend fun clearAutomaticCategories(): Int
}
