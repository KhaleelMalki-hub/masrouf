package sa.masrouf.core.statement

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class StatementTsvTest {

    private val file = """
        # layout=snb account=1887
        32,059.00		59.00	نظام الأهلي للمدفوعات	تحويل داخلي وارد	03/07/2026\n23:42
        28,959.00	3,100.00		,barq\n***1887	عملية شراء عبر الإنترنت	04/07/2026\n02:33
    """.trimIndent()

    @Test
    fun `reads the header, the cells and the cells' own line breaks`() {
        val parsed = assertIs<StatementTsv.Outcome.Ok>(StatementTsv.parse(file)).parsed

        assertEquals("snb", parsed.layout.id)
        assertEquals("1887", parsed.accountLast4)
        assertEquals(2, parsed.rows.size)
        assertEquals("03/07/2026\n23:42", parsed.rows[0].cell(5))
        assertEquals("", parsed.rows[0].cell(1))
    }

    /** The whole point of the format: it feeds the importer, which checks itself. */
    @Test
    fun `what it produces reconciles against the running balance`() {
        val parsed = assertIs<StatementTsv.Outcome.Ok>(StatementTsv.parse(file)).parsed

        val result = StatementImporter(parsed.layout).import(
            parsed.rows,
            parsed.statementId,
            parsed.accountLast4,
        )
        assertEquals(2, result.entries.size)
        assertTrue(result.entries.all { it.reconciled })
    }

    /** Same bytes, same fingerprints, so a re-import stores nothing. */
    @Test
    fun `the same file hashes the same and a different one does not`() {
        val a = assertIs<StatementTsv.Outcome.Ok>(StatementTsv.parse(file)).parsed
        val b = assertIs<StatementTsv.Outcome.Ok>(StatementTsv.parse(file)).parsed
        val other = assertIs<StatementTsv.Outcome.Ok>(
            StatementTsv.parse(file.replace("3,100.00", "3,200.00")),
        ).parsed

        assertEquals(a.statementId, b.statementId)
        assertTrue(a.statementId != other.statementId)
    }

    /**
     * Guessing the layout is guessing which column is the debit, and reading the
     * debit column as the credit turns every expense into income without failing.
     * So an unnamed or unknown layout is refused, never inferred.
     */
    @Test
    fun `a file that names no known layout is refused`() {
        assertIs<StatementTsv.Outcome.Bad>(StatementTsv.parse("32,059.00\t\t59.00"))
        assertIs<StatementTsv.Outcome.Bad>(StatementTsv.parse("# account=1887\n32,059.00"))
        val unknown = assertIs<StatementTsv.Outcome.Bad>(StatementTsv.parse("# layout=barclays\n32,059.00"))
        assertTrue(unknown.reason.contains("snb"), "the refusal should say what IS known: ${unknown.reason}")
        assertIs<StatementTsv.Outcome.Bad>(StatementTsv.parse("# layout=snb account=1887"))
    }
}
