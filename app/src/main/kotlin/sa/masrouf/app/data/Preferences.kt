package sa.masrouf.app.data

import android.content.Context
import sa.masrouf.app.ui.ThemeMode

/**
 * The handful of choices that belong to the person rather than to their money.
 *
 * SharedPreferences rather than DataStore or a Room table: there is one value, it
 * is read once at startup, and losing it costs a tap. A migration and a coroutine
 * API would be machinery for a single enum.
 */
class Preferences(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("masrouf.settings", Context.MODE_PRIVATE)

    /**
     * @return the stored preference, defaulting to [ThemeMode.System].
     *
     * An unrecognised stored value also falls back to System rather than throwing.
     * Unlike a transaction's enum, where a wrong guess changes a number, the worst
     * case here is the wrong background for one launch.
     */
    var themeMode: ThemeMode
        get() = runCatching { ThemeMode.valueOf(prefs.getString(THEME_KEY, null) ?: "") }
            .getOrDefault(ThemeMode.System)
        set(value) = prefs.edit().putString(THEME_KEY, value.name).apply()

    /**
     * Which one-off maintenance passes have run. See [MasroufApp.runMaintenance].
     * Zero on a fresh install, so every pass runs once, in order.
     */
    var maintenanceVersion: Int
        get() = prefs.getInt(MAINTENANCE_KEY, 0)
        set(value) = prefs.edit().putInt(MAINTENANCE_KEY, value).apply()

    /**
     * The monthly salary in halalas, or null when the user has not said.
     *
     * Entered, not inferred. Salary messages stopped in 2021 and income has arrived
     * as transfers since, indistinguishable from money moved between the user's
     * own accounts. One number the user types is honest; a guess from transfers
     * would be a number the app is unsure of, shown as if it were sure.
     */
    var salaryHalalas: Long?
        get() = prefs.getLong(SALARY_KEY, -1L).takeIf { it >= 0L }
        set(value) = prefs.edit().let { e -> if (value == null) e.remove(SALARY_KEY) else e.putLong(SALARY_KEY, value) }.apply()

    private companion object {
        const val THEME_KEY = "theme_mode"
        const val SALARY_KEY = "salary_halalas"
        const val MAINTENANCE_KEY = "maintenance_version"
    }
}

/**
 * How far the stored history has been brought forward.
 *
 * Bumped whenever a repair is added to [MasroufApp.Repair]; the repair records the
 * same number as the version that introduced it, and the two together decide what
 * an install behind this one still needs.
 */
const val CURRENT_MAINTENANCE_VERSION = 40
