package sa.masrouf.core.capture

import sa.masrouf.core.model.TransactionDraft
import java.time.Instant

/**
 * A message captured on the device, before anyone has tried to understand it.
 *
 * The same purchase can arrive as an SMS and as a push notification from the
 * bank's own app, so both are modelled here and deduplicated later rather than
 * being kept in separate pipelines.
 */
data class RawMessage(
    val body: String,
    val receivedAt: Instant,
    /** SMS sender id, e.g. the bank's alphanumeric sender. Null for notifications. */
    val sender: String? = null,
    /** Android package that posted the notification. Null for SMS. */
    val packageName: String? = null,
    /** Notification title, when there is one. */
    val title: String? = null,
) {
    /** Everything a parser should read, already joined and normalised for matching. */
    val fullText: String get() = listOfNotNull(title, body).joinToString("\n")
}

sealed interface ParseResult {

    /** The parser recognised the message and understood it. */
    data class Parsed(
        val draft: TransactionDraft,
        val parserId: String,
        /** 0.0-1.0. Below [ParserRegistry.CONFIRMATION_THRESHOLD] the user must review it. */
        val confidence: Float,
    ) : ParseResult {
        init {
            require(confidence in 0f..1f) { "confidence out of range: $confidence" }
        }
    }

    /** Not this parser's message. Not an error - every parser says this about most messages. */
    data object NotApplicable : ParseResult

    /**
     * The parser recognised the message but could not extract it.
     *
     * Distinct from [NotApplicable] on purpose: this is the case worth surfacing and
     * counting, because it means a known bank changed its message format.
     *
     * Deliberately carries no [RawMessage]. It used to, and no consumer ever read
     * it - `CapturePipeline` unwraps this into a reason string and drops the rest.
     * But `RawMessage` is a data class, so its generated `toString` prints the body
     * verbatim, and the obvious next debugging step - logging the failed result to
     * find out why a bank message stopped parsing - would have put a message body
     * into logcat without going anywhere near the gate that guards bodies.
     */
    data class Failed(val parserId: String, val reason: String) : ParseResult
}

/**
 * Understands the messages of one institution.
 *
 * Implementations are written against real captured samples, never against a
 * guess at the format. An invented regex that happens to compile is worse than
 * no parser: it will match something, eventually, and be wrong.
 */
interface MessageParser {
    /** Stable identifier, recorded on every record this parser produces. */
    val id: String

    /** Cheap check used to skip the parser entirely. Must not throw. */
    fun canParse(message: RawMessage): Boolean

    fun parse(message: RawMessage): ParseResult
}

/**
 * Routes a message to the first parser that claims it.
 *
 * Order matters: parsers are tried in registration order, so specific parsers
 * must be registered before general ones.
 */
class ParserRegistry(private val parsers: List<MessageParser>) {

    fun parse(message: RawMessage): ParseResult {
        var lastFailure: ParseResult.Failed? = null
        for (parser in parsers) {
            val claims = try {
                parser.canParse(message)
            } catch (e: RuntimeException) {
                // A broken parser must not take down capture for every other bank.
                lastFailure = ParseResult.Failed(parser.id, "canParse threw: ${e.message}")
                false
            }
            if (!claims) continue

            val result = try {
                parser.parse(message)
            } catch (e: RuntimeException) {
                ParseResult.Failed(parser.id, "parse threw: ${e.message}")
            }
            when (result) {
                is ParseResult.Parsed -> return result
                is ParseResult.Failed -> lastFailure = result
                ParseResult.NotApplicable -> Unit
            }
        }
        return lastFailure ?: ParseResult.NotApplicable
    }

    companion object {
        /**
         * Parsers this confident may skip manual review, once the user opts in.
         *
         * Set to 1.0 for now, so nothing is auto-confirmed. It is lowered only per
         * parser, after that parser has been measured against real captured
         * messages - not on the strength of it looking correct.
         */
        const val CONFIRMATION_THRESHOLD = 1.0f
    }
}
