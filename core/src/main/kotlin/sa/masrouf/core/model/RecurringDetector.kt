package sa.masrouf.core.model

import sa.masrouf.core.money.Money
import java.time.Duration
import java.time.Instant
import kotlin.math.abs

/**
 * Finds the merchants a person pays on a rhythm.
 *
 * Nothing is declared by the user. A subscription is recognised the way a person
 * recognises one - the same merchant, at a steady interval, for roughly the same
 * money - and the whole point is that it costs no data entry, which is the
 * product's only claim.
 *
 * Deliberately conservative. A false "you pay this every month" is worse than a
 * missed one: the app is trusted for saying nothing it is unsure of, and this is
 * the one place it infers rather than reads.
 */
object RecurringDetector {

    /**
     * A cadence the detector recognises.
     *
     * @param amountTolerance how far an amount may sit from the median and still
     *   count as the same payment. Tight for the short cadences: a shop visited
     *   every day is not a standing order, and a daily donation of exactly ten
     *   riyals is. Loose for monthly and longer, because a phone bill moves with
     *   usage and is still a bill.
     */
    enum class Cadence(val days: Int, val tolerance: Int, val amountTolerance: Double) {
        /** A daily donation is a rhythm too, and a real history has one 946 days long. */
        DAILY(1, 1, 0.0),
        WEEKLY(7, 2, 0.05),
        FORTNIGHTLY(14, 3, 0.10),
        MONTHLY(30, 6, 0.35),
        QUARTERLY(91, 12, 0.35),
        YEARLY(365, 20, 0.35),
    }

    data class Recurring(
        val merchantKey: String,
        val merchantRaw: String,
        val cadence: Cadence,
        /** What it usually costs: the median, so one odd month does not move it. */
        val typicalAmount: Money,
        val lastAt: Instant,
        val nextExpected: Instant,
        val occurrences: Int,
        val categoryId: String?,
    )

    /**
     * At least this many, so there are three gaps to agree on. Three purchases
     * in a fortnight at iHerb met a two-gap test as "weekly"; they were a person
     * shopping, not a subscription.
     */
    private const val MIN_OCCURRENCES = 4

    /**
     * Only the latest events are judged. A subscription is what someone pays NOW;
     * judged over twelve years, YouTube Premium failed because 2022 had a gap in
     * it, and the question was never whether it was always regular.
     */
    private const val RECENT_WINDOW = 8

    /** The median gap must be this close to the cadence, in days. */
    private fun Cadence.accepts(medianGapDays: Double) = abs(medianGapDays - days) <= tolerance

    /**
     * How regular the gaps are: the share of gaps within the cadence's tolerance.
     * Below this the merchant is frequent, not periodic - a coffee shop visited
     * often is not a subscription.
     */
    private const val MIN_REGULARITY = 0.6

    /**
     * For at least this share of events. Looser than the timing test on purpose:
     * a phone bill is a subscription whose amount moves with usage, and a phone
     * bill was the first thing this excluded.
     */
    private const val MIN_AMOUNT_AGREEMENT = 0.5

    /** A rhythm that stopped this long ago is history, not a commitment. */
    private fun Cadence.staleAfter(): Duration = Duration.ofDays((days * 2L).coerceAtLeast(45))

    /**
     * @param transactions confirmed debits, any order, any span.
     * @param now for deciding what is still current.
     */
    fun detect(transactions: List<Transaction>, now: Instant): List<Recurring> =
        transactions
            .asSequence()
            .filter { it.direction == Direction.DEBIT && it.status == Status.CONFIRMED }
            .filter { !it.merchantKey.isNullOrBlank() }
            // Grouped by the name a person knows, where there is one, and by the
            // raw key otherwise. Netflix arrived as "NETFLIX COM" for eighteen
            // months and then as "NETFLIX"; judged per key, the subscription looked
            // like one that stopped in June and one that started in July.
            .groupBy { MerchantNames.forMerchant(it.merchantRaw)?.en ?: it.merchantKey!! }
            .mapNotNull { (key, rows) -> recurringOrNull(key, rows.sortedBy { it.occurredAt }, now) }
            .sortedByDescending { it.typicalAmount.halalas }

    private fun recurringOrNull(key: String, allRows: List<Transaction>, now: Instant): Recurring? {
        if (allRows.size < MIN_OCCURRENCES) return null
        val rows = allRows.takeLast(RECENT_WINDOW)

        val gapsDays = rows.zipWithNext { a, b ->
            Duration.between(a.occurredAt, b.occurredAt).toHours() / 24.0
        }
        val medianGap = gapsDays.sorted().let { it[it.size / 2] }
        val cadence = Cadence.entries.firstOrNull { it.accepts(medianGap) } ?: return null

        val regular = gapsDays.count { abs(it - cadence.days) <= cadence.tolerance }
        if (regular.toDouble() / gapsDays.size < MIN_REGULARITY) return null

        val amounts = rows.map { it.amount.halalas }.sorted()
        val median = amounts[amounts.size / 2]
        val agreeing = amounts.count { abs(it - median) <= median * cadence.amountTolerance }
        if (agreeing.toDouble() / amounts.size < MIN_AMOUNT_AGREEMENT) return null

        val last = rows.last()
        if (Duration.between(last.occurredAt, now) > cadence.staleAfter()) return null

        return Recurring(
            merchantKey = key,
            merchantRaw = last.merchantRaw ?: key,
            cadence = cadence,
            typicalAmount = Money.ofHalalas(median),
            lastAt = last.occurredAt,
            nextExpected = last.occurredAt.plus(Duration.ofDays(cadence.days.toLong())),
            occurrences = allRows.size,
            categoryId = last.categoryId,
        )
    }

    /** What the detected set costs per month, for a single line of summary. */
    fun monthlyCost(recurring: List<Recurring>): Money =
        recurring.fold(Money.ZERO) { sum, r ->
            sum + Money.ofHalalas(r.typicalAmount.halalas * 30 / r.cadence.days)
        }
}
