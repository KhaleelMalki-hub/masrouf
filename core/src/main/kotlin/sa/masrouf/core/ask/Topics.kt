package sa.masrouf.core.ask

import sa.masrouf.core.model.MerchantMatch

/**
 * A thing people ask about that the app does not have a category for.
 *
 * "How much did I spend on petrol last month" is the question that forced this to
 * exist. Petrol is not a category and must not be answered with one: `transport`
 * on this history holds car servicing, a tyre shop, two spare-part suppliers,
 * parking, a vehicle tracker and a car wash as well as fuel, and answering with the
 * category total over-reports the petrol by roughly three times. A category is what
 * the app files a row under; a topic is what a person means.
 *
 * ## Why these keywords and not a pattern
 *
 * The obvious rule for fuel is "anything with STATION in it". On the owner's real
 * merchant list that takes **HUNGERSTATION** - a food-delivery app with a thousand
 * records and ninety-eight thousand riyals - and also JUICES STATION, and BREW 92
 * ALZAIDI, a coffee shop inside a filling station. So a topic is a list of BRANDS,
 * each one checked against the whole merchant list before it is added, exactly like
 * a category keyword. `TopicsTest` holds the names that must never be reached.
 *
 * Matching runs through [MerchantMatch], the same machinery the shipped merchant
 * list uses, so a topic inherits the truncation rule and the whole-word rule for
 * short keywords rather than inventing a second way to compare a merchant.
 */
enum class Topic(val keywords: List<String>) {

    /**
     * Filling stations only. Deliberately NOT the transport category.
     *
     * "ALZAIDI" alone is not here: الزيدي names a filling station in Makkah, and
     * also a coffee shop and a car-service garage that sit beside it. Only the
     * spellings that carry STATION with it are safe, and they are listed one by one.
     */
    FUEL(
        listOf(
            "ALDREES", "AL DREES",
            "SASCO",
            "NAFT SERV",
            "TOTAL ENE",
            "PETROMIN",
            "ALZAIDI STATION", "MAKKAH 12 ALZAIDI",
        ),
    ),

    /** Coffee, as a drink bought out - not the beans on a grocery run. */
    COFFEE(
        listOf(
            "BREW 92", "ATLAS ROA", "ATLASROAS", "ATLASROASTE",
            "BARN", "DUNKIN", "STARBUCKS", "TIM HORTONS",
            "ADDRESS COFFEE", "MIRAQE GATEWAY",
        ),
    ),

    /**
     * Food ordered to the door, which is a habit people ask about by itself.
     *
     * Ninja is deliberately absent. It delivers a supermarket basket rather than a
     * restaurant meal, its sixty records here file as groceries and its own name is
     * "Ninja Retail Company" - so counting it here made one app answer "how much on
     * delivery" and "how much on groceries" with the same riyals, and neither
     * number looked wrong on its own page.
     */
    DELIVERY(
        listOf("HUNGERSTATION", "KEETA", "JAHEZ", "MRSOOL", "TALABAT"),
    ),

    /**
     * A pharmacy counter.
     *
     * "INNOVATIVE" was here and is not any more. Nothing in this history or outside
     * it establishes that Innovative Union Company is a pharmacy, and its four
     * records carry no category rule of their own - so the ask screen counted them
     * as pharmacy spending while the strip filed them by transaction type. Naming a
     * merchant in a topic is a claim to know what it is, and this was a belief.
     */
    PHARMACY(
        listOf("NAHDI", "NMC2", "NMC8", "DAWAA", "ALDAWAA", "WHITES"),
    ),
    ;

    private val rules = MerchantMatch.Rules(keywords.map { it to this })

    /** Whether a stored merchant belongs to this topic. */
    fun claims(merchantRaw: String?): Boolean = MerchantMatch.firstMatch(merchantRaw, rules) != null

    companion object {
        /** Every merchant keyword any topic claims, for the query the screen runs. */
        fun claiming(merchantRaw: String?): Topic? = entries.firstOrNull { it.claims(merchantRaw) }
    }
}
