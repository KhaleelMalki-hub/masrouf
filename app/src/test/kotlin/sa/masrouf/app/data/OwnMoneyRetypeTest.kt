package sa.masrouf.app.data

import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import sa.masrouf.core.capture.AccountOwner
import sa.masrouf.core.fixtures.RealMessages
import sa.masrouf.core.model.SaudiCategories
import sa.masrouf.core.model.TransactionType
import sa.masrouf.core.model.countsAsSpending

/**
 * The history has to be corrected too, not only what arrives next.
 *
 * Capture deduplicates on a fingerprint and inserts with `OnConflictStrategy.IGNORE`,
 * so a message already stored is never re-read: teaching the classifier fixes the
 * future and leaves twelve years of rows saying what an older classifier decided.
 * Seventy-eight settlements were still filed as bills after the rule that would have
 * caught them existed.
 */
class OwnMoneyRetypeTest {

    private val dao = FakeDao()
    private val repository = TransactionRepository(dao)

    /**
     * The owner's names are supplied at runtime, so a test that relies on the
     * self-transfer demotion must say which names it means. Without this the class
     * passes or fails on whether some other test happened to run first, which is
     * the kind of green nobody can trust.
     */
    @BeforeEach
    fun configureOwner() {
        AccountOwner.configure("OWNER|NAME ; مالك|الحساب ; اسم|مالك")
    }

    private var nextId = 0

    private fun stored(
        body: String,
        type: TransactionType,
        categoryId: String? = SaudiCategories.BILLS.id,
        categorySource: String = "AUTOMATIC",
    ) = TransactionEntity(
        id = "t-${nextId++}",
        amountHalalas = 100_00L,
        direction = "DEBIT",
        type = type.name,
        occurredAtMillis = 1_724_000_000_000L,
        accountId = null,
        accountLast4 = null,
        categoryId = categoryId,
        categorySource = categorySource,
        merchantRaw = null,
        merchantKey = null,
        note = null,
        source = "SMS",
        status = "CONFIRMED",
        fingerprint = "fp-$nextId",
        rawText = body,
        currency = "SAR",
    )

    private fun typeOf(id: String) = dao.rows.first { it.id == id }.type

    @Test
    fun `a settlement stored as a bill stops counting as spending`() = runTest {
        val row = stored(RealMessages.RAJHI_CARD_SETTLEMENT, TransactionType.BILL_PAYMENT)
        dao.replaceAll(listOf(row))

        val moved = repository.retypeOwnMoney()

        assertEquals(1, moved)
        assertEquals(TransactionType.OWN_TRANSFER.name, typeOf(row.id))
    }

    @Test
    fun `a transfer the user sent to themselves stops counting as spending`() = runTest {
        val row = stored(RealMessages.BARQ_TRANSFER_TO_SELF, TransactionType.TRANSFER_OUT)
        dao.replaceAll(listOf(row))

        repository.retypeOwnMoney()

        assertEquals(TransactionType.OWN_TRANSFER.name, typeOf(row.id))
    }

    @Test
    fun `the retyped row is moved to the category its new type implies`() = runTest {
        val row = stored(RealMessages.RAJHI_CARD_SETTLEMENT, TransactionType.BILL_PAYMENT)
        dao.replaceAll(listOf(row))

        repository.retypeOwnMoney()

        assertEquals(SaudiCategories.TRANSFERS.id, dao.rows.first().categoryId)
    }

    /**
     * The user looked at the row and filed it. The type being wrong was the app's
     * mistake; their filing is not something to overwrite while fixing it.
     */
    @Test
    fun `a category the user filed by hand survives the retype`() = runTest {
        val row = stored(
            RealMessages.RAJHI_CARD_SETTLEMENT,
            TransactionType.BILL_PAYMENT,
            categoryId = SaudiCategories.GROCERIES.id,
            categorySource = "MANUAL",
        )
        dao.replaceAll(listOf(row))

        repository.retypeOwnMoney()

        assertEquals(TransactionType.OWN_TRANSFER.name, typeOf(row.id))
        assertEquals(SaudiCategories.GROCERIES.id, dao.rows.first().categoryId)
    }

    /**
     * The guard on the whole pass. It re-reads every spending row, so the thing to
     * prove is that a re-reading can only ever take money *out* of the total - a
     * pass that could invent a purchase would be worse than the bug it fixes.
     */
    @Test
    fun `a real purchase is left alone`() = runTest {
        val row = stored(RealMessages.RAJHI_ONLINE_PURCHASE, TransactionType.PURCHASE, categoryId = null)
        dao.replaceAll(listOf(row))

        val moved = repository.retypeOwnMoney()

        assertEquals(0, moved)
        assertEquals(TransactionType.PURCHASE.name, typeOf(row.id))
    }

    @Test
    fun `a transfer to a relative sharing the surname is left alone`() = runTest {
        val row = stored(RealMessages.SNB_TRANSFER_TO_RELATIVE, TransactionType.TRANSFER_OUT)
        dao.replaceAll(listOf(row))

        assertEquals(0, repository.retypeOwnMoney())
        assertEquals(TransactionType.TRANSFER_OUT.name, typeOf(row.id))
    }

    @Test
    fun `a genuine utility bill is left alone`() = runTest {
        val row = stored(RealMessages.SNB_SADAD_ELECTRICITY, TransactionType.BILL_PAYMENT)
        dao.replaceAll(listOf(row))

        assertEquals(0, repository.retypeOwnMoney())
    }

    /**
     * A row that arrived before the app stored bodies has nothing to re-read. It
     * must be skipped, not re-typed from an absent body.
     */
    @Test
    fun `a row with no stored body is untouched`() = runTest {
        val row = stored(RealMessages.RAJHI_CARD_SETTLEMENT, TransactionType.BILL_PAYMENT).copy(rawText = null)
        dao.replaceAll(listOf(row))

        assertEquals(0, repository.retypeOwnMoney())
        assertEquals(TransactionType.BILL_PAYMENT.name, typeOf(row.id))
    }

    @Test
    fun `running the pass twice moves nothing the second time`() = runTest {
        dao.replaceAll(
            listOf(
                stored(RealMessages.RAJHI_CARD_SETTLEMENT, TransactionType.BILL_PAYMENT),
                stored(RealMessages.BARQ_TRANSFER_TO_SELF, TransactionType.TRANSFER_OUT),
            )
        )

        assertEquals(2, repository.retypeOwnMoney())
        assertEquals(0, repository.retypeOwnMoney())
    }

    @Test
    fun `every row the pass moves is out of the spending total afterwards`() = runTest {
        dao.replaceAll(
            listOf(
                stored(RealMessages.RAJHI_CARD_SETTLEMENT, TransactionType.BILL_PAYMENT),
                stored(RealMessages.RAJHI_CARD_SETTLEMENT_EN, TransactionType.BILL_PAYMENT),
                stored(RealMessages.SNB_SADAD_TO_OWN_CARD, TransactionType.BILL_PAYMENT),
                stored(RealMessages.BARQ_TRANSFER_TO_SELF, TransactionType.TRANSFER_OUT),
                stored(RealMessages.D360_TRANSFER_TO_SELF, TransactionType.TRANSFER_OUT),
            )
        )

        assertEquals(5, repository.retypeOwnMoney())
        for (row in dao.rows) {
            assertEquals(false, row.toModel().type.countsAsSpending, "still counted: ${row.rawText}")
        }
    }
}
