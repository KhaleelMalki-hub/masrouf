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

    val database: MasroufDatabase by lazy { MasroufDatabase.open(this) }
    val preferences: Preferences by lazy { Preferences(this) }
    val transactions: TransactionRepository by lazy { TransactionRepository(
            dao = database.transactions(),
            rules = database.merchantRules(),
            inTransaction = { block -> database.withTransaction(block) },
        ) }
}
