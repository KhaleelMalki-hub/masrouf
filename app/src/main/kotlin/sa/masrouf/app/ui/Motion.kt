package sa.masrouf.app.ui

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
}
