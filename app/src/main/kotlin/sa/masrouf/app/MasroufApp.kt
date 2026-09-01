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
import sa.masrouf.app.data.CURRENT_MAINTENANCE_VERSION
import sa.masrouf.app.data.Preferences
import sa.masrouf.app.data.TransactionRepository
import sa.masrouf.app.ui.CreditCards
import sa.masrouf.core.capture.AccountOwner

/**
 * Holds the one database and the one repository.
 *
 * No dependency-injection framework. This is a single-user app with one graph and
 * one wiring of it; a container here would be configuration for a value that never
 * changes.
 */
class MasroufApp : Application() {

    /**
     * Hands the personal values to the code that needs them.
     *
     * They come from `local.properties` through BuildConfig, so they are on this
     * device and not in the repository. Done in `onCreate` because every capture
     * path - the notification listener, the SMS receiver, the launch-time catch-up
     * - runs after it, and an owner matcher configured late would file the first
     * message of a session as a transfer to a stranger.
     */
    override fun onCreate() {
        super.onCreate()
        AccountOwner.configure(BuildConfig.OWNER_NAMES)
        CreditCards.configure(BuildConfig.CARD_LIMITS)
    }

    /**
     * One-off repairs over stored data, each run at most once per install.
     *
     * Every launch used to re-read 22,000 bodies through the gate and re-parse
     * 14,000 of them inside one transaction, which held the database for ten
     * seconds while the screen showed an empty month as if it were true. A stamp in
     * preferences says how far the history has been brought forward.
     *
     * ## Why a set and not a ladder
     *
     * Each version below names the repairs that version introduced. An install
     * several versions behind used to run them in sequence, and because most
     * repairs reuse an existing pass, that meant the same full-table scan four
     * times over: eight scans where four would do, growing by one with every fix.
     *
     * They are idempotent, so what matters is that each runs ONCE and that they run
     * in dependency order. The union is taken, then executed in [Repair]'s declared
     * order, which is that order: purge what should not exist, repair what is
     * wrong, retype what is misfiled, and only then file - because filing reads the
     * merchant and the type that the three before it correct.
     *
     * A fresh install stamps the current version without running anything: every
     * repair here corrects what an OLDER pipeline stored, and the current pipeline
     * does not produce it. That invariant is what makes skipping safe, and
     * [MaintenanceOrderTest] holds it.
     */
    suspend fun runMaintenance() {
        val done = preferences.maintenanceVersion
        val repairs = Repair.entries.filter { done < it.introducedIn }.toSortedSet()

        for (repair in repairs) {
            when (repair) {
                Repair.PURGE_REJECTED -> transactions.purgeRejectedBodies()
                Repair.REPAIR_AMOUNTS -> transactions.repairAmounts()
                Repair.REPAIR_PARTIES -> transactions.repairNumericParties()
                Repair.BACKFILL_BALANCES -> transactions.backfillBalances()
                Repair.REPARSE_BODIES -> transactions.reparseStoredBodies()
                Repair.RETYPE_SALARY -> transactions.retypeSalaryDeposits()
                Repair.RETYPE_OWN_MONEY -> transactions.retypeOwnMoney()
                Repair.REFILE_ALL -> transactions.refileAll()
            }
        }
        preferences.maintenanceVersion = CURRENT_MAINTENANCE_VERSION

        transactions.fileUncategorised()
        catchUpOnSms()
    }

    /**
     * The repairs, in the order they must run.
     *
     * Declaration order IS dependency order and the enum's natural ordering is what
     * the pass set is sorted by, so a repair added in the wrong place changes
     * behaviour silently. [MaintenanceOrderTest] asserts the shape of it.
     *
     * @param introducedIn the maintenance version that first needed this repair. An
     *   install at or past it has already had it.
     */
    enum class Repair(val introducedIn: Int) {
        /** Bodies the gate now refuses. First: a credential must not be read again. */
        PURGE_REJECTED(12),

        /** Amounts the extractor now reads differently. Before anything reads them. */
        REPAIR_AMOUNTS(10),

        /** Account numbers standing in for a party. Re-parses, so before filing. */
        REPAIR_PARTIES(13),

        /** Balances never read out of bodies that carry one. */
        BACKFILL_BALANCES(1),

        /**
         * Merchants and cards an older parser could not extract.
         *
         * Raised to 14 for AlRajhi's reversed card field: "عبر:فيزا;8134" where it
         * had always written "عبر8134;فيزا". Ten settlements were stored with the
         * right amount and balance and no card, so a card paid off in full still
         * showed the figure from before it was paid - the balance was in the
         * database, attached to nothing.
         */
        REPARSE_BODIES(14),

        /** Salary deposits an older classifier read as transfers. */
        RETYPE_SALARY(3),

        /** The user's own money, wherever it is still counted as spending. */
        RETYPE_OWN_MONEY(7),

        /** Last: filing reads the merchant and the type everything above corrects. */
        REFILE_ALL(13),
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
