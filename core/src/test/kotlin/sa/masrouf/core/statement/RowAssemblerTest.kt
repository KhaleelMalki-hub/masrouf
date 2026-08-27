package sa.masrouf.core.statement

import org.junit.jupiter.api.Test
import sa.masrouf.core.model.Direction
import sa.masrouf.core.model.TransactionType
import sa.masrouf.core.money.Money
import sa.masrouf.core.time.RiyadhTime
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Word positions taken verbatim from real barq and Emirates NBD statement pages.
 * Personal names are replaced; every coordinate, amount and balance is exactly as
 * measured, because the coordinates are the thing under test.
 */
class RowAssemblerTest {

    private fun word(text: String, x0: Double, x1: Double, y: Double) =
        PositionedWord(text, x0, x1, top = y, bottom = y + 8)

    // ---- barq: no table structure, date split across two lines -------------

    /**
     * Two transactions from a real barq page. Note that the first row's date is
     * `27 Aug` on one line and `2026` two lines below it, with the description in
     * between - which is why a row cannot be anchored on "a line beginning with a
     * date".
     */
    private val barqWords = listOf(
        // header
        word("Date", 25.0, 46.0, 312.0),
        word("Transaction", 218.0, 271.0, 312.0),
        word("Details", 273.0, 305.0, 312.0),
        word("Balance", 509.0, 544.0, 312.0),
        // row 0
        word("27", 25.0, 35.0, 333.0),
        word("Aug", 38.0, 55.0, 333.0),
        word("CSO260827-", 93.0, 145.0, 333.0),
        word("4000.00", 381.0, 414.0, 333.0),
        word("1155.29", 537.0, 570.0, 333.0),
        word("Outgoing", 218.0, 261.0, 342.0),
        word("local", 263.0, 284.0, 342.0),
        word("transfer,", 286.0, 326.0, 342.0),
        word("2026", 25.0, 46.0, 348.0),
        word("10637YZADJ", 93.0, 145.0, 348.0),
        word("*3016,", 218.0, 246.0, 360.0),
        word("RECIPIENT", 248.0, 288.0, 360.0),
        word("NAME", 290.0, 313.0, 360.0),
        // row 1
        word("27", 25.0, 35.0, 378.0),
        word("Aug", 38.0, 55.0, 378.0),
        word("CIN20260827-", 93.0, 154.0, 378.0),
        word("5000.00", 435.0, 468.0, 378.0),
        word("5155.29", 537.0, 570.0, 378.0),
        word("Cash", 218.0, 240.0, 384.0),
        word("in", 242.0, 251.0, 384.0),
        word("via", 253.0, 266.0, 384.0),
        word("card", 269.0, 288.0, 384.0),
        word("2026", 25.0, 46.0, 393.0),
        word("NUA8YD27", 93.0, 145.0, 393.0),
    )

    private val barqAssembler =
        RowAssembler(SaudiStatements.BARQ_COLUMNS, anchorColumn = SaudiStatements.BARQ.balanceColumn)

    @Test
    fun `barq rows are rebuilt from loose words`() {
        val rows = barqAssembler.assemble(barqWords)

        assertEquals(2, rows.size, "expected two transactions, got: $rows")
        assertEquals(6, rows[0].cells.size)
    }

    /**
     * The case that rules out anchoring on the date: "27 Aug" and "2026" are two
     * lines apart with the description between them, and must still end up in one
     * date cell.
     */
    @Test
    fun `a date split across lines is reassembled`() {
        val rows = barqAssembler.assemble(barqWords)

        assertEquals(LocalDate.of(2026, 8, 27), sa.masrouf.core.time.ArabicDates.namedMonth(rows[0].cells[0]))
    }

    @Test
    fun `barq description lines are joined into one cell`() {
        val rows = barqAssembler.assemble(barqWords)

        assertTrue(
            rows[0].cells[2].contains("Outgoing local transfer"),
            "description was: '${rows[0].cells[2]}'",
        )
        assertTrue(rows[0].cells[2].contains("RECIPIENT NAME"))
    }

    @Test
    fun `page headers before the first balance are dropped`() {
        val rows = barqAssembler.assemble(barqWords)

        assertTrue(rows.none { it.cells.any { cell -> cell.contains("Details") } })
    }

    @Test
    fun `barq debit and credit land in different columns`() {
        val rows = barqAssembler.assemble(barqWords)

        assertEquals("4000.00", rows[0].cells[SaudiStatements.BARQ.debitColumn])
        assertEquals("", rows[0].cells[SaudiStatements.BARQ.creditColumn])
        assertEquals("5000.00", rows[1].cells[SaudiStatements.BARQ.creditColumn])
        assertEquals("", rows[1].cells[SaudiStatements.BARQ.debitColumn])
    }

    @Test
    fun `barq rows import, and the wallet top-up is not spending`() {
        val rows = barqAssembler.assemble(barqWords)
        val entries = StatementImporter(SaudiStatements.BARQ).import(rows, "barq-aug").entries

        assertEquals(2, entries.size)
        assertEquals(Money.ofMajor("4000.00"), entries[0].draft.amount)
        assertEquals(Direction.DEBIT, entries[0].draft.direction)
        assertEquals(TransactionType.TRANSFER_OUT, entries[0].draft.type)

        assertEquals(Money.ofMajor("5000.00"), entries[1].draft.amount)
        assertEquals(Direction.CREDIT, entries[1].draft.direction)
        assertEquals(TransactionType.OWN_TRANSFER, entries[1].draft.type)
        assertEquals(false, entries[1].draft.type.countsAsSpending)
    }

    // ---- Emirates NBD: also structureless, compact dates -------------------

    private val enbdWords = listOf(
        word("BROUGHT", 102.0, 136.0, 279.0),
        word("FORWARD", 141.0, 174.0, 279.0),
        word("0.00", 520.0, 539.0, 279.0),

        word("06Jul26", 61.0, 94.0, 303.0),
        word("INSTANT", 102.0, 136.0, 303.0),
        word("PAYMENT", 141.0, 174.0, 303.0),
        word("500.00", 420.0, 449.0, 303.0),
        word("500.00Cr", 501.0, 539.0, 303.0),
        word("RECIPIENT", 102.0, 266.0, 312.0),

        word("06Jul26", 61.0, 94.0, 351.0),
        word("INSTANT", 102.0, 136.0, 351.0),
        word("PAYMENT", 141.0, 174.0, 351.0),
        word("3,000.00", 411.0, 449.0, 351.0),
        word("3,500.00Cr", 491.0, 539.0, 351.0),

        word("09Jul26", 61.0, 94.0, 453.0),
        word("CREDIT", 102.0, 131.0, 453.0),
        word("CARD", 136.0, 155.0, 453.0),
        word("PAYMENT", 160.0, 194.0, 453.0),
        word("3,500.00", 332.0, 371.0, 453.0),
        word("0.00", 520.0, 539.0, 453.0),
    )

    private val enbdAssembler = RowAssembler(
        SaudiStatements.EMIRATES_NBD_COLUMNS,
        anchorColumn = SaudiStatements.EMIRATES_NBD.balanceColumn,
    )

    @Test
    fun `emirates nbd rows are rebuilt and reconcile`() {
        val rows = enbdAssembler.assemble(enbdWords)
        val result = StatementImporter(SaudiStatements.EMIRATES_NBD).import(rows, "enbd-jul")

        // The brought-forward row carries a balance but no debit or credit, so it
        // opens a row and is then skipped as a non-transaction.
        assertEquals(3, entriesAndCarryForward(rows))
        assertEquals(3, result.entries.size)
        assertEquals(StatementImporter.RowOrder.OLDEST_FIRST, result.order)
        assertTrue(result.trustworthy, "problems: ${result.problems}")
    }

    private fun entriesAndCarryForward(rows: List<StatementRow>) = rows.size - 1

    @Test
    fun `emirates nbd compact dates are parsed`() {
        val rows = enbdAssembler.assemble(enbdWords)
        val entries = StatementImporter(SaudiStatements.EMIRATES_NBD).import(rows, "enbd-jul").entries

        assertEquals(LocalDate.of(2026, 7, 6), RiyadhTime.localDate(entries[0].draft.occurredAt))
        assertEquals(LocalDate.of(2026, 7, 9), RiyadhTime.localDate(entries[2].draft.occurredAt))
    }

    @Test
    fun `emirates nbd debit column is read as a debit`() {
        val rows = enbdAssembler.assemble(enbdWords)
        val entries = StatementImporter(SaudiStatements.EMIRATES_NBD).import(rows, "enbd-jul").entries

        assertEquals(Direction.CREDIT, entries[0].draft.direction)
        assertEquals(Money.ofMajor("500.00"), entries[0].draft.amount)
        assertEquals(Direction.DEBIT, entries[2].draft.direction)
        assertEquals(Money.ofMajor("3500.00"), entries[2].draft.amount)
    }

    // ---- Ruler behaviour ---------------------------------------------------

    @Test
    fun `a word is placed by its centre, not its left edge`() {
        val ruler = ColumnRuler(listOf(100.0))
        assertEquals(0, ruler.columnOf(90.0))
        // Starts left of the boundary but sits mostly right of it.
        assertEquals(1, ruler.columnOf(PositionedWord("x", 95.0, 140.0, 0.0, 8.0).centerX))
    }

    @Test
    fun `an empty page produces no rows`() {
        assertTrue(barqAssembler.assemble(emptyList()).isEmpty())
    }

    @Test
    fun `words with no anchor produce no rows`() {
        val headerOnly = listOf(word("Date", 25.0, 46.0, 312.0), word("Details", 273.0, 305.0, 312.0))
        assertTrue(barqAssembler.assemble(headerOnly).isEmpty())
    }
}
