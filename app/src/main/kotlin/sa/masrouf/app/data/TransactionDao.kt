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

    /**
     * Stamps a stored record with the bank whose parser read its message.
     *
     * Keyed on the fingerprint, which is derived from the message itself, so
     * re-reading the inbox finds exactly the row a message produced rather than one
     * that resembles it. Guarded on null so a re-read can never move a record from
     * one bank to another: if the answer ever differed, one of the two readings is
     * wrong and silently taking the newer one would hide that.
     *
     * @return 1 when a record was stamped, 0 when there was none or it already had
     *   a bank.
     */
    @Query(
        """
        UPDATE transactions SET bank_id = :bankId
        WHERE fingerprint = :fingerprint AND bank_id IS NULL
        """
    )
    suspend fun stampBank(fingerprint: String, bankId: String): Int

    /**
     * Every card, with the most recent figure its messages carried, if any.
     *
     * One row per card. The figure comes from the latest message that carried one
     * at all - not the latest message, which may have said nothing; a card's last
     * message being an OTP must not erase the balance from the purchase before it.
     * A card whose messages never carry a figure (barq, Al Rajhi, D360 do not) is
     * still a row, with nulls: it exists and should be shown as such.
     */
    @Query(
        """
        SELECT c.account_last4 AS last4,
               b.balance_halalas AS halalas,
               b.balance_kind AS kind,
               c.latest AS atMillis,
               (SELECT bank_id FROM transactions
                WHERE account_last4 = c.account_last4 AND bank_id IS NOT NULL
                ORDER BY occurred_at_millis DESC LIMIT 1) AS bankId
        FROM (
            SELECT account_last4, MAX(occurred_at_millis) AS latest
            FROM transactions WHERE account_last4 IS NOT NULL
            GROUP BY account_last4
        ) c
        LEFT JOIN (
            SELECT t.account_last4, t.balance_halalas, t.balance_kind
            FROM transactions t
            JOIN (
                SELECT account_last4, MAX(occurred_at_millis) AS latest
                FROM transactions
                WHERE account_last4 IS NOT NULL AND balance_halalas IS NOT NULL
                GROUP BY account_last4
            ) n ON n.account_last4 = t.account_last4 AND n.latest = t.occurred_at_millis
            WHERE t.balance_halalas IS NOT NULL
        ) b ON b.account_last4 = c.account_last4
        ORDER BY c.latest DESC
        """
    )
    fun observeCardBalances(): Flow<List<CardBalance>>

    /** Rows whose body has not yet been read for a balance. For the one-off backfill. */
    @Query(
        """
        SELECT * FROM transactions
        WHERE raw_text IS NOT NULL AND balance_kind IS NULL
        """
    )
    suspend fun withoutBalance(): List<TransactionEntity>

    /**
     * Records a reading, or that there was none.
     *
     * [kind] is written as `NONE` when the body carried no figure, so the backfill
     * does not re-read the same 7,000 bodies on every launch.
     */
    @Query(
        """
        UPDATE transactions SET balance_halalas = :halalas, balance_kind = :kind
        WHERE id = :id
        """
    )
    suspend fun setBalance(id: String, halalas: Long?, kind: String): Int

    /** Which bank each card belongs to, for the records that know. */
    @Query(
        """
        SELECT DISTINCT account_last4 AS last4, bank_id AS bankId FROM transactions
        WHERE account_last4 IS NOT NULL AND bank_id IS NOT NULL
        """
    )
    fun observeCardBanks(): Flow<List<CardBank>>

    /**
     * Every confirmed debit with a merchant, oldest first, for the recurring
     * detector. The whole history rather than a window, because a yearly payment
     * needs years to be seen; twelve thousand small rows is a fraction of a second.
     */
    @Query(
        """
        SELECT * FROM transactions
        WHERE status = 'CONFIRMED' AND direction = 'DEBIT' AND merchant_key IS NOT NULL
        ORDER BY occurred_at_millis
        """
    )
    fun observeConfirmedDebits(): Flow<List<TransactionEntity>>

    /** Stored bodies whose merchant or card was never read. For a one-off re-parse. */
    @Query(
        """
        SELECT * FROM transactions
        WHERE raw_text IS NOT NULL AND (merchant_key IS NULL OR account_last4 IS NULL)
        """
    )
    suspend fun withMissingParty(): List<TransactionEntity>

    /** Fills only what is missing. A merchant already stored is never rewritten. */
    @Query(
        """
        UPDATE transactions SET
            merchant_raw = COALESCE(merchant_raw, :merchantRaw),
            merchant_key = COALESCE(merchant_key, :merchantKey),
            account_last4 = COALESCE(account_last4, :last4)
        WHERE id = :id
        """
    )
    suspend fun fillParty(id: String, merchantRaw: String?, merchantKey: String?, last4: String?): Int

    /** Rows whose stored body is a credential. They should never have existed. */
    @Query("SELECT * FROM transactions WHERE raw_text IS NOT NULL")
    suspend fun allWithBody(): List<TransactionEntity>

    @Query("DELETE FROM transactions WHERE id IN (:ids)")
    suspend fun deleteAll(ids: List<String>): Int

    /** Every record of one merchant, for re-deriving its category from scratch. */
    @Query("SELECT * FROM transactions WHERE merchant_key = :merchantKey")
    suspend fun uncategorisedOrMerchant(merchantKey: String): List<TransactionEntity>

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
