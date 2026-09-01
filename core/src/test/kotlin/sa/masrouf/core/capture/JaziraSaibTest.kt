package sa.masrouf.core.capture

import org.junit.jupiter.api.Test
import sa.masrouf.core.fixtures.RealMessages
import sa.masrouf.core.model.TransactionType
import sa.masrouf.core.money.Money
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Bank AlJazira and SAIB, added on 2026-09-01 when the owner named their cards:
 * 3761 is his AlJazira mada card, 9097 his SAIB one. Neither sender had a profile,
 * so neither bank's history was in the app at all.
 */
class JaziraSaibTest {

    private fun parsed(profile: BankProfile, body: String) =
        (BankMessageParser(profile).parse(RawMessage(body, Instant.EPOCH))
            as? ParseResult.Parsed)?.draft ?: error("not parsed")

    /**
     * The captured sample is a top-up of his barq wallet, which is exactly the
     * kind of "purchase" that must not be one: the same riyals are reported again
     * by the wallet as they are actually spent.
     */
    @Test
    fun `an aljazira purchase at his own wallet is a transfer, with card and amount read`() {
        val draft = parsed(SaudiBanks.AL_JAZIRA, RealMessages.JAZIRA_ONLINE_PURCHASE)

        assertEquals(TransactionType.OWN_TRANSFER, draft.type)
        assertEquals(Money.ofMajor("3000").halalas, draft.amount.halalas)
        assertEquals("3761", draft.accountLast4)
        assertEquals("barq", draft.merchantRaw)
    }

    /**
     * "From: 0001" is the funding account, four digits under the label that names a
     * person everywhere else. The guard refuses it; the merchant comes from "At:".
     */
    @Test
    fun `the account under From is not read as the party`() {
        assertEquals("barq", parsed(SaudiBanks.AL_JAZIRA, RealMessages.JAZIRA_ONLINE_PURCHASE).merchantRaw)
    }

    @Test
    fun `a pos purchase names its shop and card`() {
        val draft = parsed(SaudiBanks.AL_JAZIRA, RealMessages.JAZIRA_POS_PURCHASE)

        assertEquals("tarwah alarabyh est", draft.merchantRaw)
        assertEquals("3761", draft.accountLast4)
    }

    @Test
    fun `an incoming transfer keeps its sender`() {
        val draft = parsed(SaudiBanks.AL_JAZIRA, RealMessages.JAZIRA_INCOMING_TRANSFER)

        assertEquals(TransactionType.TRANSFER_IN, draft.type)
        // The trailing mask is trimmed with the rest of the punctuation.
        assertEquals("SENDER NAME", draft.merchantRaw)
    }

    /** A reminder that a payment is late is not a payment. */
    @Test
    fun `an overdue reminder is refused`() {
        val decision = MessageGate.evaluate(
            RawMessage(RealMessages.JAZIRA_PAYMENT_OVERDUE, Instant.EPOCH, sender = "AlJaziraSMS")
        )

        assertTrue(decision is MessageGate.Decision.Reject)
    }

    @Test
    fun `a saib purchase reads through the X mask`() {
        val draft = parsed(SaudiBanks.SAIB, RealMessages.SAIB_ONLINE_PURCHASE)

        assertEquals(TransactionType.PURCHASE, draft.type)
        assertEquals(Money.ofMajor("10").halalas, draft.amount.halalas)
        assertEquals("9097", draft.accountLast4)
        assertEquals("Health Endowment Fund R", draft.merchantRaw)
    }

    /**
     * SAIB masks an account as "XXX1001" - an X-run, not asterisks - and writes it
     * under "من:", the same word a person arrives under. The X-guard is what keeps
     * "XXX1001" out of the party field while "SENDER NAME XXX2001" stays in it.
     */
    @Test
    fun `an X-masked account is never the party, but a person with one still is`() {
        val purchase = parsed(SaudiBanks.SAIB, RealMessages.SAIB_ONLINE_PURCHASE)
        val transfer = parsed(SaudiBanks.SAIB, RealMessages.SAIB_INCOMING_TRANSFER)

        assertEquals("Health Endowment Fund R", purchase.merchantRaw)
        assertEquals("SENDER NAME XXX2001", transfer.merchantRaw)
    }
}
