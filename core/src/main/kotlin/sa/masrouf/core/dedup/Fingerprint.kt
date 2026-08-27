package sa.masrouf.core.dedup

import sa.masrouf.core.model.Direction
import sa.masrouf.core.model.Source
import sa.masrouf.core.money.Money
import sa.masrouf.core.text.ArabicText
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate

/**
 * Stable identity for a single captured record.
 *
 * This answers only one question: "have I already stored *this exact record*?" It
 * is not a judgement about whether two records describe the same purchase - that
 * is [DuplicateDetector]'s job, and it is a much harder question.
 *
 * ## Why the timestamp is included at full precision
 *
 * A day-granularity fingerprint is the obvious design and it is wrong. From real
 * captured data, two separate wallet top-ups on the same morning:
 *
 *     08:02   شراء انترنت بـSAR 5000 ... من barq ... مدى *1887
 *     08:51   شراء انترنت بـSAR 5000 ... من barq ... مدى *1887
 *
 * Same amount, same day, same card, same merchant, 49 minutes apart, and both
 * real. Collapsing them on a day key silently swallows 5,000 SAR - and the loss is
 * invisible, because the surviving record looks perfectly correct.
 */
object Fingerprint {

    private const val VERSION = "v1"

    /**
     * Identity for a record captured from a message (SMS or notification).
     *
     * Keyed on the exact receipt instant, which the device assigns and which
     * differs between two genuinely separate transactions however alike they look.
     */
    fun forMessage(
        source: Source,
        occurredAt: Instant,
        amount: Money,
        direction: Direction,
        last4: String?,
        merchantRaw: String?,
    ): String = hash(
        VERSION,
        source.name,
        occurredAt.epochSecond.toString(),
        amount.halalas.toString(),
        direction.name,
        last4.orEmpty(),
        merchantKey(merchantRaw),
    )

    /**
     * Identity for a row of an imported statement.
     *
     * Statement rows carry a date but no time, so a row is identified by where it
     * sits in the file it came from. That makes re-importing the *same* file a
     * no-op, while keeping two identical rows inside one file distinct - both of
     * which a purely content-based key gets wrong.
     *
     * Overlapping files (an August statement, then a July-August one) do produce
     * two different fingerprints for one transaction. That is deliberate: catching
     * those is [DuplicateDetector]'s job, where the decision can weigh dates and
     * counts instead of pretending a hash can settle it.
     *
     * @param statementId stable identifier for the imported file - its content hash,
     *   not its filename, so the same file re-downloaded still matches.
     */
    fun forStatementRow(
        statementId: String,
        rowIndex: Int,
        date: LocalDate,
        amount: Money,
        direction: Direction,
        last4: String?,
        merchantRaw: String?,
    ): String = hash(
        VERSION,
        Source.STATEMENT.name,
        statementId,
        rowIndex.toString(),
        date.toString(),
        amount.halalas.toString(),
        direction.name,
        last4.orEmpty(),
        merchantKey(merchantRaw),
    )

    /** Identity for a record the user typed. Always unique; nothing else can collide with it. */
    fun forManual(id: String): String = hash(VERSION, Source.MANUAL.name, id)

    private fun merchantKey(raw: String?): String =
        raw?.let(ArabicText::normalizeMerchant).orEmpty()

    /**
     * UNIT SEPARATOR, written as a code point.
     *
     * A literal control character in source is invisible in an editor and in a
     * diff - the same hazard the text normaliser is written to avoid - and this
     * file is the last place to hide one, because a separator that silently
     * changes changes every fingerprint the app has ever stored.
     */
    private val FIELD_SEPARATOR: String = Char(0x1F).toString()

    /**
     * Joins the fields and hashes them.
     *
     * The separator has to be a character that cannot appear inside a field. A
     * space cannot: merchant names are full of them, so with a space separator
     * `("TAMIMI", "MARKETS")` and `("TAMIMI MARKETS", "")` build the same
     * canonical string and collide. Commas, semicolons and hyphens all occur in
     * real merchant text too. U+001F occurs in no bank message.
     */
    private fun hash(vararg parts: String): String {
        val canonical = parts.joinToString(FIELD_SEPARATOR)
        val digest = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { byte -> "%02x".format(byte) }
    }
}
