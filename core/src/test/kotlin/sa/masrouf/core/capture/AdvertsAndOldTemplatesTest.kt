package sa.masrouf.core.capture

import org.junit.jupiter.api.Test
import sa.masrouf.core.fixtures.RealMessages
import sa.masrouf.core.model.TransactionType
import sa.masrouf.core.money.Money
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** Found on 2026-09-02 among the unfiled purchases that named no party. */
class AdvertsAndOldTemplatesTest {

    private fun gate(body: String) = MessageGate.evaluate(RawMessage(body, Instant.EPOCH))

    @Test
    fun `bank adverts with a riyal figure are refused`() {
        assertIs<MessageGate.Decision.Reject>(gate(RealMessages.SNB_CASHBACK_ADVERT))
        assertIs<MessageGate.Decision.Reject>(gate(RealMessages.JAZIRA_INSTALMENT_ADVERT))
        assertIs<MessageGate.Decision.Reject>(gate(RealMessages.RAJHI_POINTS_ADVERT))
    }

    /** "للمزيد من المعلومات" closes a real refund; the markers must not reach it. */
    @Test
    fun `a refund with an information footer is not an advert`() {
        assertIs<MessageGate.Decision.Allow>(gate(RealMessages.SNB_REFUND_WITH_FOOTER))
    }

    @Test
    fun `the 2015 one-line template names its shop`() {
        val draft = (BankMessageParser(SaudiBanks.SNB).parse(RawMessage(RealMessages.SNB_APPROVED_2015, Instant.EPOCH))
            as ParseResult.Parsed).draft

        assertEquals(TransactionType.PURCHASE, draft.type)
        assertEquals(Money.ofMajor("7248").halalas, draft.amount.halalas)
        assertEquals("JARIR BOOK STORE", draft.merchantRaw)
        assertEquals("1004", draft.accountLast4)
    }
}
