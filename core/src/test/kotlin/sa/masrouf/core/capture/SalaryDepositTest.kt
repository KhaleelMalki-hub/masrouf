package sa.masrouf.core.capture

import kotlin.test.assertEquals
import org.junit.jupiter.api.Test
import sa.masrouf.core.fixtures.RealMessages
import sa.masrouf.core.model.CategoryGuess
import sa.masrouf.core.model.SaudiCategories
import sa.masrouf.core.model.TransactionType
import sa.masrouf.core.money.Money
import java.time.Instant

/**
 * "ايداع رواتب", the plural, is a salary. It was read as an incoming transfer for
 * five years because رواتب does not contain راتب, and the app told its user the
 * salary had stopped in 2021 when it had arrived every month since.
 */
class SalaryDepositTest {

    @Test
    fun `the plural salary deposit is a salary`() {
        val body = "ايداع رواتب\nمبلغ SAR 19491\nحساب0104*\nفي 27/08/26 02:25"

        val result = BankMessageParser(SaudiBanks.SNB).parse(RawMessage(body, Instant.EPOCH))

        val draft = (result as ParseResult.Parsed).draft
        assertEquals(TransactionType.SALARY, draft.type)
        assertEquals(Money.ofMajor("19491.00"), draft.amount)
    }

    /**
     * The employer named on the heading line, which is how SNB wrote an incoming
     * transfer until 2021. Two of his allowances had no party at all and were filed
     * as ordinary transfers - the name is the only thing that separates money from
     * an employer from money from anyone else.
     */
    @Test
    fun `a sender named on the heading line is still the sender`() {
        val draft = (BankMessageParser(SaudiBanks.SNB)
            .parse(RawMessage(RealMessages.SNB_INCOMING_SENDER_INLINE, Instant.EPOCH))
            as ParseResult.Parsed).draft

        assertEquals("امانة العاصمة المقدسة", draft.merchantRaw)
        assertEquals(SaudiCategories.BONUS, CategoryGuess.forMerchant(draft.merchantRaw))
    }

    /**
     * The dividend a company pays "بصيغة إيداع راتب". Nothing in the bank message
     * distinguishes it from a salary, and this asserts that: the parser reads it as
     * one, which is why the DASHBOARD takes the largest of the three most recent
     * rather than the newest.
     */
    @Test
    fun `a dividend paid in salary form is indistinguishable to the parser`() {
        val draft = (BankMessageParser(SaudiBanks.SNB)
            .parse(RawMessage(RealMessages.DIVIDEND_PAID_AS_SALARY, Instant.EPOCH))
            as ParseResult.Parsed).draft

        assertEquals(TransactionType.SALARY, draft.type)
        assertEquals(Money.ofMajor("50").halalas, draft.amount.halalas)
    }
}
