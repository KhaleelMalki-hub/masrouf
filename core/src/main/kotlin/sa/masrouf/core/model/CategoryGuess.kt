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
    private val RULES: MerchantMatch.Rules<Category> = MerchantMatch.Rules(listOf(
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
        // Baskin Robbins' franchise descriptor: "BR-ESKAN-", "BR-SHUBANI MAKKAH-JBW",
        // "BR-EVENT MALL -JAI", and "BR-Sasco Zaidy-JFC" for the counter inside a
        // petrol station - which is why this sits above the station rules. Two
        // letters, so it matches as a whole word only; the user recognised it.
        "BR" to SaudiCategories.FOOD,
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
        // Amazon Now is the grocery arm, and it has to be tested before the plain
        // AMAZON rule below: first match wins, and both match. It arrives cut to
        // "Amazon No" 36 times out of 37.
        "AMAZON NO" to SaudiCategories.GROCERIES,
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
        // مركز إطاري الذهبي, the tyre centre the user used before the one below.
        // Named by them; the English descriptor is cut to "MY GOLDEN" and says
        // nothing. Every one of its eleven amounts is a multiple of ten, which is
        // the fingerprint the other tyre shop has and almost nothing else does, and
        // its first visit is the same day as a car service.
        //
        // One test came back against it: a petrol station falls within three hours
        // of only one visit in eleven, below the background rate, where the other
        // tyre shop was above it. Recorded because a rule written past a failed
        // test should say so.
        "MY GOLDEN" to SaudiCategories.TRANSPORT,

        // Two shops nobody could name, filed on the shape of their amounts alone.
        //
        // Of the 44 filed merchants in this history with the same fingerprint -
        // five or more records, 10 to 90 riyals, nearly always whole riyals but
        // rarely round tens - 28 are restaurants and cafes. That is 64%, against
        // 14% for the next category. Both of these sit inside it: 18 records
        // averaging 42 riyals and 11 averaging 46, beside Coffee Language at 47,
        // Subway at 45 and Al Saj Al Reefi at 49 on the same card in the same
        // years. Their neighbours in time are McDonald's, Juices Station, Barns
        // and HungerStation.
        //
        // A probability, not a fact. It beats leaving 29 records blank, and one tap
        // on any of them corrects all 29 if it is wrong.
        "ADMINISTRION" to SaudiCategories.FOOD,
        "HEAD OFFICE WEST" to SaudiCategories.FOOD,

        // بنشر: a tyre and quick-service shop, named by the user. "Fourth frame" is
        // the English of الإطار الرابع, and إطار is a tyre - the reading the name
        // invites in English, a picture frame, is the wrong language. Seventeen
        // records, 7,280 riyals, the largest of them 3,680 for a set of tyres.
        //
        // The only evidence that ever pointed here was weak and nearly discarded:
        // five of the seventeen have a petrol station within three hours, against
        // a background rate near a fifth. Weak evidence in the right direction
        // still beats a confident reading of the wrong one.
        "FOURTH FR" to SaudiCategories.TRANSPORT,

        // Car parts, Haval in particular. Named by the user; nothing in "Time-race"
        // says car, and the tap* prefix is the payment gateway rather than the shop.
        // Four records, 6,093 riyals.
        "TIME RACE" to SaudiCategories.TRANSPORT,
        "TAP TIME" to SaudiCategories.TRANSPORT,

        // Bathrobes, towels, pillows, a mattress. Named by the user. Filed as
        // shopping rather than housing, which here means the monthly cost of a
        // home, not the things put inside one - the same reading that already sends
        // the kitchen fitters and the furniture shops to shopping.
        // Not "REEFI": five characters, so MerchantMatch takes it as a substring,
        // and "Al Saj Al Reefi Restau" contains it - 29 rows of a restaurant were
        // filed as shopping by refileAll before anyone looked. الريفي is an
        // ordinary Arabic word and a bare stem of it can never be safe here. Both
        // spellings the terminal actually sends are written out instead.
        "REEFI STORE" to SaudiCategories.SHOPPING,

        // A watch shop, named by the user. The card network truncates it to
        // "ONTIME PL", which reads as a delivery service and is not one.
        "ONTIME" to SaudiCategories.SHOPPING,

        // ---- Travel ---------------------------------------------------------
        //
        // Flights and hotels. Two stems carry most of it: every airline in twelve
        // years of history spells out AIRLINES somewhere, and every hotel says
        // HOTEL. Checked against the whole merchant list before being written -
        // between them they match twelve merchants and not one of them is anything
        // else, which is the only reason a stem this short is safe here.
        //
        // Four of these were already filed as transport, where 51,289 riyals of
        // flights sat beside the petrol. That is what [SaudiCategories.TRAVEL] was
        // split out to end.
        // An exact match beats a partial one whatever the order (see
        // MerchantMatch.firstMatch), so the airlines already listed further down by
        // their full name had to be moved to TRAVEL there rather than shadowed from
        // up here. These stems catch the rest.
        "AIRLIN" to SaudiCategories.TRAVEL,
        "HOTEL" to SaudiCategories.TRAVEL,
        // Named without either word.
        "FLYNAS" to SaudiCategories.TRAVEL,
        // The stem, not the whole name: the network sends "COM FLYAKEED" in full
        // and "COM FLYAK" truncated, and neither the substring nor the truncation
        // rule can reach "FLYAKEED" from the short one - it is cut off inside the
        // keyword rather than at its end.
        "FLYAK" to SaudiCategories.TRAVEL,
        "BOOKING COM" to SaudiCategories.TRAVEL,
        "RESORT" to SaudiCategories.TRAVEL,

        // ---- Travel, continued ----------------------------------------------
        // Foreign airlines and the hotels of one 2022 trip, all unfiled.
        "QATAR AIRWAYS" to SaudiCategories.TRAVEL,
        "WESTIN" to SaudiCategories.TRAVEL,
        "ST REGIS" to SaudiCategories.TRAVEL,
        "SUNWAY" to SaudiCategories.TRAVEL,

        // ---- Chains the owner shops at, found unfiled by value ---------------
        //
        // Every name here was read off his own history and is a company anyone in
        // the country would recognise, which is what separates them from the local
        // shops further down that only he can name.
        //
        // الرقيب للأثاث, under four spellings plus its website - furniture.
        "ALRUGAIB" to SaudiCategories.SHOPPING,
        "LOUIS VUI" to SaudiCategories.SHOPPING,
        "ALBUKHARY GOLD" to SaudiCategories.SHOPPING,
        // Perfumes, named by the owner.
        "LAURE" to SaudiCategories.SHOPPING,
        // الخزائن المبتكرة - fitted cabinets and home furniture, named by the owner.
        // Three visits in two months, 3,000 then 5,900 then 8,900, the last of them
        // at the end of a day spent in a mall.
        "MAAN HAMA" to SaudiCategories.SHOPPING,
        // Motor insurance - a yearly bill, not a purchase.
        "MALATH INSURANCE" to SaudiCategories.BILLS,

        // ---- Named by the owner ---------------------------------------------
        "TORY BURCH" to SaudiCategories.SHOPPING,
        // Sportswear.
        "ATHLOCITY" to SaudiCategories.SHOPPING,
        "TAILOR SH" to SaudiCategories.SHOPPING,
        // Medical.
        "BCARE" to SaudiCategories.HEALTH,

        // Sitting unfiled next to the tyre shop above, and unambiguous.
        "AUTOMOTIV" to SaudiCategories.TRANSPORT,

        // Found while looking for something else, which is where most of these come
        // from: three opticians and a tyre shop, sitting unfiled.
        "MAGRABI" to SaudiCategories.HEALTH,
        "OPTICAL" to SaudiCategories.HEALTH,
        "TIRE SERV" to SaudiCategories.TRANSPORT,

        // Recovered by asking the history for a longer spelling of each unfiled
        // merchant. Card networks cut the name at different lengths, so one rare
        // record often carries the full name of a shop that appears truncated
        // dozens of times: "Khayal Re" is Khayal Restaurant, "ALBISHRI" is a
        // medical complex, "H amp;M-S" is H&M with the ampersand HTML-escaped.
        // Each keyword below is the truncation that actually arrives, not the full
        // name, because the full name is the rare one.
        "KHAYAL RE" to SaudiCategories.FOOD,
        "PIATTO" to SaudiCategories.FOOD,
        "FIRST DRO" to SaudiCategories.FOOD,
        "GURKAN CH" to SaudiCategories.FOOD,
        "MUNCH BAK" to SaudiCategories.FOOD,
        "THE CHEES" to SaudiCategories.FOOD,
        "WWW CALO" to SaudiCategories.FOOD,
        "TAP ATLAS" to SaudiCategories.FOOD,
        "ALBISHRI" to SaudiCategories.HEALTH,
        "AL BORG" to SaudiCategories.HEALTH,
        "LANA" to SaudiCategories.SHOPPING,
        "RARE AND" to SaudiCategories.SHOPPING,
        "MAX" to SaudiCategories.SHOPPING,
        "CENTERPOINT" to SaudiCategories.SHOPPING,
        "IKKS" to SaudiCategories.SHOPPING,
        "NICEONESA" to SaudiCategories.SHOPPING,
        "UNITED HOMEWARE" to SaudiCategories.SHOPPING,
        "H AMP M" to SaudiCategories.SHOPPING,
        "ENAYA SAL" to SaudiCategories.SERVICES,
        "HEALTH EN" to SaudiCategories.CHARITY,
        // The endowment fund cut down to one word: 48 records, every one of them
        // exactly 10.00 riyals, the same donation as the 603 that arrive with the
        // full name. Deliberately last among the rules that contain "HEALTH", so
        // that "Healthy pie bakery" is still a bakery and DR.MAZEN FAKEEH HEALTH is
        // still a doctor - both are matched by rules above this line.
        "HEALTH" to SaudiCategories.CHARITY,
        "TAKAMOL" to SaudiCategories.FEES,
        "MF DATES" to SaudiCategories.GROCERIES,

        // Kitchen Trends, who fitted the user's kitchen: 74,000 riyals across six
        // in-person card payments in six weeks, each one minutes after a transfer
        // arrived to cover it. Home Centre an hour after the largest, and a kitchen
        // design firm in the same season - the same project, so the same category.
        //
        // Filed as shopping rather than housing because housing here is the monthly
        // charge the user named it for, and IKEA and Home Centre are already
        // shopping. A kitchen is a large version of the same purchase, not a rent.
        "DISTINCTI" to SaudiCategories.SHOPPING,
        "HOME CENT" to SaudiCategories.SHOPPING,
        "KITCHEN DESIGN" to SaudiCategories.SHOPPING,
        // Not "KITCHEN": The Social Kitchen is a restaurant, and a keyword that
        // reached it would file dinner as furniture.
        "THE SOCIAL KITCHEN" to SaudiCategories.FOOD,

        // A burger restaurant. The card network sends it as "sheps"; it appears
        // once, out of twenty-nine, under its real name. Both spellings, because
        // the mangled one is the one that arrives.
        "SHEPS" to SaudiCategories.FOOD,
        "CHEFS" to SaudiCategories.FOOD,

        // A barber, named by the user, and the visits agree: fifteen of twenty-
        // seven gaps are between 15 and 21 days, the amount is a round 50 riyals
        // twelve times, and it went on across four different cards - so not a
        // subscription tied to one of them.
        "HANY IBRAHIM" to SaudiCategories.SERVICES,

        // Corrected twice: filed as a restaurant by a rule added on the name alone,
        // then as a transfer, and now as what it is. Tamra Capital is an investment
        // house and the records read like deposits - 5,000 then 2,000 three times
        // in a day, all round, all growing through 2026. Money moved, not spent, and
        // [countsAsSpending] leaves it out of the month for that reason.
        "TAMRA" to SaudiCategories.INVESTMENT,
        "MANAFE CAPITAL" to SaudiCategories.INVESTMENT,

        // أول قطرة, the same coffee shop as FIRST DROP CAFE, arriving letter-spaced
        // through a different terminal.
        "A W A L Q" to SaudiCategories.FOOD,
        // wizebutter.com: nut butters and the like, bought online. Groceries and
        // not a restaurant - it is food to keep, not a meal.
        "WIZEBUTTER" to SaudiCategories.GROCERIES,
        "RETRO7" to SaudiCategories.FOOD,
        // زد, a sandwich shop: seven visits averaging fifteen riyals.
        "ZED AL ZA" to SaudiCategories.FOOD,
        "ROCOCOA" to SaudiCategories.FOOD,
        "INTERNATIONAL OVEN" to SaudiCategories.FOOD,

        // Recruiting a domestic worker: 15,000 riyals at a terminal, named by the
        // user. Filed with the fees and wages the same household pays month to
        // month, alongside مساند. Keyed on the second word too, because
        // "INTERNATIONAL" alone is five other companies in this history - a
        // creative agency, a medical centre, a bakery, a regions firm, Alshaya.
        "INTERNATIONAL RECRUI" to SaudiCategories.FEES,
        // The same company sends "NTERNATIO" from one terminal: the network cut the
        // FIRST letter, not the last, and MerchantMatch's truncation rule only
        // forgives a missing tail. Deliberately NOT given a keyword of its own -
        // any keyword short enough to match it is a substring of every
        // "INTERNATIONAL ..." in this history, and adding one took the creative
        // agency, the regions firm and Alshaya to fees along with it. One row; the
        // user files it once in the app and every future one follows, which is the
        // mechanism that exists for exactly this.

        // A men's thobe shop, named by the user. Five visits over six years
        // averaging 1,059 riyals - the shape of clothes bought rarely and well,
        // which no keyword in a shipped list would ever have guessed.
        "SINDI" to SaudiCategories.SHOPPING,
        // النقلي, a nuts-and-chocolate shop: groceries by the owner's rule that food
        // you keep is groceries. Named by him on 2026-09-02; three spellings arrive.
        "NUKLY" to SaudiCategories.GROCERIES,
        "NUKALY" to SaudiCategories.GROCERIES,

        // مطعم بوقا, named by the user. Eight visits averaging 88 riyals.
        "BOGA" to SaudiCategories.FOOD,

        // وزارة العدل: court and notary fees. Three characters, so a whole word only.
        "ADL" to SaudiCategories.FEES,

        // ولاء بلس: 246.67 four days running, which is an instalment, not a purchase.
        "WALAPLUS" to SaudiCategories.BILLS,

        // Mobile Service, car servicing, named by the user. The name arrives as
        // "MS.21535", "MS 21534." and "MS.21515_" - a branch code that
        // `normalizeMerchant` strips as a trailing reference, leaving "MS". Two
        // characters, so it matches as a whole word only, which is what makes it
        // safe: nothing else in a 22,091-record history folds to that token. The
        // same branch also arrives as "MS.21535 KUDAY ALZAIDY", which the petrol
        // rule was already filing as transport - the same answer by a different
        // route, and a check that this one is right.
        "MS" to SaudiCategories.TRANSPORT,

        // مغسلة ربوة التميز, a car wash, named by the user. The name never reaches
        // the message: the card network sends the payment processor's "SUREPay SNB",
        // which says nothing about cars. Three washes between 30 and 35 riyals, and
        // filed with the car's other costs - servicing, tyres, fuel - rather than
        // with the laundries, so that transport reads as everything the car costs.
        "SUREPAY SNB" to SaudiCategories.TRANSPORT,

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
        // The owner's own name as a merchant used to be a rule here. It is a fact
        // about a named person and this repository is public, so it went the way
        // the owner's names in AccountOwner went - out of the source. The message
        // BODY still names him, and IntentClassifier demotes an outgoing transfer
        // to OWN_TRANSFER on that, which forType then files as a transfer; only a
        // REFUND carrying his name as the merchant is left unfiled, and there is
        // one of those in twelve years. The user files it once.

        // The employer. Transfers from the municipality are allowances and
        // end-of-service pay, not the monthly salary, which arrives separately as
        // "ايداع رواتب". 39 of them, 329,740 riyals since 2020, all filed as
        // transfers until the owner said what they were.
        "امانة العاصمة المقدسة" to SaudiCategories.BONUS,

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

        // "الخدمة", an app the user bought a service from. Ten records and every
        // one of them an internet purchase, never once at a terminal. The amounts
        // sit where Apple and YouTube Premium sit in the same months - 16 to 39
        // riyals, with two larger ones - and no other merchant in the history
        // charges any of them. Filed with the subscriptions for that reason.
        "ALKHDMAH" to SaudiCategories.BILLS,

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
        // Boxed chocolate is food to keep: groceries, by the owner's rule of 2026-09-02.
        "GODIVA" to SaudiCategories.GROCERIES,
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
        "FLYAKEED" to SaudiCategories.TRAVEL,
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
        // The same restaurant, spelled with a doubled alef by another terminal.
        // Neither spelling can be reached by a stem of الريفي - it is an ordinary
        // Arabic word - so both are written out.
        "ALSAAJ ALREEFI" to SaudiCategories.FOOD,
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
        "BAYTOTI" to SaudiCategories.FOOD,
        "IWAITER" to SaudiCategories.FOOD,
        "GOURMALIST" to SaudiCategories.FOOD,
        "SANABEL" to SaudiCategories.FOOD,
        "ASRAR FOU" to SaudiCategories.FOOD,
        "BIN DAWOOD" to SaudiCategories.GROCERIES,
        "BINDAWOOD" to SaudiCategories.GROCERIES,
        "HYPER MAR" to SaudiCategories.GROCERIES,
        "AL QIMMA" to SaudiCategories.GROCERIES,
        // المعارف, a late-night grocer. Named by nothing in the name: what settled
        // it was that not one of its nine amounts is a whole riyal - 6.90, 13.15,
        // 189.49. In this history 74% of restaurant amounts are whole riyals and
        // only 16% of grocery ones, so nine out of nine with halalas is about three
        // chances in a million under "restaurant" and one in five under "grocer".
        // Barcode prices with VAT on them, not a menu.
        "AL MAAREF" to SaudiCategories.GROCERIES,

        // The hypermarket under a new merchant descriptor. "HYPER MAR" runs to
        // 2024-10-06 and stops; "AlJoumaa2" starts 2024-09-15 and continues. On the
        // one day they overlap, a 124-riyal basket goes through the old name at
        // 14:04 and a 1,261-riyal one through the new at 14:58. The amounts match a
        // supermarket either side: bread-and-milk runs around 12 to 24 riyals, with
        // an occasional full shop.
        "ALJOUMAA" to SaudiCategories.GROCERIES,
        "ALZAIDI" to SaudiCategories.TRANSPORT,
        "ALZAIDY" to SaudiCategories.TRANSPORT,
        "NAFT" to SaudiCategories.TRANSPORT,
        "TOTAL ENE" to SaudiCategories.TRANSPORT,
        "BENZOL" to SaudiCategories.TRANSPORT,
        "NATIONAL PARKING" to SaudiCategories.TRANSPORT,
        "SAUDI AIRLINES" to SaudiCategories.TRAVEL,
        "SAUDIA AIRLINES" to SaudiCategories.TRAVEL,
        // "FLYIN" is flyin.com, and it is also the first five letters of Flying
        // Tiger Copenhagen - a stationery chain, 5 rows, filed as travel. It was
        // wrong before this session too, as transport; the session moved it to
        // travel and made it visible.
        //
        // The order is the fix and it is load-bearing. The stationery chain is
        // listed FIRST so the substring pass reaches it before "FLYIN" can, and its
        // own truncated form "FLYING TI" is an exact glued match, which beats any
        // partial whatever the order. "FLYIN" then only ever sees what is left.
        "FLYING TI" to SaudiCategories.SHOPPING,
        "FLYIN" to SaudiCategories.TRAVEL,
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
        // لعبة وحكاية, a toy shop, named by the user. The name says so in Arabic:
        // "LOUBA" is لعبة. Read as a person's name in English it says nothing,
        // which is the same mistake as reading الإطار الرابع as a picture frame.
        "LOUBA" to SaudiCategories.ENTERTAINMENT,
        "TOYS R U" to SaudiCategories.ENTERTAINMENT,
        // Not keyed on "TOY": ToYou is a delivery app, and the same three letters.
        "TOYOU" to SaudiCategories.FOOD,
        "BLVD" to SaudiCategories.ENTERTAINMENT,
        "MOAAREF PHARAMCY" to SaudiCategories.HEALTH,
        "TAMEENI" to SaudiCategories.BILLS,
        "SAUDI CREDIT BUREAU" to SaudiCategories.BILLS,
        "AWQAF" to SaudiCategories.CHARITY,

        // ---- What the name says it is, rather than who it is ------------------
        // A shop that puts its trade in its own name has already filed itself, and
        // one word reaches every branch, spelling and city of it. These were found
        // among the records left unfiled after the party fix, and each was checked
        // against the whole 22,000-record merchant column before being written -
        // "REEFI" once matched a restaurant called الساج الريفي and filed 29 meals
        // as shopping, so a word that is a word in ordinary use does not go here.
    ) + ConfirmedMerchants20260902.ENTRIES + MerchantNames20260902.ENTRIES + listOf(
        // ---- Generic tail: a word that says what kind of place --------------
        "RESTAUR" to SaudiCategories.FOOD,
        "CATERING" to SaudiCategories.FOOD,
        "SWEETS" to SaudiCategories.FOOD,
        "GROCER" to SaudiCategories.GROCERIES,
        "PHARMAC" to SaudiCategories.HEALTH,
        "LAUNDRY" to SaudiCategories.SERVICES,
        "FURNITURE" to SaudiCategories.SHOPPING,
        "BOOKSTORE" to SaudiCategories.SHOPPING,
        "AIRLINE" to SaudiCategories.TRAVEL,
        "TOURISM" to SaudiCategories.TRAVEL,

        // What the name says, continued - from the records still unfiled after the
        // 2026-09-01 sweeps, each checked against the whole merchant column.
        // "RESTURANT" is not a typo here: it is the spelling on the terminal.
        "RESTURANT" to SaudiCategories.FOOD,
        "FUNDUQ" to SaudiCategories.TRAVEL,
        "MILLENNIUM" to SaudiCategories.TRAVEL,
        "FLY DUB" to SaudiCategories.TRAVEL,
        // The airline, confirmed by the owner. Five records, all 2015-2017, in
        // three spellings - "EMIRATES", "EMIRATES 01", "EMIRATES LEIS DXBT2" (the
        // airport leisure arm, which is still a trip). Safe as a substring here
        // because the bank of the same name never appears as a MERCHANT.
        "EMIRATES" to SaudiCategories.TRAVEL,
        // عصر الجوال, a mobile-phone shop, confirmed by the owner.
        "ASER ALJAWAL" to SaudiCategories.SHOPPING,
        // ويست إلم, named by the owner. His history carries it in full once and
        // as "WES" twice - same day, same terminal, furniture-sized amounts. Three
        // characters, so MerchantMatch requires the whole word: it cannot reach
        // into WEST SIDE CAFE, and the bare "WEST" - which could be either - is
        // deliberately NOT here. It stays for the owner, as it always has.
        "WEST ELM" to SaudiCategories.SHOPPING,
        "WES" to SaudiCategories.SHOPPING,
        "BED AND BATH" to SaudiCategories.SHOPPING,
        "CHARRIOL" to SaudiCategories.SHOPPING,
        "WOJOOH" to SaudiCategories.SHOPPING,
        "TOUS" to SaudiCategories.SHOPPING,

        // Brands the list did not carry, each seen in the owner's own history.
        // "Saudia Airl01es" is how one 2016 terminal wrote it - "IN" came through
        // as "01" - so the keyword stops before the corruption.
        "SAUDIA AIRL" to SaudiCategories.TRAVEL,
        "VIRGIN MEGA" to SaudiCategories.SHOPPING,
        "TED BAKER" to SaudiCategories.SHOPPING,
        "LAVANDE" to SaudiCategories.SHOPPING,
        "UNITED LUXURY" to SaudiCategories.SHOPPING,

        // Named by the owner, from the unfiled list of 2026-09-01. Each arrived
        // once, in one spelling, and nothing in the string says what the shop sells.
        // المطلق للأثاث والمفروشات. A family name as well as a shop, so if a
        // different Almutlaq ever appears the owner's own rule overrides this.
        "ALMUTLAQ" to SaudiCategories.SHOPPING,
        // Named by the owner on 2026-09-02, off the filing worksheet.
        // لا كالي, a restaurant.
        "LA CALLE" to SaudiCategories.FOOD,
        // كرز لنن - bed linen, towels, things for the bedroom.
        "KARAZ LIN" to SaudiCategories.SHOPPING,
        // قطوف وحلا, boxed chocolate - groceries by his rule. Three spellings:
        // "QUTOUF AND HALA", "QOTOF AND HALA", "QUTOOF HALA EST".
        "AND HALA" to SaudiCategories.GROCERIES,
        "QUTOOF HALA" to SaudiCategories.GROCERIES,
        // أجواد الكرم, a grocery. 67 records; arrives as "AJWAD AL KARAM CO",
        // "AJWAD ALK", "AJWAD ALKRM COM".
        "AJWAD AL" to SaudiCategories.GROCERIES,
        // الحكير - the children's amusement arcades. "ABDULMOHSEN AL HOKAIR" and,
        // truncated, "ABDULMOHS". Named before "HOKAIR TIME", the watch shop of the
        // same group, so the group name alone never files as shopping.
        "ABDULMOHSEN AL HOKAIR" to SaudiCategories.ENTERTAINMENT,
        "ABDULMOHS" to SaudiCategories.ENTERTAINMENT,
        // اطلبها - car parts and accessories, app and site.
        "ATLOBHA" to SaudiCategories.TRANSPORT,
        // ميازو and دار زيد, restaurants.
        "MYAZU" to SaudiCategories.FOOD,
        "DAR ZIED" to SaudiCategories.FOOD,
        // آفاق إعمار - plumbing and home fittings, bought online.
        "AFAQEMAAR" to SaudiCategories.SHOPPING,
        // رداء المسك - the same men's tailor as SINDI, under its registered name.
        "RIDAA ALM" to SaudiCategories.SHOPPING,
        // هوم بوكس, furniture and bedrooms. The terminal appends a branch number.
        "HOMEBOX" to SaudiCategories.SHOPPING,
        "HOME BOX" to SaudiCategories.SHOPPING,
        // كورو, a Japanese restaurant in Jeddah. Sent as "Kuuru Jed".
        "KUURU" to SaudiCategories.FOOD,
        // شانيل on Tahlia in Jeddah, under its operator's name - 23,240 riyals, a
        // handbag, and the largest unfiled record in the history. Nothing in the
        // descriptor says Chanel; the owner remembered the shop from the date and
        // the fact that Samsung Pay means he was standing in it.
        "AL NOUJAI" to SaudiCategories.SHOPPING,
        // ريفي, household goods (reefi.me). Two records arrive as the bare word,
        // which "REEFI STORE" cannot reach, and a bare "REEFI" keyword is what
        // filed 29 meals at الساج الريفي as shopping once before.
        //
        // Safe here for two reasons, and only here. MerchantMatch tries an exact
        // match on the whole name before any partial one, so "Reefi" is caught by
        // this rule while "Al Saj Al Reefi Restau" - a different string - never
        // reaches it exactly. And the partial pass takes the FIRST rule in list
        // order, so this sits last, below both spellings of the restaurant and
        // below RESTAUR. Moving it up re-opens the old defect.
        "REEFI" to SaudiCategories.SHOPPING,

        // Money sent abroad through Western Union out of the STC Pay wallet: 68
        // transfers, 94,126 riyals, every one of them wages for domestic staff, as
        // the owner confirmed. Filed by the CHANNEL rather than the recipient,
        // because the recipient is a person and a person does not belong in a
        // shipped rule. "WU" is two characters, so MerchantMatch requires it to be
        // a whole word and it cannot reach inside another name.
        "ويسترين يونيون" to SaudiCategories.FEES,
        "WESTERN UNION" to SaudiCategories.FEES,
        "WU" to SaudiCategories.FEES,

        // The brokerage. Money moved to the investment account is money he still
        // has - the category countsAsSpending already excludes - and a dividend is
        // money that arrived there, which is neither a purchase nor a transfer
        // between strangers.
        "الحساب الاستثماري" to SaudiCategories.INVESTMENT,
        // How the bank names it when the money is coming BACK: "حوالة واردة من
        // حسابك الاستثماري". His own money either way.
        "حسابك الاستثماري" to SaudiCategories.INVESTMENT,
        "ارباح شركة" to SaudiCategories.INVESTMENT,
        "أرباح شركة" to SaudiCategories.INVESTMENT,
    ))

    /**
     * @return a suggested category, or null when nothing matches. Callers must
     *   leave a null unfiled rather than defaulting it.
     *
     * The matching itself lives in [MerchantMatch], shared with the display-name
     * list, because the truncation rules it encodes are the whole reason either
     * list works and a second copy of them would drift from this one.
     */
    fun forMerchant(merchantRaw: String?): Category? = MerchantMatch.firstMatch(merchantRaw, RULES)

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
