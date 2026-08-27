package sa.masrouf.core.statement

import sa.masrouf.core.capture.IntentClassifier
import sa.masrouf.core.dedup.Fingerprint
import sa.masrouf.core.model.Direction
import sa.masrouf.core.model.Source
import sa.masrouf.core.model.TransactionDraft
import sa.masrouf.core.model.TransactionType
import sa.masrouf.core.money.Money
import sa.masrouf.core.text.ArabicText
import sa.masrouf.core.text.VisualOrder
import sa.masrouf.core.time.RiyadhTime
import java.time.LocalDate

/**
 * Turns extracted statement rows into transaction drafts, and proves its own work.
 *
 * ## Why this checks itself
 *
 * Every other parser in this app can be wrong in a way a person would notice. A
 * statement importer cannot: it produces hundreds of rows at once, nobody reads
 * them, and the single most likely mistake - reading the debit column as the
 * credit column - inverts every sign without failing, throwing, or looking odd.
 * SNB and AlRajhi order those two columns oppositely, so this is not a
 * hypothetical.
 *
 * Statements happen to carry the antidote. Each row prints the running balance,
 * so the file states its own arithmetic:
 *
 *     balance[n] == balance[n-1] + signed(amount[n])
 *
 * If the columns were swapped, or an amount misread, or a row dropped, that
 * identity breaks. So the importer computes it for every row and reports what did
 * not reconcile rather than quietly returning plausible numbers.
 *
 * The same check reveals row order for free. SNB and AlRajhi list oldest first,
 * D360 newest first; instead of recording that per bank - where a change would go
 * unnoticed - the importer reconciles both ways and keeps whichever the file
 * actually agrees with.
 */
class StatementImporter(private val layout: StatementLayout) {

    enum class RowOrder { OLDEST_FIRST, NEWEST_FIRST, UNDETERMINED }

    data class Entry(
        val rowIndex: Int,
        val draft: TransactionDraft,
        val fingerprint: String,
        val balanceAfter: Money?,
        /** False when this row's balance did not follow from the previous one. */
        val reconciled: Boolean,
    )

    sealed interface Problem {
        val rowIndex: Int

        /** The row could not be read at all. Never becomes a transaction. */
        data class Unreadable(override val rowIndex: Int, val reason: String) : Problem

        /**
         * The row parsed, but its balance does not follow from the previous row.
         *
         * The strongest available signal that the layout is wrong for this file.
         */
        data class BalanceMismatch(
            override val rowIndex: Int,
            val expected: Money,
            val printed: Money,
        ) : Problem
    }

    data class Result(
        val entries: List<Entry>,
        val order: RowOrder,
        val problems: List<Problem>,
    ) {
        val reconciledCount: Int get() = entries.count { it.reconciled }

        /**
         * True when the file's own arithmetic agrees with how it was read.
         *
         * The gate for importing without asking the user to review every row.
         * A single unreconciled row is tolerated - statements do carry the odd
         * fee line outside the running total - but a systematic disagreement
         * means the layout is wrong and nothing should be trusted.
         */
        val trustworthy: Boolean
            get() = entries.size >= MIN_ROWS_TO_JUDGE &&
                reconciledCount >= entries.size - 1 &&
                order != RowOrder.UNDETERMINED
    }

    /**
     * @param statementId content hash of the source file, so that re-importing the
     *   same file produces the same fingerprints and changes nothing.
     * @param accountLast4 last four digits of the account the statement belongs to,
     *   read from its header. Statement rows do not repeat it.
     */
    fun import(
        rows: List<StatementRow>,
        statementId: String,
        accountLast4: String? = null,
    ): Result {
        val problems = ArrayList<Problem>()
        val parsed = ArrayList<ParsedRow>()

        for (row in rows) {
            when (val outcome = parseRow(row)) {
                is ParseOutcome.Ok -> parsed.add(outcome.row)
                is ParseOutcome.Skip -> Unit
                is ParseOutcome.Bad -> problems.add(Problem.Unreadable(row.index, outcome.reason))
            }
        }

        val oldestFirstMatches = countReconciled(parsed, RowOrder.OLDEST_FIRST)
        val newestFirstMatches = countReconciled(parsed, RowOrder.NEWEST_FIRST)
        val order = when {
            parsed.size < MIN_ROWS_TO_JUDGE -> RowOrder.UNDETERMINED
            oldestFirstMatches > newestFirstMatches -> RowOrder.OLDEST_FIRST
            newestFirstMatches > oldestFirstMatches -> RowOrder.NEWEST_FIRST
            else -> RowOrder.UNDETERMINED
        }

        // When neither direction reconciles, the order is genuinely undetermined -
        // but that is exactly the case where the user most needs to see WHICH rows
        // disagree, because it is the signature of a wrong layout. So diagnostics
        // still run, against the less-bad direction, while the reported order stays
        // undetermined and the result stays untrustworthy.
        val diagnosticOrder = when {
            order != RowOrder.UNDETERMINED -> order
            newestFirstMatches > oldestFirstMatches -> RowOrder.NEWEST_FIRST
            else -> RowOrder.OLDEST_FIRST
        }
        val reconciled = reconciliationFlags(parsed, diagnosticOrder, problems)

        val entries = parsed.mapIndexed { position, row ->
            Entry(
                rowIndex = row.index,
                draft = row.toDraft(accountLast4),
                fingerprint = Fingerprint.forStatementRow(
                    statementId = statementId,
                    rowIndex = row.index,
                    date = row.date,
                    amount = row.amount,
                    direction = row.direction,
                    last4 = accountLast4,
                    merchantRaw = row.description,
                ),
                balanceAfter = row.balance,
                reconciled = reconciled[position],
            )
        }

        return Result(entries, order, problems)
    }

    // ---- Row parsing -------------------------------------------------------

    private class ParsedRow(
        val index: Int,
        val date: LocalDate,
        val amount: Money,
        val direction: Direction,
        val type: TransactionType,
        val description: String,
        val balance: Money?,
    ) {
        fun toDraft(accountLast4: String?) = TransactionDraft(
            amount = amount,
            direction = direction,
            type = type,
            occurredAt = RiyadhTime.toInstant(date),
            merchantRaw = description.takeIf { it.isNotBlank() },
            accountLast4 = accountLast4,
            rawText = description.takeIf { it.isNotBlank() },
        )
    }

    private sealed interface ParseOutcome {
        class Ok(val row: ParsedRow) : ParseOutcome
        /** A header, a footer, or a carried-forward line. Not a transaction, not an error. */
        data object Skip : ParseOutcome
        class Bad(val reason: String) : ParseOutcome
    }

    private fun parseRow(row: StatementRow): ParseOutcome {
        val dateText = row.cell(layout.dateColumn)
        val date = layout.parseDate(dateText) ?: return ParseOutcome.Skip

        val debit = amountOrNull(row.cell(layout.debitColumn))
        val credit = amountOrNull(row.cell(layout.creditColumn))

        val direction = when {
            debit != null && !debit.isZero && (credit == null || credit.isZero) -> Direction.DEBIT
            credit != null && !credit.isZero && (debit == null || debit.isZero) -> Direction.CREDIT
            (debit == null || debit.isZero) && (credit == null || credit.isZero) ->
                return ParseOutcome.Skip

            else -> return ParseOutcome.Bad("both debit and credit are non-zero")
        }
        val amount = if (direction == Direction.DEBIT) debit!! else credit!!

        val description = readText(row.cell(layout.descriptionColumn))
        val typeText = layout.typeColumn?.let { readText(row.cell(it)) }.orEmpty()

        return ParseOutcome.Ok(
            ParsedRow(
                index = row.index,
                date = date,
                amount = amount,
                direction = direction,
                type = classify(listOf(typeText, description).joinToString("\n"), direction),
                description = description,
                balance = balanceOrNull(row.cell(layout.balanceColumn)),
            )
        )
    }

    /**
     * The wording gives the kind of transaction; the column gives the direction.
     *
     * The column wins. A statement's debit and credit columns are unambiguous,
     * whereas the wording sometimes is not - AlRajhi's "عملية تحويل داخلية" says a
     * transfer happened but not which way. So an intent whose direction disagrees
     * with the column is corrected rather than trusted.
     */
    private fun classify(text: String, direction: Direction): TransactionType {
        val intent = IntentClassifier.classify(text) ?: return TransactionType.UNKNOWN
        if (intent.direction == direction) return intent.type
        return when (intent.type) {
            TransactionType.TRANSFER_IN -> TransactionType.TRANSFER_OUT
            TransactionType.TRANSFER_OUT -> TransactionType.TRANSFER_IN
            else -> intent.type
        }
    }

    private fun readText(cell: String): String {
        val restored = if (layout.visuallyOrdered && VisualOrder.looksVisuallyOrdered(cell)) {
            VisualOrder.restore(cell)
        } else {
            cell
        }
        return ArabicText.normalize(restored)
    }

    /**
     * Reads the first amount out of a cell, ignoring currency suffixes and the
     * explicit sign D360 prints. The sign is redundant - the column already says
     * which way the money went - and amounts are stored unsigned throughout.
     */
    private fun amountOrNull(cell: String): Money? {
        val normalized = ArabicText.normalize(cell)
        val token = AMOUNT_IN_CELL.find(normalized)?.value ?: return null
        return Money.parseOrNull(token)
    }

    /**
     * Reads a running balance, honouring the `Cr`/`Dr` suffix Emirates NBD prints
     * (`500.00Cr`, and `Dr` when the account is overdrawn).
     *
     * A balance, unlike a transaction amount, is genuinely signed. Dropping the
     * suffix would make an overdraft read as a positive balance, and every
     * reconciliation from that row onward would fail for no visible reason.
     */
    private fun balanceOrNull(cell: String): Money? {
        val amount = amountOrNull(cell) ?: return null
        val overdrawn = DEBIT_BALANCE_SUFFIX.containsMatchIn(ArabicText.normalize(cell))
        return if (overdrawn) -amount else amount
    }

    // ---- Balance reconciliation --------------------------------------------

    private fun countReconciled(rows: List<ParsedRow>, order: RowOrder): Int =
        reconciliationFlags(rows, order, problems = null).count { it }

    /**
     * Marks each row as reconciled or not, and records the failures when asked.
     *
     * The first row in reading order has no predecessor to check against and is
     * accepted as given; every later row must equal its predecessor's balance plus
     * or minus its own amount.
     */
    private fun reconciliationFlags(
        rows: List<ParsedRow>,
        order: RowOrder,
        problems: MutableList<Problem>?,
    ): BooleanArray {
        val flags = BooleanArray(rows.size)
        if (order == RowOrder.UNDETERMINED || rows.isEmpty()) return flags

        // Walk the rows oldest to newest, whichever end of the list that is.
        val walk = if (order == RowOrder.OLDEST_FIRST) rows.indices.toList() else rows.indices.reversed().toList()

        var previousBalance: Money? = null
        for (position in walk) {
            val row = rows[position]
            val balance = row.balance
            val expected = previousBalance?.let { it + row.signed() }

            flags[position] = when {
                balance == null -> false
                expected == null -> true // first row in the walk: nothing to check against
                expected == balance -> true
                else -> {
                    problems?.add(Problem.BalanceMismatch(row.index, expected, balance))
                    false
                }
            }
            if (balance != null) previousBalance = balance
        }
        return flags
    }

    private fun ParsedRow.signed(): Money =
        if (direction == Direction.DEBIT) -amount else amount

    private companion object {
        /**
         * Below this, "the balances reconcile" says nothing: a single row always
         * reconciles trivially in both directions, so order cannot be derived and
         * the check cannot vouch for the layout.
         */
        const val MIN_ROWS_TO_JUDGE = 3

        /** `1,234.56`, `5.00 SAR`, `+ 2,000.00`, `- 16.30` - the number, without its sign. */
        val AMOUNT_IN_CELL = Regex("""\d[\d,]*(?:\.\d{1,2})?""")

        /** A balance written `1,234.56Dr` - an overdrawn, and therefore negative, balance. */
        val DEBIT_BALANCE_SUFFIX = Regex("""\d\s*DR\b""", RegexOption.IGNORE_CASE)
    }
}

/** Statement entries are recorded as coming from a statement, never as messages. */
val StatementImporter.Entry.source: Source get() = Source.STATEMENT
