package sa.masrouf.core.money

import org.junit.jupiter.api.Test
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class MoneyTest {

    @Test
    fun `major units convert to halalas`() {
        assertEquals(8750L, Money.ofMajor("87.50").halalas)
        assertEquals(100L, Money.ofMajor("1.00").halalas)
        assertEquals(200000L, Money.ofMajor("2000").halalas)
    }

    @Test
    fun `a single decimal place is accepted and normalised to two`() {
        assertEquals(500000L, Money.ofMajor("5000.0").halalas)
        assertEquals("5000.00", Money.ofMajor("5000.0").toPlainString())
    }

    @Test
    fun `thousands separators are stripped when parsing`() {
        assertEquals(285000L, Money.parseOrNull("2,850.00")?.halalas)
    }

    /**
     * Money is integer halalas rather than a Double specifically so that this holds.
     * Summed as Double, ten 0.10 values do not equal 1.00.
     */
    @Test
    fun `repeated addition does not drift`() {
        val tenHalalas = Money.ofMajor("0.10")
        var total = Money.ZERO
        repeat(10) { total += tenHalalas }
        assertEquals(Money.ofMajor("1.00"), total)
    }

    @Test
    fun `a year of small purchases sums exactly`() {
        val amount = Money.ofMajor("8.28")
        var total = Money.ZERO
        repeat(365) { total += amount }
        assertEquals(Money.ofMajor("3022.20"), total)
    }

    /**
     * Refusing to round is deliberate. Three decimals in a bank message means the
     * format was misread; rounding would replace a detectable bug with a plausible
     * wrong number.
     */
    @Test
    fun `excess precision is refused rather than rounded away`() {
        assertFailsWith<ArithmeticException> { Money.ofMajor(BigDecimal("1.234")) }
        assertNull(Money.parseOrNull("1.234"))
    }

    @Test
    fun `explicit rounding is available when the value is already approximate`() {
        assertEquals(Money.ofMajor("1.23"), Money.ofMajorRounded(BigDecimal("1.234")))
        assertEquals(Money.ofMajor("1.24"), Money.ofMajorRounded(BigDecimal("1.235")))
    }

    @Test
    fun `malformed tokens parse to null rather than zero`() {
        assertNull(Money.parseOrNull(""))
        assertNull(Money.parseOrNull("SAR"))
        assertNull(Money.parseOrNull("12.34.56"))
        assertNull(Money.parseOrNull("١٢٣"), "un-normalised Arabic digits must not silently parse")
    }

    @Test
    fun `subtraction and negation behave`() {
        assertEquals(Money.ofMajor("10.00"), Money.ofMajor("35.00") - Money.ofMajor("25.00"))
        assertEquals(Money.ofMajor("25.00"), (-Money.ofMajor("25.00")).abs())
    }

    @Test
    fun `ordering compares by value`() {
        assertEquals(true, Money.ofMajor("8.28") < Money.ofMajor("931.64"))
    }

    @Test
    fun `plain string is always two decimals and ascii`() {
        assertEquals("35.00", Money.ofMajor("35").toPlainString())
        assertEquals("0.00", Money.ZERO.toPlainString())
    }
}
