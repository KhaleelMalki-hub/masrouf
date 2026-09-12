package sa.masrouf.core.statement

import sa.masrouf.core.time.ArabicDates
import java.time.LocalDate

/** One extracted row of a statement, cells in the order the extractor produced them. */
data class StatementRow(val index: Int, val cells: List<String>) {
    fun cell(column: Int): String = cells.getOrNull(column).orEmpty()
}

/**
 * Where each field sits in one bank's statement table.
 *
 * Column *order* is the reason this is data rather than a shared parser, and the
 * reason getting it wrong is dangerous rather than merely broken:
 *
 *     SNB       [balance, debit,  credit, description, type, date]
 *     AlRajhi   [balance, credit, debit,  details,           date]
 *
 * Debit and credit are swapped between the two. Reading both with one order does
 * not fail, does not throw, and does not look wrong - it silently turns every
 * expense into income and every income into an expense. Nothing downstream can
 * detect that from the values alone, which is why [StatementImporter] verifies
 * every row against the running balance instead of trusting this table.
 */
data class StatementLayout(
    val id: String,
    val expectedColumns: Int,
    val descriptionColumn: Int,
    val dateColumn: Int,
    /**
     * Absent on a card statement, which prints one signed amount instead of two
     * columns and no running balance at all. Nullable rather than a placeholder
     * index: a column number that is a lie stays readable, and every reader here
     * ends in an amount of money.
     */
    val balanceColumn: Int? = null,
    val debitColumn: Int? = null,
    val creditColumn: Int? = null,
    /**
     * Set instead of [debitColumn] and [creditColumn] when the bank prints ONE
     * amount column carrying its own sign.
     *
     * This is a different shape of risk, not a variation on the same one. Two
     * columns can be read in the wrong order, which is why the running balance is
     * checked; one signed column cannot - the direction travels with the number
     * rather than with its position, so there is nothing to swap.
     */
    val signedAmountColumn: Int? = null,
    /**
     * A second printing of the same amount, checked against [signedAmountColumn]
     * row by row.
     *
     * Card statements print the transaction amount and the posted amount, and in
     * one currency those are the same number twice. That makes them a check on
     * COLUMN ALIGNMENT - the failure a statement with no running balance otherwise
     * has no defence against - though not on the arithmetic, which the file does
     * not state.
     */
    val echoAmountColumn: Int? = null,
    /** Present only where the bank prints a separate transaction-type column. */
    val typeColumn: Int? = null,
    val parseDate: (String) -> LocalDate?,
    /**
     * True when the extractor returns this bank's Arabic in visual order with
     * presentation forms, which every PDF-sourced statement here does.
     */
    val visuallyOrdered: Boolean = true,
)

/**
 * The statement formats seen so far, each described from a real file.
 *
 * Row ordering is deliberately absent: SNB and AlRajhi list oldest first while
 * D360 lists newest first, and rather than record that here - where a bank
 * changing it would go unnoticed - [StatementImporter] derives the order from
 * which direction the running balance actually reconciles in.
 */
object SaudiStatements {

    /** SNB (AlAhli). Six columns, day-first dates, separate transaction-type column. */
    val SNB = StatementLayout(
        id = "snb",
        expectedColumns = 6,
        balanceColumn = 0,
        debitColumn = 1,
        creditColumn = 2,
        descriptionColumn = 3,
        typeColumn = 4,
        dateColumn = 5,
        parseDate = ArabicDates::dayFirst,
    )

    /** AlRajhi. Five columns, year-first dates, amounts suffixed " SAR". Debit and credit swapped relative to SNB. */
    val AL_RAJHI = StatementLayout(
        id = "alrajhi",
        expectedColumns = 5,
        balanceColumn = 0,
        creditColumn = 1,
        debitColumn = 2,
        descriptionColumn = 3,
        dateColumn = 4,
        parseDate = ArabicDates::yearFirst,
    )

    /**
     * D360. Seven columns (the last is empty padding), Arabic month names, amounts
     * carrying an explicit sign, and two date columns - transaction date and
     * posting date. The transaction date is the one used: it is when the money
     * moved, which is what the user remembers and what a notification recorded.
     */
    val D360 = StatementLayout(
        id = "d360",
        expectedColumns = 7,
        balanceColumn = 0,
        creditColumn = 1,
        debitColumn = 2,
        descriptionColumn = 3,
        dateColumn = 5,
        parseDate = ArabicDates::namedMonth,
    )

    /**
     * barq wallet. Emits no table structure at all, so its rows are rebuilt from
     * word positions by [RowAssembler] before reaching the importer.
     *
     * Column boundaries measured from a real page: date ends at x=46 and the
     * transaction id begins at x=93, so the edge sits between them, and so on
     * across the row.
     */
    val BARQ = StatementLayout(
        id = "barq",
        expectedColumns = 6,
        dateColumn = 0,
        // Column 1 is the transaction id, which the importer does not need.
        descriptionColumn = 2,
        debitColumn = 3,
        creditColumn = 4,
        balanceColumn = 5,
        parseDate = ArabicDates::namedMonth,
        visuallyOrdered = false,
    )

    val BARQ_COLUMNS = ColumnRuler(listOf(70.0, 188.0, 360.0, 425.0, 485.0))

    /** Emirates NBD KSA. Also structureless; dates are written `06Jul26`. */
    val EMIRATES_NBD = StatementLayout(
        id = "emirates-nbd",
        expectedColumns = 5,
        dateColumn = 0,
        descriptionColumn = 1,
        debitColumn = 2,
        creditColumn = 3,
        balanceColumn = 4,
        parseDate = ArabicDates::compactEnglish,
        visuallyOrdered = false,
    )

    val EMIRATES_NBD_COLUMNS = ColumnRuler(listOf(98.0, 316.0, 391.0, 470.0))

    /**
     * An SNB credit-card statement. Five columns, day-first dates, and no running
     * balance anywhere on the page - a card statement lists what the card did, not
     * what an account held.
     *
     *     [posted amount, description, transaction amount, posting date, transaction date]
     *
     * Both amounts are signed and, in riyals, identical; they are checked against
     * each other. The transaction date is the one used, as in [D360]: it is when
     * the money moved, which is what the owner remembers and what any notification
     * about the same purchase recorded.
     */
    val SNB_CARD = StatementLayout(
        id = "snb-card",
        expectedColumns = 5,
        echoAmountColumn = 0,
        descriptionColumn = 1,
        signedAmountColumn = 2,
        dateColumn = 4,
        parseDate = ArabicDates::dayFirst,
    )

    val ALL: List<StatementLayout> = listOf(SNB, AL_RAJHI, D360, BARQ, EMIRATES_NBD, SNB_CARD)
}
