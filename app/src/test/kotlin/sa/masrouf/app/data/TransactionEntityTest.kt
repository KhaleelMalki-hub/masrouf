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

class TransactionEntityTest {

    private fun transaction(
        amount: Money = Money.ofHalalas(8_750),
        type: TransactionType = TransactionType.PURCHASE,
        direction: Direction = Direction.DEBIT,
    ) = Transaction(
        id = "t-1",
        amount = amount,
        direction = direction,
        type = type,
        occurredAt = Instant.ofEpochMilli(1_724_000_000_123),
        accountId = "acc-1",
        categoryId = "cat-1",
        merchantRaw = "TAMIMI MARKETS",
        merchantKey = "TAMIMI MARKETS",
        note = "ملاحظة",
        source = Source.MANUAL,
        status = Status.CONFIRMED,
        fingerprint = "fp-1",
        rawText = null,
    )

    @Test
    fun `a transaction survives a round trip through storage unchanged`() {
        val original = transaction()

        assertEquals(original, original.toEntity().toModel())
    }

    @Test
    fun `the amount is stored as integer halalas`() {
        // A REAL column would reintroduce the representation error Money exists to
        // prevent, at the layer where it would be hardest to notice.
        val entity = transaction(amount = Money.ofMajor("0.10")).toEntity()

        assertEquals(10L, entity.amountHalalas)
    }

    @Test
    fun `the instant survives at millisecond precision`() {
        val entity = transaction().toEntity()

        assertEquals(1_724_000_000_123, entity.occurredAtMillis)
        assertEquals(Instant.ofEpochMilli(1_724_000_000_123), entity.toModel().occurredAt)
    }
}
