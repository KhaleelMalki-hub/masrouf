package sa.masrouf.core.model

import sa.masrouf.core.model.SaudiCategories.BILLS
import sa.masrouf.core.model.SaudiCategories.CHARITY
import sa.masrouf.core.model.SaudiCategories.EDUCATION
import sa.masrouf.core.model.SaudiCategories.ENTERTAINMENT
import sa.masrouf.core.model.SaudiCategories.FEES
import sa.masrouf.core.model.SaudiCategories.FOOD
import sa.masrouf.core.model.SaudiCategories.GROCERIES
import sa.masrouf.core.model.SaudiCategories.HEALTH
import sa.masrouf.core.model.SaudiCategories.INVESTMENT
import sa.masrouf.core.model.SaudiCategories.SERVICES
import sa.masrouf.core.model.SaudiCategories.SHOPPING
import sa.masrouf.core.model.SaudiCategories.TRANSPORT
import sa.masrouf.core.model.SaudiCategories.TRAVEL

/**
 * Merchants named from the merchant string alone, 2026-09-02.
 *
 * Provenance, so nobody mistakes this for the owner's word: every entry below was
 * read off a merchant key in the unfiled history (1,245 merchants, 2,044 records)
 * and named because the string itself says what the shop is - a chain, a brand, a
 * word like STATION or PHARMACY. **None was confirmed by the owner.** A misfiling
 * is one tap to fix in the app; a shop the string does not explain is not here, it
 * is on his list.
 *
 * Spliced into [CategoryGuess]'s rules BEFORE its generic tail (RESTAUR, GROCER,
 * PHARMAC ...) and after its specific head, because the first match wins. Inside
 * this list the same rule holds: the specific entries come first and the generic
 * ones - STATION, STORE, FOOD, the payment-gateway prefixes - last, with the
 * exceptions that must beat them (STATIONERY before STATION, GAS before the
 * perfume house AL QURASHI, FOOD ENTERTAINMENT before ENTERTAINMENT) placed ahead.
 *
 * Matching is [MerchantMatch]: four letters or more is a substring of the
 * space-stripped merchant, shorter is a whole word, and a keyword that begins with
 * a truncated merchant of six letters or more matches it too - which is how
 * "ZAKI OPTICAL" reaches "ZAKI OPTI".
 */
object MerchantNames20260902 {

    val ENTRIES: List<Pair<String, Category>> = listOf(
        // ---- Eating out: chains and cafés ---------------------------------
                "TEXAS ROAD" to FOOD, "TEXAS RD" to FOOD, "TEXAS ROA" to FOOD, "TEXAS ZAIDI" to FOOD,
        "FUDDRUCKERS" to FOOD, "FRIDAYS" to FOOD, "FRIDAYES" to FOOD,
        "P F CHANG" to FOOD, "CHILIS" to FOOD, "SHAKE SHACK" to FOOD, "NANDOS" to FOOD,
        "ENTRECOTE" to FOOD, "GABBIA" to FOOD, "CICCHET" to FOOD, "KOSEBASI" to FOOD,
        "HALWANI CUISINE" to FOOD, "SULTANA ALKHAIR" to FOOD, "LECIEL" to FOOD,
        "FAIROUZ GARDEN" to FOOD, "SHABABIK" to FOOD, "SHABABIC" to FOOD,
        "WBJ RESTA" to FOOD, "MARHABA RESTORANT" to FOOD, "ALNAHR ALSHARGI" to FOOD,
        "WADI AL BRDONI" to FOOD, "TAMYAZ AL WAQDI" to FOOD,
        "SOFA AND COFFE" to FOOD, "COSTA COFFE" to FOOD, "CAFFE" to FOOD,
        "MELTIN" to FOOD, "ROZTA" to FOOD, "DOSE CAF" to FOOD, "NEW DOSE" to FOOD,
        "DAILY CUP" to FOOD, "TEA PARTY" to FOOD, "OVERDOSE" to FOOD,
        "SAMA ROAS" to FOOD, "MAQHAA" to FOOD, "MQHY" to FOOD, "COFFE ALOMRA" to FOOD,
        "SARAYA AL QAHWA" to FOOD, "ANGELINA" to FOOD, "AANI AND" to FOOD,
        "BRGR" to FOOD, "BUFFALO" to FOOD, "BUFALO" to FOOD, "WILD WINGS" to FOOD,
        "JOHNNY ROCKETS" to FOOD, "HARDEES" to FOOD, "POPEYES" to FOOD, "MCDONALS" to FOOD,
        "MCD" to FOOD, "KRISPY" to FOOD, "TIM HOTONS" to FOOD, "DAIRY QUEEN" to FOOD,
        "PHD" to FOOD, "MAESTRO P" to FOOD, "UPPER BUR" to FOOD, "PATTY MORE" to FOOD,
        "CINNAMOOD" to FOOD, "AMORINO" to FOOD,         "GANACHE" to FOOD, "BRIOCHE" to FOOD, "PATISSERIE" to FOOD,
        "LE GOURMET" to FOOD, "LE CONCHEUR" to FOOD, "GINGERSNAPS" to FOOD,         "SNOWFLAKE" to FOOD, "SUKKAR" to FOOD, "SPOON AND GLAZE" to FOOD, "MADO" to FOOD,
        "CRIBS OF RIBS" to FOOD, "PRIME CUT" to FOOD, "GOLD SUSH" to FOOD, "MAKI HOUSE" to FOOD,
        "WOK TO WA" to FOOD, "NOODLEZ" to FOOD, "MANGIA" to FOOD, "VAPIANO" to FOOD,
        "LA TERRASSE" to FOOD, "ALDENTE" to FOOD, "ITALIAN CUISINE" to FOOD, "LA FAMILIA" to FOOD,
        "CASPER GAMBINI" to FOOD, "CARIBBEAN CATCH" to FOOD, "KARAM BEIRUT" to FOOD,
        "KABAB" to FOOD, "KUNAFA" to FOOD, "KESTANE" to FOOD, "TAKO HUT" to FOOD, "SEVEN HUT" to FOOD,
        "HUNGRY" to FOOD, "THE CHEFZ" to FOOD, "EAT OZ" to FOOD, "EATALY" to FOOD, "MEEZ" to FOOD,
        "CRAVE" to FOOD, "MUNCH" to FOOD, "KNEAD" to FOOD, "FRIES" to FOOD, "DIP N DIP" to FOOD,
        "RUDE SHAKE" to FOOD, "RUDESHAKE" to FOOD, "SLUSHY" to FOOD, "JUICE" to FOOD,
        "ALASEER" to FOOD, "NECTAR" to FOOD, "FROZEN YO" to FOOD, "PINK BERR" to FOOD,
        "YOGORINO" to FOOD, "CONE ZONE" to FOOD, "KWALITY" to FOOD,         "TOLL HOUSE" to FOOD, "COOKIES" to FOOD, "SWEET BREAD" to FOOD,
        "PANCAKE" to FOOD, "WAFFLE" to FOOD, "CREPE" to FOOD, "FIRE GRIL" to FOOD,
        "MOROCCAN TASTE" to FOOD, "المذاق المغربي" to FOOD, "DIWANIYAH TASTE" to FOOD,
        "TASTY BITE" to FOOD, "DISCOVERED INDIA" to FOOD, "BIGCHIFS" to FOOD, "CANTON" to FOOD,
        "METRO BRAZIL" to FOOD, "RELISH" to FOOD, "MEZMIZ" to FOOD, "LEILA" to FOOD,
        "JOE AND THE JUICE" to FOOD, "JOE THE" to FOOD, "JOE AMP" to FOOD,
        "SHAWERM" to FOOD, "SHWERMA" to FOOD, "KUSHARI" to FOOD, "BALILA" to FOOD,
        "FOOL AL" to FOOD, "FOWL AL" to FOOD, "FTYRT" to FOOD, "LAQMAH" to FOOD,
        "MTAAM" to FOOD, "MATAEM" to FOOD, "MATHAQ" to FOOD, "WAJBAH" to FOOD,
        "AL TAYEBAT" to FOOD, "ALSULTAN TAMIA" to FOOD, "AL SULTAN TAMIA" to FOOD,
        "ALANDALOS TEST" to FOOD, "BEVERAGE" to FOOD, "SWISSBU" to FOOD,
        "FUTURE FIRST CLASS CATERING" to FOOD, "FIRST FOOD" to FOOD, "FOOD ENTERTAINMENT" to FOOD,
        "MAKHBOZAT" to FOOD, "MKHBOZAT" to FOOD, "MAKBPZAT" to FOOD, "SNABEL" to FOOD,
        "ZED" to FOOD, "GAT" to FOOD,

        // ---- Groceries -----------------------------------------------------
        // Food you keep is groceries whatever sold it - the owner's rule, given on
        // 2026-09-02 when asked about honey, oats, nuts and boxed chocolate.
        "PATCHI" to GROCERIES, "BATEEL" to GROCERIES, "GARRETT" to GROCERIES,
        "JEFF DE BRUGES" to GROCERIES, "ANOOSH" to GROCERIES, "CANDY" to GROCERIES,
        "CHOCOLAT" to GROCERIES,
        "اسواق" to GROCERIES,
        "ALMUSTAHL" to GROCERIES, "AMTIAZ ALMUSTAHLIK" to GROCERIES,
        "NAQI" to GROCERIES, "TMRAL" to GROCERIES, "ATARAT" to GROCERIES, "MALHAMT" to GROCERIES,
        "GOODIES SELECTIONS" to GROCERIES, "AL GHETHA" to GROCERIES, "ALGHIDHA" to GROCERIES,
        "LAGHDHIA" to GROCERIES, "FOOD DISTRIBUATION" to GROCERIES, "NESBRESSO" to GROCERIES,
        "NOORI" to GROCERIES,

        // ---- Transport ---------------------------------------------------
        "CAR SERVICE" to TRANSPORT, "CAR CARE" to TRANSPORT, "CAR ACCESSORIES" to TRANSPORT,
        "CAR DECORATION" to TRANSPORT, "FOR CAR" to TRANSPORT, "RENT A CAR" to TRANSPORT,
        "MOTORS" to TRANSPORT, "HANKOOK" to TRANSPORT, "JERI OIL" to TRANSPORT, "AL JERI O" to TRANSPORT,
        "DARB FUEL" to TRANSPORT, "GAS STATI" to TRANSPORT, "GAS" to TRANSPORT,
        "AL SHAWQIAH CTR FOR GAS" to TRANSPORT,
        "EISO" to TRANSPORT, "M FIVE STATION" to TRANSPORT, "SERVICE WAY" to TRANSPORT,
        "OOMCO" to TRANSPORT, "BETROLINA" to TRANSPORT, "AL DRE" to TRANSPORT,
        "INDSPECTION" to TRANSPORT, "INSPECTION" to TRANSPORT, "GARAGE" to TRANSPORT,
        "DUBAI TAXI" to TRANSPORT, "RENTALCARS" to TRANSPORT, "RENTALCOVER" to TRANSPORT,
        "HERTZ" to TRANSPORT,

        // ---- Travel --------------------------------------------------------
        "SWISSOTEL" to TRAVEL, "HILTON" to TRAVEL, "DUBI STAY" to TRAVEL, "ADAGIO" to TRAVEL,
        "AGODA" to TRAVEL, "SPEED RAIL" to TRAVEL, "NESMA" to TRAVEL,

        // ---- Bills, fees, charity, education, investment -------------------
        "ANTHROPIC" to BILLS, "CLAUDE" to BILLS, "REPLIT" to BILLS, "HOSTINGER" to BILLS,
        "COMHOSTER" to BILLS, "ANGHAMI" to BILLS, "MYSTC" to BILLS, "SAUDI TELEC" to BILLS,
        "ETIHAD ETIS" to BILLS, "ETISALAT" to BILLS, "TELECOM" to BILLS, "TELCOM" to BILLS,
        "TAWUNIYA" to BILLS, "MICROSOFT" to BILLS,
        "ZATCA" to FEES, "VFS" to FEES, "PASSPORTS" to FEES, "RENEW ID" to FEES,
        "KSRELIEF" to CHARITY, "DONATIONS" to CHARITY, "MOSQUE" to CHARITY, "MSAJIDONA" to CHARITY,
        "ADAHI" to CHARITY, "AYTAM" to CHARITY,
        "MAWHIBA" to EDUCATION, "TIHAMA ED" to EDUCATION, "RWAQALMARIFAH" to EDUCATION,
        "DRAHIM" to INVESTMENT, "SAHAM" to INVESTMENT,

        // ---- Entertainment -------------------------------------------------
        "LIKECARD" to ENTERTAINMENT, "LIKE CARD" to ENTERTAINMENT, "TICKETMX" to ENTERTAINMENT,
        "CINEMA" to ENTERTAINMENT, "KIDZANIA" to ENTERTAINMENT, "KIADZANIA" to ENTERTAINMENT,
        "SAND LAND" to ENTERTAINMENT, "THE ENTERTA" to ENTERTAINMENT, "ENTERTAINMEN" to ENTERTAINMENT,
        "FUN TIME" to ENTERTAINMENT, "FAMILY TI" to ENTERTAINMENT, "KIDS FUN" to ENTERTAINMENT,
        "FITNESS" to ENTERTAINMENT,

        // ---- Health --------------------------------------------------------
        "PHAMACY" to HEALTH, "PHARMCY" to HEALTH, "PH" to HEALTH, "PHA" to HEALTH,
        "DELTA MED" to HEALTH, "ZAKI OPTICAL" to HEALTH, "ENEYA" to HEALTH, "ANTIFAT" to HEALTH,
        "DIET SHOP" to HEALTH, "E CHEMIST" to HEALTH, "ESNAD HOSPITAL" to HEALTH,

        // ---- Services ------------------------------------------------------
        "PRINTOOT" to SERVICES, "NATURAL TOUCH" to SERVICES, "OLISHLASH" to SERVICES,
        "DRIP CLEANER" to SERVICES, "SAUDI POST" to SERVICES, "AJ FOR LOGISTIC" to SERVICES,

        // ---- Shopping: brands, then kinds of shop --------------------------
        "TOMMY" to SHOPPING, "MONT BLAN" to SHOPPING, "TIFFANY" to SHOPPING, "PARIS GALLERY" to SHOPPING,
        "BEAUTY BAY" to SHOPPING, "OYSHO" to SHOPPING, "BOUTIQUE" to SHOPPING, "GAZZAZ" to SHOPPING,
        "EVANS" to SHOPPING, "MILANO" to SHOPPING, "PROMOD" to SHOPPING, "DKNY" to SHOPPING,
        "COACH" to SHOPPING, "PUNTO ROMA" to SHOPPING, "SFERA" to SHOPPING, "COLE HAAN" to SHOPPING,
        "COLEHAAN" to SHOPPING, "ANOTAH" to SHOPPING, "FOOT LOCK" to SHOPPING, "FRED PERRY" to SHOPPING,
        "NINE WEST" to SHOPPING, "NINEWEST" to SHOPPING, "KOTON" to SHOPPING, "GIORDANO" to SHOPPING,
        "BHPC" to SHOPPING, "KIPLING" to SHOPPING, "GAP" to SHOPPING, "GUESS" to SHOPPING,
        "SALSA JEANS" to SHOPPING, "HOLLISTER" to SHOPPING, "CLARKS" to SHOPPING, "ARMANI" to SHOPPING,
        "LUSH" to SHOPPING, "OCCITAN" to SHOPPING, "SA LOCCITANE" to SHOPPING, "KIEHLS" to SHOPPING,
        "BODY SHOP" to SHOPPING, "FACE SHOP" to SHOPPING, "MAKE UP" to SHOPPING, "MAKEUP" to SHOPPING,
        "INGLOT" to SHOPPING, "COSMETIC" to SHOPPING, "FACES" to SHOPPING, "MIKYAJY" to SHOPPING,
        "MIKKYAJI" to SHOPPING, "CARETOBEA" to SHOPPING, "BEAUTY" to SHOPPING,
        "OKAIDI" to SHOPPING, "MAYORAL" to SHOPPING, "GYMBOREE" to SHOPPING, "JUNIOR COUTURE" to SHOPPING,
        "CHILDRENS PLACE" to SHOPPING, "EARLY LEARNING" to SHOPPING, "ELC" to SHOPPING,
        "MUMZWORLD" to SHOPPING, "MOM STORE" to SHOPPING, "ALESAYI KIDS" to SHOPPING,
        "KIABI" to SHOPPING, "JENNYFER" to SHOPPING, "PULL N BEAR" to SHOPPING, "STRADIVAR" to SHOPPING,
        "LEVIS" to SHOPPING, "AEROPOSTALE" to SHOPPING, "ABERCROMB" to SHOPPING, "SKECHERS" to SHOPPING,
        "NEW BALAN" to SHOPPING, "PAYLESS" to SHOPPING, "CHARLES AND KEITH" to SHOPPING,
        "RED TAG" to SHOPPING, "SPRINGFIE" to SHOPPING, "YARGICI" to SHOPPING, "PENTI" to SHOPPING,
        "TRENDYOL" to SHOPPING, "6THSTREET" to SHOPPING, "NICE ESTORE" to SHOPPING, "MINI SO" to SHOPPING,
        "MUJI" to SHOPPING, "SOCIETY6" to SHOPPING, "CRATE" to SHOPPING, "WWW ALRUG" to SHOPPING,
        "ALSAIF GALLERY" to SHOPPING, "JOMLAT ALBAYT" to SHOPPING, "BRIDES HOME" to SHOPPING,
        "CARPET" to SHOPPING, "BACK COMFORT" to SHOPPING, "SMART LOCK" to SHOPPING, "TEKZONE" to SHOPPING,
        "AWANI" to SHOPPING, "NEW YORKE" to SHOPPING, "MIHYAR" to SHOPPING, "LOMAR" to SHOPPING,
        "JEWEL" to SHOPPING, "JEWL" to SHOPPING, "JEWEIRY" to SHOPPING, "JEW" to SHOPPING,
        "JAWAHER" to SHOPPING, "PRECIOUS METALS" to SHOPPING, "WATCHES" to SHOPPING,
        "ALHOMAIDHI WATCHES" to SHOPPING, "HOKAIR TIME" to SHOPPING, "TIMESTYLIST" to SHOPPING,
        "BIJOU BRIGITTE" to SHOPPING,
        "SIRAJ ATTAR" to SHOPPING, "SIRAJATTA" to SHOPPING, "NASEEL" to SHOPPING, "ANFAS" to SHOPPING,
        "ATTAR" to SHOPPING, "AL QURASHI" to SHOPPING, "ALQURASHI" to SHOPPING,
        "ALSHAYA" to SHOPPING, "AZADEA" to SHOPPING, "JAMJOOM" to SHOPPING, "KAMAL OSM" to SHOPPING,
        "BIN HAMRAN" to SHOPPING, "21192 CEN" to SHOPPING, "AL RAIES" to SHOPPING, "RAIES" to SHOPPING,
        "ELLE GOWN" to SHOPPING, "GOWN" to SHOPPING, "AIZA BY" to SHOPPING, "ABAYA" to SHOPPING,
        "DEZIGNERO" to SHOPPING, "EMBROIDERY" to SHOPPING, "TEXTILE" to SHOPPING, "GARMENTS" to SHOPPING,
        "CLOTHES" to SHOPPING, "SWEATER" to SHOPPING, "SOCKS" to SHOPPING, "HOMME" to SHOPPING,
        "TAILOR" to SHOPPING, "UNIFORM" to SHOPPING, "LAABIS" to SHOPPING, "SAMACOLLE" to SHOPPING,
        "JOI GIFTS" to SHOPPING, "GIFT" to SHOPPING, "BALLOON" to SHOPPING, "BALOON" to SHOPPING,
        "ALWARD" to SHOPPING, "BLOOMING" to SHOPPING, "NABTA" to SHOPPING, "MR PLANT" to SHOPPING,
        "PET BIRDS" to SHOPPING, "LOKTA" to SHOPPING, "SOUVE" to SHOPPING, "SOUQ" to SHOPPING,
        "MATJAR" to SHOPPING, "MATAJER" to SHOPPING, "BALSAM STORE" to SHOPPING,
        "AL MAIMOUNI STATIONERY" to SHOPPING, "STATIONE" to SHOPPING, "STATIONAR" to SHOPPING,
        "QIRTASIAT" to SHOPPING, "SHOPPING CENT" to SHOPPING, "ALKAFFARY" to SHOPPING,
        "SPORT FOR ALL" to SHOPPING, "KUN SPORT" to SHOPPING, "ATHLETES" to SHOPPING,
        "SUN AND SAND" to SHOPPING, "GOAT" to SHOPPING, "DUNE" to SHOPPING, "COAST" to SHOPPING,
        "BEBE" to SHOPPING, "TAMARA" to SHOPPING, "NAYOUMI" to SHOPPING, "RETAIL" to SHOPPING,

        // ---- Generic last: a word that says what kind of place, and the
        // payment gateways whose prefix is all the string keeps ----------
        "STATION" to TRANSPORT,
        "COFF" to FOOD,
        "STORE" to SHOPPING,
        "FOOD" to FOOD,
        // Qlub pays restaurant bills: "Q KHAYAL MAKKAH QLUB S", "Q PLEO S".
        "QLUB" to FOOD, "Q" to FOOD,
        // MyFatoorah ("MF SARAH", "MF JILSTORE") and Shopify ("SP CHLOE A") carry
        // online shops; the ones that were not are listed above by name.
        "MF" to SHOPPING, "SP" to SHOPPING,
    )
}
