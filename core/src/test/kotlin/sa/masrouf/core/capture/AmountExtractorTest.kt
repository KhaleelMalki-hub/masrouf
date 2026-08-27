package sa.masrouf.core.capture

import org.junit.jupiter.api.Test
import sa.masrouf.core.fixtures.RealMessages
import sa.masrouf.core.money.Money
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AmountExtractorTest {

    private fun assertAmount(expectedMajor: String, body: String) {
        val candidate = AmountExtractor.extractOrNull(body)
        assertNotNull(candidate, "no amount extracted from:\n$body")
        assertEquals(
            Money.ofMajor(expectedMajor),
            candidate.money,
            "wrong amount extracted from:\n$body\n(matched: '${candidate.matchedText}')",
        )
    }

    // ---- The balance trap --------------------------------------------------

    /**
     * The charged amount and the resulting balance sit in the same message, and the
     * balance is the larger of the two. Picking the wrong one turns a 931.64
     * purchase into a 10,000.00 one and does not look like a bug anywhere.
     */
    @Test
    fun `the balance in the same message is never mistaken for the amount`() {
        assertAmount("931.64", RealMessages.RAJHI_ONLINE_PURCHASE)
        assertAmount("320", RealMessages.RAJHI_POS_LONG)
        assertAmount("10000", RealMessages.RAJHI_CARD_SETTLEMENT)
    }

    /** barq glues the balance straight onto its label with no separator: "رصيد15.18". */
    @Test
    fun `a balance glued to its label is still excluded`() {
        assertAmount("1.00", RealMessages.BARQ_ONLINE_PURCHASE)
    }

    /** A fees line follows the amount line immediately, and is itself an amount. */
    @Test
    fun `a fees line is never mistaken for the amount`() {
        assertAmount("2000.00", RealMessages.BARQ_TRANSFER_OUT)
    }

    // ---- Formatting variation across senders -------------------------------

    @Test
    fun `amount glued to the currency and the label is read correctly`() {
        // "بـSR 8.28"
        assertAmount("8.28", RealMessages.RAJHI_POS_SHORT)
        // "بـSAR 35"
        assertAmount("35", RealMessages.SNB_ONLINE_PURCHASE)
        // "بSAR 35"
        assertAmount("35", RealMessages.SNB_TRANSFER_IN)
    }

    @Test
    fun `thousands separators are handled`() {
        assertAmount("2850.00", RealMessages.D360_TRANSFER_IN)
    }

    /** D360 writes the currency after the number, as the Arabic word. */
    @Test
    fun `currency written after the number in Arabic is handled`() {
        assertAmount("2500.00", RealMessages.D360_OWN_ACCOUNTS_TRANSFER)
    }

    @Test
    fun `a single decimal place is handled`() {
        // "amount: 5000.0 SAR"
        assertAmount("5000.00", RealMessages.BARQ_TOPUP_EN)
    }

    @Test
    fun `english message bodies are handled`() {
        assertAmount("5000.00", RealMessages.BARQ_TOPUP_EN)
    }

    // ---- Numbers that are not amounts --------------------------------------

    @Test
    fun `card fragments and account numbers are not amounts`() {
        // "عبر2383;فيزا" / "مدى *1887" / "حساب 010*104" / "لحساب3016"
        assertAmount("931.64", RealMessages.RAJHI_ONLINE_PURCHASE)
        assertAmount("2000", RealMessages.SNB_ATM_DEPOSIT)
        assertAmount("2000", RealMessages.RAJHI_TRANSFER_IN)
    }

    @Test
    fun `a message with no amount yields nothing rather than a guess`() {
        assertNull(AmountExtractor.extractOrNull("تم تسجيل دخولك بنجاح"))
        assertNull(AmountExtractor.extractOrNull("Your login was successful at 14:41"))
        assertNull(AmountExtractor.extractOrNull(""))
    }

    // ---- Arabic-Indic input ------------------------------------------------

    @Test
    fun `arabic-indic digits with the arabic decimal separator are read correctly`() {
        assertAmount("87.50", "شراء عبر نقاط البيع\nمبلغ: ٨٧٫٥٠ ريال")
    }

    @Test
    fun `bidi marks inside a number do not break extraction`() {
        val rlm = 0x200F.toChar()
        val body = "شراء إنترنت بـ${rlm}SR 931.${rlm}64$rlm"
        assertAmount("931.64", body)
    }

    /** Every real completed transaction must yield exactly one best amount. */
    @Test
    fun `an amount is found in every real completed transaction`() {
        for (body in RealMessages.COMPLETED_TRANSACTIONS) {
            assertNotNull(
                AmountExtractor.extractOrNull(body),
                "no amount extracted from a real transaction:\n$body",
            )
        }
    }
}
