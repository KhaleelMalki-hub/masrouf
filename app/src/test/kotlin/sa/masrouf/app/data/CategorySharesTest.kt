package sa.masrouf.app.data

import kotlin.test.assertEquals
import org.junit.jupiter.api.Test
import sa.masrouf.core.dedup.Fingerprint
import sa.masrouf.core.model.Direction
import sa.masrouf.core.model.SaudiCategories
import sa.masrouf.core.model.Source
import sa.masrouf.core.model.Status
import sa.masrouf.core.model.Transaction
import sa.masrouf.core.model.TransactionType
import sa.masrouf.core.money.Money
import java.time.Instant

/**
 * The strip and the total sit one above the other, so they must be filtered by the
 * same rule. These tests pin that they are, because a strip whose bands sum to
 * something other than the number printed above them is worse than no strip.
 */
class CategorySharesTest {

    private var n = 0

    private fun record(
        riyals: String,
        categoryId: String?,
        type: TransactionType = TransactionType.PURCHASE,
        direction: Direction = Direction.DEBIT,
        status: Status = Status.CONFIRMED,
    ) = Transaction(
        id = "t-${n++}",
        amount = Money.ofMajor(riyals),
        direction = direction,
        type = type,
        occurredAt = Instant.parse("2026-08-20T09:00:00Z"),
        accountId = null,
        categoryId = categoryId,
        merchantRaw = null,
        merchantKey = null,
        note = null,
        source = Source.MANUAL,
        status = status,
        fingerprint = Fingerprint.forManual("t-$n"),
        rawText = null,
    )

    @Test
    fun `shares add up to exactly the month total`() {
        val month = listOf(
            record("100.00", SaudiCategories.FOOD.id),
            record("250.50", SaudiCategories.GROCERIES.id),
            record("40.00", SaudiCategories.FOOD.id),
        )

        val shares = month.categoryShares()

        assertEquals(
            month.spendingTotal().halalas,
            shares.sumOf { it.second.halalas },
            "the strip and the total would disagree",
        )
    }

    @Test
    fun `the largest share comes first, because it is drawn first`() {
        val shares = listOf(
            record("40.00", SaudiCategories.FOOD.id),
            record("900.00", SaudiCategories.SHOPPING.id),
            record("120.00", SaudiCategories.TRANSPORT.id),
        ).categoryShares()

        assertEquals(
            listOf(SaudiCategories.SHOPPING.id, SaudiCategories.TRANSPORT.id, SaudiCategories.FOOD.id),
            shares.map { it.first?.id },
        )
    }

    @Test
    fun `uncategorised is its own band, not folded into other`() {
        // They mean different things: one is a decision the user made, the other is
        // a decision they have not made yet.
        val shares = listOf(
            record("50.00", null),
            record("70.00", SaudiCategories.OTHER.id),
        ).categoryShares()

        assertEquals(2, shares.size)
        assertEquals(Money.ofMajor("50.00"), shares.single { it.first == null }.second)
        assertEquals(
            Money.ofMajor("70.00"),
            shares.single { it.first?.id == SaudiCategories.OTHER.id }.second,
        )
    }

    @Test
    fun `pending records are excluded, exactly as they are from the total`() {
        val shares = listOf(
            record("100.00", SaudiCategories.FOOD.id),
            record("999.00", SaudiCategories.FOOD.id, status = Status.PENDING),
        ).categoryShares()

        assertEquals(Money.ofMajor("100.00"), shares.single().second)
    }

    @Test
    fun `income and non-spending types never reach the strip`() {
        val shares = listOf(
            record("9000.00", null, type = TransactionType.SALARY, direction = Direction.CREDIT),
            record("5000.00", null, type = TransactionType.OWN_TRANSFER),
        ).categoryShares()

        assertEquals(emptyList(), shares)
    }

    @Test
    fun `an unknown stored category id shows as uncategorised, not as other`() {
        // A record filed under something this build cannot name is not the same as
        // one the user filed under "other"; merging them would lose that on the
        // next write.
        val shares = listOf(record("10.00", "a-category-from-a-later-version")).categoryShares()

        assertEquals(null, shares.single().first)
    }
}
