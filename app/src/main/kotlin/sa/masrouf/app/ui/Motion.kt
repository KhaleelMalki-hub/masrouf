package sa.masrouf.app.ui

import androidx.compose.ui.unit.dp

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing

/**
 * Material 3 motion, as m3.material.io specifies it.
 *
 * One place, so every transition in the app moves the same way. The curves are
 * M3's "emphasized" set, which it recommends for most product transitions; the
 * durations are its medium and short tokens. Nothing bounces and nothing
 * overshoots: this app shows money, and a number that wobbles into place looks
 * unsure of itself.
 *
 * Reduced motion needs no code here. Compose scales every animation by the
 * system's animator duration scale, and a user who has turned animations off gets
 * every change instantly.
 */
object Motion {
    /** Things entering the screen: fast start, gentle settle. */
    val emphasizedDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    /** Things leaving: a quick, clean exit. */
    val emphasizedAccelerate: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    /** Things changing in place: the standard curve. */
    val standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    const val SHORT = 200
    const val MEDIUM = 400

    /**
     * The two halves of a fade-through, M3's transition between peers.
     *
     * Sequential, not crossed: the outgoing screen leaves first and the incoming one
     * arrives into the space it left. A `Crossfade` runs both at once, and over two
     * screens of figures that ghosts one column of numbers through another.
     */
    const val FADE_OUT = 90
    const val FADE_IN = 210

    /** How far the incoming screen scales up from. M3's fade-through grows slightly. */
    const val FADE_IN_SCALE = 0.92f
}

/**
 * Room below a scrolling list for the floating button to sit over.
 *
 * A 56dp FAB plus M3's 16dp margin, plus a row's worth so the last value is not
 * merely clear but readable. Applied as `contentPadding`, never as a trailing
 * spacer: padding is part of the scroll range, so the final row can be brought
 * above the button.
 */
val FAB_CLEARANCE = 96.dp

/**
 * The inner padding of a panel, and the side margin of a modal sheet.
 *
 * Two numbers because they are two jobs, and named because they were six: the month
 * card sat at 20 where every other panel sat at 16, and the three sheets aligned
 * their content at 24, 20 and 20. Nothing said why, which is the whole argument -
 * a rhythm a reader cannot name is one that drifts a point at a time.
 */
val PANEL_PADDING = 16.dp
val SHEET_EDGE = 20.dp

