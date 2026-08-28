package sa.masrouf.app.ui

import kotlin.test.assertEquals
import org.junit.jupiter.api.Test
import sa.masrouf.core.money.Money

class MoneyFormatTest {

    @Test
    fun `six figures are grouped so they can be read at a glance`() {
        // A real month came out at 157767.42, which has to be counted digit by
        // digit. This is the whole reason grouping exists here.
        assertEquals("157,767.42", Money.ofMajor("157767.42").grouped())
    }

    @Test
    fun `small amounts are unchanged apart from always showing halalas`() {
        assertEquals("8.28", Money.ofMajor("8.28").grouped())
        assertEquals("0.00", Money.ZERO.grouped())
        assertEquals("999.00", Money.ofMajor("999.00").grouped())
    }

    @Test
    fun `grouping starts at four figures`() {
        assertEquals("1,000.00", Money.ofMajor("1000.00").grouped())
    }

    @Test
    fun `digits stay Western and the separator stays a comma`() {
        // A locale-aware formatter renders ١٥٧٬٧٦٧٫٤٢ under an Arabic locale. Every
        // other number in this app is Western, including the ones the banks print.
        val previous = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("ar-SA"))
            assertEquals("157,767.42", Money.ofMajor("157767.42").grouped())
        } finally {
            java.util.Locale.setDefault(previous)
        }
    }

    @Test
    fun `the currency follows the amount in logical order`() {
        assertEquals("1,338.25 ر.س", Money.ofMajor("1338.25").forDisplay("ر.س"))
    }
}
