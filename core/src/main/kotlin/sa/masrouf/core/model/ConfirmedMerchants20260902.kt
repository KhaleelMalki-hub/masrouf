package sa.masrouf.core.model

import sa.masrouf.core.model.SaudiCategories.BILLS
import sa.masrouf.core.model.SaudiCategories.CHARITY
import sa.masrouf.core.model.SaudiCategories.ENTERTAINMENT
import sa.masrouf.core.model.SaudiCategories.FOOD
import sa.masrouf.core.model.SaudiCategories.GROCERIES
import sa.masrouf.core.model.SaudiCategories.HEALTH
import sa.masrouf.core.model.SaudiCategories.INVESTMENT
import sa.masrouf.core.model.SaudiCategories.SHOPPING
import sa.masrouf.core.model.SaudiCategories.TRANSPORT

/**
 * Merchants identified by web search on 2026-09-02 and CONFIRMED by the owner.
 *
 * The strings said nothing on their own - a registered company name, a
 * truncation, a gateway prefix - so each was looked up (website, listing, news)
 * and the owner then went through the list and marked the ones he recognised.
 * Only those are here. The ones he doubted stay on his worksheet.
 *
 * Keyed on the string the terminal sends, truncation and all, like every list
 * in [CategoryGuess].
 */
object ConfirmedMerchants20260902 {

    val ENTRIES: List<Pair<String, Category>> = listOf(
        // Eating out
        "NOZOMI" to FOOD, "MAISONDE ZAID" to FOOD, "KEIKEN" to FOOD, "KERMAL" to FOOD,
        "SHOBAK" to FOOD, "BLANCA PI" to FOOD, "BUTCHER" to FOOD, "JAMM" to FOOD,
        "UPTOWN" to FOOD, "SARAYA LATEEF" to FOOD,
        // Food to keep
        "HALA BOSTANI" to GROCERIES, "MWASHIMKH" to GROCERIES, "WADIMANUKA" to GROCERIES,
        "SAUDI ALLIED" to GROCERIES, "CAPSOUL" to GROCERIES, "AVOKADO" to GROCERIES,
        // سعد الدين under its registered name, "MOHAMMED ALI SAAD ALDEN C".
        "SAAD ALDE" to GROCERIES,
        // Shops
        "TOP TATO" to SHOPPING, "AZHAR CHERRY" to SHOPPING, "ALYASRA" to SHOPPING,
        "ABDULGHAN" to SHOPPING, "LAGATESA" to SHOPPING, "EMIRATISBAIT" to SHOPPING,
        "WEFT" to SHOPPING, "KORASHI" to SHOPPING, "MY FAIR LADY" to SHOPPING,
        "BLENDS" to SHOPPING, "MULTI TREND" to SHOPPING, "BEDON ESSM" to SHOPPING,
        "BESIDE TRADING" to SHOPPING, "BLUE AGE" to SHOPPING, "LA VIE" to SHOPPING,
        "NICHI" to SHOPPING, "SSS SALAM" to SHOPPING, "FREESIA" to SHOPPING,
        "ALLIED ENTERPRISES" to SHOPPING, "F F" to SHOPPING, "CLARAHAIR" to SHOPPING,
        "BADR HARQAN" to SHOPPING, "ZIDDY" to SHOPPING,
        // Al Qurashi's factory, under the founder's name: soap and personal care.
        "ABDULAZIZ IBRAHIM ALQU" to SHOPPING,
        // The rest
        "STARS SMI" to HEALTH,
        "SPARKY" to ENTERTAINMENT, "DAINTREE" to ENTERTAINMENT,
        "SCOPEER" to INVESTMENT,
        "BEST SHIE" to TRANSPORT,
        "ERWAA" to CHARITY,
        "GAZ ALTAHASOSI" to BILLS,
    )
}
