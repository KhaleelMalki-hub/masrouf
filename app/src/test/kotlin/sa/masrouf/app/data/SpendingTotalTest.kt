package sa.masrouf.app.data

import kotlin.test.assertEquals
import org.junit.jupiter.api.Test
import sa.masrouf.core.model.Direction
import sa.masrouf.core.model.Source
import sa.masrouf.core.model.Status
import sa.masrouf.core.model.Transaction
import sa.masrouf.core.model.TransactionType
import sa.masrouf.core.money.Money
import java.time.Instant

/**
 * The monthly total is the number the user acts on, and it is the number two
 * screens are most likely to disagree about. These tests pin the total to
 * `TransactionType.countsAsSpending` rather than to a list repeated here.
 */
class SpendingTotalTest {

    private var nextId = 0

    private fun record(
        riyals: String,
        type: TransactionType,
        direction: Direction = Direction.DEBIT,
        status: Status = Status.CONFIRMED,
        source: Source = Source.MANUAL,
    ) = Transaction(
        id = "t-${nextId++}",
        amount = Money.ofMajor(riyals),
        direction = direction,
        type = type,
        occurredAt = Instant.ofEpochMilli(1_724_000_000_000),
        accountId = null,
        categoryId = null,
        merchantRaw = null,
        merchantKey = null,
        note = null,
        source = source,
        status = status,
        fingerprint = "fp-$nextId",
        rawText = null,
    )

    @Test
    fun `purchases and bills add up`() {
        val total = listOf(
            record("87.50", TransactionType.PURCHASE),
            record("12.25", TransactionType.BILL_PAYMENT),
        ).spendingTotal()

        assertEquals(Money.ofMajor("99.75"), total)
    }

    @Test
    fun `a wallet top-up does not inflate the month`() {
        // The riyals never left the user; the wallet reports them again as they are
        // spent. Counting the top-up too charges the same money twice, and because
        // top-ups are large round numbers the total looks wrong in a way that is
        // hard to trace back.
        val total = listOf(
            record("50.00", TransactionType.PURCHASE),
            record("5000.00", TransactionType.OWN_TRANSFER),
        ).spendingTotal()

        assertEquals(Money.ofMajor("50.00"), total)
    }

    @Test
    fun `a cash withdrawal is not spending`() {
        val total = listOf(record("500.00", TransactionType.ATM_WITHDRAWAL)).spendingTotal()

        assertEquals(Money.ZERO, total)
    }

    @Test
    fun `incoming money is never spending, whatever its type says`() {
        val total = listOf(
            record("9000.00", TransactionType.SALARY, direction = Direction.CREDIT),
            record("30.00", TransactionType.REFUND, direction = Direction.CREDIT),
        ).spendingTotal()

        assertEquals(Money.ZERO, total)
    }

    @Test
    fun `a captured record waiting for confirmation is not in the total`() {
        // A parser misreading an amount is a certainty over a long enough period.
        // A wrong number the user never agreed to is worse than a missing one: it
        // is a false report they will act on.
        val total = listOf(
            record("50.00", TransactionType.PURCHASE),
            record(
                "931.64",
                TransactionType.PURCHASE,
                status = Status.PENDING,
                source = Source.NOTIFICATION,
            ),
        ).spendingTotal()

        assertEquals(Money.ofMajor("50.00"), total)
    }

    @Test
    fun `the same record counts once it has been confirmed`() {
        // The pending record is withheld, not discarded - otherwise the fix above
        // would be indistinguishable from dropping captured spending entirely.
        val total = listOf(
            record("931.64", TransactionType.PURCHASE, source = Source.NOTIFICATION),
        ).spendingTotal()

        assertEquals(Money.ofMajor("931.64"), total)
    }

    @Test
    fun `an empty month totals zero rather than throwing`() {
        assertEquals(Money.ZERO, emptyList<Transaction>().spendingTotal())
    }
}
