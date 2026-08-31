package sa.masrouf.core.capture

import org.junit.jupiter.api.Test
import sa.masrouf.core.fixtures.RealMessages
import sa.masrouf.core.model.TransactionType
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Cash taken out is not a purchase.
 *
 * Three messages that all begin with سحب and all name a card. Two are the money
 * leaving as cash - what it is then spent on is recorded separately, so counting
 * the withdrawal too charges the same riyals twice - and one is a shop purchase
 * the bank happened to write with the same verb.
 *
 * The rule for the third was tested first, so it claimed all three, and 107
 * withdrawals worth 193,452 riyals were counted as purchases. The word that
 * separates them is نقدي or صراف: an ATM says one of them, a shop says neither.
 */
class CashOutTest {

    private fun typeOf(body: String): TransactionType =
        IntentClassifier.classify(body)?.type ?: error("no rule matched:\n$body")

    @Test
    fun `a machine withdrawal that names the card is still a withdrawal`() {
        assertEquals(TransactionType.ATM_WITHDRAWAL, typeOf(RealMessages.SNB_ATM_WITHDRAWAL_BY_CARD))
    }

    @Test
    fun `a cash advance on a credit card is cash out, not a purchase`() {
        assertEquals(TransactionType.ATM_WITHDRAWAL, typeOf(RealMessages.CASH_ADVANCE_ON_CREDIT_CARD))
    }

    /**
     * The guard. 3,470 real card purchases in the owner's history are written with
     * سحب and a card field, and the reordering must not touch a single one.
     */
    @Test
    fun `a shop purchase written with the same verb is still a purchase`() {
        val type = typeOf(RealMessages.CARD_PURCHASE_WRITTEN_AS_SAHB)

        assertEquals(TransactionType.PURCHASE, type)
        assertTrue(type.countsAsSpending)
    }

    @Test
    fun `neither form of cash out counts towards the spending total`() {
        val cashOut = listOf(
            RealMessages.SNB_ATM_WITHDRAWAL_BY_CARD,
            RealMessages.CASH_ADVANCE_ON_CREDIT_CARD,
        )

        for (body in cashOut) {
            assertFalse(typeOf(body).countsAsSpending, "counted as spending:\n$body")
        }
    }

    /** A deposit at the same machine must not be dragged in by the reordering. */
    @Test
    fun `a machine deposit is still a deposit`() {
        assertEquals(TransactionType.ATM_DEPOSIT, typeOf(RealMessages.SNB_ATM_DEPOSIT))
    }
}
