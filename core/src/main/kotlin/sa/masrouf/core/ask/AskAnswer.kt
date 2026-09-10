package sa.masrouf.core.ask

import sa.masrouf.core.model.INCOME_CATEGORY_IDS
import sa.masrouf.core.model.Status
import sa.masrouf.core.model.Transaction
import sa.masrouf.core.model.countsAsSpending
import sa.masrouf.core.money.Money
import sa.masrouf.core.text.ArabicText

/**
 * What a question came to.
 *
 * [rows] is capped; [count] and [total] are not. A question about a year can select
 * several thousand records and a screen cannot usefully show them, but the figure
 * has to be the figure for all of them - a total that quietly covered only the
 * first two hundred would be wrong in the direction nobody checks.
 */
data class AskAnswer(
    val query: AskQuery,
    val total: Money,
    val count: Int,
    val largest: Transaction?,
    val rows: List<Transaction>,
    val moreRows: Int,
    /**
     * Whether the subject exists at all in this history.
     *
     * A merchant nobody has ever bought from and a merchant with nothing in the
     * period asked about are different facts, and only one of them is "you spent
     * nothing". The screen says "no merchant by that name" for the first, which is
     * why the question is asked over the WHOLE history rather than the period.
     */
    val subjectSeenEver: Boolean,
) {
    val isEmpty: Boolean get() = count == 0
}

/**
 * How many rows a screen is given. Past this it is a wall of text, and the counted
 * remainder says more than the two-hundredth row would.
 */
private const val ROW_CAP = 200

/**
 * Answers a query from rows already narrowed to its period.
 *
 * Pure, and in `:core` on purpose: this is where a figure about someone's money is
 * decided, and this module is the one that is proven correct on any machine with a
 * JDK. The caller does the date range in SQL because that is what an index is for;
 * everything that decides MEANING happens here.
 *
 * @param inPeriod rows whose date already falls inside [AskQuery.period]
 * @param subjectSeenEver whether this subject appears anywhere in the history at
 *   all, which the caller answers with one indexed query rather than by loading
 *   twelve years of rows to find out. Only a merchant the user typed can be
 *   genuinely unknown; a category and a topic are the app's own words and always
 *   exist, so the caller passes true for those.
 */
fun AskQuery.answeredFrom(
    inPeriod: List<Transaction>,
    subjectSeenEver: Boolean = true,
): AskAnswer {
    val selected = inPeriod.filter { selects(it) }
    val ordered = selected.sortedByDescending { it.occurredAt }
    return AskAnswer(
        query = this,
        // Money is integer halalas and stays that way: summed as Long and wrapped
        // once, never through a Double.
        total = Money.ofHalalas(selected.sumOf { it.amount.halalas }),
        count = selected.size,
        largest = selected.maxByOrNull { it.amount.halalas },
        rows = ordered.take(ROW_CAP),
        moreRows = (ordered.size - ROW_CAP).coerceAtLeast(0),
        subjectSeenEver = subjectSeenEver,
    )
}

/** Whether one row is what the question asked about. */
private fun AskQuery.selects(row: Transaction): Boolean =
    row.status == Status.CONFIRMED && countsForFlow(row) && matchesSubject(row)

/**
 * Spending and income are decided by the same two rules the rest of the app uses -
 * `countsAsSpending` and `INCOME_CATEGORY_IDS` - and not by anything written here.
 * CLAUDE.md rule 5: two surfaces disagreeing about one month is the failure that
 * one decision in one place exists to prevent, and an answer screen is a surface.
 */
private fun AskQuery.countsForFlow(row: Transaction): Boolean = when (flow) {
    Flow.SPENDING -> row.countsAsSpending
    Flow.INCOME -> row.categoryId in INCOME_CATEGORY_IDS
}

private fun AskQuery.matchesSubject(row: Transaction): Boolean = when (val s = subject) {
    is Subject.Everything -> true
    is Subject.OfCategory -> row.categoryId == s.category.id
    is Subject.OfTopic -> s.topic.claims(row.merchantRaw)
    is Subject.AtMerchant -> merchantMatches(row.merchantRaw, s.keyword)
}

/**
 * A merchant the user typed, against the merchant a row carries.
 *
 * Contains rather than equals, and folded on both sides: a person types "امازون"
 * or "hunger" and the stored name is "Amazon SA" or "HUNGERSTATION LLC". This is
 * looser than the shipped merchant list's matching on purpose - a person naming a
 * shop out loud is not a card terminal truncating one - and it is safe here because
 * the result is shown WITH its rows, so an over-broad match is visible in a way a
 * silent mis-filing never is.
 */
private fun merchantMatches(merchantRaw: String?, typed: String): Boolean {
    val stored = merchantRaw?.let(ArabicText::foldForMatching) ?: return false
    val wanted = ArabicText.foldForMatching(MerchantAliases.resolve(typed))
    return wanted.isNotBlank() && stored.contains(wanted)
}
