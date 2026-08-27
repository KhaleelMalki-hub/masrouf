package sa.masrouf.core.statement

/** A word as a PDF text extractor reports it: the text, plus where it sits on the page. */
data class PositionedWord(
    val text: String,
    val x0: Double,
    val x1: Double,
    val top: Double,
    val bottom: Double,
) {
    val centerX: Double get() = (x0 + x1) / 2
    val centerY: Double get() = (top + bottom) / 2
}

/**
 * Where one statement's columns begin and end, as x positions on the page.
 *
 * @param boundaries the x positions *between* columns, ascending. A statement with
 *   six columns has five boundaries. A word belongs to the column whose span
 *   contains its horizontal centre, so a wide cell that overhangs its neighbour
 *   still lands in the right place.
 */
class ColumnRuler(private val boundaries: List<Double>) {

    init {
        require(boundaries.isNotEmpty()) { "a ruler needs at least one boundary" }
        require(boundaries.zipWithNext().all { (a, b) -> a < b }) {
            "boundaries must be ascending, got: $boundaries"
        }
    }

    val columnCount: Int = boundaries.size + 1

    fun columnOf(x: Double): Int = boundaries.count { it <= x }
}

/**
 * Rebuilds table rows from loose words, for statements that have no table to
 * extract.
 *
 * Three of the five banks here emit statements with real table structure. barq and
 * Emirates NBD do not: their pages are a bag of positioned words, and a
 * transaction's fields are strewn across three or four physical lines that
 * interleave with each other:
 *
 *     y=333    27 Aug        CSO260827-        4000.00      1155.29
 *     y=342                                Outgoing local transfer,
 *     y=348    2026          10637YZADJ
 *     y=360                                *3016, RECIPIENT NAME
 *
 * One transaction, four lines, and its date split in half across two of them.
 *
 * ## Finding where a row begins
 *
 * The obvious anchor - "a line that starts with a date" - fails on exactly the
 * layout above, because `27 Aug` and `2026` sit on different lines. The reliable
 * one is the **balance column**: a running balance is printed once per
 * transaction and never on a continuation line. So a line carrying a balance
 * opens a row, and every line after it belongs to that row until the next one.
 *
 * That also makes the assembler agree with [StatementImporter], which uses the
 * same column to check the arithmetic afterwards.
 */
class RowAssembler(
    private val ruler: ColumnRuler,
    /** Column that appears exactly once per transaction. In practice, the balance. */
    private val anchorColumn: Int,
    /**
     * What counts as a row-opening value in [anchorColumn].
     *
     * Not "any text": the page header prints the word `Balance` in that very
     * column, so a presence test turns the header into a transaction and shifts
     * every row after it by one. A running balance is a number, so that is the test.
     */
    private val isAnchor: (String) -> Boolean = { AMOUNT.containsMatchIn(it) },
    /**
     * How far apart two words' vertical centres may be and still be one line.
     *
     * Words on a line are not pixel-aligned: superscripts, different font sizes and
     * the extractor's own rounding spread them by a point or two.
     */
    private val lineTolerance: Double = 3.0,
) {

    /**
     * @return one [StatementRow] per transaction, cells joined in reading order.
     *   Words appearing before the first anchor - page headers, column titles - are
     *   dropped, since they belong to no transaction.
     */
    fun assemble(words: List<PositionedWord>): List<StatementRow> {
        if (words.isEmpty()) return emptyList()

        val rows = ArrayList<StatementRow>()
        var current: Array<StringBuilder>? = null

        for (line in groupIntoLines(words)) {
            val cells = cellsOf(line)
            val opensRow = isAnchor(cells[anchorColumn])

            if (opensRow) {
                current?.let { rows.add(toRow(rows.size, it)) }
                current = Array(ruler.columnCount) { StringBuilder(cells[it]) }
                continue
            }

            // A continuation line. Without an open row these are page headers.
            val open = current ?: continue
            for (column in cells.indices) {
                if (cells[column].isBlank()) continue
                if (open[column].isNotEmpty()) open[column].append('\n')
                open[column].append(cells[column])
            }
        }
        current?.let { rows.add(toRow(rows.size, it)) }

        return rows
    }

    private fun toRow(index: Int, cells: Array<StringBuilder>) =
        StatementRow(index, cells.map { it.toString().trim() })

    /**
     * Clusters words into physical lines by vertical position.
     *
     * Grouping on the running mean of a line's centre rather than on the first
     * word's, so that a line drifting slightly across the page does not split in
     * two halfway along.
     */
    private fun groupIntoLines(words: List<PositionedWord>): List<List<PositionedWord>> {
        val sorted = words.sortedWith(compareBy({ it.centerY }, { it.x0 }))
        val lines = ArrayList<MutableList<PositionedWord>>()
        var lineCenter = Double.NaN

        for (word in sorted) {
            if (lines.isEmpty() || kotlin.math.abs(word.centerY - lineCenter) > lineTolerance) {
                lines.add(mutableListOf(word))
                lineCenter = word.centerY
            } else {
                val line = lines.last()
                line.add(word)
                lineCenter = line.sumOf { it.centerY } / line.size
            }
        }
        return lines
    }

    /**
     * Places each word of a line into its column.
     *
     * Words are joined left to right by x position. For Arabic that yields the same
     * visual order a PDF text extractor produces, which is what
     * [sa.masrouf.core.text.VisualOrder] expects as its input.
     */
    private fun cellsOf(line: List<PositionedWord>): List<String> {
        val cells = Array(ruler.columnCount) { StringBuilder() }
        for (word in line.sortedBy { it.x0 }) {
            val cell = cells[ruler.columnOf(word.centerX)]
            if (cell.isNotEmpty()) cell.append(' ')
            cell.append(word.text)
        }
        return cells.map { it.toString().trim() }
    }

    private companion object {
        /** A running balance always contains a number; a column heading never does. */
        val AMOUNT = Regex("""\d""")
    }
}
