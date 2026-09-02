package sa.masrouf.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/**
 * How a bank is shown on a transaction row.
 *
 * Colours are chosen to be told apart at the size of a chip, not to reproduce a
 * brand exactly: eleven marks a few millimetres tall on both a light and a dark
 * background, which is a different problem from a logo on a billboard. They are
 * close to each bank's own colour where that survives the constraint.
 *
 * No bank logos. They are trademarks, this is a private app that will never be
 * published, and a wordmark scaled to 16dp is a smear - the short name reads
 * better and is what a person calls the bank anyway.
 */
data class BankMark(val labelAr: String, val labelEn: String, val colour: Color)

private val LightMarks: Map<String, Color> = mapOf(
    "alrajhi" to Color(0xFF0B5AA2),
    "snb" to Color(0xFF00706A),
    "barq" to Color(0xFF6A4BC0),
    "d360" to Color(0xFFC0356F),
    "enbd" to Color(0xFFB5121B),
    "aljazira" to Color(0xFF8A6A1F),
    "saib" to Color(0xFF2F5F3F),
    "stcpay" to Color(0xFF6E2C8F),
    "urpay" to Color(0xFF1F6F8B),
    "vision" to Color(0xFF3A5BA0),
    "meem" to Color(0xFFB0532A),
)

private val DarkMarks: Map<String, Color> = mapOf(
    "alrajhi" to Color(0xFF9CC4F0),
    "snb" to Color(0xFF7FD4CE),
    "barq" to Color(0xFFC3AEF5),
    "d360" to Color(0xFFF29CC0),
    "enbd" to Color(0xFFFF9F9C),
    "aljazira" to Color(0xFFE0C07A),
    "saib" to Color(0xFF9FD6AF),
    "stcpay" to Color(0xFFD8A8F0),
    "urpay" to Color(0xFF9BD3E6),
    "vision" to Color(0xFFA9BEF0),
    "meem" to Color(0xFFF2B294),
)

/**
 * Short names, in each of the app's two languages.
 *
 * A single Arabic label was wrong the moment the interface could be read in
 * English: "الراجحي 5763" inside an English row is not shorter, it is unreadable.
 * A row is scanned rather than read, so each language gets the shortest form a
 * person of that language would recognise.
 */
private val Labels: Map<String, Pair<String, String>> = mapOf(
    "alrajhi" to ("الراجحي" to "Al Rajhi"),
    "snb" to ("الأهلي" to "SNB"),
    "barq" to ("برق" to "barq"),
    "d360" to ("D360" to "D360"),
    "enbd" to ("الإمارات" to "Emirates NBD"),
    "aljazira" to ("الجزيرة" to "AlJazira"),
    "saib" to ("السعودي للاستثمار" to "SAIB"),
    "stcpay" to ("stc" to "stc"),
    "urpay" to ("urpay" to "urpay"),
    "vision" to ("فيجن" to "Vision"),
    "meem" to ("ميم" to "meem"),
)

@Composable
@ReadOnlyComposable
fun bankMark(bankId: String?): BankMark? {
    val (ar, en) = Labels[bankId] ?: return null
    val marks = if (MaterialTheme.colorScheme.surface.isDark()) DarkMarks else LightMarks
    return BankMark(ar, en, marks[bankId] ?: MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun Color.isDark(): Boolean =
    (0.2126f * red + 0.7152f * green + 0.0722f * blue) < 0.5f
