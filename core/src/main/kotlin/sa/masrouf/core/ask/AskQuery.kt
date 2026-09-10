package sa.masrouf.core.ask

import sa.masrouf.core.model.Category
import java.time.LocalDate

/**
 * A question, once it has been understood.
 *
 * The whole point of this type is that it is not a guess. A question either
 * resolves into one of these - a measure, a subject and a period, all of them
 * decided - or [AskParser] returns null and the screen says it did not understand.
 * There is no middle state where the app answers approximately, because the answer
 * is a figure about someone's money and a plausible wrong figure is worse than no
 * figure at all. That is the same rule the parsers live by.
 */
data class AskQuery(
    val measure: Measure,
    val subject: Subject,
    val period: Period,
    val flow: Flow,
)

/** What to do with the rows the question selects. */
enum class Measure {
    /** Add them up. The default, because "how much" is what people ask. */
    TOTAL,

    /** How many there were - "كم مرة". */
    COUNT,

    /** The single biggest one. */
    LARGEST,

    /** Just show them. */
    LIST,
}

/** Money leaving or money arriving. Spending unless the question says otherwise. */
enum class Flow { SPENDING, INCOME }

/** Which rows the question is about. */
sealed interface Subject {

    /** Everything that counts, with no narrowing. */
    data object Everything : Subject

    /** One of the app's categories. */
    data class OfCategory(val category: Category) : Subject

    /**
     * A named group of merchants that does NOT correspond to a category.
     *
     * "How much on petrol" is the case that forced this. Petrol is not a category:
     * `transport` holds car servicing, tyres, spare parts, parking and a vehicle
     * tracker as well as fuel, and answering a petrol question with the transport
     * total over-reports it about three times over on this history. A topic is the
     * merchants themselves. See [Topic].
     */
    data class OfTopic(val topic: Topic) : Subject

    /** One merchant the user named, matched the way every other merchant is. */
    data class AtMerchant(val keyword: String) : Subject

    /**
     * The rows that have no category yet.
     *
     * Not a category and not a topic: an ABSENCE, which is why it needs its own
     * member. It is here because filing is what this owner actually does with the
     * app - about a thousand rows remain - and until now the only way to find that
     * work was the unfiled banner on the month card, which counts one month. The
     * rows are spread over a hundred and forty-six months, so finding them meant
     * guessing which month held them, and nothing marked a month as unfinished.
     *
     * Asking for them turns this screen into the worklist: the answer already
     * lists the rows, and every row already opens the same filing sheet with the
     * same whole-merchant scope. No new screen, no new interaction.
     */
    data object Unfiled : Subject
}

/**
 * An absolute span, and the words to describe it back to the reader.
 *
 * Resolved at parse time against a supplied "today" rather than at query time: a
 * question asked at 23:59 must not answer about a different month than the one its
 * label names, and a test must be able to ask about "last month" without waiting.
 *
 * [toExclusive] is exclusive, so a single day is `[d, d+1)` and no row can fall
 * between two adjacent periods or into both.
 */
data class Period(
    val from: LocalDate?,
    val toExclusive: LocalDate?,
    val label: PeriodLabel,
) {
    companion object {
        /** No period was named, so the answer covers the whole history. */
        val Always = Period(null, null, PeriodLabel.ALL_TIME)

        fun of(from: LocalDate, toExclusive: LocalDate, label: PeriodLabel) =
            Period(from, toExclusive, label)
    }
}

/**
 * How the period should be described, so the screen can say what it answered about
 * in the reader's own language rather than printing two dates.
 *
 * A question whose period is not stated back is a question the reader cannot check.
 */
enum class PeriodLabel {
    ALL_TIME,
    TODAY,
    YESTERDAY,
    THIS_WEEK,
    THIS_MONTH,
    LAST_MONTH,
    THIS_YEAR,
    LAST_YEAR,
    NAMED_MONTH,
    NAMED_YEAR,
    LAST_N_DAYS,
}
