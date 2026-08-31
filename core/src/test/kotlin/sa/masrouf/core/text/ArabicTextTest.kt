package sa.masrouf.core.text

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ArabicTextTest {

    @Test
    fun `arabic-indic digits become ascii`() {
        assertEquals("0123456789", ArabicText.normalizeDigits("٠١٢٣٤٥٦٧٨٩"))
    }

    @Test
    fun `eastern arabic-indic digits become ascii`() {
        assertEquals("0123456789", ArabicText.normalizeDigits("۰۱۲۳۴۵۶۷۸۹"))
    }

    @Test
    fun `mixed digit systems inside one number are unified`() {
        assertEquals("8750", ArabicText.normalizeDigits("٨7٥0"))
    }

    @Test
    fun `arabic numeric separators become ascii`() {
        val arabic = "1٬234٫56"
        assertEquals("1,234.56", ArabicText.normalizeNumericSeparators(arabic))
    }

    @Test
    fun `bidi controls and zero-width characters are removed`() {
        val withMarks = "‏شراء‎ ​SAR‪ 35‬﻿"
        assertEquals("شراء SAR 35", ArabicText.normalize(withMarks))
    }

    @Test
    fun `tatweel is removed so elongated words match plain ones`() {
        assertEquals("ريال", ArabicText.normalize("ريـــال"))
    }

    @Test
    fun `non-breaking spaces are collapsed like ordinary spaces`() {
        assertEquals("SAR 35", ArabicText.normalize("SAR  35"))
    }

    @Test
    fun `newlines are preserved but each line is trimmed`() {
        assertEquals("شراء\nSAR 35", ArabicText.normalize("  شراء  \n   SAR   35  "))
    }

    @Test
    fun `the full pipeline handles a realistic glued arabic amount`() {
        val raw = "‏مبلغ‏٢٠٠٠٫٠٠SAR"
        assertEquals("مبلغ2000.00SAR", ArabicText.normalize(raw))
    }

    // ---- Matching-only folding ---------------------------------------------

    @Test
    fun `folding unifies alef and yeh spelling variants`() {
        assertEquals(
            ArabicText.foldForMatching("مكتبى"),
            ArabicText.foldForMatching("مكتبي"),
        )
        assertEquals(
            ArabicText.foldForMatching("إيداع"),
            ArabicText.foldForMatching("ايداع"),
        )
    }

    @Test
    fun `folding removes punctuation and uppercases latin text`() {
        assertEquals("IHERB ARABIA CO", ArabicText.foldForMatching("iHerb Arabia Co."))
    }

    @Test
    fun `folding strips diacritics`() {
        assertEquals(
            ArabicText.foldForMatching("شراء"),
            ArabicText.foldForMatching("شِراء"),
        )
    }

    // ---- Merchant keys -----------------------------------------------------

    /**
     * The same purchase reaches the app twice: once from a push notification and
     * once, later, from a downloaded statement. The statement pads the merchant
     * with location and reference codes. If those survive into the key, one
     * purchase looks like two.
     */
    @Test
    fun `merchant keys ignore the numeric and country-code tail cards append`() {
        assertEquals(
            ArabicText.normalizeMerchant("TAMIMI MARKETS RIYADH"),
            ArabicText.normalizeMerchant("TAMIMI MARKETS RIYADH SA 0012"),
        )
        assertEquals(
            ArabicText.normalizeMerchant("IHERB ARA"),
            ArabicText.normalizeMerchant("IHERB ARA 884213"),
        )
    }

    /**
     * A trailing *word* is deliberately NOT stripped, even when it looks like a
     * city. `ASIAN POLYCLINI` and `ASIAN AZIZIA` are two different clinics that
     * differ only in their last word, so dropping it would merge unrelated
     * merchants - a far worse error than failing to merge two spellings of one.
     *
     * Whether real statement rows pad merchants with a city name is still unknown;
     * no statement sample has been seen yet. Revisit once one has, with the rows in
     * hand rather than on a guess.
     */
    @Test
    fun `merchant keys keep a trailing word even when it looks like a city`() {
        assertEquals(
            false,
            ArabicText.normalizeMerchant("TAMIMI MARKETS") ==
                ArabicText.normalizeMerchant("TAMIMI MARKETS RIYADH"),
        )
    }

    @Test
    fun `merchant keys survive case and punctuation differences`() {
        assertEquals(
            ArabicText.normalizeMerchant("iHerb Arabia Co."),
            ArabicText.normalizeMerchant("IHERB ARABIA CO"),
        )
    }

    @Test
    fun `merchant keys do not collapse genuinely different merchants`() {
        val asianPolyclinic = ArabicText.normalizeMerchant("ASIAN POLYCLINI")
        val asianAzizia = ArabicText.normalizeMerchant("ASIAN AZIZIA")
        assertEquals(false, asianPolyclinic == asianAzizia)
    }
}
