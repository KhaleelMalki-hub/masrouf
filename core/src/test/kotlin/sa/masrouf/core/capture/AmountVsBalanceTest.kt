package sa.masrouf.core.capture

import org.junit.jupiter.api.Test
import sa.masrouf.core.money.Money
import kotlin.test.assertEquals

/**
 * The amount, not the balance sitting beside it.
 *
 * Every message here is real, and each one was stored wrong. The extractor could
 * not see a four-figure amount written without a thousands separator, so in
 *
 *     إيداع في بطاقة 2887*  /  مبلغ 8500  /  الصرف المتبقي 32167.58 SAR
 *
 * the only candidate carrying a currency token was the balance. 439 records over
 * nine years stored a balance where an amount belonged, and nothing looked wrong:
 * the figure is plausible, it is in the right message, and it is simply not what
 * was charged.
 *
 * The fix is not a wider net but a better signal - the bank labels its own amount
 * with مبلغ, and that outranks a number that merely sits near a currency token.
 */
class AmountVsBalanceTest {

    private fun read(body: String): Money? = AmountExtractor.extractOrNull(body)?.money

    /** The family that was wrong 439 times. No currency by the amount, one by the balance. */
    @Test
    fun `a labelled amount beats a balance that carries the currency`() {
        val body = "إيداع في بطاقة 2887*\nمبلغ 8500\nفي 04/27\nالصرف المتبقي 32167.58 SAR"

        assertEquals(Money.ofMajor("8500"), read(body))
    }

    @Test
    fun `a four-figure amount without a comma is visible`() {
        val body = "إيداع في بطاقة 4007*\nمبلغ 4900.51\nفي 01/15\nالصرف المتبقي 10000.00 SAR"

        assertEquals(Money.ofMajor("4900.51"), read(body))
    }

    /**
     * The 92-trillion-riyal message. The bank sent its own floating-point artifact,
     * and because nothing anchored a match to the start of a number, the engine slid
     * into the middle of the balance to find digits that a currency token could
     * follow: "91999999999999 SAR".
     */
    @Test
    fun `a match cannot begin inside another number`() {
        val body = "إيداع في بطاقة 9552*\nمبلغ 8315.08\nفي 03/27\nالصرف المتبقي 21684.91999999999999 SAR"

        assertEquals(Money.ofMajor("8315.08"), read(body))
    }

    /** Four halalas, written with no integer part. Read as four riyals for years. */
    @Test
    fun `an amount with no integer part is halalas, not riyals`() {
        val body = "ايداع:الأرباح الشهرية لحساب الادخار\nمبلغ:.04 SAR\nإلى:0111"

        assertEquals(Money.ofMajor("0.04"), read(body))
    }

    /**
     * The boundary must not be so strict that it refuses a sentence. An English
     * message puts a full stop straight after the amount, and blocking any trailing
     * dot lost sixteen messages that had been read correctly for years.
     */
    @Test
    fun `an amount ending a sentence is still read`() {
        val body = "Your card was charged SR 334.95."

        assertEquals(Money.ofMajor("334.95"), read(body))
    }

    /** The settlement family, where the balance is the larger and more tempting figure. */
    @Test
    fun `a card settlement reads what was paid, not what is left`() {
        val body = "بطاقة إئتمانية تأكيد سداد\nبطاقة 0926*\nمبلغ 400 SAR\nفي 03/28\nالصرف المتبقي 16332.83 SAR"

        assertEquals(Money.ofMajor("400"), read(body))
    }

    /**
     * The other side of the guard. A balance line on its own, with no amount
     * anywhere, must yield nothing rather than the balance - this file returns
     * nothing rather than a guess.
     */
    @Test
    fun `a balance on its own is not an amount`() {
        val body = "الرصيد المتاح 10000.00 SAR"

        assertEquals(null, read(body))
    }

    /** A fee is a second labelled figure, and must not displace the transfer. */
    @Test
    fun `a fee line does not become the amount`() {
        val body = "حوالة صادرة محلية\nمبلغ2000.00SAR\nرسوم0.00SAR\nالى RECIPIENT NAME"

        assertEquals(Money.ofMajor("2000.00"), read(body))
    }
}
