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

    @Query("SELECT * FROM transactions ORDER BY occurred_at_millis DESC LIMIT :limit")
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

    @Query("SELECT COUNT(*) FROM transactions WHERE fingerprint = :fingerprint")
    suspend fun countByFingerprint(fingerprint: String): Int

    @Query("SELECT COUNT(*) FROM transactions WHERE status = 'PENDING'")
    fun observePendingCount(): Flow<Int>
}
