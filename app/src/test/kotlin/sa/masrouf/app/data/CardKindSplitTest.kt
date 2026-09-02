package sa.masrouf.app.data

import kotlin.test.assertEquals
import org.junit.jupiter.api.Test
import sa.masrouf.core.model.CardKind
import sa.masrouf.core.model.Direction
import sa.masrouf.core.model.SaudiCategories
import sa.masrouf.core.model.Source
import sa.masrouf.core.model.Status
import sa.masrouf.core.model.Transaction
import sa.masrouf.core.model.TransactionType
import sa.masrouf.core.money.Money
import java.time.Instant

/**
 * The month read a second way: what went on mada, what went on credit.
 *
 * The owner asked for the split because a few shops take mada and nothing else,
 * and because borrowing is a different question from spending.
 */
class CardKindSplitTest {

    private var nextId = 0

    private fun record(
        riyals: String,
        last4: String?,
        type: TransactionType = TransactionType.PURCHASE,
        direction: Direction = Direction.DEBIT,
        status: Status = Status.CONFIRMED,
        categoryId: String? = null,
    ) = Transaction(
        id = "t-${nextId++}",
        amount = Money.ofMajor(riyals),
        direction = direction,
        type = type,
        occurredAt = Instant.ofEpochMilli(1_724_000_000_000),
        accountId = null,
        categoryId = categoryId,
        merchantRaw = null,
        merchantKey = null,
        note = null,
        source = Source.SMS,
        status = status,
        fingerprint = "fp-$nextId",
        rawText = null,
        accountLast4 = last4,
    )

    private val kinds = mapOf("2907" to CardKind.MADA, "2383" to CardKind.CREDIT)

    @Test
    fun `spending is split by the kind of card it went on`() {
        val rows = listOf(
            record("100", "2907"),
            record("50", "2907"),
            record("400", "2383"),
        )

        assertEquals(
            listOf(CardKind.CREDIT to Money.ofMajor("400"), CardKind.MADA to Money.ofMajor("150")),
            rows.spendingByCardKind(kinds),
        )
    }

    /**
     * A card the messages never described is left out rather than counted under a
     * guess - so the two figures can come to less than the month, by design.
     */
    @Test
    fun `a card of unknown kind is left out entirely`() {
        val rows = listOf(record("100", "2907"), record("900", "9999"), record("70", null))

        assertEquals(listOf(CardKind.MADA to Money.ofMajor("100")), rows.spendingByCardKind(kinds))
    }

    /** The same two filters the month total uses, so the split cannot disagree with it. */
    @Test
    fun `what the month does not count, the split does not count`() {
        val rows = listOf(
            record("100", "2907", status = Status.PENDING),
            record("200", "2907", type = TransactionType.OWN_TRANSFER),
            record("300", "2907", direction = Direction.CREDIT, type = TransactionType.REFUND),
            record("400", "2383", categoryId = SaudiCategories.INVESTMENT.id),
        )

        assertEquals(emptyList(), rows.spendingByCardKind(kinds))
    }
}
