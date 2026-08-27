package sa.masrouf.app.capture

import sa.masrouf.core.capture.RawMessage
import java.time.Instant

/**
 * One part of a received SMS, lifted out of Android's `SmsMessage` so the
 * assembly below can be tested without a device.
 */
data class SmsPart(val originatingAddress: String?, val body: String)

/**
 * Rebuilds the message the bank actually sent from the parts the radio delivered.
 *
 * A Saudi bank purchase message is comfortably longer than a single 70-character
 * Arabic SMS, so it arrives as several concatenated parts. Handing them to the
 * pipeline one at a time is the failure worth naming: each part parses on its own,
 * the part carrying the amount may not be the part carrying the merchant, and the
 * result is either a refusal or - worse - a draft assembled from half a message.
 */
object SmsAssembly {

    /**
     * @param receivedAt the device's own clock at delivery, never the service
     *   centre's timestamp. The two sources of capture have to agree about time,
     *   because deduplication decides that a notification and an SMS describe one
     *   purchase by how far apart they arrived - and a network-supplied clock that
     *   runs a few minutes off would push two halves of one payment outside that
     *   window and record it twice.
     *
     * @return null when the parts carry no text at all.
     */
    fun assemble(parts: List<SmsPart>, receivedAt: Instant): RawMessage? {
        if (parts.isEmpty()) return null

        val body = parts.joinToString("") { it.body }
        if (body.isBlank()) return null

        return RawMessage(
            body = body,
            receivedAt = receivedAt,
            // The sender id, e.g. "AlRajhiBank". Parsers match on it the same way
            // they match a notification's package name.
            sender = parts.firstNotNullOfOrNull { it.originatingAddress?.takeIf(String::isNotBlank) },
        )
    }

    /**
     * Splits a delivery into one message per sender.
     *
     * Two banks can have messages delivered in the same broadcast. Joining those
     * parts blindly would splice one bank's amount onto another's merchant, and the
     * result would parse - which is exactly what makes it dangerous.
     */
    fun assembleBySender(parts: List<SmsPart>, receivedAt: Instant): List<RawMessage> =
        parts.groupBy { it.originatingAddress }
            .values
            .mapNotNull { assemble(it, receivedAt) }
}
