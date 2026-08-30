package sa.masrouf.core.model

/**
 * Readable names for merchants that arrive under a card network's descriptor.
 *
 * A card network sends "HUNGERSTA", "AZIZIA PANDA UNITED P" and "ALDREES 1". A
 * person reading their month knows those three as هنقرستيشن, بنده and الدريس, and
 * reading a list of forty rows written the network's way is slower than reading
 * the same list written their way.
 *
 * Display only. Nothing matches on these, nothing is stored with them, and the raw
 * descriptor stays in the database: it is what the bank sent, it is what a future
 * rule will be written against, and replacing it would destroy the only record of
 * what actually arrived.
 *
 * Only merchants seen often enough to be worth recognising are listed. A shop with
 * two records reads perfectly well as itself.
 */
object MerchantNames {

    /** What to show, in each of the app's two languages. */
    data class MerchantName(val ar: String, val en: String)

    private val NAMES: MerchantMatch.Rules<MerchantName> = MerchantMatch.Rules(listOf(
        // Delivery and errands
        "HUNGERSTA" to MerchantName(ar = "هنقرستيشن", en = "HungerStation"),
        "JAHEZ" to MerchantName(ar = "جاهز", en = "Jahez"),
        "MRSOOL" to MerchantName(ar = "مرسول", en = "Mrsool"),
        "KEETA" to MerchantName(ar = "كيتا", en = "Keeta"),
        "TOYOU" to MerchantName(ar = "تو يو", en = "ToYou"),
        "LUGMETY" to MerchantName(ar = "لقمتي", en = "Lugmety"),

        // Groceries
        "AZIZIA PANDA" to MerchantName(ar = "بنده", en = "Panda"),
        "PANDA" to MerchantName(ar = "بنده", en = "Panda"),
        "AMAZON NO" to MerchantName(ar = "أمازون ناو", en = "Amazon Now"),
        "ANANI" to MerchantName(ar = "نينجا", en = "Ninja"),
        "NINJA" to MerchantName(ar = "نينجا", en = "Ninja"),
        "TAMIMI" to MerchantName(ar = "التميمي", en = "Tamimi"),
        "OTHAIM" to MerchantName(ar = "العثيم", en = "Othaim"),
        "DANUBE" to MerchantName(ar = "الدانوب", en = "Danube"),
        "CARREFOUR" to MerchantName(ar = "كارفور", en = "Carrefour"),
        "LULU" to MerchantName(ar = "لولو", en = "LuLu"),
        "BIN DAWOOD" to MerchantName(ar = "بن داود", en = "BinDawood"),
        "BINDAWOOD" to MerchantName(ar = "بن داود", en = "BinDawood"),
        "SHARBATLY" to MerchantName(ar = "شربتلي", en = "Sharbatly"),
        "BERAIN" to MerchantName(ar = "بيرين", en = "Berain"),
        "HYPER MAR" to MerchantName(ar = "هايبر ماركت", en = "Hyper Market"),
        "ALJOUMAA" to MerchantName(ar = "هايبر ماركت", en = "Hyper Market"),
        "CORNER GOODS" to MerchantName(ar = "ركن البضائع", en = "Corner Goods"),

        // Shopping
        "AMAZON" to MerchantName(ar = "أمازون", en = "Amazon"),
        "NOON" to MerchantName(ar = "نون", en = "noon"),
        "SALLA" to MerchantName(ar = "سلة", en = "Salla"),
        "SHEIN" to MerchantName(ar = "شي إن", en = "SHEIN"),
        "NAMSHI" to MerchantName(ar = "نمشي", en = "Namshi"),
        "IKEA" to MerchantName(ar = "ايكيا", en = "IKEA"),
        "JARIR" to MerchantName(ar = "جرير", en = "Jarir"),
        "EXTRA" to MerchantName(ar = "اكسترا", en = "eXtra"),
        "CENTREPOINT" to MerchantName(ar = "سنتربوينت", en = "Centrepoint"),
        "CENTERPOINT" to MerchantName(ar = "سنتربوينت", en = "Centrepoint"),
        "ARAMEX" to MerchantName(ar = "أرامكس", en = "Aramex"),
        "SMSA" to MerchantName(ar = "سمسا", en = "SMSA"),
        "IHERB" to MerchantName(ar = "اي هيرب", en = "iHerb"),
        "ALIEXPRESS" to MerchantName(ar = "علي إكسبرس", en = "AliExpress"),
        "HOME CENT" to MerchantName(ar = "هوم سنتر", en = "Home Centre"),
        "SACO" to MerchantName(ar = "ساكو", en = "SACO"),

        // Fuel and transport
        "ALDREES" to MerchantName(ar = "الدريس", en = "Aldrees"),
        "AL DREES" to MerchantName(ar = "الدريس", en = "Aldrees"),
        "ALDRDEES" to MerchantName(ar = "الدريس", en = "Aldrees"),
        "SASCO" to MerchantName(ar = "ساسكو", en = "Sasco"),
        "PETROMIN" to MerchantName(ar = "بترومين", en = "Petromin"),
        "ALZAIDI" to MerchantName(ar = "محطة الزيدي", en = "Alzaidi Station"),
        "ALZAIDY" to MerchantName(ar = "محطة الزيدي", en = "Alzaidi Station"),
        "EMDAD ALK" to MerchantName(ar = "إمداد الخليج", en = "Emdad Alkhaleej"),
        "UBER" to MerchantName(ar = "أوبر", en = "Uber"),
        "CAREEM" to MerchantName(ar = "كريم", en = "Careem"),
        "SAUDI AIRLINES" to MerchantName(ar = "الخطوط السعودية", en = "Saudia"),
        "SAUDIA AIRLINES" to MerchantName(ar = "الخطوط السعودية", en = "Saudia"),

        // Food
        "ALFATER" to MerchantName(ar = "الفطاير", en = "Alfater"),
        "ALBAIK" to MerchantName(ar = "البيك", en = "Albaik"),
        "MCDONALD" to MerchantName(ar = "ماكدونالدز", en = "McDonald's"),
        "HERFY" to MerchantName(ar = "هرفي", en = "Herfy"),
        "KUDU" to MerchantName(ar = "كودو", en = "Kudu"),
        "STARBUCKS" to MerchantName(ar = "ستاربكس", en = "Starbucks"),
        "DUNKIN" to MerchantName(ar = "دانكن", en = "Dunkin'"),
        "BARN" to MerchantName(ar = "بارنز", en = "Barn's"),
        "TEXAS CHICKEN" to MerchantName(ar = "تكساس تشيكن", en = "Texas Chicken"),
        "SUBWAY" to MerchantName(ar = "صب واي", en = "Subway"),
        "SUB WAY" to MerchantName(ar = "صب واي", en = "Subway"),
        "TIM HORTONS" to MerchantName(ar = "تيم هورتنز", en = "Tim Hortons"),
        "MOVENPICK" to MerchantName(ar = "موفنبيك", en = "Mövenpick"),
        "BASKIN" to MerchantName(ar = "باسكن روبنز", en = "Baskin-Robbins"),
        "KFC" to MerchantName(ar = "كنتاكي", en = "KFC"),
        "SAADEDDIN" to MerchantName(ar = "سعد الدين", en = "Saadeddin"),
        "JUICES" to MerchantName(ar = "محطة العصير", en = "Juices Station"),

        // Bills, telecoms, government
        "STC PAY" to MerchantName(ar = "اس تي سي باي", en = "STC Pay"),
        "STCPAY" to MerchantName(ar = "اس تي سي باي", en = "STC Pay"),
        "SAUDI TELECOM" to MerchantName(ar = "الاتصالات السعودية", en = "STC"),
        "MOBILY" to MerchantName(ar = "موبايلي", en = "Mobily"),
        "ZAIN" to MerchantName(ar = "زين", en = "Zain"),
        "SAUDI ELECTRICITY" to MerchantName(ar = "الكهرباء", en = "Electricity"),
        "SADAD" to MerchantName(ar = "سداد", en = "SADAD"),
        "ABSHER" to MerchantName(ar = "أبشر", en = "Absher"),
        "TAWAKKALNA" to MerchantName(ar = "توكلنا", en = "Tawakkalna"),
        // Above GOOGLE, which would otherwise take it: a subscription is judged
        // per name, and "Google" would merge YouTube with every other Google charge.
        "YOUTUBE" to MerchantName(ar = "يوتيوب", en = "YouTube"),
        "GOOGLE" to MerchantName(ar = "قوقل", en = "Google"),
        "APPLE" to MerchantName(ar = "آبل", en = "Apple"),
        "NETFLIX" to MerchantName(ar = "نتفلكس", en = "Netflix"),
        "SPOTIFY" to MerchantName(ar = "سبوتيفاي", en = "Spotify"),
        "SHAHID" to MerchantName(ar = "شاهد", en = "Shahid"),
        "TAMEENI" to MerchantName(ar = "تأميني", en = "Tameeni"),
        "ELAF COMP" to MerchantName(ar = "المياه الوطنية", en = "National Water"),
        "ALRAJHITAKAFUL" to MerchantName(ar = "الراجحي تكافل", en = "Al Rajhi Takaful"),

        // Health
        "NAHDI" to MerchantName(ar = "النهدي", en = "Nahdi"),
        "DAWAA" to MerchantName(ar = "الدواء", en = "Al Dawaa"),
        "MAGRABI" to MerchantName(ar = "مغربي", en = "Magrabi"),
        "AL BORG" to MerchantName(ar = "البرج", en = "Al Borg"),
        "ALBISHRI" to MerchantName(ar = "البشري الطبي", en = "Albishri Medical"),
        "AL NOOR T" to MerchantName(ar = "صيدلية النور", en = "Al Noor Pharmacy"),
        "SAUDI GER" to MerchantName(ar = "السعودي الألماني", en = "Saudi German"),
        "SGH" to MerchantName(ar = "السعودي الألماني", en = "Saudi German"),

        // Above the endowment, which is one word away from it. The same collision
        // the category rules hit: "HEALTH" reaches "Healthy pie bakery" as readily
        // as it reaches the fund, and only order keeps them apart.
        "HEALTHY P" to MerchantName(ar = "هيلثي باي", en = "Healthy Pie"),

        // Charity
        "HEALTH EN" to MerchantName(ar = "الوقف الصحي", en = "Health Endowment"),
        "HEALTH" to MerchantName(ar = "الوقف الصحي", en = "Health Endowment"),
        "ENDOWMENT" to MerchantName(ar = "الوقف الصحي", en = "Health Endowment"),
        "NAMAA" to MerchantName(ar = "جمعية نماء", en = "Namaa"),
        "EHSAN" to MerchantName(ar = "إحسان", en = "Ehsan"),
        "AWQAF" to MerchantName(ar = "الأوقاف", en = "Awqaf"),
        "ALTAHAJJUD" to MerchantName(ar = "التهجد", en = "Altahajjud"),
        "ALTAHAJJU" to MerchantName(ar = "التهجد", en = "Altahajjud"),

        // Wallets and banks
        "BARQ" to MerchantName(ar = "برق", en = "barq"),
        "URPAY" to MerchantName(ar = "يور باي", en = "urpay"),
        "D360" to MerchantName(ar = "دي 360", en = "D360"),
        "TAMRA" to MerchantName(ar = "تمرة كابيتال", en = "Tamra Capital"),

        // Investment, education, services
        "ALMAREFAH" to MerchantName(ar = "المعارف", en = "Al Marefah"),
        "AL QIMMA LAUNDR" to MerchantName(ar = "مغسلة القمة", en = "Al Qimma Laundry"),
        "NOOR ALMA" to MerchantName(ar = "مغسلة نور", en = "Noor Laundry"),
        "MS" to MerchantName(ar = "موبايل سيرفس", en = "Mobile Service"),
        "FOURTH FR" to MerchantName(ar = "الإطار الرابع", en = "Fourth Frame"),
        "MY GOLDEN" to MerchantName(ar = "إطاري الذهبي", en = "My Golden Tyre"),
    ))

    /**
     * @return a name in both languages, or null to show what the bank sent.
     *
     * Both, because the app switches language and a merchant list that stayed
     * Arabic under an English interface would be worse than the raw descriptors it
     * replaced. The English name is not the descriptor either: "AZIZIA PANDA
     * UNITED P" reads as Panda in either language.
     *
     * Matched with [MerchantMatch], the same way categories are, so a truncated
     * descriptor finds its name for the same reasons it finds its category.
     */
    fun forMerchant(merchantRaw: String?): MerchantName? =
        MerchantMatch.firstMatch(merchantRaw, NAMES)
}
