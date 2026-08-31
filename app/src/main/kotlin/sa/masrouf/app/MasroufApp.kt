package sa.masrouf.app

import androidx.room.withTransaction
import android.app.Application
import java.time.Duration
import sa.masrouf.app.capture.SmsInbox
import sa.masrouf.app.capture.HistoryImport
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import android.Manifest
import sa.masrouf.app.data.MasroufDatabase
import sa.masrouf.app.data.Preferences
import sa.masrouf.app.data.TransactionRepository

/**
 * Holds the one database and the one repository.
 *
 * No dependency-injection framework. This is a single-user app with one graph and
 * one wiring of it; a container here would be configuration for a value that never
 * changes.
 */
class MasroufApp : Application() {

    /**
     * One-off passes over stored data, each run exactly once per install.
     *
     * Every launch used to re-read 22,000 bodies through the gate and re-parse
     * 14,000 of them inside one transaction, which held the database for ten
     * seconds while the screen showed an empty month as if it were true. A stamp
     * in preferences says which passes have run; a new pass bumps the version.
     *
     * The filing pass has no version: it only touches rows with no category and
     * is cheap when there are none.
     */
    suspend fun runMaintenance() {
        val done = preferences.maintenanceVersion
        if (done < 1) {
            transactions.purgeRejectedBodies()
            transactions.reparseStoredBodies()
            transactions.backfillBalances()
            preferences.maintenanceVersion = 1
        }
        if (done < 2) {
            // The gate learned card-limit notices after pass 1 had run.
            transactions.purgeRejectedBodies()
            preferences.maintenanceVersion = 2
        }
        if (done < 3) {
            // "ايداع رواتب" was read as a transfer for five years.
            transactions.retypeSalaryDeposits()
            preferences.maintenanceVersion = 3
        }
        if (done < 4) {
            // The gate learned the credit-card statement notice, and the classifier
            // learned the three ways the user's own money was leaving the total:
            // card settlements, SADAD billers that are their own cards, and
            // transfers to themselves. The purge runs first so the notices are gone
            // before anything tries to re-type them.
            transactions.purgeRejectedBodies()
            transactions.retypeOwnMoney()
            preferences.maintenanceVersion = 4
        }
        transactions.fileUncategorised()
        catchUpOnSms()
    }

    /**
     * Reads the messages that arrived since the newest stored one.
     *
     * The receiver captures live, and it can be killed, throttled, or - as
     * happened - out-argued by the duplicate detector on the second of two
     * purchases a minute apart. Re-reading the tail of the inbox on every launch
     * means a miss costs one launch, not a manual re-import. Two days back rather
     * than the exact instant, because message timestamps and capture timestamps
     * are not the same clock. Deduplication keeps the overlap from doubling
     * anything; that is what it is for.
     */
    private suspend fun catchUpOnSms() {
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) return
        val since = transactions.latestSmsAt()?.minus(Duration.ofDays(2)) ?: return
        val recent = SmsInbox(contentResolver).read(since = since, newestFirst = false)
        if (recent.isEmpty()) return
        HistoryImport(transactions).run(recent)
    }

    val database: MasroufDatabase by lazy { MasroufDatabase.open(this) }
    val preferences: Preferences by lazy { Preferences(this) }
    val transactions: TransactionRepository by lazy { TransactionRepository(
            dao = database.transactions(),
            rules = database.merchantRules(),
            inTransaction = { block -> database.withTransaction(block) },
        ) }
}
