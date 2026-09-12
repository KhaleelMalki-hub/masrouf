package sa.masrouf.core.statement

import org.junit.jupiter.api.Test
import sa.masrouf.core.model.Direction
import sa.masrouf.core.money.Money
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A card statement lists what a card did, not what an account held, so it prints no
 * running balance - and the running balance is what every other layout here is
 * checked against.
 *
 * What it prints instead is the amount twice, signed. That is a weaker guarantee
 * and this file says where the line is: it catches a shifted column, which is the
 * failure a balance-free statement otherwise has no defence against, and it cannot
 * catch arithmetic, which the file does not state.
 *
 * Rows transcribed from a real statement with names replaced; amounts, signs,
 * column order and cell formatting kept, because those are what is being tested.
 */
class CardStatementTest {

    private fun rows(vararg cells: List<String>) =
        cells.mapIndexed { index, row -> StatementRow(index, row) }

    /** [posted, description, transaction amount, posting date, transaction date] */
    private val card = rows(
        listOf("-153.20", "hungerstation", "SAR -153.20", "04-11-2025", "01-11-2025"),
        listOf("-45.75", "Keeta", "SAR -45.75", "03-11-2025", "01-11-2025"),
        listOf("4585.66", "Advance Payment 2025", "SAR 4,585.66", "01-11-2025", "01-11-2025"),
        listOf("-4968.00", "Leejam Sports Compan", "SAR -4,968.00", "04-09-2025", "01-09-2025"),
    )

    private fun imported(statement: List<StatementRow> = card) =
        StatementImporter(SaudiStatements.SNB_CARD).import(statement, "card-nov")

    @Test
    fun `the sign gives the direction and is then dropped`() {
        val entries = imported().entries

        assertEquals(4, entries.size)
        assertEquals(Money.ofMajor("153.20"), entries[0].draft.amount)
        assertEquals(Direction.DEBIT, entries[0].draft.direction)
        // Money arriving on a card: a payment towards the balance, printed positive.
        assertEquals(Money.ofMajor("4585.66"), entries[2].draft.amount)
        assertEquals(Direction.CREDIT, entries[2].draft.direction)
        // Thousands separators and the currency token are not part of the number.
        assertEquals(Money.ofMajor("4968.00"), entries[3].draft.amount)
    }

    @Test
    fun `a file with no balance is vouched for by the amount printed twice`() {
        val result = imported()

        assertEquals(StatementImporter.Verification.ECHOED_AMOUNT, result.verifiedBy)
        assertEquals(4, result.reconciledCount)
        assertTrue(result.problems.isEmpty(), "unexpected problems: ${result.problems}")
        assertTrue(result.trustworthy)
    }

    /**
     * The failure the echo exists to catch. One column dropped and every cell
     * shifts: the amount column now holds a date, which is not the number the
     * other amount column holds.
     */
    @Test
    fun `a shifted column breaks the check instead of passing quietly`() {
        val shifted = rows(*card.map { it.cells.drop(1) }.toTypedArray())

        val result = StatementImporter(SaudiStatements.SNB_CARD).import(shifted, "card-nov")

        assertFalse(result.trustworthy)
    }

    /**
     * The limit, stated rather than assumed: the echo says the columns line up, not
     * that the figures are right. Both printings are the same wrong number here and
     * nothing in the file disagrees with them - which is why a card statement is
     * imported against a history that already holds most of these rows, and why
     * `trustworthy` is the floor for storing rather than proof of correctness.
     */
    @Test
    fun `the echo cannot see an amount that is wrong in both printings`() {
        val wrong = rows(
            listOf("-99999.00", "hungerstation", "SAR -99,999.00", "04-11-2025", "01-11-2025"),
            listOf("-45.75", "Keeta", "SAR -45.75", "03-11-2025", "01-11-2025"),
            listOf("4585.66", "Advance Payment 2025", "SAR 4,585.66", "01-11-2025", "01-11-2025"),
        )

        assertTrue(StatementImporter(SaudiStatements.SNB_CARD).import(wrong, "card").trustworthy)
    }

    /**
     * A layout with one signed amount and no second printing states nothing that
     * can be checked, and "no failures found" is exactly what the wrong layout also
     * produces. So it is refused rather than trusted.
     */
    @Test
    fun `a signed layout with nothing to check against is never trustworthy`() {
        val unverifiable = SaudiStatements.SNB_CARD.copy(echoAmountColumn = null)

        val result = StatementImporter(unverifiable).import(card, "card-nov")

        assertEquals(StatementImporter.Verification.NONE, result.verifiedBy)
        assertEquals(4, result.entries.size, "the rows still parse")
        assertFalse(result.trustworthy, "but nothing vouches for them")
    }
}
