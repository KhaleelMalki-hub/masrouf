package sa.masrouf.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/**
 * How a bank is shown on a transaction row.
 *
 * Colours are chosen to be told apart at the size of a chip, not to reproduce a
 * brand exactly: five marks a few millimetres tall on both a light and a dark
 * background, which is a different problem from a logo on a billboard. They are
 * close to each bank's own colour where that survives the constraint.
 *
 * No bank logos. They are trademarks, this is a private app that will never be
 * published, and a wordmark scaled to 16dp is a smear - the short name reads
 * better and is what a person calls the bank anyway.
 */
data class BankMark(val label: String, val colour: Color)

private val LightMarks: Map<String, Color> = mapOf(
    "alrajhi" to Color(0xFF0B5AA2),
    "snb" to Color(0xFF00706A),
    "barq" to Color(0xFF6A4BC0),
    "d360" to Color(0xFFC0356F),
    "enbd" to Color(0xFFB5121B),
)

private val DarkMarks: Map<String, Color> = mapOf(
    "alrajhi" to Color(0xFF9CC4F0),
    "snb" to Color(0xFF7FD4CE),
    "barq" to Color(0xFFC3AEF5),
    "d360" to Color(0xFFF29CC0),
    "enbd" to Color(0xFFFF9F9C),
)

/**
 * The label is the Arabic short name in both locales.
 *
 * These are the names on the cards in a Saudi wallet, and the English forms
 * ("SNB", "Al Rajhi") are not what anyone calls them out loud. A row is scanned,
 * not read, so the shortest recognisable form wins.
 */
private val Labels: Map<String, String> = mapOf(
    "alrajhi" to "الراجحي",
    "snb" to "الأهلي",
    "barq" to "برق",
    "d360" to "D360",
    "enbd" to "الإمارات",
)

@Composable
@ReadOnlyComposable
fun bankMark(bankId: String?): BankMark? {
    val label = Labels[bankId] ?: return null
    val marks = if (MaterialTheme.colorScheme.surface.isDark()) DarkMarks else LightMarks
    return BankMark(label, marks[bankId] ?: MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun Color.isDark(): Boolean =
    (0.2126f * red + 0.7152f * green + 0.0722f * blue) < 0.5f
