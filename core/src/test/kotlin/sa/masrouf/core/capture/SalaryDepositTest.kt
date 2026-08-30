package sa.masrouf.core.capture

import kotlin.test.assertEquals
import org.junit.jupiter.api.Test
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
}
