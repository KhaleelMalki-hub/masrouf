package sa.masrouf.app.ui

import android.os.Build
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import sa.masrouf.app.R

/**
 * Which theme the user wants, independent of what the phone is doing.
 *
 * [System] is the default because the two situations this app is used in are both
 * common - Riyadh daylight and a dark room at night - and the phone already knows
 * which one it is in. The explicit options exist because the phone is sometimes
 * wrong about that, and because the choice is one tap either way.
 */
enum class ThemeMode { System, Light, Dark }

/**
 * Material 3 colour, generated from one seed rather than picked per role.
 *
 * Seed `#2E5AAC`, a considered blue. Not M3's baseline purple, which reads as a
 * template nobody touched, and not the teal of the app this one was explicitly
 * designed against.
 *
 * Both schemes are complete. An earlier release shipped dark only, on the
 * reasoning that the palette looked better dark - which is an aesthetic argument
 * for a decision that should come from where the screen is actually read, and the
 * answer there is "outdoors, in the sun, often".
 */
private val LightScheme = lightColorScheme(
    primary = Color(0xFF3A5FA8),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD9E2FF),
    onPrimaryContainer = Color(0xFF001945),
    secondary = Color(0xFF575E71),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDBE2F9),
    onSecondaryContainer = Color(0xFF141B2C),
    tertiary = Color(0xFF715573),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFCD7FC),
    onTertiaryContainer = Color(0xFF29132D),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFBF8FF),
    onBackground = Color(0xFF1A1B21),
    surface = Color(0xFFFBF8FF),
    onSurface = Color(0xFF1A1B21),
    surfaceVariant = Color(0xFFE1E2EC),
    onSurfaceVariant = Color(0xFF44464F),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF5F2FA),
    surfaceContainer = Color(0xFFEFECF4),
    surfaceContainerHigh = Color(0xFFE9E7EF),
    surfaceContainerHighest = Color(0xFFE3E1E9),
    outline = Color(0xFF757780),
    outlineVariant = Color(0xFFC5C6D0),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFAEC6FF),
    onPrimary = Color(0xFF05306B),
    primaryContainer = Color(0xFF22468E),
    onPrimaryContainer = Color(0xFFD9E2FF),
    secondary = Color(0xFFBFC6DC),
    onSecondary = Color(0xFF293042),
    secondaryContainer = Color(0xFF3F4759),
    onSecondaryContainer = Color(0xFFDBE2F9),
    tertiary = Color(0xFFDFBBDF),
    onTertiary = Color(0xFF402743),
    tertiaryContainer = Color(0xFF583D5A),
    onTertiaryContainer = Color(0xFFFCD7FC),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF121318),
    onBackground = Color(0xFFE3E1E9),
    surface = Color(0xFF121318),
    onSurface = Color(0xFFE3E1E9),
    surfaceVariant = Color(0xFF44464F),
    onSurfaceVariant = Color(0xFFC5C6D0),
    surfaceContainerLowest = Color(0xFF0D0E13),
    surfaceContainerLow = Color(0xFF1A1B21),
    surfaceContainer = Color(0xFF1E1F25),
    surfaceContainerHigh = Color(0xFF292A30),
    surfaceContainerHighest = Color(0xFF34343B),
    outline = Color(0xFF8F909A),
    outlineVariant = Color(0xFF45464F),
)

/**
 * IBM Plex Sans Arabic, bundled - with one glyph of our own.
 *
 * The system Arabic face is a Naskh, and it is what makes an Android app look like
 * an Android app rather than like itself. Plex Arabic is drawn on the same
 * skeleton as its Latin, so the Arabic labels and the Western numerals this app
 * insists on sit together instead of looking like two typefaces sharing a line.
 * SIL OFL, so bundling it is fine.
 *
 * Bundled as "Masrouf Arabic" rather than as Plex, because it is no longer Plex:
 * the Saudi riyal sign (U+20C1) is drawn into all four weights from the central
 * bank's own outline. Plex 1.005 has no glyph for it, no Noto build checked in
 * September 2026 had one either, and a currency this app prints on every screen
 * cannot be a box on a phone whose system font has not caught up yet. The OFL's
 * reserved-name clause is why the family is renamed; `tools/add_riyal_glyph.py`
 * does both and is the only way these files should ever be regenerated.
 */
private val MasroufArabic = FontFamily(
    Font(R.font.masrouf_arabic_regular, FontWeight.Normal),
    Font(R.font.masrouf_arabic_medium, FontWeight.Medium),
    Font(R.font.masrouf_arabic_semibold, FontWeight.SemiBold),
    Font(R.font.masrouf_arabic_bold, FontWeight.Bold),
)

/** The M3 scale, with every role set in Masrouf Arabic so nothing falls back. */
private val MasroufTypography = Typography().run {
    copy(
        displayLarge = displayLarge.copy(fontFamily = MasroufArabic),
        displayMedium = displayMedium.copy(
            fontFamily = MasroufArabic, fontWeight = FontWeight.Bold, letterSpacing = (-1.5).sp,
        ),
        displaySmall = displaySmall.copy(fontFamily = MasroufArabic, fontWeight = FontWeight.Bold),
        headlineLarge = headlineLarge.copy(fontFamily = MasroufArabic),
        headlineMedium = headlineMedium.copy(fontFamily = MasroufArabic, fontWeight = FontWeight.SemiBold),
        headlineSmall = headlineSmall.copy(fontFamily = MasroufArabic, fontWeight = FontWeight.SemiBold),
        titleLarge = titleLarge.copy(fontFamily = MasroufArabic, fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontFamily = MasroufArabic, fontWeight = FontWeight.Medium),
        titleSmall = titleSmall.copy(fontFamily = MasroufArabic, fontWeight = FontWeight.Medium),
        bodyLarge = bodyLarge.copy(fontFamily = MasroufArabic),
        bodyMedium = bodyMedium.copy(fontFamily = MasroufArabic),
        bodySmall = bodySmall.copy(fontFamily = MasroufArabic),
        labelLarge = labelLarge.copy(fontFamily = MasroufArabic, fontWeight = FontWeight.Medium),
        labelMedium = labelMedium.copy(
            fontFamily = MasroufArabic, fontWeight = FontWeight.Medium, letterSpacing = 0.8.sp,
        ),
        labelSmall = labelSmall.copy(fontFamily = MasroufArabic, fontWeight = FontWeight.Medium),
    )
}

/**
 * The amount, wherever it is not already a headline.
 *
 * Applied as `typography.role.merge(MoneyStyle)` and never the other way round.
 * `merge` fills the RECEIVER's gaps from the argument, and every role in this scale
 * sets a weight and a tracking - so written the other way it was inert at thirteen
 * call sites, and the card's last-four was actually WIDENED by `labelMedium`'s
 * 0.8sp, the opposite of what this style is for.
 *
 * The two headline amounts take their role plain: the month total and the entry
 * field carry their weight in the scale itself, and are heavier than this.
 */
val MoneyStyle: TextStyle = TextStyle(
    fontWeight = FontWeight.SemiBold,
    letterSpacing = (-0.3).sp,
)

@Composable
fun MasroufTheme(
    mode: ThemeMode = ThemeMode.System,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    // Material You where the device offers it: the scheme is derived from the
    // wallpaper, which is what "Material 3 as Google specifies it" means on
    // Android 12 and later. The seeded schemes below are the fallback for older
    // devices, and the record of what the app looks like with no wallpaper to
    // read. Category colours are untouched either way; they are data, not theme.
    val context = LocalContext.current
    val dynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val scheme = when {
        dynamic && dark -> dynamicDarkColorScheme(context)
        dynamic -> dynamicLightColorScheme(context)
        dark -> DarkScheme
        else -> LightScheme
    }
    MaterialTheme(
        colorScheme = scheme,
        typography = MasroufTypography,
        content = content,
    )
}
