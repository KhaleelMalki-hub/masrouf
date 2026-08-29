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
        // ananinja.com, cut by the card network before the NINJA the rule above
        // looks for: the same 109 orders arrive as "www.anani". Restaurant orders
        // placed through it are groceries too, by the user's own decision - one
        // shop, one category, rather than a split nothing in the message supports.
        "ANANI" to SaudiCategories.GROCERIES,
        "بقالة" to SaudiCategories.GROCERIES,
        "تموينات" to SaudiCategories.GROCERIES,

        // Eating out
        "STARBUCKS" to SaudiCategories.FOOD,
        "DUNKIN" to SaudiCategories.FOOD,
        "POTTERY BARN" to SaudiCategories.SHOPPING,
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
        // صيدلية النور. The trailing letter is deliberate: it is where the card
        // network cuts the name, and it is what separates this pharmacy from
        // "Noor AlMaabadi", a laundry with 28 records of its own. "AL NOOR"
        // alone would file both.
        "AL NOOR T" to SaudiCategories.HEALTH,
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
        "H M" to SaudiCategories.SHOPPING,
        "SALLA" to SaudiCategories.SHOPPING,
        "ARAMEX" to SaudiCategories.SHOPPING,
        "SMSA" to SaudiCategories.SHOPPING,
        "ALIEXPRESS" to SaudiCategories.SHOPPING,
        "TEMU" to SaudiCategories.SHOPPING,
        "DUKAN" to SaudiCategories.SHOPPING,

        // ---- Added from a real 1,925-merchant list ------------------------
        //
        // Every entry below was taken from merchants that actually appear in this
        // user's twelve years of messages, ordered by how many transactions each
        // accounts for. None of them are guesses at what a Saudi merchant list
        // might contain.

        // Charity and endowment - the largest single miss, 738 transactions.
        "ENDOWMENT" to SaudiCategories.CHARITY,
        "CHARITY" to SaudiCategories.CHARITY,
        "WAQF" to SaudiCategories.CHARITY,
        "NAMAA" to SaudiCategories.CHARITY,
        "ALTAHAJJUD" to SaudiCategories.CHARITY,
        "EHSAN" to SaudiCategories.CHARITY,
        "جمعية" to SaudiCategories.CHARITY,
        "خيرية" to SaudiCategories.CHARITY,
        "وقف" to SaudiCategories.CHARITY,
        "صدقة" to SaudiCategories.CHARITY,
        "زكاة" to SaudiCategories.CHARITY,
        "تبرع" to SaudiCategories.CHARITY,

        // Delivery and food, which arrive truncated as often as not.
        "MRSOOL" to SaudiCategories.FOOD,
        "LUGMETY" to SaudiCategories.FOOD,
        "TEXAS CHICKEN" to SaudiCategories.FOOD,
        "HEALTHY PIE" to SaudiCategories.FOOD,
        "JUICES" to SaudiCategories.FOOD,
        "MOVENPICK" to SaudiCategories.FOOD,
        "BUNS" to SaudiCategories.FOOD,
        "BAKERY" to SaudiCategories.FOOD,
        "SHAWARMA" to SaudiCategories.FOOD,
        "BROAST" to SaudiCategories.FOOD,
        "PIZZA" to SaudiCategories.FOOD,
        "BURGER" to SaudiCategories.FOOD,
        "SUSHI" to SaudiCategories.FOOD,
        "مخبز" to SaudiCategories.FOOD,
        "حلويات" to SaudiCategories.FOOD,
        "بوفيه" to SaudiCategories.FOOD,

        // Utilities and government billers.
        "SAUDI TELECOM" to SaudiCategories.BILLS,
        "SAUDI ELECTRICITY" to SaudiCategories.BILLS,
        "SADAD" to SaudiCategories.BILLS,
        "WATER" to SaudiCategories.BILLS,
        "ABSHER" to SaudiCategories.BILLS,
        "TAWAKKALNA" to SaudiCategories.BILLS,
        "MOBILE" to SaudiCategories.BILLS,
        "كهرباء" to SaudiCategories.BILLS,
        "مياه" to SaudiCategories.BILLS,

        // Groceries and household supply.
        "BERAIN" to SaudiCategories.GROCERIES,
        "SHARBATLY" to SaudiCategories.GROCERIES,
        "CORNER GOODS" to SaudiCategories.GROCERIES,
        "SUPERMARKET" to SaudiCategories.GROCERIES,
        "MARKET" to SaudiCategories.GROCERIES,
        "FRUIT" to SaudiCategories.GROCERIES,
        "خضار" to SaudiCategories.GROCERIES,
        "فواكه" to SaudiCategories.GROCERIES,

        // Health.
        "DR " to SaudiCategories.HEALTH,
        "DENTAL" to SaudiCategories.HEALTH,
        "LAB" to SaudiCategories.HEALTH,
        "طبي" to SaudiCategories.HEALTH,
        "مختبر" to SaudiCategories.HEALTH,

        // Services that belong nowhere else, named so they stop sitting unfiled.
        // "LAUNDR", not "LAUNDRY": the card network truncates, and one shop was
        // arriving as both "AL QIMMA LAUNDRY" and "AL QIMMA LAUNDR". The full
        // spelling matched here and the short one fell through to a grocery rule
        // further down, so the same laundry was filed under two categories.
        "LAUNDR" to SaudiCategories.SERVICES,
        // A petrol station, named by the user. Thirty-eight fill-ups between 55 and
        // 103 riyals, weekly and then fortnightly: the shape reads as a recurring
        // delivery of a variable quantity of one thing, which is what filling a
        // tank is. "EMDAD ALK" and not the full name, because the card network
        // sends both and the shorter one is the truncation of the longer.
        "EMDAD ALK" to SaudiCategories.TRANSPORT,
        "NOOR ALMA" to SaudiCategories.SERVICES,
        "MGHASL" to SaudiCategories.SERVICES,
        "مغسلة" to SaudiCategories.SERVICES,
        "مغاسل" to SaudiCategories.SERVICES,
        "BARBER" to SaudiCategories.SERVICES,
        "SALON" to SaudiCategories.SERVICES,
        "SALOON" to SaudiCategories.SERVICES,
        "حلاق" to SaudiCategories.SERVICES,

        // Wallets and the user's own name on a transfer: money moving between
        // places they control, not spending on anything.
        "BARQ" to SaudiCategories.TRANSFERS,
        // Both named by the user off their own history: D360 is a wallet, and
        // CASH TRANSFER is a transfer, not a purchase. 33 and 28 records.
        "D360" to SaudiCategories.TRANSFERS,
        "CASH TRANSFER" to SaudiCategories.TRANSFERS,
        "STCPAY" to SaudiCategories.TRANSFERS,
        "URPAY" to SaudiCategories.TRANSFERS,
        "بطاقه مدي" to SaudiCategories.TRANSFERS,
        "بطاقه ايتمانيه" to SaudiCategories.TRANSFERS,
        "بطاقة ائتمانية" to SaudiCategories.TRANSFERS,

        // Schools, and the fees and wages that are neither a purchase nor a
        // transfer. Both categories were asked for by name.
        "EJAR" to SaudiCategories.HOUSING,
        "MASKAN" to SaudiCategories.HOUSING,
        "إيجار" to SaudiCategories.HOUSING,
        "ايجار" to SaudiCategories.HOUSING,
        "سكن" to SaudiCategories.HOUSING,
        "عقار" to SaudiCategories.HOUSING,
        "صيانة المبنى" to SaudiCategories.HOUSING,
        "SCHOOL" to SaudiCategories.EDUCATION,
        "ACADEMY" to SaudiCategories.EDUCATION,
        "UNIVERSITY" to SaudiCategories.EDUCATION,
        "COLLEGE" to SaudiCategories.EDUCATION,
        "INSTITUTE" to SaudiCategories.EDUCATION,
        "KINDERGARTEN" to SaudiCategories.EDUCATION,
        "NURSERY" to SaudiCategories.EDUCATION,
        "TUITION" to SaudiCategories.EDUCATION,
        "مدرس" to SaudiCategories.EDUCATION,
        "مدارس" to SaudiCategories.EDUCATION,
        "جامعة" to SaudiCategories.EDUCATION,
        "روضة" to SaudiCategories.EDUCATION,
        "تعليم" to SaudiCategories.EDUCATION,
        "أكاديم" to SaudiCategories.EDUCATION,
        "MUSANED" to SaudiCategories.FEES,
        "MAKTAB ALAML" to SaudiCategories.FEES,
        "JAWAZAT" to SaudiCategories.FEES,
        "MUQEEM" to SaudiCategories.FEES,
        "IQAMA" to SaudiCategories.FEES,
        "QIWA" to SaudiCategories.FEES,
        "مساند" to SaudiCategories.FEES,
        "رسوم" to SaudiCategories.FEES,
        "إقامة" to SaudiCategories.FEES,
        "جوازات" to SaudiCategories.FEES,
        "مكتب العمل" to SaudiCategories.FEES,
        "راتب عامل" to SaudiCategories.FEES,
        "أجر عامل" to SaudiCategories.FEES,

        // شركة المياه الوطنية, named by the user: water delivery, ordered online.
        // "ELAF COMP" and not "ELAF", because Elaf Hotels is a different business
        // with three records of its own in the same history.
        "ELAF COMP" to SaudiCategories.BILLS,

        // Confirmed by the user after the pattern in their own records pointed at
        // an answer the name could not give:
        //
        //   CELEBRITY  80.00 exactly, eight times, every other week, always in the
        //              evening - the shape of a standing barber's appointment.
        //   NAQUI      2.00 exactly, eleven times. A water refill.
        //   TARWAH     forty purchases averaging 7.20, thirty-two of them between
        //   TAMYAT     19:00 and 23:00; and one whose six purchases are all between
        //   BLACK SEE  15:00 and 17:00. Small, frequent, and at the hours people
        //              buy coffee and something to eat.
        //
        // The evidence narrowed each one to a shape; it never named it. The user
        // did that, and these rules are their answer, not the inference.
        "CELEBRITY" to SaudiCategories.SERVICES,
        "NAQUI" to SaudiCategories.GROCERIES,
        "TARWAH" to SaudiCategories.FOOD,
        "THRWAH" to SaudiCategories.FOOD,
        "TAMYAT" to SaudiCategories.FOOD,
        "BLACK SEE" to SaudiCategories.FOOD,

        // Named by the user, who is the only person who could: a women's salon, a
        // children's barber, a juice shop and a طعمية shop, none of which say so
        // in the name the card network sends.
        "LAMASAT" to SaudiCategories.SERVICES,
        "BABY SALO" to SaudiCategories.SERVICES,
        "SIGNATURE" to SaudiCategories.FOOD,
        "TAMIA ALSULTAN" to SaudiCategories.FOOD,

        // Read off the unfiled remainder of the same history, name by name. Only
        // where the name says what was bought: a chain anyone would recognise, or a
        // word that means something. The local shops and the people's names in that
        // remainder are left alone, because guessing at them would produce wrong
        // categories rather than empty ones, and only their owner can say.
        "LC WAIKIKI" to SaudiCategories.SHOPPING,
        "MANGO" to SaudiCategories.SHOPPING,
        "BERSHKA" to SaudiCategories.SHOPPING,
        "PULL & BEAR" to SaudiCategories.SHOPPING,
        "VICTORIA SECRET" to SaudiCategories.SHOPPING,
        "WOMEN SECRET" to SaudiCategories.SHOPPING,
        "SACO" to SaudiCategories.SHOPPING,
        "YOYOSO" to SaudiCategories.SHOPPING,
        "WOOL WORL" to SaudiCategories.SHOPPING,
        "RAIES JEWELRY" to SaudiCategories.SHOPPING,
        "JAMALOUKI" to SaudiCategories.SHOPPING,
        "ALHADAYA" to SaudiCategories.SHOPPING,
        "DOKKANAFKAR" to SaudiCategories.SHOPPING,
        "HALAAS" to SaudiCategories.SHOPPING,
        "NEXT" to SaudiCategories.SHOPPING,
        "APSCO" to SaudiCategories.SHOPPING,
        "CHEESECAKE" to SaudiCategories.FOOD,
        "CHEESE CAKE" to SaudiCategories.FOOD,
        "URTH CAFF" to SaudiCategories.FOOD,
        "STEAK HOUSE" to SaudiCategories.FOOD,
        "GODIVA" to SaudiCategories.FOOD,
        "DUTCH ICE" to SaudiCategories.FOOD,
        "WRAPS ARABIA" to SaudiCategories.FOOD,
        "NUTRITIONAL FOOD" to SaudiCategories.FOOD,
        "FOOD GATE" to SaudiCategories.FOOD,
        "BWW" to SaudiCategories.FOOD,
        "THE SHAKER" to SaudiCategories.FOOD,
        "F6OR" to SaudiCategories.FOOD,
        "SECTION B" to SaudiCategories.FOOD,
        "SGH" to SaudiCategories.HEALTH,
        "SAUDI GER" to SaudiCategories.HEALTH,
        "AL SAEDY" to SaudiCategories.HEALTH,
        "ALJABR LA" to SaudiCategories.SERVICES,
        "ALDRDEES" to SaudiCategories.TRANSPORT,
        "FLYAKEED" to SaudiCategories.TRANSPORT,
        "ALRAJHITAKAFUL" to SaudiCategories.BILLS,
        "TAP TAMEE" to SaudiCategories.BILLS,

        // ---- Added from a real 22,084-record history --------------------------
        // Every name below was taken from that export's own merchant column, in
        // descending count order, and only where the name says what was bought.
        // Local shops whose name gives nothing away are left for the user to file,
        // which is what the learned-rule table is for.
        "TIM HORTONS" to SaudiCategories.FOOD,
        "SUB WAY" to SaudiCategories.FOOD,
        "SUBWAY" to SaudiCategories.FOOD,
        "KFC" to SaudiCategories.FOOD,
        "BASKIN" to SaudiCategories.FOOD,
        "COLD STONE" to SaudiCategories.FOOD,
        "CHOCOLINE" to SaudiCategories.FOOD,
        "SAADEDDIN" to SaudiCategories.FOOD,
        "HADYAH BAKERIES" to SaudiCategories.FOOD,
        "BREW 92" to SaudiCategories.FOOD,
        "ATLASROAS" to SaudiCategories.FOOD,
        "ATLAS ROA" to SaudiCategories.FOOD,
        "PANINO" to SaudiCategories.FOOD,
        "BYBLOS" to SaudiCategories.FOOD,
        "SHRIMPANA" to SaudiCategories.FOOD,
        "BURNT" to SaudiCategories.FOOD,
        "AL SAJ AL REEFI" to SaudiCategories.FOOD,
        "ALFATER" to SaudiCategories.FOOD,
        "ALMUSBAH" to SaudiCategories.FOOD,
        "EXPRESS FOOD" to SaudiCategories.FOOD,
        "UNITED CATERING" to SaudiCategories.FOOD,
        "NICHE FOO" to SaudiCategories.FOOD,
        "PASSION FOR THE FOOD" to SaudiCategories.FOOD,
        "THE FUTURE OF FOOD" to SaudiCategories.FOOD,
        "EAST FOOD" to SaudiCategories.FOOD,
        "COFFE LANGUAGE" to SaudiCategories.FOOD,
        "SHAJRAT LYMOON" to SaudiCategories.FOOD,
        "QOOT" to SaudiCategories.FOOD,
        "AL AMTEAZ" to SaudiCategories.FOOD,
        "BAYT BIRAJR" to SaudiCategories.FOOD,
        "TAMRA CAP" to SaudiCategories.FOOD,
        "BAYTOTI" to SaudiCategories.FOOD,
        "IWAITER" to SaudiCategories.FOOD,
        "GOURMALIST" to SaudiCategories.FOOD,
        "SANABEL" to SaudiCategories.FOOD,
        "ASRAR FOU" to SaudiCategories.FOOD,
        "BIN DAWOOD" to SaudiCategories.GROCERIES,
        "BINDAWOOD" to SaudiCategories.GROCERIES,
        "HYPER MAR" to SaudiCategories.GROCERIES,
        "AL QIMMA" to SaudiCategories.GROCERIES,
        "ALZAIDI" to SaudiCategories.TRANSPORT,
        "ALZAIDY" to SaudiCategories.TRANSPORT,
        "NAFT" to SaudiCategories.TRANSPORT,
        "TOTAL ENE" to SaudiCategories.TRANSPORT,
        "BENZOL" to SaudiCategories.TRANSPORT,
        "NATIONAL PARKING" to SaudiCategories.TRANSPORT,
        "SAUDI AIRLINES" to SaudiCategories.TRANSPORT,
        "SAUDIA AIRLINES" to SaudiCategories.TRANSPORT,
        "FLYIN" to SaudiCategories.TRANSPORT,
        "TAKER" to SaudiCategories.TRANSPORT,
        "ZARA" to SaudiCategories.SHOPPING,
        "NEXTDIRECTORY" to SaudiCategories.SHOPPING,
        "NEXTJAFZA" to SaudiCategories.SHOPPING,
        "LANDMARK" to SaudiCategories.SHOPPING,
        "OUNASS" to SaudiCategories.SHOPPING,
        "BATH AND BODY" to SaudiCategories.SHOPPING,
        "BATH & BODY" to SaudiCategories.SHOPPING,
        "SEPHORA" to SaudiCategories.SHOPPING,
        "MOTHER CARE" to SaudiCategories.SHOPPING,
        "MAMAS" to SaudiCategories.SHOPPING,
        "CARTERS" to SaudiCategories.SHOPPING,
        "BABY SHOP" to SaudiCategories.SHOPPING,
        "SPLASH" to SaudiCategories.SHOPPING,
        "NAYOMI" to SaudiCategories.SHOPPING,
        "DEBENEHAMS" to SaudiCategories.SHOPPING,
        "NATURALIZER" to SaudiCategories.SHOPPING,
        "MONSOON" to SaudiCategories.SHOPPING,
        "CLAIRES" to SaudiCategories.SHOPPING,
        "LEFTIES" to SaudiCategories.SHOPPING,
        "RIVA" to SaudiCategories.SHOPPING,
        "BHS" to SaudiCategories.SHOPPING,
        "BOOTS" to SaudiCategories.SHOPPING,
        "TAVOLA" to SaudiCategories.SHOPPING,
        "VOGACLOSET" to SaudiCategories.SHOPPING,
        "SOUQ.COM" to SaudiCategories.SHOPPING,
        "ABYAT" to SaudiCategories.SHOPPING,
        "APPAREL" to SaudiCategories.SHOPPING,
        "LANAFLOWERS" to SaudiCategories.SHOPPING,
        "RAWAIE ALMAKTABAT" to SaudiCategories.SHOPPING,
        "ALQRTAS" to SaudiCategories.SHOPPING,
        "ALQERTASS" to SaudiCategories.SHOPPING,
        "PAYPAL" to SaudiCategories.SHOPPING,
        "DHL" to SaudiCategories.SHOPPING,
        "SPL" to SaudiCategories.SHOPPING,
        "MUVI" to SaudiCategories.ENTERTAINMENT,
        "VOX CINEMA" to SaudiCategories.ENTERTAINMENT,
        "DISNEY" to SaudiCategories.ENTERTAINMENT,
        "CHUCK E CHEESE" to SaudiCategories.ENTERTAINMENT,
        "LEEJAM" to SaudiCategories.ENTERTAINMENT,
        "X CORP" to SaudiCategories.ENTERTAINMENT,
        "TOY AND S" to SaudiCategories.ENTERTAINMENT,
        "BLVD" to SaudiCategories.ENTERTAINMENT,
        "MOAAREF PHARAMCY" to SaudiCategories.HEALTH,
        "TAMEENI" to SaudiCategories.BILLS,
        "SAUDI CREDIT BUREAU" to SaudiCategories.BILLS,
        "AWQAF" to SaudiCategories.CHARITY,
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
            matches(folded, ArabicText.normalizeMerchant(keyword))
        }?.second
    }

    /**
     * Whether a merchant name is this rule's.
     *
     * Two things a plain `contains` gets wrong, both found on a real 1,925-merchant
     * list where the rules covered only 34% of transactions:
     *
     * **Card networks truncate.** The merchant arrives as "HUNGERSTA", not
     * "HUNGERSTATION", so the keyword is LONGER than the name it is meant to match
     * and `contains` can never fire. 510 transactions turned on that one case.
     * A truncated name is therefore accepted when the keyword starts with it,
     * which is what truncation means.
     *
     * **Spacing is not stable.** The same station is "ALDREES" one day and
     * "AL DREES" the next, so both sides are compared with spaces removed.
     *
     * The length floor is what keeps the prefix rule safe: without it "AL" would
     * match ALDREES, ALBAIK and half the list. Six characters is long enough that
     * a prefix is evidence rather than a coincidence.
     */
    private fun matches(foldedMerchant: String, foldedKeyword: String): Boolean {
        if (foldedKeyword.length < MIN_SUBSTRING_LENGTH) {
            return matchesWholeWord(foldedMerchant, foldedKeyword)
        }
        val merchant = foldedMerchant.replace(" ", "")
        val keyword = foldedKeyword.replace(" ", "")
        if (merchant.contains(keyword)) return true
        return merchant.length >= MIN_TRUNCATED_LENGTH && keyword.startsWith(merchant)
    }

    /**
     * A short keyword has to be a whole word, never a fragment of one.
     *
     * Measured on the same 22,084-record history: as substrings, "HM" filed TAHA
     * AHMED and TAREQ MOHAMMED as shopping, "SEC" filed the Cheesecake Factory and
     * Victoria's Secret as a utility bill, "DR" filed FIRST DROP CAFE as healthcare,
     * and "LAB" filed BURGER & LABSTER. Each was a rule for a real thing - H&M, the
     * electricity company, a doctor, a laboratory - reaching into words that have
     * nothing to do with it, and every one of them produced a category the user
     * would then have to notice and undo.
     *
     * The Arabic definite article is stripped from the merchant's words before
     * comparing, because Arabic attaches it: "المركز الطبي" carries the word طبي and
     * should match, while "موقف" merely contains those letters and must not.
     */
    private fun matchesWholeWord(foldedMerchant: String, foldedKeyword: String): Boolean {
        val keyword = foldedKeyword.split(" ")
        return foldedMerchant
            .split(" ")
            .map { it.removePrefix(DEFINITE_ARTICLE) }
            .windowed(keyword.size)
            .any { it == keyword }
    }

    private const val MIN_TRUNCATED_LENGTH = 6

    /** Below this, a keyword matches a whole word only. See [matchesWholeWord]. */
    private const val MIN_SUBSTRING_LENGTH = 4

    private const val DEFINITE_ARTICLE = "\u0627\u0644"

    /**
     * A transaction type can decide a category on its own when the merchant cannot.
     *
     * This is what covers the 9,301 records in a real history that carry no merchant
     * name at all - a transfer to a person, a machine withdrawal, a salary - and can
     * therefore never be matched by any list of shops however long it grows.
     *
     * [TransactionType.PURCHASE] and [TransactionType.REFUND] are deliberately absent.
     * Those are the two the merchant decides, and a type-level answer for them would
     * be a guess about what the money went on rather than a fact about the movement.
     */
    fun forType(type: TransactionType): Category? = when (type) {
        TransactionType.BILL_PAYMENT -> SaudiCategories.BILLS
        TransactionType.TRANSFER_OUT,
        TransactionType.TRANSFER_IN,
        TransactionType.OWN_TRANSFER -> SaudiCategories.TRANSFERS
        TransactionType.ATM_WITHDRAWAL, TransactionType.ATM_DEPOSIT -> SaudiCategories.CASH
        TransactionType.SALARY -> SaudiCategories.INCOME
        else -> null
    }

    /** Merchant first, then type. Null when neither knows. */
    fun suggest(merchantRaw: String?, type: TransactionType): Category? =
        forMerchant(merchantRaw) ?: forType(type)
}
