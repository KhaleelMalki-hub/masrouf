package sa.masrouf.core.text

/**
 * Normalisation of Arabic bank message text.
 *
 * Saudi bank SMS and push notifications are the messiest text this app will ever
 * handle. A single "87.50 SAR" can arrive as any of:
 *
 *   - Arabic-Indic digits with the Arabic decimal separator (U+066B)
 *   - the same digits wrapped in RIGHT-TO-LEFT MARKs so the sender controls layout
 *   - mixed digit systems inside a single number
 *   - the Arabic thousands separator (U+066C)
 *
 * Every downstream step (amount extraction, merchant matching, deduplication)
 * assumes it is working on normalised text. Nothing else in the codebase should
 * look at a raw message body.
 *
 * ## Why code points and not character literals
 *
 * Every non-printing character below is declared as an integer code point rather
 * than a quoted literal. Bidi controls and zero-width characters are invisible in
 * an editor, in a terminal and in a diff. Written as literals they are one bad
 * copy-paste away from silently becoming the wrong character - or vanishing - with
 * nothing for a reviewer to see. `0x200B` is auditable; the character it names is
 * not. This file is the one place in the codebase where that distinction matters,
 * and the rule is absolute here.
 */
object ArabicText {

    // ---- Digit systems -----------------------------------------------------

    private const val ARABIC_INDIC_ZERO = 0x0660   // U+0660..U+0669  Arabic-Indic
    private const val EXTENDED_INDIC_ZERO = 0x06F0 // U+06F0..U+06F9  Eastern Arabic-Indic

    // ---- Numeric separators ------------------------------------------------

    private const val ARABIC_DECIMAL_SEPARATOR = 0x066B
    private const val ARABIC_THOUSANDS_SEPARATOR = 0x066C
    private const val ARABIC_PERCENT_SIGN = 0x066A

    /**
     * Characters that carry no meaning for parsing but routinely break regexes,
     * string equality and `trim()`. The bidi controls are the dangerous ones: they
     * are invisible, senders insert them to force RTL/LTR layout, and they sit
     * *inside* numbers.
     */
    private val INVISIBLE: Set<Char> = intArrayOf(
        0x200B, // ZERO WIDTH SPACE
        0x200C, // ZERO WIDTH NON-JOINER
        0x200D, // ZERO WIDTH JOINER
        0x200E, // LEFT-TO-RIGHT MARK
        0x200F, // RIGHT-TO-LEFT MARK
        0x061C, // ARABIC LETTER MARK
        0x202A, // LEFT-TO-RIGHT EMBEDDING
        0x202B, // RIGHT-TO-LEFT EMBEDDING
        0x202C, // POP DIRECTIONAL FORMATTING
        0x202D, // LEFT-TO-RIGHT OVERRIDE
        0x202E, // RIGHT-TO-LEFT OVERRIDE
        0x2066, // LEFT-TO-RIGHT ISOLATE
        0x2067, // RIGHT-TO-LEFT ISOLATE
        0x2068, // FIRST STRONG ISOLATE
        0x2069, // POP DIRECTIONAL ISOLATE
        0xFEFF, // ZERO WIDTH NO-BREAK SPACE / BOM
        0x0640, // ARABIC TATWEEL - decorative elongation, e.g. in "riyal"
    ).mapTo(HashSet()) { it.toChar() }

    /** Space-like characters that are not U+0020 and therefore survive a naive `trim()`. */
    private val SPACE_LIKE: Set<Char> = intArrayOf(
        0x00A0, // NO-BREAK SPACE
        0x2007, // FIGURE SPACE
        0x2009, // THIN SPACE
        0x202F, // NARROW NO-BREAK SPACE
        0x3000, // IDEOGRAPHIC SPACE
        0x0009, // TAB
        0x000B, // VERTICAL TAB
        0x000C, // FORM FEED
        // NOT the carriage return, which is a line break - see FIELD_BREAK. It was
        // in this set, and a sender that separates its fields with CR alone had
        // every one of them collapsed onto a single line. Field patterns are
        // anchored to the start of a line, so all of them stopped matching at once:
        // 68 Emirates NBD purchases stored their amount and no merchant, the name
        // sitting untouched in the body.
    ).mapTo(HashSet()) { it.toChar() }

    /** Arabic letters whose written form varies between senders but means the same thing. */
    private val LETTER_FOLDING: Map<Char, Char> = mapOf(
        0x0623 to 0x0627, // ALEF WITH HAMZA ABOVE -> ALEF
        0x0625 to 0x0627, // ALEF WITH HAMZA BELOW -> ALEF
        0x0622 to 0x0627, // ALEF WITH MADDA ABOVE -> ALEF
        0x0671 to 0x0627, // ALEF WASLA            -> ALEF
        0x0649 to 0x064A, // ALEF MAKSURA          -> YEH
        0x0626 to 0x064A, // YEH WITH HAMZA ABOVE  -> YEH
        0x0624 to 0x0648, // WAW WITH HAMZA ABOVE  -> WAW
        0x0629 to 0x0647, // TEH MARBUTA           -> HEH
    ).entries.associate { (from, to) -> from.toChar() to to.toChar() }

    /** Arabic diacritics (tashkeel): U+064B..U+0652, plus the common standalone marks. */
    private val DIACRITICS: Set<Char> =
        ((0x064B..0x0652) + listOf(0x0653, 0x0654, 0x0655, 0x0670))
            .mapTo(HashSet()) { it.toChar() }

    private val SPACE_RUN = Regex(" {2,}")

    /** Anything that is not a letter, digit or space contributes nothing to matching. */
    private val NON_MATCHABLE = Regex("[^\\p{L}\\p{N} ]+")

    /** A trailing run of standalone digit groups and country codes appended by card networks. */
    private val TRAILING_REFERENCE = Regex("(?: (?:\\d{2,}|SA|KSA|SAU))+$")

    // ---- Pipeline steps ----------------------------------------------------

    /** Converts Arabic-Indic and Eastern Arabic-Indic digits to ASCII `0`-`9`. */
    fun normalizeDigits(input: String): String = buildString(input.length) {
        for (ch in input) {
            val code = ch.code
            append(
                when (code) {
                    in ARABIC_INDIC_ZERO..(ARABIC_INDIC_ZERO + 9) ->
                        ('0'.code + (code - ARABIC_INDIC_ZERO)).toChar()

                    in EXTENDED_INDIC_ZERO..(EXTENDED_INDIC_ZERO + 9) ->
                        ('0'.code + (code - EXTENDED_INDIC_ZERO)).toChar()

                    else -> ch
                }
            )
        }
    }

    /** Converts Arabic numeric separators to their ASCII equivalents. */
    fun normalizeNumericSeparators(input: String): String = buildString(input.length) {
        for (ch in input) {
            append(
                when (ch.code) {
                    ARABIC_DECIMAL_SEPARATOR -> '.'
                    ARABIC_THOUSANDS_SEPARATOR -> ','
                    ARABIC_PERCENT_SIGN -> '%'
                    else -> ch
                }
            )
        }
    }

    /** Removes bidi controls, zero-width characters and tatweel. */
    fun stripInvisible(input: String): String = input.filterNot { it in INVISIBLE }

    /**
     * What senders use to end a field, other than a newline.
     *
     * Three of them, each found by a family of messages that stored an amount and
     * no merchant:
     * - a carriage return alone (Emirates NBD's أثير purchases);
     * - the two literal characters `^M`, which is a carriage return that something
     *   upstream of this app has already written out in caret notation. 68 stored
     *   bodies carry it and no bank writes it on purpose;
     * - a pipe, which D360 and SNB's newer templates use to put every field of a
     *   message on one line;
     * - a run of five or more spaces BETWEEN two visible characters, which the
     *   older AlRajhi templates use to pad a column ("EXTRA        MAKKAH   SA").
     *   Short runs are left alone - they are typing slips inside a name - and so is
     *   indentation, which separates a field from nothing.
     *
     * A boundary, not noise: a field pattern that reads to the end of its line
     * would otherwise swallow every field after it.
     */
    private val FIELD_BREAK = Regex("""\r\n?|\^M|\s*\|\s*|(?<=\S)[ ]{5,}(?=\S)""")

    /**
     * Rewrites every field boundary a sender uses as a newline.
     *
     * Space-like characters are unified first, because a column padded with
     * no-break spaces is a boundary exactly as one padded with ordinary ones, and
     * the run has to be visible as spaces before it can be counted.
     */
    fun normalizeFieldBreaks(input: String): String =
        FIELD_BREAK.replace(unifySpaces(input), "\n")

    /** Space-like characters as plain U+0020, newlines untouched. */
    private fun unifySpaces(input: String): String = buildString(input.length) {
        for (ch in input) append(if (ch in SPACE_LIKE) ' ' else ch)
    }

    /** Collapses space-like characters into single U+0020 spaces and trims. Newlines are kept. */
    fun normalizeWhitespace(input: String): String {
        return unifySpaces(input)
            .split('\n')
            .joinToString("\n") { line -> line.replace(SPACE_RUN, " ").trim() }
            .trim()
    }

    /**
     * The standard pipeline. Apply this once, where a message enters the system,
     * and work on the result everywhere after.
     *
     * Whitespace collapsing must come last, so that removing a zero-width character
     * from between two spaces does not leave a double space behind.
     */
    fun normalize(input: String): String =
        normalizeWhitespace(
            normalizeFieldBreaks(normalizeNumericSeparators(normalizeDigits(stripInvisible(input))))
        )

    /**
     * Aggressive folding used only for *matching* (merchant comparison, dedup keys),
     * never for anything shown to the user, because it destroys spelling.
     */
    fun foldForMatching(input: String): String {
        val normalized = normalize(input)
        val folded = buildString(normalized.length) {
            for (ch in normalized) {
                if (ch in DIACRITICS) continue
                append(LETTER_FOLDING[ch] ?: ch)
            }
        }
        return folded.uppercase()
            .replace(NON_MATCHABLE, " ")
            .replace(SPACE_RUN, " ")
            .trim()
    }

    /**
     * Normalises a merchant name into the key used for matching and deduplication.
     *
     * Card networks pad merchant names with location and reference codes, and those
     * tails differ between the push notification and the statement row for the very
     * same purchase. Left in, they would make one purchase look like two.
     */
    fun normalizeMerchant(raw: String): String =
        foldForMatching(raw)
            .replace(TRAILING_REFERENCE, "")
            .replace(SPACE_RUN, " ")
            .trim()
}
