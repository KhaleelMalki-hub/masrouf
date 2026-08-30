package sa.masrouf.core.dedup

import sa.masrouf.core.model.Direction
import sa.masrouf.core.model.Source
import sa.masrouf.core.money.Money
import sa.masrouf.core.text.ArabicText
import sa.masrouf.core.time.RiyadhTime
import java.time.Instant
import java.time.LocalDate

/**
 * The facts about a transaction that decide whether two records describe the same
 * real-world event.
 *
 * A deliberately narrow view. The detector is given signatures rather than whole
 * transactions so it cannot accidentally start matching on a field that is not
 * evidence of identity - a category the user picked, or a note they typed, tells
 * you nothing about whether the money moved once or twice.
 */
data class EventSignature(
    val amount: Money,
    val direction: Direction,
    /** Last four digits of the card or account, when the source revealed them. */
    val last4: String?,
    val occurredAt: Instant,
    /** Folded merchant or counterparty. Used to break ties, never to decide a match alone. */
    val merchantKey: String?,
    val source: Source,
    /**
     * The message body, normalised, when the record came from one.
     *
     * Two messages from the SAME source are one event only if their bodies are
     * one text. A bank that redelivers an SMS redelivers it byte for byte; a bank
     * announcing two purchases a minute apart writes two bodies, because it
     * stamps each with its own time. Without this the second of two 250-riyal
     * deposits at Tamra Capital, 69 seconds after the first, was dropped as a
     * redelivery of it. It was not; the user had made two.
     */
    val body: String? = null,
) {
    val day: LocalDate get() = RiyadhTime.localDate(occurredAt)

    companion object {
        fun of(
            amount: Money,
            direction: Direction,
            occurredAt: Instant,
            source: Source,
            last4: String? = null,
            merchantRaw: String? = null,
            body: String? = null,
        ): EventSignature = EventSignature(
            amount = amount,
            direction = direction,
            last4 = last4,
            occurredAt = occurredAt,
            merchantKey = merchantRaw?.let(ArabicText::normalizeMerchant)?.takeIf { it.isNotBlank() },
            source = source,
            body = body?.let(ArabicText::normalize)?.takeIf { it.isNotBlank() },
        )
    }
}
