package sa.masrouf.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import sa.masrouf.app.R

/**
 * The palette is taken from سدو - Sadu, the Bedouin weaving of the peninsula:
 * bands of madder red, saffron and undyed wool laid across a dark wool ground.
 *
 * It is a deliberate move away from the teal-on-near-black that every Saudi
 * finance app currently wears. It also earns its place rather than decorating:
 * a month of spending is a set of proportions, which is exactly what a woven band
 * is, so the categories can be the bands and the strip can be read as a strip.
 */
object Sadu {
    /** Dark wool ground. Warm, not the blue-black of a default dark theme. */
    val Ground = Color(0xFF14110F)
    val GroundRaised = Color(0xFF1E1A17)
    val Loom = Color(0xFF2C2622)

    /** Undyed wool. The reading colour. */
    val Bone = Color(0xFFEDE4D4)
    val BoneDim = Color(0xFF9C9184)

    val Madder = Color(0xFFC24B3A)
    val Saffron = Color(0xFFD9A441)
    val Indigo = Color(0xFF4C6A9E)
    val Palm = Color(0xFF6E8F5E)
    val Clay = Color(0xFF9C6B4E)
    val Dusk = Color(0xFF7A5C86)
    val Sand = Color(0xFFB9A88C)
    val Ash = Color(0xFF6B625A)
}

/**
 * IBM Plex Sans Arabic, bundled.
 *
 * The system Arabic face is a Naskh, and it is what makes an Android app look like
 * an Android app rather than like itself. Plex Arabic is drawn on the same
 * skeleton as its Latin, so the Arabic labels and the Western numerals this app
 * insists on sit together instead of looking like two typefaces sharing a line.
 * SIL OFL, so bundling it is fine.
 */
private val PlexArabic = FontFamily(
    Font(R.font.plex_arabic_regular, FontWeight.Normal),
    Font(R.font.plex_arabic_medium, FontWeight.Medium),
    Font(R.font.plex_arabic_semibold, FontWeight.SemiBold),
    Font(R.font.plex_arabic_bold, FontWeight.Bold),
)

private val SaduTypography = Typography().run {
    copy(
        displaySmall = displaySmall.copy(
            fontFamily = PlexArabic, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp,
        ),
        headlineMedium = headlineMedium.copy(fontFamily = PlexArabic, fontWeight = FontWeight.SemiBold),
        headlineSmall = headlineSmall.copy(fontFamily = PlexArabic, fontWeight = FontWeight.SemiBold),
        titleLarge = titleLarge.copy(fontFamily = PlexArabic, fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontFamily = PlexArabic, fontWeight = FontWeight.Medium),
        bodyLarge = bodyLarge.copy(fontFamily = PlexArabic),
        bodyMedium = bodyMedium.copy(fontFamily = PlexArabic),
        bodySmall = bodySmall.copy(fontFamily = PlexArabic),
        labelLarge = labelLarge.copy(fontFamily = PlexArabic, fontWeight = FontWeight.Medium),
        labelMedium = labelMedium.copy(fontFamily = PlexArabic, fontWeight = FontWeight.Medium),
        labelSmall = labelSmall.copy(
            fontFamily = PlexArabic, fontWeight = FontWeight.Medium, letterSpacing = 0.6.sp,
        ),
    )
}

/** The amount, wherever it appears. Tabular so columns of money line up. */
val MoneyStyle: TextStyle = TextStyle(
    fontFamily = PlexArabic,
    fontWeight = FontWeight.SemiBold,
    letterSpacing = (-0.3).sp,
)

private val SaduScheme = darkColorScheme(
    primary = Sadu.Saffron,
    onPrimary = Sadu.Ground,
    primaryContainer = Sadu.Madder,
    onPrimaryContainer = Sadu.Bone,
    secondary = Sadu.Sand,
    onSecondary = Sadu.Ground,
    secondaryContainer = Sadu.Loom,
    onSecondaryContainer = Sadu.Bone,
    tertiary = Sadu.Palm,
    onTertiary = Sadu.Ground,
    background = Sadu.Ground,
    onBackground = Sadu.Bone,
    surface = Sadu.Ground,
    onSurface = Sadu.Bone,
    surfaceVariant = Sadu.GroundRaised,
    onSurfaceVariant = Sadu.BoneDim,
    outline = Sadu.Loom,
    outlineVariant = Sadu.Loom,
    error = Sadu.Madder,
    onError = Sadu.Bone,
)

/**
 * One scheme, dark only.
 *
 * Not an oversight: the palette is a dark wool ground with dyed bands on it, and a
 * light inversion of that is a different artefact, not the same design in another
 * mode. Committing to one look and executing it is better than shipping two
 * half-considered ones.
 */
@Composable
fun MasroufTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SaduScheme,
        typography = SaduTypography,
        content = content,
    )
}
