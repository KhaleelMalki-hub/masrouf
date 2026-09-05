package sa.masrouf.core.dedup

import sa.masrouf.core.model.Source
import java.time.Duration
import kotlin.math.abs

/**
 * Decides which incoming records describe transactions the app already has.
 *
 * The same purchase can reach this app three times: as a bank SMS, as a push
 * notification from the same bank's app, and later as a row in a downloaded
 * statement. All three must collapse into one record. At the same time, two
 * genuinely separate transactions that look identical must stay separate.
 *
 * ## The case that shapes the whole design
 *
 * From real captured data - two wallet top-ups on one morning:
 *
 *     08:02   شراء انترنت بـSAR 5000 ... من barq ... مدى *1887
 *     08:51   شراء انترنت بـSAR 5000 ... من barq ... مدى *1887
 *
 * Identical in amount, day, card and merchant. Both real. Any rule that merges
 * records sharing those four fields destroys 5,000 SAR, and leaves behind a record
 * that looks entirely correct.
 *
 * What separates them is that they came from the *same source* 49 minutes apart.
 * Two messages from one sender are two events unless they arrive within seconds of
 * each other (a redelivery). Whereas a statement row and a notification are never
 * two events when they agree on amount, direction, card and date - a statement is
 * a second telling of the same day, not new spending.
 *
 * ## Matching is on counts, not just keys
 *
 * When a day holds two 5,000 top-ups in the notification history and two 5,000
 * rows in an imported statement, the answer is two merges - not one merge and one
 * duplicate, and not four records. So candidates are paired off as a multiset:
 * `min(existing, incoming)` per bucket merge, and any surplus on the incoming side
 * is genuinely new. This falls out of the greedy pairing below rather than being a
 * special case.
 */
class DuplicateDetector(
    /**
     * How far apart two records may sit and still describe one transaction, when at
     * least one of them came from a statement.
     *
     * One day, because a purchase made late at night commonly posts to the
     * statement on the following date.
     */
    private val statementDayWindow: Long = 1,

    /**
     * How far apart two records from message sources may sit and still be one
     * event.
     *
     * Two minutes: long enough to absorb a redelivered SMS or the same alert
     * arriving as both an SMS and a push, short enough that the 49 minutes between
     * the two 5,000 top-ups is never mistaken for one.
     */
    private val messageRedeliveryWindow: Duration = Duration.ofMinutes(2),

    /**
     * How far apart one bank's two DIFFERENTLY-WORDED messages may sit and still be
     * two tellings of one event.
     *
     * Much tighter than the redelivery window, deliberately. A bank that announces
     * one transfer under several templates sends them in the same breath; two
     * minutes is long enough for a person to make a second transfer of the same
     * size, and that is the error that costs money.
     */
    private val retellWindow: Duration = Duration.ofSeconds(30),
) {

    /** A pairing of one incoming record with the already-stored record it duplicates. */
    data class Match(
        val existingIndex: Int,
        val incomingIndex: Int,
        val dayGap: Long,
        /** True when both sides named the same merchant. Raises confidence, never required. */
        val merchantAgrees: Boolean,
    )

    data class Result(
        val matches: List<Match>,
        /** Indices into `incoming` that are new and should be stored. */
        val newIncoming: List<Int>,
    )

    /**
     * @param existing signatures of records already stored, in any order.
     * @param incoming signatures of records just captured or imported.
     */
    fun reconcile(existing: List<EventSignature>, incoming: List<EventSignature>): Result {
        val candidates = buildList {
            existing.forEachIndexed { existingIndex, stored ->
                incoming.forEachIndexed { incomingIndex, fresh ->
                    if (couldBeSameEvent(stored, fresh)) {
                        add(
                            Match(
                                existingIndex = existingIndex,
                                incomingIndex = incomingIndex,
                                dayGap = abs(stored.day.toEpochDay() - fresh.day.toEpochDay()),
                                merchantAgrees = stored.merchantKey != null &&
                                    stored.merchantKey == fresh.merchantKey,
                            )
                        )
                    }
                }
            }
        }

        // Best evidence first: same day beats adjacent day, and an agreeing merchant
        // beats a missing one. Greedy over this order rather than a full optimal
        // bipartite matching: within a bucket the candidates are interchangeable by
        // construction, so the assignment differs only in which equal record pairs
        // with which - and the resulting counts, which are what matter, are the same.
        val ordered = candidates.sortedWith(
            compareBy<Match> { it.dayGap }
                .thenByDescending { it.merchantAgrees }
                .thenBy { it.incomingIndex }
        )

        val usedExisting = HashSet<Int>()
        val usedIncoming = HashSet<Int>()
        val matches = ArrayList<Match>()
        for (candidate in ordered) {
            if (candidate.existingIndex in usedExisting) continue
            if (candidate.incomingIndex in usedIncoming) continue
            usedExisting.add(candidate.existingIndex)
            usedIncoming.add(candidate.incomingIndex)
            matches.add(candidate)
        }

        return Result(
            matches = matches,
            newIncoming = incoming.indices.filterNot { it in usedIncoming },
        )
    }

    private fun couldBeSameEvent(a: EventSignature, b: EventSignature): Boolean {
        if (a.amount != b.amount) return false
        if (a.direction != b.direction) return false
        if (!cardsCompatible(a.last4, b.last4)) return false

        // A record the user typed is never merged away. They saw the transaction and
        // meant it; silently folding it into an imported row would discard the only
        // record in the system that a human vouched for.
        if (a.source == Source.MANUAL || b.source == Source.MANUAL) return false

        val bothFromMessages = !a.source.isStatement && !b.source.isStatement
        if (!bothFromMessages) {
            return abs(a.day.toEpochDay() - b.day.toEpochDay()) <= statementDayWindow
        }

        val gap = Duration.between(a.occurredAt, b.occurredAt).abs()
        val sameRoute = a.source == b.source && a.body != null && b.body != null
        return if (sameRoute) {
            // A redelivered SMS is the same SMS, byte for byte. Two purchases a
            // minute apart are two bodies, because the bank stamps each with its own
            // time - and that is what kept the second of two 250-riyal Tamra
            // deposits alive.
            //
            // But a bank also announces ONE transfer under several templates, and
            // those bodies differ too: the owner read three rows for one hundred
            // riyals he was sent once. So a second telling is allowed - only from
            // the same bank, only within seconds, and only when the two bodies are
            // not the same sentence with different numbers in it.
            if (a.body == b.body) gap <= messageRedeliveryWindow
            else {
                gap <= retellWindow && sameBank(a, b) &&
                    differentTemplate(a.body, b.body) && neitherReverses(a.body, b.body)
            }
        } else {
            // Across routes - the bank's push and its SMS for one purchase - the
            // bodies always differ, so the text cannot decide. Time alone is not
            // enough either: two anonymous debits of one size two minutes apart are
            // two payments, and merging them destroys one. Something has to agree
            // beyond the amount.
            gap <= messageRedeliveryWindow && corroborated(a, b)
        }
    }

    /** Same sender, positively known on both sides. A missing bank is not a match. */
    private fun sameBank(a: EventSignature, b: EventSignature): Boolean =
        a.bankId != null && a.bankId == b.bankId

    /**
     * True when two bodies are not the same sentence with different numbers in it.
     *
     * Every digit run collapses to one placeholder, so a template stamped with its
     * own time and amount reduces to the same skeleton every time it is sent. Two
     * separate transfers always arrive on ONE template, so they always share a
     * skeleton and this route can never merge them; one transfer told twice arrives
     * on two templates, and only that pair differs.
     */
    private fun differentTemplate(a: String, b: String): Boolean = skeleton(a) != skeleton(b)

    private fun skeleton(body: String): String = body.replace(DIGITS, "#")

    /**
     * Neither message undoes the other.
     *
     * A reversal is a differently-worded message from the same bank about the same
     * amount seconds later - which is exactly the shape of a second telling, and it
     * is the opposite of one: one is money arriving and the other is that money
     * going back. Found in the owner's own history, where a hundred riyals arrived,
     * was reversed eight seconds later, and was sent again a minute after that.
     * Without this the reversal would be swallowed by the transfer it undoes.
     */
    private fun neitherReverses(a: String, b: String): Boolean =
        !a.contains(REVERSAL) && !b.contains(REVERSAL)

    /**
     * Something beyond the amount agrees: the card, or the party.
     *
     * A pair anonymous on card AND on party is not evidence of one event, whatever
     * the clock says.
     */
    private fun corroborated(a: EventSignature, b: EventSignature): Boolean =
        (a.last4 != null && a.last4 == b.last4) ||
            (a.merchantKey != null && a.merchantKey == b.merchantKey)

    /**
     * Cards match, or at least one side never revealed one.
     *
     * Treating a missing card as a mismatch would leave every transfer - which
     * names no card at all - permanently un-mergeable across sources.
     */
    private fun cardsCompatible(a: String?, b: String?): Boolean =
        a == null || b == null || a == b

    private val Source.isStatement: Boolean get() = this == Source.STATEMENT

    private companion object {
        /** Arabic-Indic digits too: a stored body is folded, not transliterated. */
        val DIGITS = Regex("[0-9\u0660-\u0669]+")

        /** How every Saudi bank in this corpus words a reversal. */
        const val REVERSAL = "عكسي"
    }
}
