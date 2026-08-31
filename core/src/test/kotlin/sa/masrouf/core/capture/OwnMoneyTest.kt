package sa.masrouf.core.capture

import org.junit.jupiter.api.Test
import sa.masrouf.core.fixtures.RealMessages
import sa.masrouf.core.model.TransactionType
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Money that moves between the user's own places is not spending.
 *
 * Four ways the app was getting this wrong at once, found by reading a twelve-year
 * corpus off the owner's phone. August 2026 showed 168,864 riyals of spending, of
 * which 79,087 was the user's own money moving from one of their pockets to
 * another - the month read as roughly double what it was.
 *
 * The check that matters is [TransactionType.countsAsSpending]; the type is only
 * how it is reached. Each test asserts the type it should now get.
 */
class OwnMoneyTest {

    private fun typeOf(body: String): TransactionType =
        IntentClassifier.classify(body)?.type
            ?: error("no rule matched, which is itself the bug:\n$body")

    // ---- Settling a credit card -------------------------------------------

    /**
     * AlRajhi changed this template in April 2026. The rule in place looked for
     * ائتمانية, and the new wording names the network instead - so 43 settlements
     * worth 180,954 riyals were filed as bills and counted twice: once as each
     * purchase on the card, and again when the balance was paid.
     */
    @Test
    fun `settling a visa card is not spending`() {
        assertEquals(TransactionType.OWN_TRANSFER, typeOf(RealMessages.RAJHI_CARD_SETTLEMENT))
    }

    @Test
    fun `the older credit-card wording is still not spending`() {
        val body = "بطاقة ائتمانية:سداد\nبطاقة:فيزا 2383\nمبلغ:SAR 964.44\nرصيد:1041.47 SAR"

        assertEquals(TransactionType.OWN_TRANSFER, typeOf(body))
    }

    @Test
    fun `the english settlement template is not spending`() {
        assertEquals(TransactionType.OWN_TRANSFER, typeOf(RealMessages.RAJHI_CARD_SETTLEMENT_EN))
    }

    // ---- SADAD, where only the biller code tells you anything ---------------

    /**
     * The user confirmed biller 255: paying off an AlRajhi credit card, funded that
     * day from an Emirates NBD card. Both ends are his. Nothing in the wording says
     * so - "سداد فاتورة" is what a genuine electricity bill says too.
     */
    @Test
    fun `a sadad payment to the user's own card is not spending`() {
        assertEquals(TransactionType.OWN_TRANSFER, typeOf(RealMessages.SNB_SADAD_TO_OWN_CARD))
    }

    @Test
    fun `a sadad payment to a utility is still spending`() {
        val type = typeOf(RealMessages.SNB_SADAD_ELECTRICITY)

        assertEquals(TransactionType.BILL_PAYMENT, type)
        assertTrue(type.countsAsSpending)
    }

    /**
     * The biller is matched with its label, so the digits cannot fire on their own.
     * An amount of 255 riyals to the electricity company is not a card settlement.
     */
    @Test
    fun `a biller code is not matched inside an amount`() {
        val body = "سداد فاتورة\nمبلغ 255.00 SAR\nمن 104*010\nمفوتر 001\nفاتورة 05183896808"

        assertEquals(TransactionType.BILL_PAYMENT, typeOf(body))
    }

    // ---- Transfers the user sends to themselves ----------------------------

    @Test
    fun `an outgoing transfer to the owner is not spending`() {
        assertEquals(TransactionType.OWN_TRANSFER, typeOf(RealMessages.BARQ_TRANSFER_TO_SELF))
    }

    /**
     * D360 masks the surname before it appears, so this one is recognised by the
     * given and middle names. Without it, 27 transfers read as money leaving.
     */
    @Test
    fun `a masked spelling of the owner's name is still the owner`() {
        assertEquals(TransactionType.OWN_TRANSFER, typeOf(RealMessages.D360_TRANSFER_TO_SELF))
    }

    /**
     * The guard on the whole idea. Four hundred outgoing transfers in the same
     * history go to relatives sharing the surname, and every one of them is real
     * spending. Matching a surname alone would erase 400 transfers.
     */
    @Test
    fun `a transfer to a relative sharing the surname is still spending`() {
        val type = typeOf(RealMessages.SNB_TRANSFER_TO_RELATIVE)

        assertEquals(TransactionType.TRANSFER_OUT, type)
        assertTrue(type.countsAsSpending)
    }

    @Test
    fun `a transfer to a stranger is still spending`() {
        val body = "حوالة صادرة محلية\nمن:104*010\nإلى:باسم ابن محمدظاهر ابن قاري حام\nعبر:AL RAJHI BANK\nمبلغ:1001 SAR"

        assertEquals(TransactionType.TRANSFER_OUT, typeOf(body))
    }

    /**
     * The owner's name arriving in an incoming transfer must not turn it into
     * anything else. Only [TransactionType.TRANSFER_OUT] is ever demoted by a name.
     */
    @Test
    fun `the owner's name on an incoming transfer changes nothing`() {
        val body = "حوالة واردة محلية\nمبلغ 500 SAR\nمن KHALEEL MALKI\nحساب 104*010"

        assertEquals(TransactionType.TRANSFER_IN, typeOf(body))
    }

    // ---- None of it counts -------------------------------------------------

    @Test
    fun `every form of the user's own money moving is out of the spending total`() {
        val ownMoney = listOf(
            RealMessages.RAJHI_CARD_SETTLEMENT,
            RealMessages.RAJHI_CARD_SETTLEMENT_EN,
            RealMessages.SNB_SADAD_TO_OWN_CARD,
            RealMessages.BARQ_TRANSFER_TO_SELF,
            RealMessages.D360_TRANSFER_TO_SELF,
        )

        for (body in ownMoney) {
            assertFalse(typeOf(body).countsAsSpending, "counted as spending:\n$body")
        }
    }

    // ---- A statement is not a transaction ----------------------------------

    /**
     * "إجمالي المبلغ المستحق:SAR 16608.05" is an announcement of what is owed. The
     * parser read the figure and stored a payment; nine notices became 166,926
     * riyals of spending that never happened.
     */
    @Test
    fun `a card statement notice never reaches a parser`() {
        val notices = listOf(
            RealMessages.RAJHI_CARD_STATEMENT_NOTICE,
            RealMessages.SNB_CARD_STATEMENT_NOTICE,
        )

        for (body in notices) {
            assertFalse(
                MessageGate.allows(RawMessage(body, java.time.Instant.EPOCH)),
                "a statement notice was allowed through as a transaction:\n$body",
            )
        }
    }
}
