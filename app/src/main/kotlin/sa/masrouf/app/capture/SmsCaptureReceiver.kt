package sa.masrouf.app.capture

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import sa.masrouf.app.MasroufApp
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

        // goAsync keeps the broadcast alive across the database write. Without it
        // the process can be killed the moment onReceive returns, and a captured
        // transaction is lost with no error anywhere.
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                messages.forEach { message ->
                    when (val decision = recorder.decide(message, UUID.randomUUID().toString())) {
                        is CaptureRecorder.Decision.Store ->
                            repository.recordCaptured(decision.transaction, decision.accountLast4)

                        is CaptureRecorder.Decision.Skip ->
                            // Reason only. A refused SMS is usually an OTP, and its
                            // body is a credential that must not reach a log buffer.
                            Log.d(TAG, "skipped sms from ${message.sender}: ${decision.reason}")
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
