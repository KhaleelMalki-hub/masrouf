package sa.masrouf.core.model

import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import sa.masrouf.core.money.Money
import java.time.Instant

/**
 * What a month adds up to.
 *
 * One function decides, because two surfaces deciding separately is how a total
 * and the strip beneath it come to disagree. It reads the category as well as the
 * type for one reason: a deposit at an investment house reaches the bank as an
 * ordinary card purchase at a terminal, and a month that counts it tells the user
 * they spent money they still have.
 */
class CountsAsSpendingTest {

    private fun transaction(
        type: TransactionType,
        direction: Direction = Direction.DEBIT,
        categoryId: String? = null,
    ) = Transaction(
        id = "t",
        amount = Money.ofMajor("100.00"),
        direction = direction,
        type = type,
        occurredAt = Instant.parse("2026-08-20T09:00:00Z"),
        accountId = null,
        categoryId = categoryId,
        merchantRaw = null,
        merchantKey = null,
        note = null,
        source = Source.SMS,
        status = Status.CONFIRMED,
        fingerprint = "f",
        rawText = null,
    )

    @Test
    fun `an ordinary purchase is spending`() {
        assertTrue(transaction(TransactionType.PURCHASE).countsAsSpending)
        assertTrue(transaction(TransactionType.PURCHASE, categoryId = SaudiCategories.FOOD.id).countsAsSpending)
    }

    @Test
    fun `an investment deposit is not, even though the bank calls it a purchase`() {
        val deposit = transaction(TransactionType.PURCHASE, categoryId = SaudiCategories.INVESTMENT.id)
        assertFalse(deposit.countsAsSpending)
    }

    @Test
    fun `money coming in is never spending`() {
        assertFalse(transaction(TransactionType.PURCHASE, direction = Direction.CREDIT).countsAsSpending)
    }

    @Test
    fun `a transfer between the user's own accounts is not spending`() {
        assertFalse(transaction(TransactionType.OWN_TRANSFER).countsAsSpending)
    }
}
