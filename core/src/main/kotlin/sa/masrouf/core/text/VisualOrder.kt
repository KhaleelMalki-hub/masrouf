package sa.masrouf.core.text

import java.text.Normalizer

/**
 * Restores logical character order to Arabic text extracted from a PDF.
 *
 * A PDF stores glyphs, not sentences. Arabic in a bank statement is written into
 * the file already laid out for display: right-to-left runs stored in visual
 * order, using Arabic Presentation Forms rather than base letters. Extracted
 * naively it comes back as gibberish that still looks like Arabic.
 *
 * This is a different problem from [ArabicText.normalize], which cleans up text
 * that was always in logical order. This one has to undo a layout, which means
 * partially re-implementing the Unicode bidirectional algorithm in reverse.
 *
 * ## Three rules, each learned from a real statement cell
 *
 * **1. Reverse before decomposing.** A lam-alef ligature is one character in
 * presentation form and two after decomposition. Decompose first and the reversal
 * gets two characters where the layout had one, and swaps them:
 *
 *     decompose then reverse:   عملية شراء عبر اإلنترنت     (wrong)
 *     reverse then decompose:   عملية شراء عبر الإنترنت     (right)
 *
 * **2. Left-to-right runs keep their own order.** Reversing the whole line
 * reverses the numbers in it. Branch code 0828 becomes 8280 and national id
 * 1123854760 becomes 0674583211 - identifiers that are wrong and perfectly
 * plausible. Latin merchant names come back spelled backwards.
 *
 * **3. A neutral between two same-direction characters takes their direction.**
 * This is the rule that is easy to miss, and it corrupts times and prices. In
 * `21:14:22` the colons sit between digits; treated as standalone neutrals they
 * split the timestamp into three pieces which then reverse to `22:14:21` - a
 * valid-looking time that is not the one on the statement.
 */
object VisualOrder {

    /**
     * Characters the layout engine mirrors when it lays them out right-to-left.
     * Per Unicode UBA rule L4, mirroring applies to characters resolved as
     * right-to-left, and only to those.
     */
    private val MIRRORED: Map<Char, Char> = mapOf(
        '(' to ')', ')' to '(',
        '[' to ']', ']' to '[',
        '{' to '}', '}' to '{',
        '<' to '>', '>' to '<',
        Char(0x00AB) to Char(0x00BB),
        Char(0x00BB) to Char(0x00AB),
    )

    private enum class Direction { RTL, LTR, NEUTRAL }

    /**
     * Digits resolve left-to-right even inside Arabic text, which is why an amount
     * or an account number keeps its reading order in a right-to-left line.
     */
    private fun directionOf(ch: Char): Direction = when {
        ch.isDigit() -> Direction.LTR
        !ch.isLetter() -> Direction.NEUTRAL
        isArabicLetter(ch) -> Direction.RTL
        else -> Direction.LTR
    }

    private fun isArabicLetter(ch: Char): Boolean = when (ch.code) {
        in 0x0600..0x06FF, // Arabic
        in 0x0750..0x077F, // Arabic Supplement
        in 0x08A0..0x08FF, // Arabic Extended-A
        in 0xFB50..0xFDFF, // Presentation Forms-A
        in 0xFE70..0xFEFF, // Presentation Forms-B
        -> true

        else -> false
    }

    /**
     * @param visual one cell of PDF-extracted text, laid out visually. Multi-line
     *   cells are handled line by line, but the paragraph direction is decided for
     *   the cell as a whole - a purely Latin line inside an Arabic cell is still
     *   sitting in a right-to-left paragraph.
     */
    fun restore(visual: String): String {
        if (visual.isEmpty()) return visual
        val paragraph =
            if (visual.any { directionOf(it) == Direction.RTL }) Direction.RTL else Direction.LTR
        return visual.split('\n').joinToString("\n") { restoreLine(it, paragraph) }
    }

    private fun restoreLine(line: String, paragraph: Direction): String {
        if (line.isEmpty()) return line

        val resolved = resolveNeutrals(line, paragraph)

        // Merge into maximal same-direction segments.
        val segments = ArrayList<Pair<Direction, String>>()
        var start = 0
        while (start < line.length) {
            val direction = resolved[start]
            var end = start + 1
            while (end < line.length && resolved[end] == direction) end++
            segments.add(direction to line.substring(start, end))
            start = end
        }

        val ordered = if (paragraph == Direction.RTL) segments.asReversed() else segments
        val rebuilt = buildString(line.length) {
            for ((direction, segment) in ordered) {
                if (direction == Direction.RTL) {
                    // Presentation forms are still intact here, so a ligature
                    // reverses as the single unit the layout treated it as.
                    for (i in segment.indices.reversed()) {
                        val ch = segment[i]
                        append(MIRRORED[ch] ?: ch)
                    }
                } else {
                    append(segment)
                }
            }
        }

        // Only now: presentation forms back to base letters.
        return Normalizer.normalize(rebuilt, Normalizer.Form.NFKC)
    }

    /**
     * Assigns a direction to every character, giving each run of neutrals the
     * direction of its neighbours when they agree, and the paragraph direction when
     * they do not or when the run touches an edge.
     *
     * This is the whole reason `21:14:22` survives and `alarabyh est, MAKKAH, SA`
     * is not shredded into three reordered pieces.
     */
    private fun resolveNeutrals(line: String, paragraph: Direction): Array<Direction> {
        val resolved = Array(line.length) { directionOf(line[it]) }
        var i = 0
        while (i < resolved.size) {
            if (resolved[i] != Direction.NEUTRAL) {
                i++
                continue
            }
            var end = i
            while (end < resolved.size && resolved[end] == Direction.NEUTRAL) end++

            val before = (i - 1 downTo 0).firstOrNull { resolved[it] != Direction.NEUTRAL }
                ?.let { resolved[it] }
            val after = if (end < resolved.size) resolved[end] else null

            val direction = if (before != null && before == after) before else paragraph
            for (k in i until end) resolved[k] = direction
            i = end
        }
        return resolved
    }

    /**
     * True when the text contains Arabic Presentation Forms - the tell that it came
     * out of a PDF and needs [restore] rather than plain normalisation.
     *
     * Text typed by a user, or read from an SMS, never contains these, and running
     * [restore] over already-logical Arabic would reverse it.
     */
    fun looksVisuallyOrdered(text: String): Boolean =
        text.any { it.code in 0xFB50..0xFDFF || it.code in 0xFE70..0xFEFF }
}
