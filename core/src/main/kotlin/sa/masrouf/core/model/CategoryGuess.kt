package sa.masrouf.core.model

import sa.masrouf.core.text.ArabicText

/**
 * Suggests a category from a merchant name.
 *
 * This app refuses to guess amounts, and that rule is not being relaxed here: a
 * wrong amount is money the user never spent, while a wrong category is a filing
 * error they can see and fix in one tap. The costs are not comparable, so the
 * rules are not the same.
 *
 * What does carry over is the shape of the refusal. A merchant this list does not
 * recognise returns null - the record stays unfiled rather than being swept into
 * [SaudiCategories.OTHER], because "I have not decided" and "I decided it was
 * other" have to stay distinguishable. It is also why every suggestion lands on a
 * PENDING record the user is already being asked to look at.
 *
 * Matched against [ArabicText.normalizeMerchant], which is what the merchant key
 * is stored as - so the same folding that makes deduplication work makes this work
 * across the spelling and padding differences between a notification and an SMS.
 */
object CategoryGuess {

    /**
     * Keyword to category. Substring matches against the folded merchant.
     *
     * Written from merchants that actually appear in this user's messages plus the
     * chains any Saudi phone sees, not from an invented taxonomy of brands. An
     * entry earns its place by having been seen.
     */
    private val RULES: List<Pair<String, Category>> = listOf(
        // Groceries
        "TAMIMI" to SaudiCategories.GROCERIES,
        "PANDA" to SaudiCategories.GROCERIES,
        "OTHAIM" to SaudiCategories.GROCERIES,
        "DANUBE" to SaudiCategories.GROCERIES,
        "CARREFOUR" to SaudiCategories.GROCERIES,
        "LULU" to SaudiCategories.GROCERIES,
        "NINJA" to SaudiCategories.GROCERIES,
        "بقالة" to SaudiCategories.GROCERIES,
        "تموينات" to SaudiCategories.GROCERIES,

        // Eating out
        "STARBUCKS" to SaudiCategories.FOOD,
        "DUNKIN" to SaudiCategories.FOOD,
        "BARN" to SaudiCategories.FOOD,
        "HALF MILLION" to SaudiCategories.FOOD,
        "MCDONALD" to SaudiCategories.FOOD,
        "HERFY" to SaudiCategories.FOOD,
        "ALBAIK" to SaudiCategories.FOOD,
        "KUDU" to SaudiCategories.FOOD,
        "HUNGERSTATION" to SaudiCategories.FOOD,
        "JAHEZ" to SaudiCategories.FOOD,
        "TALABAT" to SaudiCategories.FOOD,
        "KEETA" to SaudiCategories.FOOD,
        "CAFE" to SaudiCategories.FOOD,
        "COFFEE" to SaudiCategories.FOOD,
        "RESTAURANT" to SaudiCategories.FOOD,
        "مطعم" to SaudiCategories.FOOD,
        "قهوة" to SaudiCategories.FOOD,
        "كافيه" to SaudiCategories.FOOD,

        // Transport
        "PETROMIN" to SaudiCategories.TRANSPORT,
        "ALDREES" to SaudiCategories.TRANSPORT,
        "SASCO" to SaudiCategories.TRANSPORT,
        "PETRO" to SaudiCategories.TRANSPORT,
        "UBER" to SaudiCategories.TRANSPORT,
        "CAREEM" to SaudiCategories.TRANSPORT,
        "BOLT" to SaudiCategories.TRANSPORT,
        "SAPTCO" to SaudiCategories.TRANSPORT,
        "محطة" to SaudiCategories.TRANSPORT,
        "بنزين" to SaudiCategories.TRANSPORT,
        "وقود" to SaudiCategories.TRANSPORT,

        // Bills and subscriptions
        "GOOGLE" to SaudiCategories.BILLS,
        "APPLE" to SaudiCategories.BILLS,
        "NETFLIX" to SaudiCategories.BILLS,
        "SPOTIFY" to SaudiCategories.BILLS,
        "YOUTUBE" to SaudiCategories.BILLS,
        "SHAHID" to SaudiCategories.BILLS,
        "STC" to SaudiCategories.BILLS,
        "MOBILY" to SaudiCategories.BILLS,
        "ZAIN" to SaudiCategories.BILLS,
        "SEC" to SaudiCategories.BILLS,
        "فاتورة" to SaudiCategories.BILLS,
        "سداد" to SaudiCategories.BILLS,
        "كهرباء" to SaudiCategories.BILLS,

        // Health
        "PHARMACY" to SaudiCategories.HEALTH,
        "NAHDI" to SaudiCategories.HEALTH,
        "DAWAA" to SaudiCategories.HEALTH,
        "POLYCLINI" to SaudiCategories.HEALTH,
        "CLINIC" to SaudiCategories.HEALTH,
        "HOSPITAL" to SaudiCategories.HEALTH,
        "MEDICAL" to SaudiCategories.HEALTH,
        "صيدلية" to SaudiCategories.HEALTH,
        "مستشفى" to SaudiCategories.HEALTH,
        "عيادة" to SaudiCategories.HEALTH,

        // Shopping
        "AMAZON" to SaudiCategories.SHOPPING,
        "NOON" to SaudiCategories.SHOPPING,
        "IHERB" to SaudiCategories.SHOPPING,
        "SHEIN" to SaudiCategories.SHOPPING,
        "NAMSHI" to SaudiCategories.SHOPPING,
        "IKEA" to SaudiCategories.SHOPPING,
        "JARIR" to SaudiCategories.SHOPPING,
        "EXTRA" to SaudiCategories.SHOPPING,
        "CENTREPOINT" to SaudiCategories.SHOPPING,
        "HM" to SaudiCategories.SHOPPING,
    )

    /**
     * @return a suggested category, or null when nothing matches. Callers must
     *   leave a null unfiled rather than defaulting it.
     */
    fun forMerchant(merchantRaw: String?): Category? {
        val folded = merchantRaw?.let(ArabicText::normalizeMerchant)?.takeIf { it.isNotBlank() }
            ?: return null
        // First match wins, and the list is ordered so the specific sits above the
        // general - "HUNGERSTATION" before any bare "STATION" rule would be.
        return RULES.firstOrNull { (keyword, _) ->
            folded.contains(ArabicText.normalizeMerchant(keyword))
        }?.second
    }

    /** A transaction type can decide a category on its own when the merchant cannot. */
    fun forType(type: TransactionType): Category? = when (type) {
        TransactionType.BILL_PAYMENT -> SaudiCategories.BILLS
        TransactionType.TRANSFER_OUT, TransactionType.TRANSFER_IN -> SaudiCategories.TRANSFERS
        else -> null
    }

    /** Merchant first, then type. Null when neither knows. */
    fun suggest(merchantRaw: String?, type: TransactionType): Category? =
        forMerchant(merchantRaw) ?: forType(type)
}
