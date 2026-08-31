package sa.masrouf.app.data

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import sa.masrouf.core.fixtures.RealMessages
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What the purge may and may not delete.
 *
 * A credential on disk and a mis-guessed category are not the same kind of problem,
 * and this pass used to treat them the same: it deleted any row the gate now
 * rejects, for any of the gate's three reasons, whatever the user had done with it.
 *
 * Marketing markers are broad Arabic stems - اربح, فرصك, خصم يصل - of exactly the
 * kind a bank might append as a footer to a real purchase, and a new one is added
 * most sessions. A row the user filed by hand is a row they looked at and meant,
 * and deleting it destroys the raw text, which is the one field that cannot be
 * typed back.
 */
class PurgeGuardTest {

    private val dao = FakeDao()
    private val repository = TransactionRepository(dao)

    private var nextId = 0

    private fun stored(body: String, categorySource: String) = TransactionEntity(
        id = "t-${nextId++}",
        amountHalalas = 25_000_00L,
        direction = "DEBIT",
        type = "TRANSFER_OUT",
        occurredAtMillis = 1_774_942_119_316L,
        accountId = null,
        accountLast4 = null,
        categoryId = "transfers",
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

    /**
     * The row this test was written for. It sat on the owner's phone as a confirmed
     * 25,000-riyal transfer out - a movement that never happened - with the code
     * that authorised the real one kept beside it.
     */
    @Test
    fun `a body holding a one-time code is deleted even when the user filed the row`() = runTest {
        dao.replaceAll(listOf(stored(RealMessages.RAJHI_TEMPORARY_CODE, categorySource = "MANUAL")))

        assertEquals(1, repository.purgeRejectedBodies())
        assertTrue(dao.rows.isEmpty(), "a credential survived because the row was filed")
    }

    @Test
    fun `the english activation-code wording is deleted too`() = runTest {
        dao.replaceAll(listOf(stored(RealMessages.SNB_BILL_ACTIVATION_CODE, categorySource = "AUTOMATIC")))

        assertEquals(1, repository.purgeRejectedBodies())
    }

    /**
     * The other side. Marketing is a judgement about meaning, not about safety, and
     * the user's own filing outranks it.
     */
    @Test
    fun `a row the user filed by hand survives a marketing marker`() = runTest {
        dao.replaceAll(listOf(stored(RealMessages.RAJHI_PRIZE_DRAW_ADVERT, categorySource = "MANUAL")))

        assertEquals(0, repository.purgeRejectedBodies())
        assertEquals(1, dao.rows.size)
    }

    @Test
    fun `the same marketing row is still deleted when the app filed it`() = runTest {
        dao.replaceAll(listOf(stored(RealMessages.RAJHI_PRIZE_DRAW_ADVERT, categorySource = "AUTOMATIC")))

        assertEquals(1, repository.purgeRejectedBodies())
    }

    @Test
    fun `a real transaction is never purged, filed or not`() = runTest {
        dao.replaceAll(
            listOf(
                stored(RealMessages.RAJHI_ONLINE_PURCHASE, categorySource = "MANUAL"),
                stored(RealMessages.RAJHI_ONLINE_PURCHASE, categorySource = "AUTOMATIC"),
            )
        )

        assertEquals(0, repository.purgeRejectedBodies())
        assertEquals(2, dao.rows.size)
    }
}
