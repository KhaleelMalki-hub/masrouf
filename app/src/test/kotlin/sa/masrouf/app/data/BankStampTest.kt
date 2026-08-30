package sa.masrouf.app.data

import kotlin.test.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import sa.masrouf.core.dedup.Fingerprint
import sa.masrouf.core.model.Direction
import sa.masrouf.core.model.Source
import sa.masrouf.core.model.Status
import sa.masrouf.core.model.Transaction
import sa.masrouf.core.model.TransactionType
import sa.masrouf.core.money.Money
import java.time.Instant

/**
 * How a twelve-year history acquires bank identity it was never stored with.
 *
 * The sender address is what says which bank sent a message, and it is not kept.
 * Only about 1,000 of 22,000 real messages name their own bank in the body, so
 * reading it back off what was stored is not an option. What is stored is the
 * fingerprint, derived from the message itself - so reading the inbox again
 * produces the same fingerprints and each one names the exact row it belongs to.
 */
class BankStampTest {

    private val dao = FakeDao()
    private val repository = TransactionRepository(dao)
    private val at = Instant.parse("2026-08-20T09:00:00Z")

    private fun record(id: String, last4: String?, bankId: String?, minute: Long = 0) =
        Transaction(
            id = id,
            amount = Money.ofMajor("50.00"),
            direction = Direction.DEBIT,
            type = TransactionType.PURCHASE,
            occurredAt = at.plusSeconds(minute * 60),
            accountId = null,
            categoryId = null,
            merchantRaw = "SHOP",
            merchantKey = "SHOP",
            note = null,
            source = Source.SMS,
            status = Status.CONFIRMED,
            // Deliberately not derived from bankId: two readings of one message
            // agree on the fingerprint, which is the whole mechanism under test.
            fingerprint = Fingerprint.forMessage(
                Source.SMS, at.plusSeconds(minute * 60), Money.ofMajor("50.00"),
                Direction.DEBIT, "SHOP", "SHOP $minute",
            ),
            rawText = null,
            accountLast4 = last4,
            bankId = bankId,
        )

    @Test
    fun `re-reading the inbox stamps the bank onto a record stored without one`() = runTest {
        repository.recordCaptured(record("old", last4 = "8101", bankId = null), "8101")
        assertEquals(null, dao.rows.single().bankId)

        // The same message, read again now that the app records a bank.
        repository.recordCaptured(record("again", last4 = "8101", bankId = "alrajhi"), "8101")

        assertEquals(1, dao.rows.size)
        assertEquals("alrajhi", dao.rows.single().bankId)
    }

    @Test
    fun `a second reading never moves a record to a different bank`() = runTest {
        // If two readings disagreed, one of them is wrong, and quietly taking the
        // newer answer would hide that rather than surface it.
        repository.recordCaptured(record("first", last4 = "8101", bankId = "alrajhi"), "8101")
        repository.recordCaptured(record("second", last4 = "8101", bankId = "snb"), "8101")

        assertEquals("alrajhi", dao.rows.single().bankId)
    }

    @Test
    fun `one record teaches the whole card`() = runTest {
        // The point of the lookup: the record that knows is a different record from
        // the ones being labelled, and there may be thousands of those.
        repository.recordCaptured(record("known", last4 = "8101", bankId = "snb"), "8101")
        repository.recordCaptured(record("unknown", last4 = "8101", bankId = null, minute = 30), "8101")

        assertEquals(mapOf("8101" to "snb"), repository.observeCardBanks().first())
    }

    /**
     * A row written by an older parser keeps the body but not what a newer parser
     * reads out of it. Re-parsing fills the gaps and only the gaps.
     */
    @Test
    fun `re-parsing stored bodies fills a missing merchant and card, and nothing else`() = runTest {
        val body = "شراء إنترنت\nبطاقة فيزا: **2166\nمبلغ 12.99 SAR\nلدى APPLE COM BILL\nحساب **8982"
        repository.recordCaptured(
            record("gap", last4 = null, bankId = null).copy(merchantRaw = null, merchantKey = null, rawText = body),
        )
        repository.recordCaptured(
            record("kept", last4 = null, bankId = null, minute = 180).copy(merchantRaw = "MY NAME", merchantKey = "MY NAME", rawText = body),
        )

        assertEquals(2, repository.reparseStoredBodies())

        val gap = dao.rows.single { it.id == "gap" }
        assertEquals("APPLE COM BILL", gap.merchantRaw)
        assertEquals("2166", gap.accountLast4)
        // The one that already had a merchant keeps it; only its card is filled.
        val kept = dao.rows.single { it.id == "kept" }
        assertEquals("MY NAME", kept.merchantRaw)
        assertEquals("2166", kept.accountLast4)
    }

    /** A stored credential is removed, and the gate decides what counts as one. */
    @Test
    fun `stored one-time codes are purged`() = runTest {
        repository.recordCaptured(
            record("otp", last4 = "2887", bankId = null)
                .copy(rawText = "Your secure code is 6659\nFor internet purchase SAR155.81\nCard ending 2887"),
            "2887",
        )
        repository.recordCaptured(record("real", last4 = "2887", bankId = null, minute = 180), "2887")

        assertEquals(1, repository.purgeCredentialBodies())
        assertEquals(listOf("real"), dao.rows.map { it.id })
    }
}
