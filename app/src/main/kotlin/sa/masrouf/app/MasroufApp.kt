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
import sa.masrouf.app.ui.CardIssuers
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
        // A repair that could not run must not be recorded as done. See below.
        var deferred = false

        for (repair in repairs) {
            when (repair) {
                Repair.PURGE_REJECTED -> transactions.purgeRejectedBodies()
                Repair.REPAIR_AMOUNTS -> transactions.repairAmounts()
                Repair.REPAIR_PARTIES -> transactions.repairNumericParties()
                Repair.BACKFILL_BALANCES -> transactions.backfillBalances()
                Repair.REPARSE_BODIES -> transactions.reparseStoredBodies()
                Repair.RETYPE_SALARY -> transactions.retypeSalaryDeposits()
                Repair.RETYPE_OWN_MONEY -> transactions.retypeOwnMoney()
                Repair.RETYPE_REVERSALS -> transactions.retypeMisreadDirections()
                Repair.RETYPE_OWN_DIRECTION -> transactions.retypeMisreadDirections()
                Repair.RETYPE_INBOUND -> transactions.retypeMisreadDirections()
                Repair.RETYPE_WAGES -> transactions.retypeMisreadDirections()
                Repair.RENUMBER_CARDS -> transactions.renumberCards(CardIssuers.REISSUED)
                Repair.REREAD_WHOLE_INBOX -> if (!rereadWholeInbox()) deferred = true
                Repair.REFILE_ALL -> transactions.refileAll()
            }
        }
        // A repair that could not run leaves the stamp below the version that
        // introduced it, so the next launch tries again.
        //
        // Written after READ_SMS was found ungranted on the owner's phone. Every
        // path that reads the inbox checked the permission and returned quietly,
        // so the launch catch-up documented as "a miss costs one launch" had never
        // run at all, and the one-off re-read that recovers seven years of a wallet
        // reported success having done nothing. A silent no-op that stamps itself
        // complete is worse than a failure: it cannot be retried, because nothing
        // knows it did not happen.
        preferences.maintenanceVersion = if (deferred) {
            // Never BELOW where this install already was. The floor used to be a
            // literal, so an install past the deferred repair would have been walked
            // backwards and every repair between here and there re-run - or, worse,
            // a future repair introduced in that gap marked complete by a pass that
            // never touched it. Nothing sits in that gap today; the next one added
            // would have found this the hard way.
            maxOf(done, minOf(CURRENT_MAINTENANCE_VERSION, Repair.REREAD_WHOLE_INBOX.introducedIn - 1))
        } else {
            CURRENT_MAINTENANCE_VERSION
        }

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
        /**
         * Bodies the gate now refuses. First: a credential must not be read again.
         *
         * Raised to 15 for "رفض العملية" - the active voice of a refusal, which the
         * passive markers beside it never reached, so a purchase the bank declined
         * on a cancelled card was stored as money spent.
         *
         * Raised to 26 for the gate's new marketing markers: four AlJazira adverts
         * ("قسط مشترياتك ... بمبلغ 1,499 ريال أو أكثر") were stored as purchases of
         * 1,499 and 1,000 riyals. Raised to 27 the same day for nine more found
         * among the unfiled rows with no party, two of them at 8,000 riyals.
         */
        PURGE_REJECTED(27),

        /** Amounts the extractor now reads differently. Before anything reads them. */
        REPAIR_AMOUNTS(10),

        /**
         * An account number or a card standing in for a party. Re-parses, so before
         * filing.
         *
         * Raised to 45 for the card: SNB's 2017-2018 point-of-sale template names
         * the card on one `من` line and the shop on the next, and the party pattern
         * took the first. 179 purchases carried a card where a shop belonged - all
         * of them filed as transfers, none of them fileable - and 59 refunds
         * carried the credit card. Measured old-against-new over all 26,429 stored
         * bodies: 179 gain a real merchant, 59 lose a wrong one, nothing else in the
         * history moves.
         *
         * Raised to 46 the same day, because 45 fixed one bank. The clear ran and
         * the re-parse put the card straight back: seven profiles carry the same
         * `^من` line pattern and the guard had gone into SNB's copy of it, so 113
         * rows on the other banks were cleared and refilled with the same card.
         * The guard is now in `BankMessageParser.firstMatch`, where all seven
         * route through, and those rows lose the card for good.
         *
         * Raised to 47 for what 46 produced. Skipping the card makes the pattern
         * walk on, and a walk that keeps going finds something: on a body naming
         * no shop at all it reached "للتفاصيل http://alah.li/mobile", so seventeen
         * purchases were filed as BILLS by a link to the bank's app. Measured at
         * exactly those seventeen over all 26,434 stored bodies.
         */
        REPAIR_PARTIES(47),

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
         *
         * Raised to 15 for the senders that end a field with something other than a
         * newline: a carriage return, the two characters `^M`, a pipe, a run of
         * padding. Normalisation folded all of them into spaces, so every
         * line-anchored field pattern stopped matching at once and 588 records were
         * stored with no party at all - unfileable, since a category is learned
         * from a merchant. Measured at 337 recovered on the owner's history.
         *
         * Raised to 27 for SNB's 2014-2015 one-line template, whose shop sits
         * after فى with an alef maksura: 30 records, 62,000 riyals, no party.
         *
         * Raised to 45 with REPAIR_PARTIES above: that pass clears a party that is
         * a card, and this one is what reads the shop out of the body afterwards.
         * The two are one repair in two halves and their versions must move
         * together - clearing without re-reading leaves 238 rows with no party at
         * all.
         *
         * Raised to 46 with it, for the same reason and by the same rule: the two
         * halves of one repair move together. And to 47 with it, for the same
         * reason a third time.
         */
        REPARSE_BODIES(47),

        /** Salary deposits an older classifier read as transfers. */
        RETYPE_SALARY(3),

        /**
         * The user's own money, wherever it is still counted as spending.
         *
         * Raised to 18 when STC Pay was recognised as one of his own wallets: 670
         * top-ups of it, 650,280 riyals, were stored as purchases at a shop of that
         * name and counted as money spent.
         *
         * Raised to 26 for urpay, the same again at a smaller scale (26 top-ups),
         * and for "حوالة بين حساباتك" - AlRajhi's and SNB's noun for a movement
         * between the owner's own accounts, which the classifier knew only as
         * تحويل: 220 rows counted as money leaving and 45 as money arriving. The
         * pass now visits incoming transfers too, for exactly those 45.
         */
        RETYPE_OWN_MONEY(26),

        /**
         * Reversals read backwards: money that came back, stored as money that left.
         *
         * AlAhli's card refunds say حوالة عكسية, and the classifier knew only
         * عملية عكسية - so حوالة carried them into the outgoing-transfer rules. 105
         * records, 12,568.63 riyals, every one counted as spending on top of the
         * purchase it refunds; and the mirror case, a reversed incoming transfer,
         * stored as arriving when the money had gone back out.
         *
         * Before REFILE_ALL, because a refund's category is not a transfer's.
         */
        RETYPE_REVERSALS(41),

        /**
         * The other wording whose direction was read backwards.
         *
         * "حوالة واردة بين حساباتك" is money arriving in the account that received
         * the message, and the rules called it money leaving - so eighty-seven
         * identical messages were stored forty-five one way and forty-two the other,
         * decided by whether the classifier or a later retype had read them. Neither
         * counts as spending, which is why no total ever disagreed and nothing found
         * it until the templates were grouped and compared with what they produced.
         *
         * The same pass as above: it re-reads a stored body and takes the parser's
         * answer where the two disagree.
         */
        RETYPE_OWN_DIRECTION(42),

        /**
         * The largest of the three, and the same shape as the other two.
         *
         * "تحويل من <a person> / مبلغ / حساب <his>" is money arriving, and it was
         * read as an ordinary outgoing transfer: 567 records, 846,912 riyals,
         * counted as spending for twelve years. The bank's own running balance
         * settles it - 332 of the rows that carry one show it rising by exactly the
         * amount, and not one shows it falling.
         */
        RETYPE_INBOUND(43),

        /**
         * Wages abroad, read as the owner moving money to himself.
         *
         * barq names him on the `من:` line of every international transfer, because
         * he is the one sending it, and the demotion asked only whether his name
         * appeared. Thirty-one transfers, 49,580 riyals of a domestic worker's pay,
         * were filed as his own money and left his spending entirely.
         *
         * Same pass as the three above: re-read the stored body, take the parser's
         * answer where the two disagree. Measured before it shipped by running the
         * old and new classifiers over all 26,429 stored bodies - 31 rows move, all
         * of them barq international transfers, and nothing else in the history
         * changes at all.
         */
        RETYPE_WAGES(44),

        /**
         * The whole inbox, re-read once, because the app can now understand a
         * sender it never could.
         *
         * Every launch reads the tail of the inbox, which is enough for a message
         * that arrived while the receiver was asleep. It is not enough for 4,446
         * messages from 2019 onward that were passed over because no profile
         * claimed STC Pay - they are older than any tail. Deduplication is what
         * makes re-reading everything safe, and it is what it is for.
         *
         * Raised to 26 for urpay, Vision Bank and meem - 950 messages between them,
         * 2015 to 2026, none ever claimed.
         */
        /**
         * One card, under the two numbers the bank printed for it.
         *
         * 7404 and 2383 are the same AlRajhi credit card - his statement is headed
         * 2383 and 277 of its 298 transactions for September and October 2025 are
         * stored here under 7404. The app had 2,088 rows on a card it did not
         * recognise: no bank mark, no tile, no share of the limit.
         *
         * Before the filing, which reads nothing from the card, and after the
         * retypes, which do not touch it.
         */
        RENUMBER_CARDS(48),

        REREAD_WHOLE_INBOX(26),

        /** Last: filing reads the merchant and the type everything above corrects. */
        // 28: chocolate, nuts and dates moved to groceries by the owner's rule.
        // 29: the ninety-odd merchants he named and confirmed on 2026-09-02.
        // 30: the third wave, twenty more.
        // 31: the names recovered from the one-time-password messages.
        // 32: سيتي دبليو, named by the owner and confirmed by his own inbox.
        // 33: the Dubai terminal string, placed by the trip around it.
        // 34: four more names, once the code-message reader stopped requiring
        //     the label to start a line.
        // 35: three shops that texted him on the day he bought from them.
        // 36: the freelance licence, and the cabinet maker under its older name.
        // 37: the doubtful list settled, Ounass among it.
        // 38: الدهام للساعات.
        // 39: مؤسسة عبود باحشوان, Nissan and Haval parts.
        // 40: الخزائن الاحترافية, the wardrobe.
        // 43: the inbound transfers that were counted as spending.
        // 42: the incoming own-transfers, which now carry the other direction.
        // 41: after the reversals below were corrected - a refund that becomes a
        //     credit has a different category from the transfer it used to be.
        // 44: the wages abroad, which stop being own-money and become transfers out.
        // 45: the 179 shops recovered from behind a card, and لمسة شفرة, the barber
        //     the owner named - the plaza's name is what the terminal sends.
        // 46: the 119 rows whose card came back, on the six banks the first fix
        //     did not reach.
        // 47: the seventeen rows a link to the bank's app had filed as bills.
        // 48: اي هوم للمفروشات, the last merchant on the unplaced list, named by his
        //     card statement and confirmed by him.
        // 49: twenty-one more the statement named and he confirmed - and one he
        //     corrected: الإنجاز الفوري is a tyre shop, not a paperwork office.
        // 50: three more from the deep search - two confirmed by the history around
        //     the purchase, one by his memory of renting a car that week.
        // 51: the second deep pass - a Makkah clinic hiding behind a transliterated
        //     "مجمع", a ladies' salon named only by its register activity, Nahdi
        //     under its acronym, a gym and a mall shop he confirmed.
        REFILE_ALL(51),
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
    /**
     * Re-reads every message in the inbox, not just the tail.
     *
     * Run once, from the repair set, when a sender the app could not read becomes
     * one it can. Everything it produces lands PENDING like any other capture, and
     * the duplicate detector keeps the overlap with what is already stored from
     * doubling anything.
     */
    private suspend fun rereadWholeInbox(): Boolean {
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) return false
        val all = SmsInbox(contentResolver).read(newestFirst = false)
        if (all.isEmpty()) return false
        HistoryImport(transactions).run(all)
        return true
    }

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
