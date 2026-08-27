package sa.masrouf.core.statement

import org.junit.jupiter.api.Test
import sa.masrouf.core.model.Direction
import sa.masrouf.core.model.TransactionType
import sa.masrouf.core.money.Money
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Rows transcribed from real statement PDFs, with personal names and account
 * fragments replaced. Amounts, balances, dates, column order and cell formatting
 * are kept exactly, because those are what is being tested.
 */
class StatementImporterTest {

    private fun rows(vararg cells: List<String>) =
        cells.mapIndexed { index, row -> StatementRow(index, row) }

    // ---- SNB: [balance, debit, credit, description, type, date] ------------

    private val snbRows = rows(
        listOf("32,059.00", "", "59.00", "نظام الأهلي للمدفوعات", "تحويل داخلي وارد", "03/07/2026\n23:42"),
        listOf("33,459.00", "", "1,400.00", "نظام الأهلي للمدفوعات", "تحويل داخلي وارد", "04/07/2026\n01:39"),
        listOf("28,959.00", "4,500.00", "", ",barq\n***1887", "عملية شراء عبر الإنترنت", "04/07/2026\n02:33"),
        listOf("26,959.00", "2,000.00", "", "تحويل الى الاهل والاصدقاء", "تحويل داخلي صادر", "05/07/2026\n11:34"),
        listOf("26,859.00", "100.00", "", ",barq\n***1887", "عملية شراء عبر الإنترنت", "06/07/2026\n15:39"),
    )

    @Test
    fun `snb statement imports and reconciles`() {
        val result = StatementImporter(SaudiStatements.SNB).import(snbRows, "snb-july")

        assertEquals(5, result.entries.size)
        assertEquals(StatementImporter.RowOrder.OLDEST_FIRST, result.order)
        assertEquals(5, result.reconciledCount)
        assertTrue(result.problems.isEmpty(), "unexpected problems: ${result.problems}")
        assertTrue(result.trustworthy)
    }

    @Test
    fun `snb rows carry the right amounts, directions and dates`() {
        val entries = StatementImporter(SaudiStatements.SNB).import(snbRows, "snb-july").entries

        assertEquals(Money.ofMajor("59.00"), entries[0].draft.amount)
        assertEquals(Direction.CREDIT, entries[0].draft.direction)
        assertEquals(TransactionType.TRANSFER_IN, entries[0].draft.type)
        assertEquals(LocalDate.of(2026, 7, 3), entries[0].draft.occurredAt.let {
            sa.masrouf.core.time.RiyadhTime.localDate(it)
        })

        assertEquals(Money.ofMajor("4500.00"), entries[2].draft.amount)
        assertEquals(Direction.DEBIT, entries[2].draft.direction)
        assertEquals(TransactionType.PURCHASE, entries[2].draft.type)

        assertEquals(TransactionType.TRANSFER_OUT, entries[3].draft.type)
    }

    // ---- AlRajhi: [balance, credit, debit, details, date] ------------------

    private val rajhiRows = rows(
        listOf("5,164.51 SAR", "0.00 SAR", "5.00 SAR", "شراء عبر نقاط البيع - جوجل باي-محلي", "2026/05/26"),
        listOf("5,634.91 SAR", "470.40 SAR", "0.00 SAR", "حوالات فورية واردة", "2026/05/27"),
        listOf("9,634.91 SAR", "4,000.00 SAR", "0.00 SAR", "حوالات فورية واردة", "2026/05/28"),
        listOf("11,105.91 SAR", "1,471.00 SAR", "0.00 SAR", "عملية تحويل داخلية", "2026/05/30"),
        listOf("11,085.59 SAR", "0.00 SAR", "20.32 SAR", "شراء عبر نقاط البيع - جوجل باي-محلي", "2026/05/31"),
    )

    @Test
    fun `alrajhi statement imports and reconciles`() {
        val result = StatementImporter(SaudiStatements.AL_RAJHI).import(rajhiRows, "rajhi-may")

        assertEquals(5, result.entries.size)
        assertEquals(StatementImporter.RowOrder.OLDEST_FIRST, result.order)
        assertEquals(5, result.reconciledCount)
        assertTrue(result.trustworthy)
    }

    @Test
    fun `alrajhi amounts ignore the SAR suffix and the zero in the unused column`() {
        val entries = StatementImporter(SaudiStatements.AL_RAJHI).import(rajhiRows, "rajhi-may").entries

        assertEquals(Money.ofMajor("5.00"), entries[0].draft.amount)
        assertEquals(Direction.DEBIT, entries[0].draft.direction)
        assertEquals(Money.ofMajor("470.40"), entries[1].draft.amount)
        assertEquals(Direction.CREDIT, entries[1].draft.direction)
    }

    /**
     * "عملية تحويل داخلية" says a transfer happened but not which way. The debit
     * and credit columns do, so the column corrects the wording.
     */
    @Test
    fun `direction-less wording takes its direction from the column`() {
        val entries = StatementImporter(SaudiStatements.AL_RAJHI).import(rajhiRows, "rajhi-may").entries

        assertEquals(Direction.CREDIT, entries[3].draft.direction)
        assertEquals(TransactionType.TRANSFER_IN, entries[3].draft.type)
    }

    // ---- The check the whole design exists for -----------------------------

    /**
     * SNB and AlRajhi order their debit and credit columns oppositely. Reading one
     * with the other's order does not fail, does not throw and does not look wrong:
     * it turns every expense into income. Nothing downstream can catch that from
     * the values alone.
     *
     * The running balance can, and does.
     */
    @Test
    fun `a swapped debit and credit column fails to reconcile`() {
        val swapped = SaudiStatements.AL_RAJHI.copy(
            id = "alrajhi-swapped",
            debitColumn = SaudiStatements.AL_RAJHI.creditColumn,
            creditColumn = SaudiStatements.AL_RAJHI.debitColumn,
        )

        val correct = StatementImporter(SaudiStatements.AL_RAJHI).import(rajhiRows, "rajhi-may")
        val wrong = StatementImporter(swapped).import(rajhiRows, "rajhi-may")

        assertTrue(correct.trustworthy, "the correct layout must reconcile")
        assertFalse(wrong.trustworthy, "a swapped layout must be caught, not silently imported")
        assertTrue(
            wrong.problems.any { it is StatementImporter.Problem.BalanceMismatch },
            "the mismatch must be reported, not merely counted",
        )
    }

    // ---- D360: newest first, Arabic month names, signed amounts -----------

    private val d360Rows = rows(
        listOf("2,000.00", "+ 2,000.00", "", "قناة الحوالة: SARIE", "28 يوليو 2026", "28 يوليو 2026", ""),
        listOf("0.00", "", "- 2,584.98", "اسم المستفيد: RECIPIENT", "24 يوليو 2026", "24 يوليو 2026", ""),
        listOf("2,584.98", "", "- 16.30", "عملية غير محددة", "18 يوليو 2026", "18 يوليو 2026", ""),
        listOf("2,601.28", "+ 2,500.00", "", "قناة الحوالة: SARIE", "19 يوليو 2026", "19 يوليو 2026", ""),
        listOf("101.28", "", "- 350.00", "قناة الحوالة: SARIE", "28 يونيو 2026", "28 يونيو 2026", ""),
        listOf("451.28", "+ 300.00", "", "قناة الحوالة: SARIE", "28 يونيو 2026", "28 يونيو 2026", ""),
    )

    /**
     * D360 lists newest first while SNB and AlRajhi list oldest first. Rather than
     * record that per bank - where a change would go unnoticed - the importer
     * reconciles both ways and keeps whichever the file agrees with.
     */
    @Test
    fun `row order is derived from the file, not configured`() {
        val result = StatementImporter(SaudiStatements.D360).import(d360Rows, "d360-summer")

        assertEquals(StatementImporter.RowOrder.NEWEST_FIRST, result.order)
        assertEquals(6, result.entries.size)
        assertEquals(6, result.reconciledCount)
        assertTrue(result.trustworthy)
    }

    @Test
    fun `arabic month names are parsed`() {
        val entries = StatementImporter(SaudiStatements.D360).import(d360Rows, "d360-summer").entries
        val days = entries.map { sa.masrouf.core.time.RiyadhTime.localDate(it.draft.occurredAt) }

        assertEquals(LocalDate.of(2026, 7, 28), days[0])
        assertEquals(LocalDate.of(2026, 6, 28), days[4])
    }

    @Test
    fun `the explicit sign is ignored in favour of the column`() {
        val entries = StatementImporter(SaudiStatements.D360).import(d360Rows, "d360-summer").entries

        assertEquals(Money.ofMajor("2584.98"), entries[1].draft.amount)
        assertFalse(entries[1].draft.amount.isNegative, "amounts are stored unsigned")
        assertEquals(Direction.DEBIT, entries[1].draft.direction)
        assertEquals(Direction.CREDIT, entries[0].draft.direction)
    }

    // ---- Fingerprints ------------------------------------------------------

    @Test
    fun `re-importing the same file produces the same fingerprints`() {
        val first = StatementImporter(SaudiStatements.SNB).import(snbRows, "snb-july")
        val again = StatementImporter(SaudiStatements.SNB).import(snbRows, "snb-july")

        assertEquals(first.entries.map { it.fingerprint }, again.entries.map { it.fingerprint })
    }

    @Test
    fun `an overlapping file gives different fingerprints for the same rows`() {
        val july = StatementImporter(SaudiStatements.SNB).import(snbRows, "snb-july")
        val juneToJuly = StatementImporter(SaudiStatements.SNB).import(snbRows, "snb-june-july")

        assertNotEquals(july.entries[0].fingerprint, juneToJuly.entries[0].fingerprint)
    }

    // ---- Rows that are not transactions ------------------------------------

    @Test
    fun `header and footer rows are skipped, not reported as errors`() {
        val withNoise = rows(
            listOf("Balance", "Debit", "Credit", "Description", "Type", "Date"),
            listOf("32,059.00", "", "59.00", "نظام الأهلي للمدفوعات", "تحويل داخلي وارد", "03/07/2026"),
            listOf("33,459.00", "", "1,400.00", "نظام الأهلي للمدفوعات", "تحويل داخلي وارد", "04/07/2026"),
            listOf("28,959.00", "4,500.00", "", ",barq", "عملية شراء عبر الإنترنت", "04/07/2026"),
            listOf("This statement would be considered correct if no objection...", "", "", "", "", ""),
        )

        val result = StatementImporter(SaudiStatements.SNB).import(withNoise, "snb-noisy")

        assertEquals(3, result.entries.size)
        assertTrue(result.problems.isEmpty(), "noise rows are not errors: ${result.problems}")
    }

    @Test
    fun `a row with neither a debit nor a credit is skipped`() {
        val zeroed = rows(
            listOf("32,059.00", "0.00", "0.00", "رصيد مرحل", "", "03/07/2026"),
        )
        assertTrue(StatementImporter(SaudiStatements.SNB).import(zeroed, "x").entries.isEmpty())
    }

    /**
     * A single row reconciles trivially in both directions, so nothing can be
     * concluded from it - and claiming trust on that basis would be worse than
     * admitting the file was too short to check.
     */
    @Test
    fun `too few rows to judge is reported rather than trusted`() {
        val one = rows(
            listOf("32,059.00", "", "59.00", "نظام الأهلي للمدفوعات", "تحويل داخلي وارد", "03/07/2026"),
        )
        val result = StatementImporter(SaudiStatements.SNB).import(one, "snb-tiny")

        assertEquals(StatementImporter.RowOrder.UNDETERMINED, result.order)
        assertFalse(result.trustworthy)
        assertEquals(1, result.entries.size, "the row is still imported, just not vouched for")
    }

    @Test
    fun `an empty statement produces nothing and claims nothing`() {
        val result = StatementImporter(SaudiStatements.SNB).import(emptyList(), "empty")
        assertTrue(result.entries.isEmpty())
        assertFalse(result.trustworthy)
    }
}
