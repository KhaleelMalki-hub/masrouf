package sa.masrouf.app.ui

import kotlin.test.assertEquals
import kotlin.test.assertIs
import org.junit.jupiter.api.Test
import sa.masrouf.core.money.Money

/**
 * The amount field is the only place in the UI where a wrong answer becomes a
 * wrong number in the user's history rather than a wrong pixel, so it is tested
 * apart from the composable that hosts it.
 */
class AmountInputTest {

    @Test
    fun `ascii riyals and halalas parse`() {
        val result = AmountInput.parse("87.50")

        assertIs<AmountInput.Result.Valid>(result)
        assertEquals(Money.ofHalalas(8750), result.amount)
    }

    @Test
    fun `arabic-indic digits and the arabic decimal separator parse`() {
        // What an Arabic keyboard actually produces.
        val typed = "١٢٣٫٤٥"

        val result = AmountInput.parse(typed)

        assertIs<AmountInput.Result.Valid>(result)
        assertEquals(Money.ofHalalas(12345), result.amount)
    }

    @Test
    fun `a thousands separator is not a parse failure`() {
        val result = AmountInput.parse("1,200")

        assertIs<AmountInput.Result.Valid>(result)
        assertEquals(Money.ofHalalas(120_000), result.amount)
    }

    @Test
    fun `an empty field is not yet an error`() {
        assertIs<AmountInput.Result.Empty>(AmountInput.parse(""))
        assertIs<AmountInput.Result.Empty>(AmountInput.parse("   "))
    }

    @Test
    fun `three decimals are refused rather than rounded`() {
        // Rounding here would replace a detectable mistake with a plausible number,
        // which is the one outcome Money is written to prevent.
        assertIs<AmountInput.Result.Invalid>(AmountInput.parse("1.234"))
    }

    @Test
    fun `text is not an amount`() {
        assertIs<AmountInput.Result.Invalid>(AmountInput.parse("abc"))
        assertIs<AmountInput.Result.Invalid>(AmountInput.parse("12.5.5"))
    }

    @Test
    fun `a negative amount is refused because direction carries the sign`() {
        assertIs<AmountInput.Result.Invalid>(AmountInput.parse("-5"))
    }
}
