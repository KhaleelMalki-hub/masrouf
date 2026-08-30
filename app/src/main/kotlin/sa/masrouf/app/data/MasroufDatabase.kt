package sa.masrouf.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [TransactionEntity::class, MerchantRule::class],
    version = 6,
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
        /**
         * Records who filed each transaction, so re-filing can replace the app's
         * decisions and leave the user's alone.
         *
         * Existing filed rows are marked automatic; see [CategorySource.LEGACY] for
         * why that is the safe reading and what it costs.
         */
        /**
         * Records which bank's parser read each message.
         *
         * Left null for existing rows rather than guessed. The sender address is
         * what identifies the bank and it is not stored, and the body names its own
         * bank in only about 1,000 of 22,000 real messages. The history fills in
         * when the message inbox is read again, which is exact: the same message
         * produces the same fingerprint, so the row it belongs to is known and not
         * matched by resemblance.
         */
        /**
         * Records what each message said was left afterwards.
         *
         * Columns only. The existing rows are filled in by [BalanceBackfill] on the
         * next launch rather than here, because reading 22,000 message bodies
         * belongs on a background thread with a progress state, not inside the
         * migration that has to finish before the first screen can open.
         */
        /** In order, for the app and for the migration test alike. */
        val ALL_MIGRATIONS: Array<Migration>
            get() = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN balance_halalas INTEGER")
                db.execSQL("ALTER TABLE transactions ADD COLUMN balance_kind TEXT")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN bank_id TEXT")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN category_source TEXT")
                db.execSQL(
                    "UPDATE transactions SET category_source = 'AUTOMATIC' " +
                        "WHERE category_id IS NOT NULL"
                )
                renameMerchantRuleColumns(db)
            }
        }

        /**
         * Repairs a `merchant_rules` created by the first spelling of version 3.
         *
         * Version 3 was installed on two devices with `merchantKey`/`categoryId`,
         * then the migration that creates the table was edited in place to the
         * snake_case names the rest of the schema uses. Editing a migration that
         * has already run does not re-run it: both devices kept the old columns and
         * Room refused to open the database at all, with "Migration didn't properly
         * handle: merchant_rules".
         *
         * So version 3 exists in two shapes and this has to recognise both. The
         * rows are the user's own filing decisions and are carried across rather
         * than dropped, even though the table happened to be nearly empty when this
         * was found.
         */
        private fun renameMerchantRuleColumns(db: SupportSQLiteDatabase) {
            val isLegacy = db.query("PRAGMA table_info(merchant_rules)").use { columns ->
                val name = columns.getColumnIndexOrThrow("name")
                generateSequence { if (columns.moveToNext()) columns.getString(name) else null }
                    .any { it == "merchantKey" }
            }
            if (!isLegacy) return

            db.execSQL("ALTER TABLE merchant_rules RENAME TO merchant_rules_legacy")
            db.execSQL(
                """
                CREATE TABLE merchant_rules (
                    merchant_key TEXT NOT NULL PRIMARY KEY,
                    category_id TEXT NOT NULL
                )
                """
            )
            db.execSQL(
                """
                INSERT INTO merchant_rules (merchant_key, category_id)
                SELECT merchantKey, categoryId FROM merchant_rules_legacy
                """
            )
            db.execSQL("DROP TABLE merchant_rules_legacy")
        }

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
                .addMigrations(*ALL_MIGRATIONS)
                .build()
    }
}
