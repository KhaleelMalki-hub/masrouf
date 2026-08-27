package sa.masrouf.core.text

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Cells taken verbatim from real bank statement PDFs.
 *
 * The inputs are written as code points rather than literals on purpose. Arabic
 * Presentation Forms render as ordinary Arabic in an editor, so a literal here
 * would look identical to correctly-ordered text while being the opposite - and a
 * test whose input silently becomes its expected output proves nothing.
 */
class VisualOrderTest {

    private fun codePoints(vararg points: Int): String =
        buildString { points.forEach { append(it.toChar()) } }

    /** "عملية شراء عبر الإنترنت" as stored by SNB's statement generator. */
    private val onlinePurchase = codePoints(
        0xFE96, 0xFEE7, 0xFEAE, 0xFE98, 0xFEE7, 0xFEF9, 0xFE8D, 0x0020,
        0xFEAE, 0xFE92, 0xFECB, 0x0020, 0x0621, 0xFE8D, 0xFEAE, 0xFEB7, 0x0020,
        0xFE94, 0xFEF4, 0xFEE0, 0xFEE4, 0xFECB,
    )

    private val transferIn = codePoints(
        0xFEA9, 0xFEAD, 0xFE8D, 0xFEED, 0x0020,
        0xFEF2, 0xFEE0, 0xFEA7, 0xFE8D, 0xFEA9, 0x0020,
        0xFEDE, 0xFEF3, 0xFEEE, 0xFEA4, 0xFE97,
    )

    private val transferOut = codePoints(
        0xFEAD, 0xFEA9, 0xFE8E, 0xFEBB, 0x0020,
        0xFEF2, 0xFEE0, 0xFEA7, 0xFE8D, 0xFEA9, 0x0020,
        0xFEDE, 0xFEF3, 0xFEEE, 0xFEA4, 0xFE97,
    )

    /** An Arabic channel name with a trailing branch code: "الأهلي موبايل0828". */
    private val mobileChannel = codePoints(
        0x0030, 0x0038, 0x0032, 0x0038,
        0xFEDE, 0xFEF3, 0xFE8E, 0xFE91, 0xFEEE, 0xFEE3, 0x0020,
        0xFEF2, 0xFEE0, 0xFEEB, 0xFEF7, 0xFE8D,
    )

    /** A label followed by a ten-digit identifier: "رقم الهوية:1123854760". */
    private val idNumber = codePoints(
        0x0031, 0x0031, 0x0032, 0x0033, 0x0038, 0x0035, 0x0034, 0x0037, 0x0036, 0x0030,
        0x003A,
        0xFE94, 0xFEF3, 0xFEEE, 0xFEEC, 0xFEDF, 0xFE8D, 0x0020,
        0xFEE2, 0xFED7, 0xFEAD,
    )

    @Test
    fun `arabic transaction types come back in logical order`() {
        assertEquals("تحويل داخلي وارد", VisualOrder.restore(transferIn))
        assertEquals("تحويل داخلي صادر", VisualOrder.restore(transferOut))
    }

    /**
     * The lam-alef case. Decomposing before reversing turns "الإنترنت" into
     * "اإلنترنت" - still Arabic-looking, and wrong.
     */
    @Test
    fun `a lam-alef ligature survives the reversal`() {
        assertEquals("عملية شراء عبر الإنترنت", VisualOrder.restore(onlinePurchase))
    }

    /**
     * The case that rules out reversing the whole string.
     *
     * A statement cell is full of numbers that identify things: account fragments,
     * reference numbers, national ids, branch codes. Reversing the line reverses
     * those too - 0828 becomes 8280, and 1123854760 becomes 0674583211 - producing
     * identifiers that are wrong but perfectly plausible.
     */
    @Test
    fun `digits keep their own order`() {
        assertEquals("الأهلي موبايل0828", VisualOrder.restore(mobileChannel))
        assertEquals("رقم الهوية:1123854760", VisualOrder.restore(idNumber))
    }

    /**
     * A merchant line inside an Arabic statement cell, stored exactly as it appears
     * here - Latin runs are written in logical order by the layout engine, not
     * reversed. Reversing the line, or reordering its words, corrupts it.
     */
    @Test
    fun `latin merchant lines pass through untouched`() {
        assertEquals(
            "alarabyh est, MAKKAH, SA",
            VisualOrder.restore("alarabyh est, MAKKAH, SA"),
        )
    }

    /**
     * The neutral-resolution case. The colons in "21:14:22" sit between digits, so
     * they belong to the timestamp. Treated as standalone neutrals they split it
     * into three pieces which reverse to "22:14:21" - a valid-looking time that is
     * not the one printed on the statement.
     */
    @Test
    fun `a timestamp is not reordered by its own colons`() {
        // "21:14:22:" followed by the visual form of "الوقت"
        val visual = "21:14:22:" + codePoints(0xFE96, 0xFED7, 0xFEEE, 0xFEDF, 0xFE8D)
        assertEquals("الوقت:21:14:22", VisualOrder.restore(visual))
    }

    @Test
    fun `each line is restored independently`() {
        val twoLines = transferIn + "\n" + transferOut
        assertEquals("تحويل داخلي وارد\nتحويل داخلي صادر", VisualOrder.restore(twoLines))
    }

    @Test
    fun `empty and blank input is returned unchanged`() {
        assertEquals("", VisualOrder.restore(""))
        assertEquals("   ", VisualOrder.restore("   "))
    }

    // ---- Knowing when to apply it ------------------------------------------

    @Test
    fun `presentation forms are detected as needing restoration`() {
        assertTrue(VisualOrder.looksVisuallyOrdered(onlinePurchase))
    }

    /**
     * Text from an SMS or typed by the user is already in logical order. Running
     * restore over it would reverse perfectly good Arabic, so the two paths must be
     * distinguishable.
     */
    @Test
    fun `ordinary arabic is not mistaken for visually ordered text`() {
        assertFalse(VisualOrder.looksVisuallyOrdered("عملية شراء عبر الإنترنت"))
        assertFalse(VisualOrder.looksVisuallyOrdered("شراء إنترنت بـSR 931.64"))
        assertFalse(VisualOrder.looksVisuallyOrdered("Money Added to your Barq wallet"))
    }

    /** Restoring already-logical text must be recognisably destructive, not silent. */
    @Test
    fun `restoring twice returns the original`() {
        val once = VisualOrder.restore(transferIn)
        assertEquals("تحويل داخلي وارد", once)
        assertEquals(once, VisualOrder.restore(VisualOrder.restore(once)))
    }
}
