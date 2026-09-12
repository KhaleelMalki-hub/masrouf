package sa.masrouf.app.data

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import sa.masrouf.core.dedup.Fingerprint
import sa.masrouf.core.model.Direction
import sa.masrouf.core.model.Source
import sa.masrouf.core.model.Status
import sa.masrouf.core.model.Transaction
import sa.masrouf.core.model.TransactionType
import sa.masrouf.core.money.Money
import sa.masrouf.core.statement.SaudiStatements
import sa.masrouf.core.statement.StatementImporter
import sa.masrouf.core.statement.StatementRow
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A statement import is the only path in this app that writes hundreds of rows at
 * once with nobody reading them, so every way it can be wrong is silent. These are
 * the four.
 *
 * Rows transcribed from a real SNB statement with names replaced; amounts,
 * balances and column order kept, because those are what is being tested.
 */
class StatementImportTest {

    private val dao = FakeDao()
    private val repository = TransactionRepository(dao)

    private fun rows(vararg cells: List<String>) =
        cells.mapIndexed { index, row -> StatementRow(index, row) }

    /** [balance, debit, credit, description, type, date] */
    private val july = rows(
        listOf("32,059.00", "", "59.00", "نظام الأهلي للمدفوعات", "تحويل داخلي وارد", "03/07/2026\n23:42"),
        listOf("28,959.00", "3,100.00", "", ",barq\n***1887", "عملية شراء عبر الإنترنت", "04/07/2026\n02:33"),
        listOf("26,959.00", "2,000.00", "", "تحويل الى الاهل والاصدقاء", "تحويل داخلي صادر", "05/07/2026\n11:34"),
    )

    private fun imported(id: String = "snb-july", statement: List<StatementRow> = july) =
        StatementImporter(SaudiStatements.SNB).import(statement, id)

    @Test
    fun `a trustworthy statement stores its rows as confirmed statement records`() = runTest {
        val outcome = repository.importStatement(imported(), accountLast4 = "1887")

        assertFalse(outcome.refused)
        assertEquals(3, outcome.stored)
        assertEquals(0, outcome.duplicates)
        assertEquals(3, dao.rows.size)
        assertTrue(dao.rows.all { it.source == Source.STATEMENT.name })
        // Not PENDING: a statement is the bank's ledger, not a guess about an SMS,
        // and three hundred rows in the pending strip bury the captures that
        // genuinely need a decision.
        assertTrue(dao.rows.all { it.status == Status.CONFIRMED.name })
    }

    /**
     * The failure this path exists to prevent. The same file imported twice must
     * store nothing the second time - and it is the fingerprint, derived from the
     * file's own content hash, that makes that true.
     */
    @Test
    fun `re-importing the same file stores nothing`() = runTest {
        repository.importStatement(imported(), accountLast4 = "1887")
        val second = repository.importStatement(imported(), accountLast4 = "1887")

        assertEquals(0, second.stored)
        assertEquals(3, dao.rows.size)
    }

    /**
     * A purchase already captured from its SMS must not be counted again when the
     * statement that also lists it arrives.
     */
    @Test
    fun `a row already captured from its SMS is not stored twice`() = runTest {
        repository.recordCaptured(
            Transaction(
                id = "sms-row",
                amount = Money.ofMajor("3100.00"),
                direction = Direction.DEBIT,
                type = TransactionType.PURCHASE,
                occurredAt = Instant.parse("2026-07-03T23:33:00Z"),
                accountId = null,
                categoryId = null,
                merchantRaw = "barq",
                merchantKey = "BARQ",
                note = null,
                source = Source.SMS,
                status = Status.PENDING,
                fingerprint = Fingerprint.forMessage(
                    source = Source.SMS,
                    occurredAt = Instant.parse("2026-07-03T23:33:00Z"),
                    amount = Money.ofMajor("3100.00"),
                    direction = Direction.DEBIT,
                    last4 = "1887",
                    merchantRaw = "barq",
                ),
                rawText = "شراء بمبلغ 3100.00",
                accountLast4 = "1887",
            ),
        )

        val outcome = repository.importStatement(imported(), accountLast4 = "1887")

        assertEquals(2, outcome.stored)
        assertEquals(1, outcome.duplicates)
        assertEquals(3, dao.rows.size)
    }

    /**
     * The dangerous one. Reading the debit column as the credit column inverts
     * every sign without throwing, and afterwards the invented income is
     * indistinguishable from the real thing. The importer detects it through the
     * running balance; this asserts the repository refuses to write it even so.
     */
    @Test
    fun `nothing is stored when the file does not reconcile`() = runTest {
        // The same rows read with AlRajhi's layout, where debit and credit are
        // swapped and the dates are year-first.
        val wrongLayout = StatementImporter(SaudiStatements.AL_RAJHI).import(july, "snb-july")

        val outcome = repository.importStatement(wrongLayout, accountLast4 = "1887")

        assertTrue(outcome.refused)
        assertEquals(0, outcome.stored)
        assertTrue(dao.rows.isEmpty())
    }
}
