package sa.masrouf.core.model

import sa.masrouf.core.model.SaudiCategories.BILLS
import sa.masrouf.core.model.SaudiCategories.CHARITY
import sa.masrouf.core.model.SaudiCategories.ENTERTAINMENT
import sa.masrouf.core.model.SaudiCategories.FOOD
import sa.masrouf.core.model.SaudiCategories.GROCERIES
import sa.masrouf.core.model.SaudiCategories.HEALTH
import sa.masrouf.core.model.SaudiCategories.INVESTMENT
import sa.masrouf.core.model.SaudiCategories.SERVICES
import sa.masrouf.core.model.SaudiCategories.SHOPPING
import sa.masrouf.core.model.SaudiCategories.TRANSPORT
import sa.masrouf.core.model.SaudiCategories.TRAVEL

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

        // ---- The second wave, searched and then confirmed the same evening ----
        //
        // Same method and same gate: each string was looked up, and only what the
        // owner recognised is here. Two of them he corrected outright, and those
        // two are the reason the gate exists - the search had both wrong.

        // Eating out
        // Covers "SHRIMP ZO", "SHRIMP ZONE" and "SHRIMP AREA" (منطقة الجمبري),
        // three spellings of the same kind of place.
        "SHRIMP" to FOOD,
        "ROKON ALJAMBARY" to FOOD,
        "ALKOFEIA" to FOOD, "KAWKAB MUSATTAH" to FOOD, "OVER JAR" to FOOD,
        "AGAVE" to FOOD, "ONS LOUNG" to FOOD, "BOCU" to FOOD, "C HUB" to FOOD,
        "C HOUSE" to FOOD, "NEURON" to FOOD, "QAMREYA" to FOOD, "SIBLINGS" to FOOD,
        "LOADED TRUE" to FOOD, "MAWRED ALBARAKA" to FOOD, "SSP ARABI" to FOOD,
        // الاتحاد المبتكرة, an Alhokair company that runs restaurants. Only the
        // full name: the bare "INNOVATIV" in this history could be anything.
        "INNOVATIVE UNION" to FOOD, "INNOVATION UNION" to FOOD,
        // مطعم تكوة - the owner's correction. The search had it as a shop.
        "TAKWAH" to FOOD,

        // Food to keep
        "ALGHARBIS" to GROCERIES, "BAIDAALHIJAZ" to GROCERIES,
        "TALAH AL JOOD" to GROCERIES, "NATWAN" to GROCERIES, "BAFAR" to GROCERIES,

        // Giving
        "JOODESKAN" to CHARITY, "BIR GHOMAIGAH" to CHARITY, "FURIJAT" to CHARITY,
        "ABNAWUNA" to CHARITY, "IRQAHORG" to CHARITY,

        // Shops
        "ALRUGAI" to SHOPPING, "BAYTONIA" to SHOPPING, "DESARKSA" to SHOPPING,
        "FG4 KIDS" to SHOPPING, "THEIDEALHAIR" to SHOPPING, "BARLLINA" to SHOPPING,
        "TARHATI" to SHOPPING, "SHALFA" to SHOPPING, "ZMAKAN" to SHOPPING,
        "NEO HAIRLOTION" to SHOPPING, "AL HIJAZ COMMERCIAL" to SHOPPING,
        "RINA MALL" to SHOPPING, "RINA HAIFA" to SHOPPING,

        // The rest
        "RWAEA ALMARAH" to ENTERTAINMENT, "PRIMERA" to ENTERTAINMENT,
        "KABLI" to TRANSPORT, "ONE STOP FM" to TRANSPORT,
        // "شكلها رسوم مواقف المول" - the owner's reading of a charge billed by the
        // mall itself, and the reason this is transport rather than the shopping
        // the search assumed.
        "MALL OF ARABIA" to TRANSPORT,

        // ---- The third wave -------------------------------------------------
        //
        // Ninety strings searched, twenty answered: what is left in this history
        // is establishments registered in a person's name, and no search reaches
        // those. The owner took everything the search was confident about.
        "ADDIDAS" to SHOPPING, "ADIDAS" to SHOPPING, "DEER FOOT" to SHOPPING,
        // لازورد on Sitteen Street: chocolate, and the wedding carts an engagement
        // is announced with. His single record is 5,600 riyals - a cart, not a box
        // - so it files as shopping rather than under the groceries rule that
        // covers boxed chocolate. One shop, one category, and this is the purchase
        // the history actually holds.
        "LAZWARD" to SHOPPING,
        "SPEED TRACK" to TRANSPORT,
        "ROUTE" to FOOD, "ALZAWAQA" to FOOD, "BONON" to FOOD, "MEDIUM WE" to FOOD,
        "TAKKA EXPRESS" to FOOD, "DAMMAH" to FOOD, "THE CODE" to FOOD,
        "SANAR" to HEALTH,
        "HALA YALA" to ENTERTAINMENT, "KL TOWER" to ENTERTAINMENT,
        "RED SEA BEACH" to ENTERTAINMENT,
        "ALAIZDIHA" to SERVICES,

        // ---- Recovered from the inbox, not from the web ----------------------
        //
        // The confirmation message truncates the merchant to nine or ten
        // characters; the one-time-password message for the SAME purchase spells
        // it out. The gate refuses those bodies and always will - they carry a
        // credential - but the phone's inbox still holds them, and reading the
        // NAME out of a message the app must never store costs nothing.
        //
        // "لدى:AL RASHED" in the confirmation, "لدى:AL RASHED TIRES COMPANY LLC"
        // in the code message thirty seconds earlier. Same amount, same card, same
        // minute. The owner guessed the tyre shop before this was found, and it
        // agrees with him.
        //
        // Two ways of reading a full name out of the inbox, and they are not
        // equally strong. Matching a code message to a purchase by AMOUNT, CARD
        // and MINUTE identifies that purchase. Merely finding a longer string that
        // starts with the truncation identifies nothing: "Karam" prefix-matched
        // "KARAM BEIRUT" while its own code message said SALLA APP, and the
        // keyword it would have justified claimed أجواد الكرم, a grocery. Only the
        // matched kind is trusted below; the prefix kind was put to the owner.
        "AL RASHED" to TRANSPORT,
        // "TECHNICAL INDSPECTION" - the periodic vehicle inspection, spelled as
        // the terminal spells it.
        "TECHNICAL" to TRANSPORT,
        // "Bader Ch.." is Bader CHARITY, not the chocolate factory a web search
        // offered with low confidence. The corpus outranks the search.
        "BADER CH" to CHARITY,
        "SAUDI ARABIAN" to TRAVEL,
        // "SAEED ALI MORSH" is the health endowment fund, which the code message
        // names in full and the confirmation does not.
        "SAEED ALI MORSH" to CHARITY,
        "AL AHLIA" to FOOD,
        "AHMED ARA" to GROCERIES,
        "TAP TAIBA" to SHOPPING, "TAIBAHGIFTS" to SHOPPING,
        // سيتي دبليو - furniture, and the owner named it before the inbox
        // confirmed it three ways: a sender called "CityW" thanking him for
        // choosing "شركة سيتي دبليو" and asking him to rate the DELIVERY, an
        // AlJazira instalment offer that lists it beside المطلق, كرييتف هوم and
        // روومز - furniture retailers all - and three purchases in Jeddah of
        // 1,925, 3,654 and 9,730 riyals, which is what furniture costs.
        //
        // The earlier sweep of the inbox missed it: it searched for "سيتي ويندو",
        // a transliteration of the English string, and the shop writes itself
        // "سيتي دبليو". A brand's own Arabic name is not always a transliteration
        // of the one the terminal sends.
        "CITY WINDOW" to SHOPPING,
    )
}
