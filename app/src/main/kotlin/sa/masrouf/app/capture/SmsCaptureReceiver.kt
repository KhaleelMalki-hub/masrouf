package sa.masrouf.app.capture

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import sa.masrouf.app.MasroufApp
import sa.masrouf.core.model.Source
import java.time.Instant
import java.util.UUID

/**
 * Receives bank SMS and hands it to the same pipeline the notification listener
 * uses.
 *
 * SMS is not a redundant second path: two of the accounts this app is built for
 * have no Android app installed at all, so their transactions exist only as text
 * messages. It is also why deduplication had to be wired up before this class
 * shipped - a bank with both an app and SMS reports one purchase twice.
 */
class SmsCaptureReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val parts = runCatching {
            Telephony.Sms.Intents.getMessagesFromIntent(intent).orEmpty().map { part ->
                SmsPart(
                    originatingAddress = part.originatingAddress,
                    // displayMessageBody is the decoded body; messageBody can be
                    // null on some encodings.
                    body = part.displayMessageBody ?: part.messageBody.orEmpty(),
                )
            }
        }.getOrElse {
            // A malformed PDU must not crash the receiver: it would take down
            // capture for every message that follows, with nothing on screen to say
            // so. The exception message is logged, never the payload.
            Log.w(TAG, "unreadable SMS delivery: ${it.javaClass.simpleName}")
            return
        }

        val receivedAt = Instant.ofEpochMilli(System.currentTimeMillis())
        val messages = SmsAssembly.assembleBySender(parts, receivedAt)
        if (messages.isEmpty()) return

        val recorder = CaptureRecorder()
        val repository = (context.applicationContext as MasroufApp).transactions

        // goAsync keeps the broadcast alive across the database write, which makes
        // losing a captured transaction unlikely - not impossible. It raises
        // priority; it does not prevent a low-memory kill, and the SMS body is never
        // persisted before parsing, so there is nothing to replay from.
        val pending = goAsync()
        val failures = CoroutineExceptionHandler { _, e ->
            Log.w(TAG, "sms capture failed: ${e.javaClass.simpleName}")
        }
        CoroutineScope(Dispatchers.IO + failures).launch {
            try {
                messages.forEach { message ->
                    // Per message, not per batch. One delivery can carry two banks
                    // (see SmsAssembly.assembleBySender), and a failure on the first
                    // must not silently discard the second - a fail-fast loop reports
                    // the item that failed and never the items it never reached.
                    runCatching {
                        when (val decision =
                            recorder.decide(message, UUID.randomUUID().toString(), Source.SMS)) {
                            is CaptureRecorder.Decision.Store ->
                                repository.recordCaptured(decision.transaction, decision.accountLast4)

                            is CaptureRecorder.Decision.Skip ->
                                // Reason only. A refused SMS is usually an OTP, and
                                // its body is a credential that must not reach a log.
                                Log.d(TAG, "skipped sms from ${message.sender}: ${decision.reason}")
                        }
                    }.onFailure { e ->
                        Log.w(TAG, "sms from ${message.sender} not stored: ${e.javaClass.simpleName}")
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "MasroufCapture"
    }
}
