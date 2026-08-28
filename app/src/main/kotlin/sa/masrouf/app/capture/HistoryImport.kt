package sa.masrouf.app.capture

import sa.masrouf.app.data.TransactionRepository
import sa.masrouf.core.capture.RawMessage
import sa.masrouf.core.model.Source
import java.util.UUID

/**
 * Runs a batch of already-received messages through the live capture pipeline.
 *
 * Deliberately the same [CaptureRecorder] the listener and the receiver use, with
 * no import-only shortcuts. A backfill that parsed messages its own way would be a
 * second implementation of the rules that matter most - the OTP gate, refusing to
 * guess, nothing auto-confirming - and the two would drift.
 *
 * Everything it stores lands PENDING, like any capture. A history import that
 * silently added hundreds of confirmed transactions would put numbers the user has
 * never seen straight into their totals.
 */
class HistoryImport(
    private val repository: TransactionRepository,
    private val recorder: CaptureRecorder = CaptureRecorder(),
) {

    data class Report(
        val examined: Int,
        val stored: Int,
        /** Recognised sender, unreadable message. The count worth watching. */
        val notUnderstood: Int,
        /** Already known - the same message seen live, or an earlier import. */
        val alreadyKnown: Int,
        val refused: Int,
        val notBank: Int,
    )

    /**
     * @param onProgress called with (examined, stored) so a long import can show
     *   movement rather than appearing hung.
     */
    suspend fun run(
        messages: List<RawMessage>,
        source: Source = Source.SMS,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
    ): Report {
        var stored = 0
        var notUnderstood = 0
        var alreadyKnown = 0
        var refused = 0
        var notBank = 0

        messages.forEachIndexed { index, message ->
            when (val decision = recorder.decide(message, UUID.randomUUID().toString(), source)) {
                is CaptureRecorder.Decision.Store -> {
                    // Deduplication is the repository's, so a message already
                    // captured live does not become a second record.
                    val written = repository.recordCaptured(
                        decision.transaction,
                        decision.accountLast4,
                    )
                    if (written) stored++ else alreadyKnown++
                }

                is CaptureRecorder.Decision.Skip -> when {
                    decision.reason == "UNKNOWN_SENDER" -> notBank++
                    decision.reason.startsWith("NOT_UNDERSTOOD") -> notUnderstood++
                    else -> refused++
                }
            }
            onProgress(index + 1, stored)
        }

        return Report(
            examined = messages.size,
            stored = stored,
            notUnderstood = notUnderstood,
            alreadyKnown = alreadyKnown,
            refused = refused,
            notBank = notBank,
        )
    }
}
