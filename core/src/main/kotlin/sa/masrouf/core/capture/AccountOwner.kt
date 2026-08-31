package sa.masrouf.core.capture

/**
 * The account holder's own name, as the banks actually spell it.
 *
 * A transfer to yourself is not spending. The banks do not say so - the message
 * for money sent to your own account at another bank is word-for-word the message
 * for money sent to a stranger, and the only thing that separates them is the
 * name in the recipient field. Without this list, 298 transfers worth 540,000
 * riyals were counted as spending because the app had no way to know whose name
 * that was.
 *
 * ## Why a pair of tokens rather than one
 *
 * The surname alone is wrong: 400 outgoing transfers in the same history go to
 * relatives who share it, and those are real spending. The given name alone is
 * weaker still. Requiring both, in any order, separates the owner from the family.
 *
 * ## Why several entries
 *
 * Banks mask the tail of a name at different points and transliterate it
 * differently, so no single pair covers every message:
 *
 *     الى KHALEEL MALKI          (barq)
 *     إلى: Khaleel Malk****      (D360, masked mid-surname)
 *     الى:خليل سامى مالكى        (ANB via SNB)
 *     إلى: خليل سامي خل****      (masked before the surname appears at all)
 *
 * The last of those never shows the surname, so it is matched on the given and
 * middle names instead. Matching is done on folded text, so ى/ي and أ/ا variants
 * collapse before comparison and are not listed separately here.
 *
 * Kept as a constant for the same reason as [sa.masrouf.app.ui.ActiveCards]: this
 * is a single-user app, the value changes when the user's name changes, and a
 * settings screen for one string is machinery for a value that never moves.
 */
object AccountOwner {

    /**
     * Each entry is a set of tokens that must *all* appear for the message to be
     * naming the owner. Any one entry matching is enough.
     */
    val NAME_TOKENS: List<List<String>> = listOf(
        listOf("خليل", "مالك"),
        listOf("خليل", "سامي"),
        // Latin tokens are matched as whole words, so the masked form and the full
        // spelling need an entry each - "MALK" cannot match inside "MALKI".
        listOf("KHALEEL", "MALKI"),
        listOf("KHALEEL", "MALK"),
    )
}
