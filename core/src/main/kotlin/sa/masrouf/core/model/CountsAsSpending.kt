package sa.masrouf.core.model

/**
 * Whether a transaction is money the user spent.
 *
 * The single decision, as [TransactionType.countsAsSpending] was before it. It
 * takes the category as well now, because one category answers the question
 * differently from its type: a deposit at an investment house arrives as a card
 * purchase at a terminal - which is exactly what it is - and a month that counts
 * it says the user spent five thousand riyals they still have.
 *
 * Still one place. Two surfaces disagreeing about the same month is the failure
 * this exists to prevent, and that is a matter of there being one function, not of
 * how many inputs it reads.
 */
val Transaction.countsAsSpending: Boolean
    get() = direction == Direction.DEBIT &&
        type.countsAsSpending &&
        categoryId != SaudiCategories.INVESTMENT.id

/**
 * Whether a record is money arriving that the user earned.
 *
 * The mirror of [countsAsSpending], and here for the same reason: one decision in
 * one place. The income screen, the month's own figures and the SQL that feeds them
 * were three implementations of this sentence, two of them string literals no
 * Kotlin refactor could reach, and CLAUDE.md rule 5 exists because two surfaces
 * disagreeing about one month is the failure that produces.
 *
 * Category rather than type, deliberately. The employer's bonuses arrive as
 * ordinary incoming transfers and nothing in the message distinguishes them from a
 * relative repaying a loan; what separates them is the filing. Typing them SALARY
 * instead would make the dashboard read the newest one as the user's salary.
 */
val Transaction.countsAsIncome: Boolean
    get() = direction == Direction.CREDIT && categoryId in INCOME_CATEGORY_IDS

/**
 * The categories [countsAsIncome] accepts, exposed so that SQL can bind them
 * rather than spell them. A query is the one caller that cannot ask the property.
 */
val INCOME_CATEGORY_IDS: List<String> =
    listOf(SaudiCategories.INCOME.id, SaudiCategories.BONUS.id)
