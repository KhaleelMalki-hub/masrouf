package sa.masrouf.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import java.time.LocalDate
import java.time.format.TextStyle

/**
 * A month, named.
 *
 * The month name comes from the locale, so Arabic reads أغسطس and English reads
 * August. The year does not: it is appended with `toString`, which is always
 * ASCII, because a localised date formatter renders `2026` as `٢٠٢٦` in Arabic and
 * this app prints Western digits everywhere else on the screen. One line showing
 * both numeral systems is the thing that has already been fixed twice here.
 */
@Composable
@ReadOnlyComposable
fun LocalDate.monthLabel(): String {
    val locale = LocalConfiguration.current.locales[0]
    return "${month.getDisplayName(TextStyle.FULL_STANDALONE, locale)} $year"
}
