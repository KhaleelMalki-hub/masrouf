package sa.masrouf.app.capture

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import sa.masrouf.app.data.FakeDao
import sa.masrouf.app.data.TransactionRepository
import sa.masrouf.core.capture.RawMessage
import sa.masrouf.core.fixtures.RealMessages
import sa.masrouf.core.model.SaudiCategories
import sa.masrouf.core.model.Source
import sa.masrouf.core.model.Status
import java.time.Instant

/**
 * The backfill runs real captured messages through the real pipeline. It shares
 * every rule with live capture on purpose - an import that parsed its own way
 * would be a second implementation of the OTP gate and the refusal to guess.
 */
class HistoryImportTest {

    private val dao = FakeDao()
    private val repository = TransactionRepository(dao)
    private val import = HistoryImport(repository)

    private fun sms(body: String, sender: String, minutesAgo: Long) = RawMessage(
        body = body,
        receivedAt = Instant.parse("2026-08-28T09:00:00Z").minusSeconds(minutesAgo * 60),
        sender = sender,
    )

    @Test
    fun `bank messages are stored and everything else is counted but not`() = runTest {
        val report = import.run(
            listOf(
                sms(RealMessages.RAJHI_POS_SHORT, "AlRajhiBank", 10),
                sms(RealMessages.SNB_ONLINE_PURCHASE, "SNB ALAHLI", 20),
                sms("Your package is out for delivery", "ARAMEX", 30),
                sms("مرحبا كيف حالك", "+966500000000", 40),
            )
        )

        assertEquals(4, report.examined)
        assertEquals(2, report.stored)
        assertEquals(2, report.notBank)
        assertEquals(2, dao.rows.size)
    }

    @Test
    fun `an imported record lands pending, never confirmed`() = runTest {
        // A backfill that silently added hundreds of confirmed rows would put
        // numbers the user has never seen straight into their totals.
        import.run(listOf(sms(RealMessages.RAJHI_POS_SHORT, "AlRajhiBank", 10)))

        assertTrue(dao.rows.all { it.status == Status.PENDING.name })
    }

    @Test
    fun `imported records are marked as coming from sms`() = runTest {
        import.run(listOf(sms(RealMessages.RAJHI_POS_SHORT, "AlRajhiBank", 10)))

        assertEquals(Source.SMS.name, dao.rows.single().source)
    }

    @Test
    fun `an otp in the history is refused, exactly as it is live`() = runTest {
        val report = import.run(
            listOf(
                sms(RealMessages.SNB_OTP, "SNB ALAHLI", 10),
                sms(RealMessages.BARQ_OTP, "barq", 20),
            )
        )

        assertEquals(0, report.stored)
        assertEquals(2, report.refused)
        assertTrue(dao.rows.isEmpty())
    }

    @Test
    fun `re-importing the same inbox adds nothing the second time`() = runTest {
        val inbox = listOf(sms(RealMessages.RAJHI_POS_SHORT, "AlRajhiBank", 10))

        val first = import.run(inbox)
        val second = import.run(inbox)

        assertEquals(1, first.stored)
        assertEquals(0, second.stored)
        assertEquals(1, second.alreadyKnown)
        assertEquals(1, dao.rows.size)
    }

    @Test
    fun `a recognised merchant is filed on the way in`() = runTest {
        // RAJHI_POS_SHORT names ASIAN POLYCLINI, which the merchant rules know.
        import.run(listOf(sms(RealMessages.RAJHI_POS_SHORT, "AlRajhiBank", 10)))

        assertEquals(SaudiCategories.HEALTH.id, dao.rows.single().categoryId)
    }

    @Test
    fun `a message from a known bank that cannot be read is counted, not stored`() = runTest {
        // The signal worth watching: a bank whose template changed. It has to be
        // visible as a number rather than vanishing into "not a bank".
        // A login notice: from the bank, no transaction in it, and not marketing -
        // marketing is now refused by the gate, which is a different outcome.
        val report = import.run(
            listOf(sms("تم تسجيل الدخول إلى حسابك من جهاز جديد", "AlRajhiBank", 10))
        )

        assertEquals(1, report.notUnderstood)
        assertEquals(0, report.stored)
    }

    @Test
    fun `progress is reported for every message examined`() = runTest {
        val seen = mutableListOf<Int>()
        import.run(
            List(5) { sms("noise $it", "SOMEONE", it.toLong()) },
            onProgress = { examined, _ -> seen += examined },
        )

        assertEquals(listOf(1, 2, 3, 4, 5), seen)
    }
}
