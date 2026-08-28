package sa.masrouf.core.model

/**
 * The spending taxonomy, defined in code rather than stored in a table.
 *
 * There is no categories table and no migration for one, because nothing in the
 * app lets a person add or rename a category. A table would be storage for a value
 * that never changes, and it would need seeding on both a fresh install and every
 * existing database - two code paths that can disagree about what the taxonomy is.
 * A transaction keeps a `categoryId`, and this is what those ids mean.
 *
 * The list is short on purpose. A taxonomy a person has to scroll is a taxonomy
 * they stop using, and the point of categorising at all is to answer "what did the
 * month go on" in one glance. Anything that does not fit belongs in [OTHER] until
 * a real pattern earns its own band.
 */
object SaudiCategories {

    val FOOD = Category(id = "food", labelAr = "مطاعم وقهوة", labelEn = "Eating out")
    val GROCERIES = Category(id = "groceries", labelAr = "بقالة", labelEn = "Groceries")
    val TRANSPORT = Category(id = "transport", labelAr = "مواصلات", labelEn = "Transport")
    val BILLS = Category(id = "bills", labelAr = "فواتير واشتراكات", labelEn = "Bills")
    val HEALTH = Category(id = "health", labelAr = "صحة", labelEn = "Health")
    val SHOPPING = Category(id = "shopping", labelAr = "تسوق", labelEn = "Shopping")
    val TRANSFERS = Category(id = "transfers", labelAr = "تحويلات", labelEn = "Transfers")

    /**
     * Charity and endowment.
     *
     * A ninth category, added because a real history had 738 transactions to
     * endowment funds and charities and none of the other eight described them.
     * Folding زكاة and صدقة into "other" would hide a category the user gives to
     * deliberately and would want to see.
     */
    val CHARITY = Category(id = "charity", labelAr = "صدقة وزكاة", labelEn = "Charity")

    /**
     * Cinemas, streaming, gyms, toys.
     *
     * Split out of [SHOPPING] because it answers a different question. Clothes and
     * a cinema ticket are both discretionary, but a month where the discretionary
     * money went on one rather than the other is a month the user wants to be able
     * to see.
     */
    val ENTERTAINMENT = Category(id = "entertainment", labelAr = "ترفيه", labelEn = "Entertainment")

    /**
     * Cash out of, or into, a machine.
     *
     * Not spending, and not a transfer either: the money has left the account but
     * has not yet been spent on anything, and the bank message cannot say what it
     * later went on. Filing it as [OTHER] would put a category that means "unknown
     * spending" on the one movement whose destination is knowable and simply is
     * not a purchase.
     */
    val CASH = Category(id = "cash", labelAr = "نقد", labelEn = "Cash")

    /**
     * Money arriving: salary, refunds of a known kind, profit.
     *
     * It never enters a spending total - [TransactionType.countsAsSpending] decides
     * that, not the category - but income with no category at all shows up in the
     * history as an unfiled row that the user keeps opening and finding nothing to
     * decide about.
     */
    val INCOME = Category(id = "income", labelAr = "دخل", labelEn = "Income")
    val OTHER = Category(id = "other", labelAr = "أخرى", labelEn = "Other")

    /**
     * Display order, and the order of the bands in the month strip.
     *
     * [OTHER] is last deliberately: it is the residue, and a strip that shows it
     * growing is telling the user their taxonomy has stopped describing their
     * spending.
     */
    val ALL: List<Category> = listOf(
        FOOD, GROCERIES, TRANSPORT, BILLS, HEALTH, SHOPPING, ENTERTAINMENT,
        CHARITY, CASH, TRANSFERS, INCOME, OTHER,
    )

    private val BY_ID: Map<String, Category> = ALL.associateBy(Category::id)

    /**
     * @return the category for a stored id, or null when the id is not one this
     *   build knows. Callers show uncategorised rather than substituting [OTHER]:
     *   a record the user filed under something this version cannot name is not the
     *   same as one they filed under "other", and quietly merging the two would
     *   lose the distinction on the next write.
     */
    fun byId(id: String?): Category? = id?.let(BY_ID::get)
}
