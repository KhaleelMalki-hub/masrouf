package sa.masrouf.app

import androidx.room.withTransaction
import android.app.Application
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
            transactions.purgeCredentialBodies()
            transactions.reparseStoredBodies()
            transactions.backfillBalances()
            preferences.maintenanceVersion = 1
        }
        transactions.fileUncategorised()
    }

    val database: MasroufDatabase by lazy { MasroufDatabase.open(this) }
    val preferences: Preferences by lazy { Preferences(this) }
    val transactions: TransactionRepository by lazy { TransactionRepository(
            dao = database.transactions(),
            rules = database.merchantRules(),
            inTransaction = { block -> database.withTransaction(block) },
        ) }
}
