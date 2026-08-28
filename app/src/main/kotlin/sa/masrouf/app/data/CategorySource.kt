package sa.masrouf.app.data

/**
 * Who decided a transaction's category.
 *
 * Needed because re-filing has to spare the user's own decisions while replacing
 * the app's. Nothing about a stored category id says which it was, and inferring
 * it - "the app would have guessed this one, so it must have been the app" -
 * cannot tell a correct guess from a person agreeing with it, and would throw away
 * the agreement.
 *
 * Provenance is therefore passed in at every write, the same rule
 * `CaptureRecorder` follows for `Source`.
 */
enum class CategorySource {

    /** A rule filed it: [sa.masrouf.core.model.CategoryGuess] or a learned merchant rule. */
    AUTOMATIC,

    /** A person chose it, and re-filing must not touch it. */
    MANUAL,
    ;

    companion object {
        /**
         * Rows that predate this column.
         *
         * Read as automatic, because almost all of them are: a real history of
         * 22,084 records was filed by a backfill over the merchant rules. The few
         * that were a person's choice cannot be told apart from the rest and a
         * first re-file will reset them. Every choice made after this migration is
         * recorded and safe, and the alternative - treating them all as manual -
         * would make the re-file the user asked for do nothing at all.
         */
        val LEGACY = AUTOMATIC
    }
}
