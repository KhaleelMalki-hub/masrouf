package sa.masrouf.core.statement

import java.security.MessageDigest

/**
 * Reads a statement that has already been turned into a table.
 *
 * Nothing on Android extracts table cells from a PDF, and this history needs the
 * years before the app existed imported ONCE. So the extraction happens off the
 * phone and lands here as tab-separated cells, in the column order the bank prints
 * and [SaudiStatements] describes - deliberately not a format of its own, because
 * a second layout table is a second thing to get wrong, and getting a column order
 * wrong is the mistake this whole package is built to catch.
 *
 * ```
 * # layout=snb account=1887
 * 32,059.00	<TAB>	59.00	نظام الأهلي للمدفوعات	تحويل داخلي وارد	03/07/2026\n23:42
 * ```
 *
 * A cell's own line break is written `\n`, because the banks print a date and its
 * time in one cell and a tab-separated line cannot hold a real one.
 */
object StatementTsv {

    data class Parsed(
        val layout: StatementLayout,
        val rows: List<StatementRow>,
        val accountLast4: String?,
        /**
         * Content hash of the file. The importer turns it into each row's
         * fingerprint, so importing the same file twice stores nothing the second
         * time - which is the property that makes a one-shot backfill safe to retry.
         */
        val statementId: String,
    )

    sealed interface Outcome {
        data class Ok(val parsed: Parsed) : Outcome

        /** The file cannot be read at all. Never becomes transactions. */
        data class Bad(val reason: String) : Outcome
    }

    private const val HEADER = "#"
    private const val LAST4_LENGTH = 4

    /**
     * Validates before it converts. Every failure here is a refusal with a reason:
     * this is a trust boundary, the rows behind it become money, and a file that
     * names no layout must never be guessed at - guessing the layout is guessing
     * which column is the debit.
     */
    fun parse(text: String): Outcome {
        val lines = text.lineSequence().filter { it.isNotBlank() }.toList()
        val header = lines.firstOrNull()?.takeIf { it.startsWith(HEADER) }
            ?: return Outcome.Bad("no header line; expected \"# layout=<bank> account=<last4>\"")

        val fields = header.removePrefix(HEADER).trim()
            .split(' ')
            .filter { it.isNotBlank() }
            .mapNotNull { field ->
                val at = field.indexOf('=')
                if (at <= 0) null else field.take(at) to field.substring(at + 1)
            }
            .toMap()

        val layoutId = fields["layout"]
            ?: return Outcome.Bad("header names no layout")
        val layout = SaudiStatements.ALL.firstOrNull { it.id == layoutId }
            ?: return Outcome.Bad(
                "unknown layout \"$layoutId\"; known: " +
                    SaudiStatements.ALL.joinToString(", ") { it.id },
            )

        val accountLast4 = fields["account"]?.takeIf { it.length == LAST4_LENGTH && it.all(Char::isDigit) }

        val rows = lines.drop(1).mapIndexed { index, line ->
            StatementRow(index, line.split('\t').map { it.replace("\\n", "\n").trim() })
        }
        if (rows.isEmpty()) return Outcome.Bad("no rows under the header")

        return Outcome.Ok(
            Parsed(
                layout = layout,
                rows = rows,
                accountLast4 = accountLast4,
                statementId = sha256(text),
            ),
        )
    }

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
