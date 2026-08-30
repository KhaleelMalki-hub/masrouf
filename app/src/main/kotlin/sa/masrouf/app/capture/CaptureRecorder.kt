package sa.masrouf.app.capture

import sa.masrouf.core.capture.BalanceReader
import sa.masrouf.core.capture.CapturePipeline
import sa.masrouf.core.capture.RawMessage
import sa.masrouf.core.dedup.Fingerprint
import sa.masrouf.core.model.CategoryGuess
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

        /**
         * Understood. Always [Status.PENDING]; the user confirms it themselves.
         *
         * @param accountLast4 carried alongside rather than inside [transaction],
         *   which has no field for it, because deduplication needs it: a missing
         *   card fragment is treated as compatible with every other, so losing it
         *   makes matching credulous rather than cautious.
         */
        data class Store(
            val transaction: Transaction,
            val parserId: String,
            val accountLast4: String?,
            val balance: BalanceReader.Reading? = null,
        ) : Decision

        /**
         * Deliberately not stored.
         *
         * @param bodyWasSensitive true when the message carried a credential.
         *
         *   Note what actually enforces the invariant: this type has no body field
         *   at all, so logging one through a Skip is structurally impossible rather
         *   than merely discouraged. A `mustNotPersistBody` helper used to sit in a
         *   companion here, documented as the guard - nothing ever called it, and a
         *   comment naming a guard that is not the guard is worse than no comment.
         */
        data class Skip(val reason: String, val bodyWasSensitive: Boolean = false) : Decision
    }

    /**
     * @param id identity for the new record. Passed in rather than generated here so
     *   that the same message decided twice produces the same answer, which is what
     *   makes this testable at all.
     * @param source which transport delivered this. A parameter, not something
     *   inferred from the message: this class is shared by the notification listener
     *   and the SMS receiver, and a shared decision layer must not know either
     *   caller's name. Deriving it from `packageName != null` would be a shorter
     *   diff that silently guesses when both fields are absent - the same class of
     *   defect as hardcoding it, which is what this parameter replaces.
     */
    fun decide(message: RawMessage, id: String, source: Source): Decision =
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
                        // A suggestion, not a decision. It lands on a PENDING
                        // record the user is already being asked to look at, the
                        // chips on the slip show what was guessed, and an
                        // unrecognised merchant leaves it null rather than
                        // defaulting to "other" - so the strip keeps showing what
                        // is genuinely unexamined.
                        categoryId = CategoryGuess.suggest(draft.merchantRaw, draft.type)?.id,
                        merchantRaw = draft.merchantRaw,
                        merchantKey = draft.merchantRaw
                            ?.let(ArabicText::normalizeMerchant)
                            ?.takeIf { it.isNotBlank() },
                        note = null,
                        source = source,
                        // PENDING regardless of the parser's confidence. Lowering
                        // CONFIRMATION_THRESHOLD is a decision taken per parser after
                        // it has been measured against real messages, not one this
                        // class is allowed to make on the strength of a float.
                        status = Status.PENDING,
                        fingerprint = Fingerprint.forMessage(
                            source = source,
                            occurredAt = draft.occurredAt,
                            amount = draft.amount,
                            direction = draft.direction,
                            last4 = draft.accountLast4,
                            merchantRaw = draft.merchantRaw,
                        ),
                        rawText = draft.rawText ?: message.fullText,
                        accountLast4 = draft.accountLast4,
                        // The parser is the bank: there is one per bank, chosen by
                        // the sender address. Recorded here, where that is known,
                        // rather than read back off the body later - only about
                        // 1,000 of 22,000 real messages name their own bank.
                        bankId = outcome.parserId,
                    ),
                    parserId = outcome.parserId,
                    accountLast4 = draft.accountLast4,
                    // Read here, where the body is in hand, and carried beside the
                    // transaction like the card fragment is: the model has no field
                    // for it and the screen that needs it reads storage directly.
                    balance = BalanceReader.read(draft.rawText ?: message.fullText),
                )
            }
        }
}
