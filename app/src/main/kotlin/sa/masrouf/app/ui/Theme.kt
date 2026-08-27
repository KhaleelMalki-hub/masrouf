package sa.masrouf.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme()
private val DarkColors = darkColorScheme()

/**
 * Material 3 defaults, unmodified.
 *
 * Layout direction is not set here. Compose reads it from the configured locale,
 * so the Arabic build is right-to-left and the English one is left-to-right
 * without either being hard-coded - and forcing a direction is what breaks the
 * one that was not forced.
 */
@Composable
fun MasroufTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
