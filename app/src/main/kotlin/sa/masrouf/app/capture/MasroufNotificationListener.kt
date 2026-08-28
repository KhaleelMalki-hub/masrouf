package sa.masrouf.app.capture

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import sa.masrouf.app.MasroufApp
import sa.masrouf.core.capture.RawMessage
import sa.masrouf.core.model.Source
import java.time.Instant
import java.util.UUID

/**
 * Reads bank notifications off the status bar and hands them to the capture
 * pipeline.
 *
 * Kept deliberately thin: it converts an Android object into a [RawMessage],
 * asks [CaptureRecorder] what that means, and writes the answer. Every rule worth
 * protecting lives in the recorder, where it can be tested without a device.
 *
 * This service runs whenever the user has granted notification access, including
 * before the app has been opened, so it must not assume any screen has run first.
 */
class MasroufNotificationListener : NotificationListenerService() {

    /**
     * SupervisorJob stops one failure cancelling its siblings; it does NOT stop an
     * uncaught exception in a root launch reaching the thread's default handler and
     * killing the process. A storage error must degrade to one lost record, not to
     * a dead app that silently stops capturing with nothing on screen to say so.
     * The class name is logged, never the message body.
     */
    private val failures = CoroutineExceptionHandler { _, e ->
        Log.w(TAG, "capture failed: ${e.javaClass.simpleName}")
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + failures)
    private val recorder = CaptureRecorder()

    /**
     * Sweeps what is already on the shade when the listener binds.
     *
     * The service is unbound across boot, app update and process death, and for the
     * moment between the user granting access and Android connecting us. Anything
     * posted in those gaps is never delivered to [onNotificationPosted] and would
     * simply be lost. This is cheap only because the unique fingerprint index makes
     * a resweep idempotent - the design already paid for this and was not
     * collecting.
     *
     * Note the asymmetry it does not fix: a missed SMS is gone for good, because
     * the body is never persisted before parsing.
     */
    override fun onListenerConnected() {
        super.onListenerConnected()
        val existing = runCatching { activeNotifications }.getOrNull() ?: return
        existing.forEach(::capture)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) = capture(sbn)

    private fun capture(sbn: StatusBarNotification) {
        // Reading the Bundle stays on the callback thread - the system object should
        // not be touched after this method returns. Everything after it moves off:
        // the gate runs before the sender check by design, so parsing cost is paid
        // for every notification any app on the device posts, and that does not
        // belong on the main thread.
        val message = sbn.toRawMessage() ?: return
        val postingPackage = sbn.packageName

        scope.launch {
            when (val decision =
                recorder.decide(message, UUID.randomUUID().toString(), Source.NOTIFICATION)) {
                is CaptureRecorder.Decision.Store -> {
                    val repository = (application as MasroufApp).transactions
                    repository.recordCaptured(decision.transaction, decision.accountLast4)
                }

                is CaptureRecorder.Decision.Skip -> {
                    // The reason is logged, never the body. A rejected message is
                    // usually an OTP, and an OTP body in logcat is a credential in a
                    // buffer any other app on an older device could read.
                    Log.d(TAG, "skipped $postingPackage: ${decision.reason}")
                }
            }
        }
    }

    /**
     * @return null when the notification carries no text at all - a media control,
     *   a progress bar, a group summary. There is nothing to parse and no reason to
     *   wake the pipeline for it.
     */
    private fun StatusBarNotification.toRawMessage(): RawMessage? {
        val extras = notification?.extras ?: return null
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val body = listOfNotNull(
            extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
            // The collapsed text is often truncated with an ellipsis while the big
            // text carries the whole message, and the amount is frequently in the
            // part that got cut.
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString(),
        ).distinct().joinToString("\n")

        if (title.isNullOrBlank() && body.isBlank()) return null

        return RawMessage(
            body = body,
            // The device's own receipt time, never a date read out of the message.
            // Statement and notification text is visually ordered, so a date's
            // character order there is not its logical order.
            receivedAt = Instant.ofEpochMilli(postTime),
            packageName = packageName,
            title = title,
        )
    }

    companion object {
        private const val TAG = "MasroufCapture"

        /**
         * Whether the user has granted notification access to this app.
         *
         * Read from the setting rather than remembered in our own storage: the user
         * can revoke it from system settings at any time, and a cached "yes" would
         * leave the app claiming to be capturing while it silently is not.
         */
        fun isEnabled(context: Context): Boolean {
            val expected = ComponentName(context, MasroufNotificationListener::class.java)
            val granted = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners",
            ).orEmpty()

            return granted.split(':')
                .mapNotNull(ComponentName::unflattenFromString)
                .any { it == expected }
        }
    }
}
