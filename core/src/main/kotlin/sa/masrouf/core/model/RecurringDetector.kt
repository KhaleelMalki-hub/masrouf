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

    /**
     * How far apart the extremes sit, as a share of the median.
     *
     * Above [VARIES_ABOVE] the single figure is not worth showing on its own. The
     * threshold is not the detector's own [Cadence.amountTolerance]: that decides
     * whether a stream is a subscription at all, and this decides only how to say
     * so. A bill can be a real monthly commitment and still be a poor thing to
     * quote one number for.
     */
    fun Recurring.spread(): Double =
        if (typicalAmount.halalas == 0L) 0.0
        else (highAmount.halalas - lowAmount.halalas).toDouble() / typicalAmount.halalas

    /** Above this share, show the range beside the typical figure. */
    const val VARIES_ABOVE = 0.25

    data class Recurring(
        val merchantKey: String,
        val merchantRaw: String,
        val cadence: Cadence,
        /** What it usually costs: the median, so one odd month does not move it. */
        val typicalAmount: Money,
        /**
         * The lowest and highest actually seen in the window that was judged.
         *
         * Carried because [typicalAmount] alone reads as a promise. An electricity
         * bill's median of 347 was shown beside the word "monthly" and the owner
         * read it as a figure the app was claiming he would pay; what he pays is
         * between 299 and 554. The median is the honest single number and the range
         * is what stops it being mistaken for a fixed one.
         */
        val lowAmount: Money,
        val highAmount: Money,
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
            .filter { it.status == Status.CONFIRMED }
            // A payment, not a movement. [countsAsSpending] is the app's single
            // decision about which debits are money leaving, and this panel had
            // been asking a different question: it read the owner's 186 transfers
            // to his own AlRajhi account as a recurring payment, and told him on
            // the home screen that he pays out 102,890 riyals a month.
            //
            // Reusing that one function rather than listing types here is CLAUDE.md
            // rule 5 - two surfaces disagreeing about what counts is the failure it
            // exists to prevent, and a figure at the top of the screen is a surface.
            .filter { it.countsAsSpending }
            .filter { !it.merchantKey.isNullOrBlank() }
            // Grouped by the name a person knows, where there is one, and by the
            // raw key otherwise. Netflix arrived as "NETFLIX COM" for eighteen
            // months and then as "NETFLIX"; judged per key, the subscription looked
            // like one that stopped in June and one that started in July.
            .groupBy { MerchantNames.forMerchant(it.merchantRaw)?.en ?: it.merchantKey!! }
            .flatMap { (key, rows) -> recurringIn(key, rows.sortedBy { it.occurredAt }, now) }
            .sortedByDescending { it.typicalAmount.halalas }

    /**
     * One merchant can carry several rhythms.
     *
     * STC bills the phone line on the 2nd and the home internet on the 28th under
     * one descriptor; a person paid every month also receives one-off transfers
     * under the same name. Judged as one stream those fail on amount or timing, so
     * when the whole fails, the rows are split into clusters of like amounts and
     * each cluster is judged on its own.
     */
    private fun recurringIn(key: String, rows: List<Transaction>, now: Instant): List<Recurring> {
        recurringOrNull(key, rows, now)?.let { return listOf(it) }
        return amountClusters(rows)
            .filter { it.size >= MIN_OCCURRENCES }
            .mapNotNull { recurringOrNull(key, it, now) }
    }

    /** Greedy clusters of amounts within a fifth of their seed, seed taken from the sorted list. */
    private fun amountClusters(rows: List<Transaction>): List<List<Transaction>> {
        val remaining = rows.sortedBy { it.amount.halalas }.toMutableList()
        val clusters = mutableListOf<List<Transaction>>()
        while (remaining.isNotEmpty()) {
            val seed = remaining.first().amount.halalas
            // The seed is the smallest remaining. A non-positive one makes the
            // tolerance non-positive too, so nothing qualifies - not even the seed -
            // `removeAll` removes nothing, and the loop spins forever on
            // Dispatchers.Default with no error anywhere. Amounts are non-negative
            // by construction today; this costs one token and removes the class.
            val cluster = remaining
                .filter { abs(it.amount.halalas - seed) <= seed * CLUSTER_TOLERANCE }
                .ifEmpty { listOf(remaining.first()) }
            clusters += cluster.sortedBy { it.occurredAt }
            remaining.removeAll(cluster)
        }
        return clusters
    }

    private const val CLUSTER_TOLERANCE = 0.2

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
            lowAmount = Money.ofHalalas(amounts.first()),
            highAmount = Money.ofHalalas(amounts.last()),
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
