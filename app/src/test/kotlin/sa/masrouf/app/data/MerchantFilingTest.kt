package sa.masrouf.app.data

import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import sa.masrouf.core.dedup.Fingerprint
import sa.masrouf.core.model.Direction
import sa.masrouf.core.model.SaudiCategories
import sa.masrouf.core.model.Source
import sa.masrouf.core.model.Status
import sa.masrouf.core.model.Transaction
import sa.masrouf.core.model.TransactionType
import sa.masrouf.core.money.Money
import java.time.Instant

/**
 * The built-in merchant rules cover about 62% of a real twelve-year history. The
 * rest are local shops and people's names that no shipped list could contain, so
 * the app learns them from the user: file a merchant once, and every transaction
 * from it follows - including the ones that have not arrived yet.
 */
class MerchantFilingTest {

    /** In-memory stand-in for the learned-rules table. */
    private class FakeRules : MerchantRuleDao {
        val stored = mutableMapOf<String, String>()
        override suspend fun upsert(rule: MerchantRule) {
            stored[rule.merchantKey] = rule.categoryId
        }
        override suspend fun categoryFor(merchantKey: String) = stored[merchantKey]
        override suspend fun all() = stored.map { MerchantRule(it.key, it.value) }
    }

    private val dao = FakeDao()
    private val rules = FakeRules()
    private val repository = TransactionRepository(dao, rules = rules)
    private val at = Instant.parse("2026-08-20T09:00:00Z")

    /**
     * Distinct minute per record on purpose. Same amount in the same second is the
     * shape [sa.masrouf.core.dedup.DuplicateDetector] exists to collapse, so records
     * meant to be separate must look separate.
     */
    private fun record(id: String, merchant: String?, minute: Long) = Transaction(
        id = id,
        amount = Money.ofMajor("50.00"),
        direction = Direction.DEBIT,
        type = TransactionType.PURCHASE,
        occurredAt = at.plusSeconds(minute * 60),
        accountId = null,
        categoryId = null,
        merchantRaw = merchant,
        merchantKey = merchant,
        note = null,
        source = Source.SMS,
        status = Status.CONFIRMED,
        fingerprint = Fingerprint.forMessage(
            Source.SMS, at.plusSeconds(minute * 60), Money.ofMajor("50.00"), Direction.DEBIT,
            merchant, id,
        ),
        rawText = null,
    )

    @Test
    fun `filing a merchant files every transaction from it`() = runTest {
        // Refiling only the row on screen leaves the other forty from the same shop
        // wrong, which is the thing that makes people give up on categorising.
        repository.recordCaptured(record("a", "AL QIMMA", 0))
        repository.recordCaptured(record("b", "AL QIMMA", 30))
        repository.recordCaptured(record("c", "SOMEWHERE ELSE", 60))

        assertEquals(2, repository.fileMerchant("AL QIMMA", SaudiCategories.OTHER.id))

        assertEquals(
            listOf(SaudiCategories.OTHER.id, SaudiCategories.OTHER.id),
            dao.rows.filter { it.merchantKey == "AL QIMMA" }.map { it.categoryId },
        )
        assertEquals(null, dao.rows.single { it.merchantKey == "SOMEWHERE ELSE" }.categoryId)
    }

    @Test
    fun `a merchant filed once is filed for messages that arrive later`() = runTest {
        // The half that stops the user doing it again next month.
        repository.recordCaptured(record("a", "AL QIMMA", 0))
        repository.fileMerchant("AL QIMMA", SaudiCategories.OTHER.id)

        repository.recordCaptured(record("later", "AL QIMMA", 60 * 24 * 30))

        assertEquals(
            SaudiCategories.OTHER.id,
            dao.rows.single { it.id == "later" }.categoryId,
        )
    }

    @Test
    fun `refiling a merchant replaces the earlier decision`() = runTest {
        repository.recordCaptured(record("a", "AMMAR", 0))
        repository.fileMerchant("AMMAR", SaudiCategories.FOOD.id)
        repository.fileMerchant("AMMAR", SaudiCategories.GROCERIES.id)

        assertEquals(SaudiCategories.GROCERIES.id, rules.stored["AMMAR"])
        assertEquals(SaudiCategories.GROCERIES.id, dao.rows.single().categoryId)
    }

    @Test
    fun `a learned rule outranks the built-in guess`() = runTest {
        // TAMIMI is groceries by the shipped rules. If the user says otherwise
        // about their own spending, they are right.
        repository.fileMerchant("TAMIMI MARKETS", SaudiCategories.OTHER.id)

        repository.recordCaptured(record("t", "TAMIMI MARKETS", 0))

        assertEquals(SaudiCategories.OTHER.id, dao.rows.single().categoryId)
    }

    @Test
    fun `the backfill uses learned rules as well as built-in ones`() = runTest {
        repository.recordCaptured(record("a", "AL QIMMA", 0))
        repository.recordCaptured(record("b", "NAHDI PHARMACY", 30))
        rules.upsert(MerchantRule("AL QIMMA", SaudiCategories.OTHER.id))

        assertEquals(2, repository.fileUncategorised())

        assertEquals(SaudiCategories.OTHER.id, dao.rows.single { it.id == "a" }.categoryId)
        assertEquals(SaudiCategories.HEALTH.id, dao.rows.single { it.id == "b" }.categoryId)
    }

    /**
     * Re-filing replaces what the app decided and keeps what the user decided.
     *
     * The distinction has to be stored, not re-derived. Asking "would the current
     * rules produce this category" cannot tell a rule that got it right from a
     * person who agreed with it, and would throw the agreement away.
     */
    @Test
    fun `re-filing keeps the user's own choices and redoes the app's`() = runTest {
        // NAHDI is a shipped rule: the app files it, so a re-file may redo it.
        repository.recordCaptured(record("auto", "NAHDI PHARMACY", 0))
        repository.fileUncategorised()
        // The user filed this one by hand.
        repository.recordCaptured(record("mine", "AL QIMMA", 30))
        repository.setCategory("mine", SaudiCategories.OTHER.id)

        repository.refileAll()

        assertEquals(SaudiCategories.HEALTH.id, dao.rows.single { it.id == "auto" }.categoryId)
        assertEquals(SaudiCategories.OTHER.id, dao.rows.single { it.id == "mine" }.categoryId)
    }

    /**
     * A rule that changes reaches records it already filed wrongly.
     *
     * The ordinary backfill only ever fills a gap, so a wrong category it wrote
     * stays wrong for ever however many times the backfill is re-run. That is what
     * this exists for.
     */
    @Test
    fun `re-filing corrects a category an earlier rule got wrong`() = runTest {
        repository.recordCaptured(record("stale", "NAHDI PHARMACY", 0))
        // Stand-in for what a since-corrected rule left behind.
        dao.setCategory("stale", SaudiCategories.SHOPPING.id, CategorySource.AUTOMATIC.name)

        assertEquals(1, repository.refileAll())

        assertEquals(SaudiCategories.HEALTH.id, dao.rows.single().categoryId)
    }

    /** A merchant the user filed keeps its learned rule through a re-file. */
    @Test
    fun `a learned merchant rule survives re-filing`() = runTest {
        repository.recordCaptured(record("a", "AL QIMMA", 0))
        repository.fileMerchant("AL QIMMA", SaudiCategories.FOOD.id)

        repository.refileAll()

        assertEquals(SaudiCategories.FOOD.id, dao.rows.single().categoryId)
    }
}
