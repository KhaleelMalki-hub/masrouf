package sa.masrouf.app.data

import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
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
 * Confirming a slip is one decision about the money and, sometimes, a second about
 * the filing. Only the second one is the user's category.
 *
 * The slip opens with the app's own guess already selected, so "confirm" with the
 * chips untouched is agreement with the app, not a filing decision. Recording it as
 * MANUAL made it permanent: `refileAll` clears what the app filed and refuses what
 * the user did, so every slip confirmed that way froze a guess that a later rule
 * would have corrected - and the escape hatch exists because four keywords once
 * filed 152 records under categories nothing about them suggested.
 */
class ConfirmProvenanceTest {

    private val dao = FakeDao()
    private val repository = TransactionRepository(dao)
    private val at = Instant.parse("2026-09-04T09:00:00Z")

    private fun guessed() = Transaction(
        id = "guessed",
        amount = Money.ofMajor("31.00"),
        direction = Direction.DEBIT,
        type = TransactionType.PURCHASE,
        occurredAt = at,
        accountId = null,
        categoryId = SaudiCategories.FOOD.id,
        merchantRaw = "A CAFE",
        merchantKey = "A CAFE",
        note = null,
        source = Source.SMS,
        status = Status.PENDING,
        fingerprint = Fingerprint.forMessage(
            Source.SMS, at, Money.ofMajor("31.00"), Direction.DEBIT, null, "A CAFE",
        ),
        rawText = "شراء\nمبلغ 31 SAR\nلدى A CAFE",
    )

    private suspend fun storedSource(): String? =
        dao.allWithBody().single { it.id == "guessed" }.categorySource

    @Test
    fun `confirming without touching the chips leaves the guess the app's own`() = runTest {
        repository.recordCaptured(guessed())

        repository.confirmWithCategory("guessed", SaudiCategories.FOOD.id, chosenByUser = false)

        assertEquals(CategorySource.AUTOMATIC.name, storedSource())
    }

    @Test
    fun `choosing a category on the slip records it as the user's`() = runTest {
        repository.recordCaptured(guessed())

        repository.confirmWithCategory("guessed", SaudiCategories.GROCERIES.id, chosenByUser = true)

        assertEquals(CategorySource.MANUAL.name, storedSource())
        assertEquals(
            SaudiCategories.GROCERIES.id,
            dao.allWithBody().single { it.id == "guessed" }.categoryId,
        )
    }

    @Test
    fun `confirming still vouches for the record either way`() = runTest {
        repository.recordCaptured(guessed())

        repository.confirmWithCategory("guessed", SaudiCategories.FOOD.id, chosenByUser = false)

        assertEquals(
            Status.CONFIRMED.name,
            dao.allWithBody().single { it.id == "guessed" }.status,
        )
    }
}
