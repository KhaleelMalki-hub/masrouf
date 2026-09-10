package sa.masrouf.app.ui

import androidx.compose.runtime.Immutable
import sa.masrouf.core.model.CardKind

/**
 * What is known about the owner's cards, as one value the compiler can trust.
 *
 * These were two separate `Map` parameters on `TransactionRow`, and a `Map` is an
 * INTERFACE - the compiler cannot know the instance behind it will not be mutated,
 * so it treats the parameter as changed on every pass. That made the one composable
 * in this app built hundreds of times in a single scroll the one composable that
 * could never skip.
 *
 * `@Immutable` is a promise, so here is the evidence for it: both maps arrive from
 * `stateIn` flows that build a fresh map per emission out of a DAO query
 * (`associate { }`), and nothing anywhere writes into one after it is published.
 * Nothing here can change without the whole value being replaced, which is exactly
 * what the annotation asserts.
 */
@Immutable
data class CardLookup(
    val banks: Map<String, String> = emptyMap(),
    val kinds: Map<String, CardKind> = emptyMap(),
) {
    fun bankOf(last4: String?): String? = last4?.let(banks::get)

    fun kindOf(last4: String?): CardKind? = last4?.let(kinds::get)

    companion object {
        val Empty = CardLookup()
    }
}
