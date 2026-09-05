package sa.masrouf.app.data

import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.map
import sa.masrouf.core.capture.BankMessageParser
import sa.masrouf.core.capture.MessageGate
import sa.masrouf.core.capture.ParseResult
import sa.masrouf.core.capture.RawMessage
import sa.masrouf.core.capture.SaudiBanks
import sa.masrouf.core.capture.BalanceReader
import sa.masrouf.core.dedup.DuplicateDetector
import sa.masrouf.core.dedup.EventSignature
import sa.masrouf.core.dedup.Fingerprint
import sa.masrouf.core.model.Category
import sa.masrouf.core.model.CardKind
import sa.masrouf.core.model.CardKinds
import sa.masrouf.core.model.CategoryGuess
import sa.masrouf.core.model.Direction
import sa.masrouf.core.model.INCOME_CATEGORY_IDS
import sa.masrouf.core.model.countsAsSpending
import sa.masrouf.core.model.SaudiCategories
import sa.masrouf.core.model.Source
import sa.masrouf.core.model.Status
import sa.masrouf.core.model.Transaction
import sa.masrouf.core.model.TransactionType
import sa.masrouf.core.money.Money
import sa.masrouf.core.text.ArabicText
import sa.masrouf.core.time.RiyadhTime
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * The app's only route to stored transactions.
 *
 * Screens are given [Transaction] values, never entities, so no screen can start
 * reading a stored enum name or a raw halala count and reach its own conclusion
 * about what it means.
 */
class TransactionRepository(
    private val dao: TransactionDao,
    private val detector: DuplicateDetector = DuplicateDetector(),
    private val rules: MerchantRuleDao? = null,
    /**
     * Runs a block as one database transaction.
     *
     * Passed in rather than taken as a `RoomDatabase`, so the repository stays
     * constructible from a fake DAO in a test with no Android at all. The default
     * runs the block as it is, which is what those tests want: each write commits
     * on its own and the assertions read them back one by one.
     *
     * It exists for the backfill. Filing 18,334 records as 18,334 separate
     * transactions took two and a half minutes on a real history, with the screen
     * showing nothing the whole time; as one transaction it is seconds.
     */
    private val inTransaction: suspend (suspend () -> Unit) -> Unit = { block -> block() },
    /**
     * Where a flow's mapping runs.
     *
     * Every observing flow here maps every row of a growing table, and the map runs
     * where the flow is COLLECTED - the main thread, under `stateIn`. Twelve
     * thousand rows through a whole-history scan there froze the launch: no total,
     * no strip, touches ignored. (That scan was the recurring detector, since
     * removed; the mechanism it exposed belongs to every flow in this file, and
     * `observePending` in particular is unbounded and re-emits on every write
     * during a 22,000-message backfill.)
     *
     * Injected rather than hardcoded so a test can pass its own scheduler. With
     * `Dispatchers.Default` fixed in place, `advanceUntilIdle()` returns before a
     * real background thread has produced anything, and a test that reads `.value`
     * straight after sees the previous month's answer - which is how this parameter
     * was found.
     */
    private val computation: CoroutineContext = Dispatchers.Default,
) {

    /**
     * Serialises the check-then-insert in [recordCaptured].
     *
     * ponytail: a process-local lock, which is enough because `MasroufApp` holds
     * exactly one repository in one process. A second process writing this database
     * would need `db.withTransaction` instead.
     */
    private val captureLock = Mutex()

    fun observeRecent(limit: Int = RECENT_LIMIT): Flow<List<Transaction>> =
        dao.observeRecent(limit)
            .map { rows -> rows.map(TransactionEntity::toModel) }
            .flowOn(computation)

    /**
     * The first Riyadh month that has anything in it, or null when nothing is
     * stored. Resolved through [RiyadhTime] like every other day decision.
     */
    fun observeEarliestMonth(): Flow<LocalDate?> = dao.observeEarliest().map { millis ->
        millis?.let { RiyadhTime.localDate(Instant.ofEpochMilli(it)).withDayOfMonth(1) }
    }

    /**
     * The months that actually contain something, newest first.
     *
     * The '+3 hours' in the query is Riyadh's offset, applied in SQL so the
     * grouping matches what [RiyadhTime] would decide row by row. Saudi Arabia has
     * no daylight saving, which is the only reason a fixed offset is safe here.
     */
    fun observeMonthsWithData(): Flow<List<LocalDate>> =
        dao.observeMonthsWithData().map { keys ->
            keys.mapNotNull { key ->
                runCatching { LocalDate.parse(key + "-01") }.getOrNull()
            }
        }

    /** Everything belonging to a Riyadh calendar month, newest first. */
    fun observeMonth(anyDayInMonth: LocalDate): Flow<List<Transaction>> {
        val first = anyDayInMonth.withDayOfMonth(1)
        return dao.observeBetween(
            fromMillis = RiyadhTime.startOfDay(first).toEpochMilli(),
            untilMillis = RiyadhTime.startOfDay(first.plusMonths(1)).toEpochMilli(),
        )
            .map { rows -> rows.map(TransactionEntity::toModel) }
            .flowOn(computation)
    }

    /**
     * Stores a transaction the user typed.
     *
     * Recorded as [Status.CONFIRMED], which is not a contradiction of the rule that
     * nothing auto-confirms: that rule is about *captured* records, where a parser
     * decided the amount. Here the user read the amount and typed it, so asking
     * them to confirm their own keystrokes would only teach them to dismiss the
     * confirmation step that matters.
     */
    suspend fun recordManual(
        amount: Money,
        direction: Direction,
        type: TransactionType,
        occurredAt: Instant,
        merchantRaw: String?,
        note: String?,
        accountId: String? = null,
        categoryId: String? = null,
    ): Transaction {
        val id = UUID.randomUUID().toString()
        val transaction = Transaction(
            id = id,
            amount = amount,
            direction = direction,
            type = type,
            occurredAt = occurredAt,
            accountId = accountId,
            categoryId = categoryId,
            merchantRaw = merchantRaw?.takeIf { it.isNotBlank() },
            merchantKey = merchantRaw
                ?.let(ArabicText::normalizeMerchant)
                ?.takeIf { it.isNotBlank() },
            note = note?.takeIf { it.isNotBlank() },
            source = Source.MANUAL,
            status = Status.CONFIRMED,
            // Keyed on the generated id, so a manual record can never collide with
            // another record - the user meaning to enter the same amount twice is a
            // thing they are allowed to do.
            fingerprint = Fingerprint.forManual(id),
            rawText = null,
        )
        // A category typed in on the entry screen is the user's own choice, and a
        // re-file must not overwrite it.
        dao.insert(transaction.toEntity(categorySource = CategorySource.MANUAL))
        return transaction
    }

    /**
     * Stores a record the capture pipeline produced, unless the app already has the
     * transaction it describes.
     *
     * @return true when it was written.
     *
     * Two different duplicates have to be caught here and only one of them is a
     * database concern.
     *
     * The first is the *same record* arriving again: Android reposting a
     * notification, or a redelivered SMS. The unique index on `fingerprint` settles
     * that, which makes it a property of the schema rather than of whoever calls
     * this.
     *
     * The second is the *same purchase* arriving from a different source. A bank
     * sends the SMS and its app posts a notification for one payment, seconds apart.
     * Those are two genuinely different records - different source, different text,
     * different fingerprint - describing one movement of money, and no index can
     * see it. Counting both silently doubles the month. So neighbours in time are
     * loaded and handed to [DuplicateDetector], which weighs amount, direction,
     * card and how far apart they arrived.
     *
     * The whole check-then-insert is under [captureLock]. It has to be: the two
     * capture paths run on different coroutines, and the arrival pattern this
     * method exists for - a bank's SMS and that bank's own push, seconds apart - is
     * exactly the one that interleaves them. Unlocked, both read the neighbour
     * window before either inserts, both find nothing to match, and both write. The
     * fingerprints differ by design here, so the unique index cannot catch it
     * either, and the month doubles with nothing reporting an error.
     */
    suspend fun recordCaptured(
        transaction: Transaction,
        accountLast4: String? = null,
        balance: BalanceReader.Reading? = null,
    ): Boolean = captureLock.withLock {
        // A decision the user already made about this merchant outranks the
        // built-in guess, and outranks having no category at all. Applied here
        // rather than in the recorder because the recorder has no database and
        // should not grow one.
        val learned = transaction.merchantKey?.let { key -> learnedCategory(key, transaction.bankId) }
        val entity = transaction
            .let { if (it.categoryId == null && learned != null) it.copy(categoryId = learned) else it }
            .toEntity(accountLast4)
            .copy(
                balanceHalalas = balance?.amount?.halalas,
                balanceKind = balance?.kind?.name ?: BALANCE_NONE,
            )

        // Wide enough to cover the detector's own widest window, which is a day for
        // anything involving a statement. Narrower here and the detector would never
        // be shown the row it was meant to match.
        val from = transaction.occurredAt.minus(NEIGHBOUR_WINDOW)
        val until = transaction.occurredAt.plus(NEIGHBOUR_WINDOW)
        val neighbours = dao.neighbours(from.toEpochMilli(), until.toEpochMilli())

        val result = detector.reconcile(
            // A neighbour that cannot be read is skipped, not thrown on. Unlike
            // `toModel`, where a substituted enum would silently drop a record out
            // of the monthly total, this row is only an input to a decision about a
            // different record: the worst case of ignoring it is one duplicate,
            // while throwing turns a single bad row into a permanent capture outage.
            existing = neighbours.mapNotNull { it.toSignatureOrNull() },
            incoming = listOf(entity.toSignature()),
        )
        // The single incoming record matched something already stored. Kept out
        // rather than merged: merging fields is a decision that needs a screen and a
        // user, and inventing one here would overwrite whichever telling was better.
        if (result.newIncoming.isEmpty()) {
            // Already stored, so nothing new to write - except which bank sent it,
            // if this reading knows and the stored one does not. That is how the
            // history acquires bank identity: re-reading the inbox produces the
            // same fingerprints, and each one names the row it belongs to.
            transaction.bankId?.let { dao.stampBank(transaction.fingerprint, it) }
            return@withLock false
        }

        dao.insert(entity) != -1L
    }

    /**
     * Captured records waiting for the user, newest first.
     *
     * The screen counts this list rather than asking the database separately. Two
     * ways to count the same thing is how a badge and a list come to disagree.
     */
    /** The newest SMS-sourced record's instant, or null on an empty history. */
    suspend fun latestSmsAt(): Instant? = dao.latestSmsMillis()?.let(Instant::ofEpochMilli)

    /** See [TransactionDao.retypeSalaryDeposits]. */
    suspend fun retypeSalaryDeposits(): Int = dao.retypeSalaryDeposits()

    /**
     * Re-reads the amount of every captured row whose body says something else.
     *
     * The riskiest pass in this file, and the one with the most behind it. The
     * extractor could not see a four-figure amount written without a comma, so in
     * "إيداع في بطاقة 2887* / مبلغ 8500 / الصرف المتبقي 32167.58 SAR" the only
     * candidate carrying a currency token was the balance - and the balance was
     * stored as the amount 439 times over nine years. One message went further: a
     * bank sent its own floating-point artifact, "21684.91999999999999 SAR", and a
     * match that began inside that number stored 91,999,999,999,999 riyals.
     *
     * Run over all 22,037 stored bodies before being written, comparing the old
     * reading to the new: 1,287 amounts corrected, 275 messages that carried no
     * readable amount at all now readable, and nothing lost. Every correction
     * inspected was a balance or a fragment giving way to the figure the message
     * labels as its own.
     *
     * Only captured rows: a hand-entered amount is what the user typed and is never
     * re-derived. See [TransactionDao.setAmount].
     *
     * @return how many amounts changed.
     */
    suspend fun repairAmounts(): Int {
        val parsers = SaudiBanks.ALL.map(::BankMessageParser)
        var fixed = 0
        dao.allWithBody().chunked(REPARSE_BATCH).forEach { batch ->
            inTransaction {
                batch.forEach { row ->
                    val body = row.rawText ?: return@forEach
                    val message = RawMessage(body = body, receivedAt = Instant.EPOCH)
                    // Through the whole parser, never the extractor alone. The
                    // parser refuses an amount in a foreign currency and refuses a
                    // zero, and this pass writes money: reading "مبلغ 4.34 USD" with
                    // the extractor gives 4.34, which would be stored as riyals, and
                    // a zero would overwrite a real figure with nothing.
                    val read = parsers.firstNotNullOfOrNull {
                        (it.parse(message) as? ParseResult.Parsed)?.draft?.amount
                    } ?: return@forEach
                    if (read.halalas == row.amountHalalas) return@forEach
                    if (dao.setAmount(row.id, read.halalas) == 1) fixed++
                }
            }
        }
        return fixed
    }

    /**
     * Replaces account numbers standing in for a party with the name beside them.
     *
     * The pattern that read the party matched the account line, so 2,014 records
     * carried "104*010" or "3016" where a person or a company belonged. Nothing can
     * be filed against a number, so every one of them stayed unfiled however long
     * the merchant list grew.
     *
     * Two existing steps, in order, rather than a third that rewrites the party
     * itself: clear what is certainly wrong, then let [reparseStoredBodies] - which
     * only ever fills a gap - read the name out of the body it already has. A
     * merchant the user filed by hand is never cleared.
     *
     * @return how many rows lost an account number as their party.
     */
    suspend fun repairNumericParties(): Int {
        val cleared = dao.clearNumericParties()
        if (cleared > 0) reparseStoredBodies()
        return cleared
    }

    /**
     * Takes the user's own money back out of the spending total.
     *
     * Settling a credit card, paying a SADAD biller that *is* one of your cards, and
     * transferring to your own account at another bank all read as spending to an
     * older classifier, and each one charges the same riyals twice - once when the
     * purchase happened and again when the balance moved. August 2026 read as
     * 168,864 riyals spent, of which 79,087 was money that never left.
     *
     * A stored row keeps the verdict of the classifier that was current when it
     * arrived: capture deduplicates on a fingerprint and inserts with IGNORE, so
     * re-reading the same message later cannot correct it. That is why teaching the
     * classifier is not enough on its own and this pass exists.
     *
     * It re-parses rather than carrying its own list of wordings. Two lists would be
     * two answers to one question, and the day a bank invents a new template only
     * one of them would learn it.
     *
     * The whole parser, not [IntentClassifier] alone: a purchase whose merchant is
     * one of the user's own wallets is reclassified in [BankMessageParser], after
     * the classifier has had its say, so a pass that asked the classifier by itself
     * would silently do nothing for exactly that family.
     *
     * ## Why it can only ever lower the total
     *
     * The classifier is re-run over every row that counts as spending, but its
     * verdict is accepted only when it takes the row *out* of spending. So a
     * re-reading can correct an overcount and can never invent a purchase - and a
     * row the user has filed keeps the category they gave it.
     *
     * @return how many rows stopped counting as spending.
     */
    /** Whether a stored party is one of the owner's own wallets. */
    private fun isOwnWallet(merchantRaw: String?): Boolean {
        val key = merchantRaw?.let(ArabicText::normalizeMerchant) ?: return false
        return SaudiBanks.OWN_WALLETS.any { ArabicText.normalizeMerchant(it) == key }
    }

    suspend fun retypeOwnMoney(): Int {
        val spending = TransactionType.entries.filter { it.countsAsSpending }.map { it.name }
        // Incoming transfers too, for the one verdict that can move them: "حوالة
        // واردة بين حساباتك" was stored as money arriving 45 times before the
        // classifier knew حوالة as well as تحويل. An incoming row is only ever
        // retyped to OWN_TRANSFER here - anything else it might re-read as is
        // another pass's question.
        val scanned = spending + TransactionType.TRANSFER_IN.name
        val parsers = SaudiBanks.ALL.map(::BankMessageParser)
        var moved = 0
        // Batched for the same reason as reparseStoredBodies: one transaction over
        // the whole history holds the database long enough for the dashboard to
        // show an empty month as though it were true.
        dao.withBodyOfType(scanned).chunked(REPARSE_BATCH).forEach { batch ->
            inTransaction {
                batch.forEach { row ->
                    val body = row.rawText ?: return@forEach
                    val message = RawMessage(body = body, receivedAt = Instant.EPOCH)
                    // The sender is not stored, so every profile is tried. They
                    // agree on the type - it comes from the shared classifier - and
                    // differ only in what else they can read, so the first that
                    // parses at all answers the question this pass is asking.
                    val type = parsers.firstNotNullOfOrNull {
                        (it.parse(message) as? ParseResult.Parsed)?.draft?.type
                    } ?: return@forEach
                    // The stored party decides too, not only the re-parse.
                    //
                    // "شراء محلي عبر الانترنت / بـ3000 SAR / من STC Pay" is a
                    // top-up of his own wallet, and the row already says so - the
                    // merchant was read correctly when it was captured. But this
                    // pass takes the FIRST profile that parses at all, and AlRajhi
                    // parses it without reading a merchant after "من", so the
                    // wallet was invisible here and 253 top-ups worth 124,422
                    // riyals stayed counted as spending while 416 identical ones
                    // moved.
                    if (isOwnWallet(row.merchantRaw)) {
                        val own = TransactionType.OWN_TRANSFER
                        if (dao.retype(row.id, own.name, CategoryGuess.forType(own)?.id) == 1) moved++
                        return@forEach
                    }
                    if (type.countsAsSpending) return@forEach
                    if (row.type == TransactionType.TRANSFER_IN.name && type != TransactionType.OWN_TRANSFER) return@forEach
                    if (dao.retype(row.id, type.name, CategoryGuess.forType(type)?.id) == 1) moved++
                }
            }
        }
        return moved
    }

    /**
     * The salary the bank last announced, or null when it never has.
     *
     * Read off a message the bank sent, so it is a fact and not a guess, and it is
     * the default the screen uses until the user types a figure of their own.
     */
    fun observeLatestSalary(): Flow<Money?> = dao.observeLatestSalary().map { it?.let(Money::ofHalalas) }

    /**
     * Every month that brought in salary or a bonus, newest first.
     *
     * Aggregated by the database. A month with neither is absent rather than zero:
     * the series is what arrived, and inventing empty months for the years before
     * the app existed would draw a decade of nothing that never happened.
     */
    fun observeIncomeByMonth(): Flow<List<IncomeMonth>> =
        dao.observeIncomeByMonth(
            salaryId = SaudiCategories.INCOME.id,
            bonusId = SaudiCategories.BONUS.id,
        )
            .map { rows -> rows.mapNotNull { it.toModel() } }
            .flowOn(computation)

    /**
     * Every salary and bonus deposit, newest first.
     *
     * A row that cannot be read is skipped rather than substituted: an enum this
     * app does not know would otherwise put a real deposit under the wrong heading.
     */
    fun observeIncomeRows(): Flow<List<Transaction>> =
        dao.observeIncomeRows(INCOME_CATEGORY_IDS)
            // Throws rather than skipping, unlike the flows that only inform a
            // decision. These rows are money the user reads: a dropped one is
            // absent from the deposits a month opens to show while still inside the
            // SUM in its header, so the header says 45,000 and the list adds to
            // 30,000 with no error anywhere.
            .map { rows -> rows.map(TransactionEntity::toModel) }
            .flowOn(computation)

    /**
     * The last balance each card's messages reported, newest card first.
     *
     * Straight from storage. A card whose messages never carried a figure is
     * absent rather than shown as zero: zero is a balance, and absence is not.
     */
    fun observeCardBalances(): Flow<List<CardBalance>> = dao.observeCardBalances()

    /**
     * Removes stored rows whose body the gate now rejects.
     *
     * The gate runs before every parser, so a row whose body it rejects should
     * never have existed; it exists because the gate learned the phrase later.
     * Fifty-eight English one-time codes were stored as confirmed purchases, and a
     * card-limit notice was stored as a 200,000-riyal purchase. The gate is the
     * authority on what is not a transaction, so this asks it rather than keeping
     * a second list here.
     *
     * @return how many rows were removed.
     */
    suspend fun purgeRejectedBodies(): Int {
        val doomed = dao.allWithBody()
            .filter { row ->
                val decision = MessageGate.evaluate(
                    RawMessage(body = row.rawText!!, receivedAt = Instant.EPOCH)
                )
                decision is MessageGate.Decision.Reject && shouldPurge(decision.reason, row)
            }
            .map { it.id }
        if (doomed.isEmpty()) return 0
        return dao.deleteAll(doomed)
    }

    /**
     * Whether a rejected row goes, given why it was rejected and who filed it.
     *
     * The reason matters, and this is the one pass in the file that used to ignore
     * it. A body holding a one-time code is deleted whatever the user has done with
     * the row: the point is that a credential must not sit on disk, and a category
     * the user chose does not make it safe to keep.
     *
     * The other two reasons are judgements about meaning rather than about safety.
     * Marketing markers are broad Arabic stems - `اربح`, `فرصك`, `خصم يصل` - of
     * exactly the kind a bank might append as a promotional footer to a real
     * purchase, and a new marker is added most sessions. A row the user filed by
     * hand is a row they looked at and meant, and deleting it destroys the one
     * field that cannot be typed back. Erring toward keeping it costs a wrong row
     * the user can delete; erring the other way costs a record that is simply gone.
     *
     * Every other pass in this file already guards MANUAL. This one now does too,
     * except where safety outranks the guard.
     */
    private fun shouldPurge(reason: MessageGate.Rejection, row: TransactionEntity): Boolean =
        reason == MessageGate.Rejection.ONE_TIME_PASSWORD ||
            row.categorySource != CategorySource.MANUAL.name

    /**
     * Re-reads every stored body whose merchant or card was never extracted.
     *
     * A row written by an older parser keeps the body but not what a newer parser
     * can now read out of it. Only gaps are filled: a merchant already stored is
     * never rewritten, so a name the user has filed stays filed. The sender is
     * not stored, so every profile's parser is tried and the one that reads the
     * most out of the body decides.
     *
     * @return how many rows gained a merchant or a card.
     */
    suspend fun reparseStoredBodies(): Int {
        val parsers = SaudiBanks.ALL.map(::BankMessageParser)
        var filled = 0
        // In batches, each its own transaction, so the screen's reads get a turn
        // between them. One transaction over 14,000 rows held the database for
        // ten seconds and the dashboard showed an empty month the whole time.
        dao.withMissingParty().chunked(REPARSE_BATCH).forEach { batch ->
          inTransaction {
            batch.forEach { row ->
                val message = RawMessage(body = row.rawText ?: return@forEach, receivedAt = Instant.EPOCH)
                // Every profile's parser reads the amount and intent alike; they
                // differ in the merchant and card patterns. So the one that reads
                // the most wins, not the first that reads anything - the first in
                // the list understood every body and read a card from none of them.
                val draft = parsers
                    .map { it.parse(message) }
                    .filterIsInstance<ParseResult.Parsed>()
                    .maxByOrNull { (if (it.draft.merchantRaw != null) 1 else 0) + (if (it.draft.accountLast4 != null) 1 else 0) }
                    ?.draft ?: return@forEach
                val merchant = draft.merchantRaw?.takeIf { row.merchantKey == null }
                val last4 = draft.accountLast4?.takeIf { row.accountLast4 == null }
                if (merchant == null && last4 == null) return@forEach
                val key = merchant?.let(ArabicText::normalizeMerchant)?.takeIf { it.isNotBlank() }
                if (dao.fillParty(row.id, merchant, key, last4) == 1) filled++
            }
          }
        }
        return filled
    }

    /**
     * Reads a balance out of every stored body that has not been read yet.
     *
     * For the history captured before balances were recorded. Bodies that say
     * nothing are marked so, so this touches each one exactly once.
     *
     * @return how many bodies carried a figure.
     */
    suspend fun backfillBalances(): Int {
        var found = 0
        inTransaction {
            dao.withoutBalance().forEach { row ->
                val reading = BalanceReader.read(row.rawText)
                if (reading != null) found++
                dao.setBalance(row.id, reading?.amount?.halalas, reading?.kind?.name ?: BALANCE_NONE)
            }
        }
        return found
    }

    /**
     * Which bank each card belongs to, for the records that know.
     *
     * A card belongs to one bank, so a single message naming both answers it for
     * every record that card appears on, including ones captured before the app
     * recorded a bank.
     */
    fun observeCardBanks(): Flow<Map<String, String>> =
        dao.observeCardBanks()
            .map { rows -> rows.associate { it.last4 to it.bankId } }
            .flowOn(computation)

    /**
     * What kind of card each one is, decided by its own bank's wording.
     *
     * Suspending and read once, not a flow: the verdict is a fact about the card
     * rather than about the month, and it is settled by messages that have already
     * arrived. See [CardKinds] for why the network is not evidence.
     */
    suspend fun cardKinds(): Map<String, CardKind> = withContext(computation) {
        dao.cardBodies()
            .groupBy(CardBody::last4)
            .mapNotNull { (last4, rows) ->
                CardKinds.verdict(rows.map(CardBody::body))?.let { last4 to it }
            }
            .toMap()
    }

    fun observePending(): Flow<List<Transaction>> =
        dao.observePending()
            .map { rows -> rows.map(TransactionEntity::toModel) }
            .flowOn(computation)

    /**
     * Files every transaction from one merchant, and remembers the decision.
     *
     * Both halves matter. Refiling only the row in front of the user leaves the
     * other forty from the same shop wrong, and refiling all of them without
     * remembering means the next message from that shop arrives unfiled again. The
     * built-in rules cover about 62% of a real history; this is how the rest gets
     * covered, one decision at a time, by the only person who knows.
     *
     * @return how many existing records were refiled.
     */
    suspend fun fileMerchant(merchantKey: String, categoryId: String): Int {
        rules?.upsert(MerchantRule(merchantKey = merchantKey, categoryId = categoryId))
        return dao.setCategoryForMerchant(merchantKey, categoryId, CategorySource.MANUAL.name)
    }

    /**
     * Drops a learned rule and lets the built-in ones answer again.
     *
     * The undo for [fileMerchant]. Every record of the merchant is refiled from
     * [CategoryGuess], and marked automatic, so a later change to the shipped rules
     * reaches them too - which is the whole point of giving the decision back.
     *
     * @return how many records were refiled.
     */
    suspend fun forgetMerchant(merchantKey: String): Int {
        rules?.forget(merchantKey)
        var filed = 0
        inTransaction {
            dao.uncategorisedOrMerchant(merchantKey).forEach { row ->
                if (row.merchantKey != merchantKey) return@forEach
                val model = runCatching { row.toModel() }.getOrNull() ?: return@forEach
                val category = CategoryGuess.suggest(model.merchantRaw, model.type)?.id
                val source = category?.let { CategorySource.AUTOMATIC.name }
                if (dao.setCategory(row.id, category, source) == 1) filed++
            }
        }
        return filed
    }

    /**
     * The learned category for a merchant, the bank-specific rule first.
     *
     * Two rules can exist for one name: "AMMAR@barq" and "AMMAR". The first is
     * the more specific decision and wins where it applies.
     */
    private suspend fun learnedCategory(merchantKey: String, bankId: String?): String? {
        val rules = rules ?: return null
        return bankId?.let { rules.categoryFor(ruleKey(merchantKey, it)) } ?: rules.categoryFor(merchantKey)
    }

    /**
     * Files every record of one merchant that arrived through one bank, and
     * remembers that decision for that bank alone.
     *
     * The card network sends the same word for two shops; the bank that announced
     * the purchase is what tells them apart. Records of the same name through
     * other banks are untouched, and keep whatever rule they had.
     *
     * @return how many existing records were refiled.
     */
    suspend fun fileMerchantAtBank(merchantKey: String, bankId: String, categoryId: String): Int {
        rules?.upsert(MerchantRule(merchantKey = ruleKey(merchantKey, bankId), categoryId = categoryId))
        return dao.setCategoryForMerchantAtBank(merchantKey, bankId, categoryId, CategorySource.MANUAL.name)
    }

    /**
     * Files a record under a category, or clears it when [categoryId] is null.
     *
     * @return false when no such record exists.
     */
    suspend fun setCategory(id: String, categoryId: String?): Boolean =
        dao.setCategory(id, categoryId, CategorySource.MANUAL.name.takeIf { categoryId != null }) == 1

    /**
     * Clears everything the app filed and files it again with the current rules.
     *
     * For when the rules themselves change. The ordinary backfill only ever fills a
     * gap, so a rule that was wrong when it ran leaves a wrong category behind for
     * ever: four keywords matching inside longer words filed 152 records under
     * categories nothing about them suggested, and no amount of re-running the
     * backfill would have corrected one of them.
     *
     * The user's own decisions are not touched. They are identified by
     * [CategorySource.MANUAL], recorded when the choice was made, rather than by
     * asking whether the current rules would agree: a person agreeing with a guess
     * has still made a decision, and re-deriving it would discard the agreement.
     *
     * @return how many records ended up filed.
     */
    suspend fun refileAll(): Int {
        dao.clearAutomaticCategories()
        return fileUncategorised()
    }

    // A single transaction around both halves would be tidier, but the clear and
    // the re-file are independently correct: interrupted between them the history
    // is unfiled, which the ordinary backfill fixes, and never half-filed under two
    // different rule sets.

    /**
     * Files every uncategorised record whose merchant is recognised.
     *
     * For the history that predates categories existing. It only ever fills a gap:
     * a record the user already filed is never touched, and one whose merchant is
     * not recognised is left unfiled rather than swept into "other".
     *
     * @return how many were filed.
     */
    suspend fun fileUncategorised(): Int {
        // The user's own decisions first: they were made about this exact merchant
        // and are never a guess.
        val learned = rules?.all().orEmpty().associate { it.merchantKey to it.categoryId }
        var filed = 0
        inTransaction {
            dao.uncategorised().forEach { row ->
                val model = runCatching { row.toModel() }.getOrNull() ?: return@forEach
                val category = row.merchantKey?.let { key ->
                    row.bankId?.let { bank -> learned[ruleKey(key, bank)] } ?: learned[key]
                }
                    ?: CategoryGuess.suggest(model.merchantRaw, model.type)?.id
                    ?: return@forEach
                if (dao.setCategory(row.id, category, CategorySource.AUTOMATIC.name) == 1) filed++
            }
        }
        return filed
    }

    /**
     * Confirms a record and files it in one write.
     *
     * One call because it is one decision. The user is looking at the bank's own
     * words when they answer both questions - is this right, and what was it for -
     * and splitting them into two taps is how the second one stops being answered.
     */
    suspend fun confirmWithCategory(id: String, categoryId: String?): Boolean {
        setCategory(id, categoryId)
        return confirm(id)
    }

    /**
     * The user vouched for a captured record. It enters their totals from here on.
     *
     * @return false when there was nothing pending under that id, which means the
     *   screen was acting on a record that had already been dealt with.
     */
    suspend fun confirm(id: String): Boolean = dao.confirm(id) == 1

    /**
     * The user vouched for everything waiting, in one action.
     *
     * @return how many were confirmed.
     */
    suspend fun confirmAllPending(): Int = dao.confirmAllPending()

    /** The user rejected a captured record - a misparse, or a message that was not theirs. */
    suspend fun dismiss(id: String): Boolean = dao.dismiss(id) == 1

    /**
     * Removes a record the user no longer wants, confirmed or not.
     *
     * There is no undo and no server to restore from, so the screen asks before
     * calling this.
     *
     * ponytail: delete-and-retype is the whole correction story for now. It costs
     * the original message text on a captured record, which is the one thing that
     * cannot be typed back - if correcting a near-miss becomes routine, this should
     * become an edit that keeps `rawText` and records what was changed.
     */
    suspend fun delete(id: String): Boolean = dao.delete(id) == 1

    companion object {
        const val RECENT_LIMIT = 50

        /** Stored in `balance_kind` when a body was read and carried no figure. */
        const val BALANCE_NONE = "NONE"

        const val REPARSE_BATCH = 500

        /** A learned rule scoped to one bank: "AMMAR@barq". The bare key is the general rule. */
        fun ruleKey(merchantKey: String, bankId: String) = "$merchantKey@$bankId"

        /** How far either side of an incoming record to look for what it may duplicate. */
        private val NEIGHBOUR_WINDOW: Duration = Duration.ofDays(1)
    }
}

/**
 * The month broken into its category shares, largest first.
 *
 * Filtered exactly as [spendingTotal] filters, so the strip and the number above
 * it can never disagree - one rule decides both.
 *
 * Uncategorised spending gets a band of its own rather than being folded into
 * "other". They mean different things: one is a decision the user made, the other
 * is a decision they have not made yet, and a strip that hides the second cannot
 * show how much of the month is still unexamined.
 */
fun List<Transaction>.categoryShares(): List<Pair<Category?, Money>> =
    filter { it.status == Status.CONFIRMED }
        .filter { it.countsAsSpending }
        .groupBy { SaudiCategories.byId(it.categoryId) }
        .map { (category, rows) ->
            category to rows.fold(Money.ZERO) { sum, row -> sum + row.amount }
        }
        .sortedByDescending { it.second.halalas }

/**
 * What the user spent over these transactions.
 *
 * Two filters, and both are load-bearing.
 *
 * [Status.CONFIRMED] only. A captured record is a parser's reading of a bank
 * message, and a parser that misreads an amount is a certainty over a long enough
 * period. Letting a [Status.PENDING] record into this total is the exact failure
 * `Status` exists to prevent: a number the user never agreed to, presented to them
 * as fact. They are told how many are waiting instead.
 *
 * The "does this count" decision is delegated to [countsAsSpending] rather than
 * listed here, because two surfaces each deciding for themselves is how they come
 * to disagree about the same month. That function reads the category as well as the
 * type: an investment deposit reaches the bank as a card purchase and is not money
 * the user spent.
 */
/**
 * What the month spent on mada cards and what it put on credit.
 *
 * The owner asked for the split: a few shops still take mada and nothing else, and
 * what he borrowed is a different question from what he spent. A card the messages
 * never describe is left out entirely rather than counted under a guess, so these
 * figures do not have to add up to the month's total and the panel says so.
 */
fun List<Transaction>.spendingByCardKind(kinds: Map<String, CardKind>): List<Pair<CardKind, Money>> =
    filter { it.status == Status.CONFIRMED }
        .filter { it.countsAsSpending }
        .mapNotNull { row -> row.accountLast4?.let(kinds::get)?.let { it to row.amount } }
        .groupBy({ it.first }, { it.second })
        .map { (kind, amounts) -> kind to amounts.fold(Money.ZERO) { sum, a -> sum + a } }
        .sortedByDescending { it.second.halalas }

fun List<Transaction>.spendingTotal(): Money =
    filter { it.status == Status.CONFIRMED }
        .filter { it.countsAsSpending }
        .fold(Money.ZERO) { running, transaction -> running + transaction.amount }

/**
 * What the month put into investments.
 *
 * Its own number rather than a band in the strip, because it is deliberately not
 * part of the total the strip adds up to - see [countsAsSpending]. Excluding it
 * from spending without showing it anywhere made 14,710 riyals disappear from the
 * app entirely, which is worse than counting them wrongly: a number that is absent
 * cannot be questioned.
 */
fun List<Transaction>.investedTotal(): Money =
    totalOfCategory(SaudiCategories.INVESTMENT, Direction.DEBIT)

/**
 * One place that adds up a category the spending total leaves out.
 *
 * One caller today. It was three - income and bonuses were summed here too until
 * they got a screen of their own - and it stays a function because the interesting
 * part is the pair of filters: confirmed only, and the right direction. Inlining it
 * would put those back where the next reader has to re-derive them.
 */
private fun List<Transaction>.totalOfCategory(category: Category, direction: Direction): Money =
    filter { it.status == Status.CONFIRMED }
        .filter { it.direction == direction && it.categoryId == category.id }
        .fold(Money.ZERO) { running, transaction -> running + transaction.amount }

/**
 * The narrow view of a stored row that decides whether two records are one event.
 *
 * Built from the entity rather than the model because the card fragment lives only
 * on the entity - and a signature missing it matches too eagerly, not too little.
 */
internal fun TransactionEntity.toSignatureOrNull(): EventSignature? =
    runCatching { toSignature() }.getOrNull()

internal fun TransactionEntity.toSignature(): EventSignature = EventSignature(
    amount = Money.ofHalalas(amountHalalas),
    direction = enumValueOf(direction),
    last4 = accountLast4,
    occurredAt = Instant.ofEpochMilli(occurredAtMillis),
    merchantKey = merchantKey,
    source = enumValueOf(source),
    body = rawText?.let(ArabicText::normalize)?.takeIf { it.isNotBlank() },
    bankId = bankId,
)
