package sa.masrouf.app.ui

import sa.masrouf.core.model.Category

/**
 * What the month's history is narrowed to.
 *
 * [Unfiled] is a filter and not the absence of one. The shipped merchant list and
 * the type rules together file about 84% of a real history; the remainder is a few
 * thousand rows spread over more than a thousand local shops, roughly two each, and
 * no list that ships in an APK will ever reach them. What reaches them is the user
 * filing a merchant once, so the app's job is to hand them that worklist rather than
 * leave it scattered through a list of 22,000.
 *
 * A nullable [Category] cannot say this: null there already means "no filter", so
 * the uncategorised band in the legend had nothing to select and tapping it cleared
 * the filter instead of applying one.
 */
sealed interface HistoryFilter {

    /** Only what the user, or the app, filed under [category]. */
    data class OfCategory(val category: Category) : HistoryFilter

    /** Only what nothing has filed yet. */
    data object Unfiled : HistoryFilter
}
