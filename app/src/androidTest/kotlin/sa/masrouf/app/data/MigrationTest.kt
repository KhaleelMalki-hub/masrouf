package sa.masrouf.app.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The real migrations, run forward over a real version-1 database.
 *
 * Unit tests cannot do this: Room's migrations are SQL against SQLite, and the
 * one that matters most - the repair in 3 to 4 - exists because a migration that
 * had already run on two devices was edited in place, and the app then refused to
 * open at all. Nothing but running the chain on a device catches that class.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MasroufDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrates_from_1_to_7_and_keeps_the_rows() {
        helper.createDatabase(DB, 1).use { db ->
            db.execSQL(
                """
                INSERT INTO transactions
                  (id, amount_halalas, direction, type, occurred_at_millis, account_id, category_id,
                   merchant_raw, merchant_key, note, source, status, fingerprint, raw_text, currency)
                VALUES ('t1', 12345, 'DEBIT', 'PURCHASE', 1700000000000, NULL, 'food',
                        'ALBAIK', 'ALBAIK', NULL, 'SMS', 'CONFIRMED', 'fp-1', 'شراء\nرصيد:35409.48 SR', 'SAR')
                """
            )
        }

        val db = helper.runMigrationsAndValidate(DB, 7, true, *MasroufDatabase.ALL_MIGRATIONS)

        db.query("SELECT category_source, account_last4, bank_id, balance_kind FROM transactions WHERE id = 't1'").use { c ->
            assertEquals(1, c.count)
            c.moveToFirst()
            // 3 to 4 marks an existing filed row as the app's decision.
            assertEquals("AUTOMATIC", c.getString(0))
            assertEquals(null, c.getString(1))
            assertEquals(null, c.getString(2))
            // 5 to 6 adds the column only; the body is read on the next launch.
            assertEquals(null, c.getString(3))
        }

        // 6 to 7 is three indexes, and `runMigrationsAndValidate` above has already
        // compared them against the entity's own declaration - the check that catches
        // a hand-named index, which Room would otherwise reject on the next launch
        // rather than here.
        db.query("SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = 'transactions'").use { c ->
            val names = buildList { while (c.moveToNext()) add(c.getString(0)) }
            assertEquals(true, names.contains("index_transactions_status"))
            assertEquals(true, names.contains("index_transactions_account_last4"))
            assertEquals(true, names.contains("index_transactions_merchant_key"))
        }
    }

    /**
     * Version 3 shipped in two shapes. The first spelt merchant_rules' columns
     * merchantKey/categoryId; the migration creating it was then edited in place,
     * and both devices that had the first shape refused to open. 3 to 4 has to
     * recognise the old shape and carry the rows across.
     */
    @Test
    fun repairs_the_first_shape_of_merchant_rules_without_losing_rules() {
        helper.createDatabase(DB, 2).use { db ->
            // Reproduce what the first version 3 created, by hand, then stamp 3.
            db.execSQL("CREATE TABLE merchant_rules (merchantKey TEXT NOT NULL PRIMARY KEY, categoryId TEXT NOT NULL)")
            db.execSQL("INSERT INTO merchant_rules VALUES ('AMMAR', 'food')")
            db.execSQL("PRAGMA user_version = 3")
        }

        val db = helper.runMigrationsAndValidate(DB, 7, true, *MasroufDatabase.ALL_MIGRATIONS)

        db.query("SELECT merchant_key, category_id FROM merchant_rules").use { c ->
            assertEquals(1, c.count)
            c.moveToFirst()
            assertEquals("AMMAR", c.getString(0))
            assertEquals("food", c.getString(1))
        }
    }

    private companion object {
        const val DB = "migration-test"
    }
}
