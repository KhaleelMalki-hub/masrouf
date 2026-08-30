package sa.masrouf.app.capture

import android.content.ContentResolver
import android.provider.Telephony
import sa.masrouf.core.capture.RawMessage
import java.time.Instant

/**
 * Reads messages already sitting in the device's SMS inbox.
 *
 * Capture only ever saw messages that arrived after the app was installed, which
 * left the user's actual history - years of it, for the two banks that have no app
 * - permanently invisible. This is the one-time backfill.
 *
 * It reads every message, because the inbox does not know which are from banks;
 * deciding that is `CapturePipeline`'s job and it is the same code that decides it
 * for live messages. Nothing is stored unless a bank parser recognises it, so the
 * personal messages this necessarily reads are examined and dropped in memory.
 * OTP bodies are refused by the gate exactly as they are live.
 */
class SmsInbox(private val resolver: ContentResolver) {

    /**
     * @param newestFirst matches how the user thinks about their history, and means
     *   an interrupted import has covered the recent months rather than the oldest.
     */
    fun read(
        limit: Int = DEFAULT_LIMIT,
        newestFirst: Boolean = true,
        /** Only messages received after this instant. Null reads the whole inbox. */
        since: Instant? = null,
    ): List<RawMessage> {
        val order = if (newestFirst) "${Telephony.Sms.DATE} DESC" else "${Telephony.Sms.DATE} ASC"
        val columns = arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE)
        val selection = since?.let { "${Telephony.Sms.DATE} > ?" }
        val args = since?.let { arrayOf(it.toEpochMilli().toString()) }

        return resolver.query(Telephony.Sms.Inbox.CONTENT_URI, columns, selection, args, order)
            ?.use { cursor ->
                val address = cursor.getColumnIndex(Telephony.Sms.ADDRESS)
                val body = cursor.getColumnIndex(Telephony.Sms.BODY)
                val date = cursor.getColumnIndex(Telephony.Sms.DATE)
                if (address < 0 || body < 0 || date < 0) return emptyList()

                buildList {
                    while (cursor.moveToNext() && size < limit) {
                        val text = cursor.getString(body) ?: continue
                        if (text.isBlank()) continue
                        add(
                            RawMessage(
                                body = text,
                                // The inbox's own timestamp. Unlike a date parsed out
                                // of the body, this is when the device received it,
                                // which is what every other capture path records.
                                receivedAt = Instant.ofEpochMilli(cursor.getLong(date)),
                                sender = cursor.getString(address),
                            )
                        )
                    }
                }
            }
            .orEmpty()
    }

    private companion object {
        /**
         * High enough not to be a cap in practice.
         *
         * It was 5,000, which sounded generous and was not: a real inbox held
         * 24,000 messages, so the import silently stopped partway and the months
         * before it simply never appeared. The user was told "665 added from 5000
         * messages" and had no way to know 19,000 were never looked at.
         *
         * The bound still exists so a pathological inbox cannot exhaust memory,
         * but it is now far above any real one. Reading is cheap; it is the
         * per-record deduplication that costs, and that is bounded by how many
         * messages are actually from banks.
         */
        const val DEFAULT_LIMIT = 100_000
    }
}
