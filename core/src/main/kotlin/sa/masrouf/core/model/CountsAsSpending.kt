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
