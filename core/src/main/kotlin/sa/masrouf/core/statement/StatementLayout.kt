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
    val balanceColumn: Int,
    val debitColumn: Int,
    val creditColumn: Int,
    val descriptionColumn: Int,
    val dateColumn: Int,
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

    val ALL: List<StatementLayout> = listOf(SNB, AL_RAJHI, D360)
}
