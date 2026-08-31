package sa.masrouf.core.capture

/**
 * The account holder's own name, as the banks actually spell it.
 *
 * A transfer to yourself is not spending. The banks do not say so - the message
 * for money sent to your own account at another bank is word-for-word the message
 * for money sent to a stranger, and the only thing that separates them is the
 * name in the recipient field. Without this, 298 transfers worth 540,000 riyals
 * were counted as spending because the app had no way to know whose name that was.
 *
 * ## Why it is empty here
 *
 * A real person's name is not something this repository may carry: it is public,
 * and CLAUDE.md's Privacy section already forbids a real name in a fixture. So the
 * value is supplied at runtime from `local.properties`, which is gitignored, and
 * the default is no names at all. On anyone else's clone the matcher matches
 * nobody, every outgoing transfer stays a transfer out, and nothing is claimed
 * that cannot be justified.
 *
 * ## Why a pair of tokens rather than one
 *
 * The surname alone is wrong: 400 outgoing transfers in one real history go to
 * relatives who share it, and those are real spending. The given name alone is
 * weaker still. Requiring both, in any order, separates the owner from the family.
 *
 * ## Why several entries
 *
 * Banks mask the tail of a name at different points and transliterate it
 * differently, so no single pair covers every message: one template gives the full
 * Latin name, another masks it mid-surname, a third masks before the surname
 * appears at all and has to be matched on the given and middle names instead.
 * Matching runs on folded text, so ى/ي and أ/ا variants collapse before comparison
 * and never need listing separately.
 */
object AccountOwner {

    /**
     * Each entry is a set of tokens that must *all* appear for the message to be
     * naming the owner. Any one entry matching is enough.
     *
     * Written once at startup, read on every message. Empty until something sets
     * it, which is the safe direction: an empty list demotes nothing.
     */
    @Volatile
    var nameTokens: List<List<String>> = emptyList()
        private set

    /**
     * @param spec entries separated by `;`, tokens within an entry by `|`, as in
     *   `"GIVEN|FAMILY ; اسم|عائلة"`. Blank entries and blank tokens are dropped,
     *   so a missing or half-written property leaves fewer rules rather than a rule
     *   that matches everything.
     */
    fun configure(spec: String) {
        nameTokens = spec.split(';')
            .map { entry -> entry.split('|').map(String::trim).filter(String::isNotEmpty) }
            .filter { it.isNotEmpty() }
    }
}
