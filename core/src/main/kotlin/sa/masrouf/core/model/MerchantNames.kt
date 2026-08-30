package sa.masrouf.core.model

/**
 * Arabic names for merchants that arrive under a Latin descriptor.
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

    private val NAMES: List<Pair<String, String>> = listOf(
        // Delivery and errands
        "HUNGERSTA" to "هنقرستيشن",
        "JAHEZ" to "جاهز",
        "MRSOOL" to "مرسول",
        "KEETA" to "كيتا",
        "TOYOU" to "تو يو",
        "LUGMETY" to "لقمتي",

        // Groceries
        "AZIZIA PANDA" to "بنده",
        "PANDA" to "بنده",
        "AMAZON NO" to "أمازون ناو",
        "ANANI" to "نينجا",
        "NINJA" to "نينجا",
        "TAMIMI" to "التميمي",
        "OTHAIM" to "العثيم",
        "DANUBE" to "الدانوب",
        "CARREFOUR" to "كارفور",
        "LULU" to "لولو",
        "BIN DAWOOD" to "بن داود",
        "BINDAWOOD" to "بن داود",
        "SHARBATLY" to "شربتلي",
        "BERAIN" to "بيرين",
        "HYPER MAR" to "هايبر ماركت",
        "ALJOUMAA" to "هايبر ماركت",
        "CORNER GOODS" to "ركن البضائع",

        // Shopping
        "AMAZON" to "أمازون",
        "NOON" to "نون",
        "SALLA" to "سلة",
        "SHEIN" to "شي إن",
        "NAMSHI" to "نمشي",
        "IKEA" to "ايكيا",
        "JARIR" to "جرير",
        "EXTRA" to "اكسترا",
        "CENTREPOINT" to "سنتربوينت",
        "CENTERPOINT" to "سنتربوينت",
        "ARAMEX" to "أرامكس",
        "SMSA" to "سمسا",
        "IHERB" to "اي هيرب",
        "ALIEXPRESS" to "علي إكسبرس",
        "HOME CENT" to "هوم سنتر",
        "SACO" to "ساكو",

        // Fuel and transport
        "ALDREES" to "الدريس",
        "AL DREES" to "الدريس",
        "ALDRDEES" to "الدريس",
        "SASCO" to "ساسكو",
        "PETROMIN" to "بترومين",
        "ALZAIDI" to "محطة الزيدي",
        "ALZAIDY" to "محطة الزيدي",
        "EMDAD ALK" to "إمداد الخليج",
        "UBER" to "أوبر",
        "CAREEM" to "كريم",
        "SAUDI AIRLINES" to "الخطوط السعودية",
        "SAUDIA AIRLINES" to "الخطوط السعودية",

        // Food
        "ALFATER" to "الفطاير",
        "ALBAIK" to "البيك",
        "MCDONALD" to "ماكدونالدز",
        "HERFY" to "هرفي",
        "KUDU" to "كودو",
        "STARBUCKS" to "ستاربكس",
        "DUNKIN" to "دانكن",
        "BARN" to "بارنز",
        "TEXAS CHICKEN" to "تكساس تشيكن",
        "SUBWAY" to "صب واي",
        "SUB WAY" to "صب واي",
        "TIM HORTONS" to "تيم هورتنز",
        "MOVENPICK" to "موفنبيك",
        "BASKIN" to "باسكن روبنز",
        "KFC" to "كنتاكي",
        "SAADEDDIN" to "سعد الدين",
        "JUICES" to "محطة العصير",

        // Bills, telecoms, government
        "STC PAY" to "اس تي سي باي",
        "STCPAY" to "اس تي سي باي",
        "SAUDI TELECOM" to "الاتصالات السعودية",
        "MOBILY" to "موبايلي",
        "ZAIN" to "زين",
        "SAUDI ELECTRICITY" to "الكهرباء",
        "SADAD" to "سداد",
        "ABSHER" to "أبشر",
        "TAWAKKALNA" to "توكلنا",
        "GOOGLE" to "قوقل",
        "APPLE" to "آبل",
        "NETFLIX" to "نتفلكس",
        "SPOTIFY" to "سبوتيفاي",
        "SHAHID" to "شاهد",
        "TAMEENI" to "تأميني",
        "ELAF COMP" to "المياه الوطنية",
        "ALRAJHITAKAFUL" to "الراجحي تكافل",

        // Health
        "NAHDI" to "النهدي",
        "DAWAA" to "الدواء",
        "MAGRABI" to "مغربي",
        "AL BORG" to "البرج",
        "ALBISHRI" to "البشري الطبي",
        "AL NOOR T" to "صيدلية النور",
        "SAUDI GER" to "السعودي الألماني",
        "SGH" to "السعودي الألماني",

        // Above the endowment, which is one word away from it. The same collision
        // the category rules hit: "HEALTH" reaches "Healthy pie bakery" as readily
        // as it reaches the fund, and only order keeps them apart.
        "HEALTHY P" to "هيلثي باي",

        // Charity
        "HEALTH EN" to "الوقف الصحي",
        "HEALTH" to "الوقف الصحي",
        "ENDOWMENT" to "الوقف الصحي",
        "NAMAA" to "جمعية نماء",
        "EHSAN" to "إحسان",
        "AWQAF" to "الأوقاف",
        "ALTAHAJJUD" to "التهجد",
        "ALTAHAJJU" to "التهجد",

        // Wallets and banks
        "BARQ" to "برق",
        "URPAY" to "يور باي",
        "D360" to "دي 360",
        "TAMRA" to "تمرة كابيتال",

        // Investment, education, services
        "ALMAREFAH" to "المعارف",
        "AL QIMMA LAUNDR" to "مغسلة القمة",
        "NOOR ALMA" to "مغسلة نور",
        "MS" to "موبايل سيرفس",
        "FOURTH FR" to "الإطار الرابع",
        "MY GOLDEN" to "إطاري الذهبي",
    )

    /**
     * @return an Arabic name for this merchant, or null to show what the bank sent.
     *
     * Matched with [MerchantMatch], the same way categories are, so a truncated
     * descriptor finds its name for the same reasons it finds its category.
     */
    fun forMerchant(merchantRaw: String?): String? =
        MerchantMatch.firstMatch(merchantRaw, NAMES)
}
