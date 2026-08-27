package sa.masrouf.app

import android.app.Application
import sa.masrouf.app.data.MasroufDatabase
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
    val transactions: TransactionRepository by lazy { TransactionRepository(database.transactions()) }
}
