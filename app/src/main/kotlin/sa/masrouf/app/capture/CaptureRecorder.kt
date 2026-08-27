package sa.masrouf.app.capture

import sa.masrouf.core.capture.CapturePipeline
import sa.masrouf.core.capture.MessageGate
import sa.masrouf.core.capture.RawMessage
import sa.masrouf.core.dedup.Fingerprint
import sa.masrouf.core.model.Source
import sa.masrouf.core.model.Status
import sa.masrouf.core.model.Transaction
import sa.masrouf.core.text.ArabicText

/**
 * Decides what a captured message becomes, without touching Android or storage.
 *
 * The service that receives notifications is deliberately thin and this is where
 * the judgement lives, because every rule worth protecting here - the gate running
 * first, an OTP body never reaching disk, nothing auto-confirming - is a rule that
 * can only be tested if it is not tangled up in a system callback.
 */
class CaptureRecorder(private val pipeline: CapturePipeline = CapturePipeline()) {

    sealed interface Decision {

        /** Understood. Always [Status.PENDING]; the user confirms it themselves. */
        data class Store(val transaction: Transaction, val parserId: String) : Decision

        /**
         * Deliberately not stored.
         *
         * @param bodyWasSensitive true when the message carried a credential. The
         *   body is absent from this decision entirely, not merely unused, so there
         *   is no field a later caller could log by accident.
         */
        data class Skip(val reason: String, val bodyWasSensitive: Boolean = false) : Decision
    }

    /**
     * @param id identity for the new record. Passed in rather than generated here so
     *   that the same message decided twice produces the same answer, which is what
     *   makes this testable at all.
     */
    fun decide(message: RawMessage, id: String): Decision =
        when (val outcome = pipeline.process(message)) {
            is CapturePipeline.Outcome.Rejected -> Decision.Skip(
                reason = outcome.reason.name,
                bodyWasSensitive = outcome.bodyIsSensitive,
            )

            CapturePipeline.Outcome.UnknownSender ->
                Decision.Skip("UNKNOWN_SENDER")

            // Worth surfacing rather than swallowing: a known bank whose message no
            // longer parses usually means the template changed, and transactions are
            // being missed for as long as nobody notices.
            is CapturePipeline.Outcome.NotUnderstood ->
                Decision.Skip("NOT_UNDERSTOOD:${outcome.parserId}:${outcome.reason}")

            is CapturePipeline.Outcome.Captured -> {
                val draft = outcome.draft
                Decision.Store(
                    transaction = Transaction(
                        id = id,
                        amount = draft.amount,
                        direction = draft.direction,
                        type = draft.type,
                        occurredAt = draft.occurredAt,
                        accountId = null,
                        categoryId = null,
                        merchantRaw = draft.merchantRaw,
                        merchantKey = draft.merchantRaw
                            ?.let(ArabicText::normalizeMerchant)
                            ?.takeIf { it.isNotBlank() },
                        note = null,
                        source = Source.NOTIFICATION,
                        // PENDING regardless of the parser's confidence. Lowering
                        // CONFIRMATION_THRESHOLD is a decision taken per parser after
                        // it has been measured against real messages, not one this
                        // class is allowed to make on the strength of a float.
                        status = Status.PENDING,
                        fingerprint = Fingerprint.forMessage(
                            source = Source.NOTIFICATION,
                            occurredAt = draft.occurredAt,
                            amount = draft.amount,
                            direction = draft.direction,
                            last4 = draft.accountLast4,
                            merchantRaw = draft.merchantRaw,
                        ),
                        rawText = draft.rawText ?: message.fullText,
                    ),
                    parserId = outcome.parserId,
                )
            }
        }

    companion object {
        /**
         * True when this message must not have its body written anywhere, including
         * a diagnostic log.
         *
         * Exposed so the service can check it before logging, without having to
         * reproduce the gate's reasoning - reproducing it is how the two drift apart
         * and how a credential eventually reaches a log file.
         */
        fun mustNotPersistBody(message: RawMessage): Boolean =
            MessageGate.mustNotPersistBody(message)
    }
}
