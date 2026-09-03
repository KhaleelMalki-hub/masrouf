package sa.masrouf.core.model

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The batch named from merchant strings on 2026-09-02, asserted against the exact
 * keys the unfiled history holds - truncated, gateway-prefixed, and all.
 */
class MerchantNames20260902Test {

    private fun cat(merchant: String) = CategoryGuess.forMerchant(merchant)

    @Test
    fun `chains and brands are filed from the string alone`() {
        assertEquals(SaudiCategories.FOOD, cat("TEXAS ROA"))
        // Boxed chocolate is food to keep - groceries, by the owner's rule.
        assertEquals(SaudiCategories.GROCERIES, cat("PATCHI"))
        assertEquals(SaudiCategories.GROCERIES, cat("AL NUKLY"))
        assertEquals(SaudiCategories.SHOPPING, cat("COLE HAAN JEDDAH PARK"))
        assertEquals(SaudiCategories.SHOPPING, cat("FOOT LOCK"))
        assertEquals(SaudiCategories.BILLS, cat("ANTHROPIC"))
        assertEquals(SaudiCategories.BILLS, cat("CLAUDE AI SUBSCRIPTION"))
        assertEquals(SaudiCategories.CHARITY, cat("KSRELIEF"))
        assertEquals(SaudiCategories.INVESTMENT, cat("DRAHIM APP"))
        assertEquals(SaudiCategories.TRAVEL, cat("AL HARAMAIN SPEED RAIL"))
    }

    /** "ZAKI OPTI" is what the network sends; the keyword is the whole name. */
    @Test
    fun `a truncated merchant matches a keyword that begins with it`() {
        assertEquals(SaudiCategories.HEALTH, cat("ZAKI OPTI"))
        assertEquals(SaudiCategories.HEALTH, cat("ESNAD HOS"))
        assertEquals(SaudiCategories.SHOPPING, cat("ALHOMAIDH"))
    }

    /** Qlub pays a restaurant's bill: "Q " is the only thing every one of them shares. */
    @Test
    fun `the restaurant-payment prefix files as eating out`() {
        assertEquals(SaudiCategories.FOOD, cat("Q KHAYAL MAKKAH QLUB S"))
        assertEquals(SaudiCategories.FOOD, cat("Q PLEO S"))
        assertEquals(SaudiCategories.FOOD, cat("QLU EATALYDUBAI"))
    }

    /** STATIONERY is a shop; STATION is petrol. The longer word must win. */
    @Test
    fun `stationery is not a petrol station`() {
        assertEquals(SaudiCategories.SHOPPING, cat("AL MAIMOUNI STATIONE"))
        assertEquals(SaudiCategories.SHOPPING, cat("AHAL ALQALAM STATIONAR"))
        assertEquals(SaudiCategories.TRANSPORT, cat("FOURTH RING STATION"))
        assertEquals(SaudiCategories.TRANSPORT, cat("EISO STAT"))
    }

    /** Al Qurashi sells perfume; Al Qurashi Gas sells petrol. */
    @Test
    fun `a gas station named after the perfume house is transport`() {
        assertEquals(SaudiCategories.TRANSPORT, cat("AL QURASHI GAS"))
        assertEquals(SaudiCategories.TRANSPORT, cat("MYSR AL QURASHE GAS"))
        assertEquals(SaudiCategories.SHOPPING, cat("IBRAHIM AL QURASHI SHO"))
    }

    @Test
    fun `payment gateways carry shops, and the food ones are named ahead of them`() {
        assertEquals(SaudiCategories.SHOPPING, cat("MF JILSTORE"))
        assertEquals(SaudiCategories.SHOPPING, cat("SP CHLOE A"))
        assertEquals(SaudiCategories.GROCERIES, cat("MF DATES"))
        assertEquals(SaudiCategories.SHOPPING, cat("TAP HALAUNIFORMS"))
        assertEquals(SaudiCategories.GROCERIES, cat("TAP TMRALBWADI"))
    }

    /**
     * A shop registered in its owner's name says nothing on its own; it stays
     * unfiled until he names it or the corpus does.
     *
     * "OBOUD BAH" was here until 2026-09-03, when two messages that were not from
     * the shop - a barq beneficiary activation and an SNB refund - spelled out
     * مؤسسة عبود باحشوان and he named the trade. It moved to
     * [ConfirmedMerchants20260902]; the ones below have had no such luck.
     */
    @Test
    fun `a personal name is not filed`() {
        assertNull(cat("MOHAMMED"))
        assertNull(cat("EST MUNIRAH SIDDIQUE"))
    }
}
