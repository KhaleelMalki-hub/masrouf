package sa.masrouf.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [TransactionEntity::class, MerchantRule::class],
    version = 3,
    exportSchema = true,
)
abstract class MasroufDatabase : RoomDatabase() {

    abstract fun transactions(): TransactionDao

    abstract fun merchantRules(): MerchantRuleDao

    companion object {
        private const val NAME = "masrouf.db"

        /**
         * Adds the card fragment that deduplication needs.
         *
         * Nullable and backfilled with nothing, because the rows already stored were
         * written without it and there is nowhere to recover it from - the fragment
         * was never in the database to begin with. Those rows keep matching
         * permissively, which is the behaviour they already had.
         */
        /**
         * Adds the table the app learns filing decisions into.
         *
         * Nothing to backfill: before this existed the user could not express a
         * decision about a merchant, so there are none to carry forward.
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS merchant_rules (
                        merchant_key TEXT NOT NULL PRIMARY KEY,
                        category_id TEXT NOT NULL
                    )
                    """
                )
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN account_last4 TEXT")
            }
        }

        /**
         * Opens the on-device database.
         *
         * No `fallbackToDestructiveMigration`. This is the user's only copy of their
         * own financial history and there is no server to restore it from, so a
         * missing migration must fail loudly at open time rather than silently
         * delete every transaction they have ever recorded.
         */
        fun open(context: Context): MasroufDatabase =
            Room.databaseBuilder(context.applicationContext, MasroufDatabase::class.java, NAME)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
    }
}
