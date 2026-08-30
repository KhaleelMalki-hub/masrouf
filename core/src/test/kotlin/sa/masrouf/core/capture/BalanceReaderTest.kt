package sa.masrouf.core.capture

import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.Test
import sa.masrouf.core.money.Money

/**
 * Every message below is a real one, copied from a captured history.
 *
 * The distinction under test is not cosmetic. "رصيد" is money in an account and
 * "الصرف المتبقي" is what remains of a credit card's limit; showing the second as
 * the first tells someone they can spend money that does not exist.
 */
class BalanceReaderTest {

    @Test
    fun `a current account balance is read as a balance`() {
        val message = """
            شراء إنترنت بـSR 44.29
            عبر2383;فيزا
            لـNinja Food company
            رصيد:35409.48 SR
        """.trimIndent()

        val reading = BalanceReader.read(message)

        assertEquals(Money.ofMajor("35409.48"), reading?.amount)
        assertEquals(BalanceReader.Kind.ACCOUNT, reading?.kind)
    }

    @Test
    fun `the currency can come before the number`() {
        val message = "شراء\nبطاقة:7404 ;فيزا-مدى باي\nمبلغ:SAR 150\nرصيد:SAR 33821.97"

        assertEquals(Money.ofMajor("33821.97"), BalanceReader.read(message)?.amount)
    }

    @Test
    fun `the currency can be missing entirely`() {
        assertEquals(Money.ofMajor("1145.29"), BalanceReader.read("شراء إنترنت\nرصيد1145.29")?.amount)
    }

    @Test
    fun `a remaining credit limit is never reported as a balance`() {
        val message = """
            إسترداد مبلغ
            مبلغ SAR 1400.00
            من بطاقة إئتمانية **3396
            حد الصرف المتبقي SAR 1875.50
        """.trimIndent()

        val reading = BalanceReader.read(message)

        assertEquals(Money.ofMajor("1875.50"), reading?.amount)
        assertEquals(BalanceReader.Kind.CREDIT_LIMIT, reading?.kind)
    }

    /** The shorter phrase, which the same banks also use for the same thing. */
    @Test
    fun `the short form of the credit phrase is read the same way`() {
        val message = "بطاقة إئتمانية تأكيد سداد\nبطاقة 3396*\nمبلغ 46.76 SAR\nالصرف المتبقي 500 SAR"

        assertEquals(BalanceReader.Kind.CREDIT_LIMIT, BalanceReader.read(message)?.kind)
    }

    /**
     * A merchant called BALANCED, which a search for "Balance" finds and a reader
     * must not. Found in the history while writing this.
     */
    @Test
    fun `a merchant whose name contains the word balance is not a balance`() {
        val message = "سحب مبلغ 58.00 SAR\nبطاقة 2887*\nمن BALANCED"

        assertNull(BalanceReader.read(message))
    }

    @Test
    fun `a message with no balance at all reads as none`() {
        assertNull(BalanceReader.read("شراء\nبطاقة:2383\nلدى:AMMAR\nمبلغ:1.00 SAR"))
        assertNull(BalanceReader.read(null))
    }
}
