package sa.masrouf.app.data

/**
 * What one statement import did.
 *
 * `duplicates` is reported rather than hidden because it is the number that says
 * the import worked: re-importing a file this history already has should store
 * nothing and count everything, and a second run that stores rows again is the
 * failure this whole path exists to prevent.
 */
data class StatementImport(
    val stored: Int,
    val duplicates: Int,
    /** True when the importer did not trust its own reading and nothing was stored. */
    val refused: Boolean,
)
