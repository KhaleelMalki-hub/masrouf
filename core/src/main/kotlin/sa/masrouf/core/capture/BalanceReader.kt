package sa.masrouf.core.capture

import sa.masrouf.core.money.Money
import sa.masrouf.core.text.ArabicText

/**
 * Reads what a bank message says is left, and what kind of "left" it means.
 *
 * Saudi bank messages carry two different quantities in the same position, and
 * they are not interchangeable:
 *
 *   رصيد:35409.48 SR            the money in a current account
 *   حد الصرف المتبقي SAR 1875.50  what is left of a credit card's limit
 *
 * The second is not money the user has. Showing it under one label with the first
 * would tell someone they can spend 1,875 riyals that do not exist, which is the
 * single worst thing an expense app can say. So the kind is carried, not dropped.
 */
object BalanceReader {

    enum class Kind {
        /** Money in the account. */
        ACCOUNT,

        /** What is left of a credit card's spending limit. Not money held. */
        CREDIT_LIMIT,
    }

    data class Reading(val amount: Money, val kind: Kind)

    /**
     * A number after the keyword, in either order the banks use.
     *
     * Both `رصيد:35409.48 SR` and `رصيد:SAR 33821.97` occur, as do the same two with
     * the currency omitted entirely (`رصيد1145.29`).
     */
    private fun amountAfter(keyword: String) = Regex(
        Regex.escape(keyword) + """\s*:?\s*(?:SAR|SR|ريال|ر\.س)?\s*([\d,]+(?:\.\d{1,2})?)"""
    )

    // Longest first: "حد الصرف المتبقي" contains "الصرف المتبقي", and reading the
    // shorter one first would file an explicit credit limit as an account balance.
    private val CREDIT = listOf(
        amountAfter("حد الصرف المتبقي"),
        amountAfter("الصرف المتبقي"),
    )

    private val ACCOUNT = listOf(
        amountAfter("رصيد"),
        amountAfter("Balance"),
    )

    /**
     * A credit-card template, where "Balance" is the card's and not an account's.
     *
     * Only 63 messages carry the English "Balance:", and on a "Credit Card:Payment"
     * it is ambiguous enough not to be worth a guess.
     */
    private val CREDIT_CARD_TEMPLATE = Regex("""Credit\s*Card""", RegexOption.IGNORE_CASE)

    /**
     * @return what the message says is left, or null when it says nothing.
     *
     * Credit limits are looked for first. A message that carries both is a credit
     * card message, and the account figure in it is not the one the user is being
     * told about.
     */
    fun read(rawText: String?): Reading? {
        val text = rawText?.let(ArabicText::normalize) ?: return null

        CREDIT.firstNotNullOfOrNull { it.find(text) }?.let { match ->
            return money(match)?.let { Reading(it, Kind.CREDIT_LIMIT) }
        }
        if (CREDIT_CARD_TEMPLATE.containsMatchIn(text)) return null

        return ACCOUNT.firstNotNullOfOrNull { it.find(text) }
            ?.let { match -> money(match)?.let { Reading(it, Kind.ACCOUNT) } }
    }

    /**
     * Refuses rather than rounds, as `Money` does everywhere: a balance with three
     * decimals means the line was misread, and a plausible wrong number is worse
     * than none.
     */
    private fun money(match: MatchResult): Money? =
        runCatching { Money.ofMajor(match.groupValues[1].replace(",", "")) }.getOrNull()
}
