package sa.masrouf.core.capture

import sa.masrouf.core.model.TransactionDraft

/**
 * The single entry point for a message arriving from the device.
 *
 * The ordering it enforces is the whole point: [MessageGate] runs *before* any
 * parser, always. A one-time-password message parses perfectly - same amount, same
 * merchant, same card as the purchase it authorises - so a pipeline that gates
 * afterwards, or that leaves gating to each parser to remember, will eventually let
 * one through and silently double a month of online spending.
 *
 * Nothing else in the app should call a [MessageParser] directly.
 */
class CapturePipeline(private val registry: ParserRegistry = SaudiBanks.registry()) {

    sealed interface Outcome {

        /** Understood. Always lands as a pending transaction for the user to confirm. */
        data class Captured(
            val draft: TransactionDraft,
            val parserId: String,
            val confidence: Float,
        ) : Outcome

        /**
         * Deliberately not a transaction.
         *
         * @param bodyIsSensitive true when the body contains a credential and must
         *   not be written to storage or logs, even for diagnostics.
         */
        data class Rejected(
            val reason: MessageGate.Rejection,
            val matched: String,
            val bodyIsSensitive: Boolean,
        ) : Outcome

        /** No parser claimed the sender. Expected for every non-bank message on the device. */
        data object UnknownSender : Outcome

        /**
         * A known sender sent something this parser could not read.
         *
         * The signal worth watching: it usually means a bank changed a template, and
         * transactions are being missed until the profile is updated.
         */
        data class NotUnderstood(val parserId: String, val reason: String) : Outcome
    }

    fun process(message: RawMessage): Outcome {
        when (val decision = MessageGate.evaluate(message)) {
            is MessageGate.Decision.Reject -> return Outcome.Rejected(
                reason = decision.reason,
                matched = decision.matched,
                bodyIsSensitive = decision.reason == MessageGate.Rejection.ONE_TIME_PASSWORD,
            )

            MessageGate.Decision.Allow -> Unit
        }

        return when (val result = registry.parse(message)) {
            is ParseResult.Parsed -> Outcome.Captured(result.draft, result.parserId, result.confidence)
            is ParseResult.Failed -> Outcome.NotUnderstood(result.parserId, result.reason)
            ParseResult.NotApplicable -> Outcome.UnknownSender
        }
    }
}
