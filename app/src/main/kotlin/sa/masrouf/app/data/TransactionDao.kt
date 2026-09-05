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
     * What arrived each month, split into salary and the employer's bonuses.
     *
     * Aggregated in SQL for the same reason as [observeMonthsWithData]: the answer
     * is one row per month over twelve years, and loading 22,000 rows to fold them
     * in Kotlin put the whole history through the main thread once per collection.
     *
     * Confirmed only. A pending row is a parser's reading that nobody has agreed
     * to, and this screen exists to be read as fact.
     *
     * The category ids are BOUND, not spelled - but this query binds them ONE BY
     * ONE, because it also splits them: the screen shows salary and bonus as two
     * segments of one bar, and a list cannot express that. So it takes the two ids
     * separately while the rows query below takes the whole of
     * [sa.masrouf.core.model.INCOME_CATEGORY_IDS], and the two can drift: add a
     * third income category and the deposit list would show deposits this header
     * does not count - the exact failure the comment two functions down describes.
     * `IncomeIdentityTest` fails the moment that list stops being these two ids.
     *
     * The `+3 hours` is Riyadh, matching every other month boundary in this file;
     * a month bucketed in UTC puts a salary that arrived at 02:25 on the 1st into
     * the previous month.
     */
    @Query(
        """
        SELECT strftime('%Y-%m', occurred_at_millis/1000, 'unixepoch', '+3 hours') AS month,
               SUM(CASE WHEN category_id = :salaryId THEN amount_halalas ELSE 0 END) AS salaryHalalas,
               SUM(CASE WHEN category_id = :bonusId  THEN amount_halalas ELSE 0 END) AS bonusHalalas
        FROM transactions
        WHERE direction = 'CREDIT' AND status = 'CONFIRMED'
          AND category_id IN (:salaryId, :bonusId)
        GROUP BY month
        ORDER BY month DESC
        """
    )
    fun observeIncomeByMonth(salaryId: String, bonusId: String): Flow<List<IncomeMonthRow>>

    /**
     * The individual deposits behind [observeIncomeByMonth].
     *
     * Loaded whole rather than a month at a time, which the comment on the
     * aggregate above would seem to argue against - but the two are different
     * sizes. That one would have folded 22,000 rows; this one is filtered to two
     * categories and returns about 220 in twelve years, and a month opened on tap
     * has to be instant.
     *
     * A month's total says one figure arrived. It does not say whether that was a
     * salary and one bonus or a salary and three, which is the question its owner
     * asked next.
     */
    @Query(
        """
        SELECT * FROM transactions
        WHERE direction = 'CREDIT' AND status = 'CONFIRMED'
          AND category_id IN (:incomeIds)
        ORDER BY occurred_at_millis DESC
        """
    )
    fun observeIncomeRows(incomeIds: List<String>): Flow<List<TransactionEntity>>

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
     * Every stored body that names a card, for [sa.masrouf.core.model.CardKinds].
     *
     * Read once at launch rather than watched: what kind a card is does not change,
     * and folding twenty-six thousand bodies on every database write would cost the
     * dashboard its first frame - the lesson `MerchantMatch.Rules` already records.
     */
    @Query(
        """
        SELECT account_last4 AS last4, raw_text AS body FROM transactions
        WHERE account_last4 IS NOT NULL AND raw_text IS NOT NULL
        """
    )
    suspend fun cardBodies(): List<CardBody>

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

    /**
     * Clears a party that is really an account number.
     *
     * 2,014 rows carried "104*010" or "3016" where a name belonged, because the
     * pattern that read the party matched the account line. Cleared rather than
     * corrected here, so that [TransactionRepository.reparseStoredBodies] - which
     * only ever fills a gap - can read the name the message actually carries. A
     * merchant the user filed by hand is left alone.
     *
     * GLOB, not LIKE: SQLite's LIKE is case-insensitive for ASCII and has no
     * character classes, so this is the only way to say "digits and punctuation,
     * no letters" in a query.
     */
    @Query(
        """
        UPDATE transactions
        SET merchant_raw = NULL, merchant_key = NULL
        WHERE raw_text IS NOT NULL
          AND merchant_key IS NOT NULL
          AND merchant_key GLOB '*[0-9]*'
          AND merchant_key NOT GLOB '*[A-Za-z]*'
          AND merchant_key NOT GLOB '*[أ-ي]*'
          AND (category_source IS NULL OR category_source <> 'MANUAL')
        """
    )
    suspend fun clearNumericParties(): Int

    /**
     * Corrects one row's amount.
     *
     * The only write in this file that touches a figure the user may have seen, so
     * it is deliberately narrow: one row, one field, by id. A row the user entered
     * by hand is never a candidate - callers select on a stored body, which only a
     * captured row has.
     */
    @Query("UPDATE transactions SET amount_halalas = :halalas WHERE id = :id AND source <> 'MANUAL'")
    suspend fun setAmount(id: String, halalas: Long): Int

    /** Rows whose stored body is a credential. They should never have existed. */
    @Query("SELECT * FROM transactions WHERE raw_text IS NOT NULL")
    suspend fun allWithBody(): List<TransactionEntity>

    @Query("DELETE FROM transactions WHERE id IN (:ids)")
    suspend fun deleteAll(ids: List<String>): Int

    /**
     * Re-types the salary deposits an older classifier read as transfers.
     *
     * Targeted rather than a general re-classification: a general pass would
     * re-decide every row's type from its body, and a type the user has vouched
     * for is not something to re-decide in a maintenance pass.
     *
     * The type moves either way - a salary deposit is a salary whatever it was
     * filed under - but a category the user chose survives, and so does the
     * provenance that says they chose it. Rewriting `category_source` to AUTOMATIC
     * destroys the one thing rule 9 says cannot be recovered: whether a person
     * agreed with a guess or made a decision.
     */
    @Query(
        """
        UPDATE transactions
        SET type = 'SALARY',
            category_id = CASE WHEN category_source = 'MANUAL' THEN category_id ELSE 'income' END,
            category_source = CASE WHEN category_source = 'MANUAL' THEN 'MANUAL' ELSE 'AUTOMATIC' END
        WHERE direction = 'CREDIT' AND type <> 'SALARY' AND raw_text LIKE '%ايداع رواتب%'
        """
    )
    suspend fun retypeSalaryDeposits(): Int

    /**
     * Stored rows that currently count towards the spending total, with their body.
     *
     * The caller passes the list of spending types rather than this query naming
     * them, so [sa.masrouf.core.model.TransactionType.countsAsSpending] stays the
     * one place that decides what spending is. An SQL literal here would be a
     * second copy of that answer, and the two would part company the first time a
     * type changed sides.
     */
    @Query("SELECT * FROM transactions WHERE raw_text IS NOT NULL AND type IN (:spendingTypes)")
    suspend fun withBodyOfType(spendingTypes: List<String>): List<TransactionEntity>

    /**
     * Moves one row to a new type, and to the category that type implies.
     *
     * A category the user filed by hand is left alone: they looked at the row and
     * meant it, and the type being wrong is the app's mistake, not theirs.
     */
    @Query(
        """
        UPDATE transactions
        SET type = :type,
            category_id = CASE WHEN category_source = 'MANUAL' THEN category_id ELSE :categoryId END
        WHERE id = :id
        """
    )
    suspend fun retype(id: String, type: String, categoryId: String?): Int

    /**
     * The same, for a row whose DIRECTION was read backwards.
     *
     * Separate from [retype] because direction is the one field no other pass
     * changes: a type can be wrong and the money still moved the way the row says,
     * and every pass so far has been about the first. A reversal is the case where
     * both are wrong at once, and where leaving the direction alone would keep money
     * that came back counted as money that left.
     */
    @Query(
        """
        UPDATE transactions
        SET type = :type,
            direction = :direction,
            category_id = CASE WHEN category_source = 'MANUAL' THEN category_id ELSE :categoryId END
        WHERE id = :id AND source <> 'MANUAL'
        """
    )
    suspend fun redirect(id: String, type: String, direction: String, categoryId: String?): Int

    /**
     * The salary the bank last announced, in halalas. Read, not inferred.
     *
     * The largest of the three most recent, not simply the newest. A company the
     * owner holds shares in pays its dividends "بصيغة إيداع راتب" - the bank
     * message is word for word a salary deposit and only the company's own SMS says
     * otherwise - so on 21 July 2025 the newest salary was 50 riyals, and the
     * dashboard spent six days measuring his month against it.
     *
     * Three, because that is a quarter: long enough for one odd deposit to be
     * outvoted, short enough that a raise shows up the month it arrives.
     */
    @Query(
        """
        SELECT MAX(amount_halalas) FROM (
            SELECT amount_halalas FROM transactions
            WHERE type = 'SALARY' AND direction = 'CREDIT' AND status = 'CONFIRMED'
            ORDER BY occurred_at_millis DESC LIMIT 3
        )
        """
    )
    fun observeLatestSalary(): Flow<Long?>

    /** When the newest stored record happened, for the launch-time catch-up. */
    @Query("SELECT MAX(occurred_at_millis) FROM transactions WHERE source = 'SMS'")
    suspend fun latestSmsMillis(): Long?

    /** Every record of one merchant, for re-deriving its category from scratch. */
    @Query("SELECT * FROM transactions WHERE merchant_key = :merchantKey")
    suspend fun uncategorisedOrMerchant(merchantKey: String): List<TransactionEntity>

    /**
     * Files one merchant's records that arrived through one bank.
     *
     * For a name two shops share. A card network sends "Ammar" for a cafe and for
     * a bakery, and the only thing that tells them apart is which bank's message
     * announced the purchase.
     */
    @Query(
        """
        UPDATE transactions SET category_id = :categoryId, category_source = :source
        WHERE merchant_key = :merchantKey AND bank_id = :bankId
        """
    )
    suspend fun setCategoryForMerchantAtBank(
        merchantKey: String,
        bankId: String,
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
