package sa.masrouf.app.data

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import sa.masrouf.core.money.Money
import kotlin.test.assertEquals

/**
 * The two passes that rewrite stored data, and the guards that keep them honest.
 *
 * `repairAmounts` is the riskiest function in the repository: it overwrites the
 * figure the user sees, on every captured row, from a re-reading of the body. Its
 * whole safety argument used to be a one-off simulation that lived nowhere. These
 * are the parts of that argument that can be held.
 */
class RepairPassTest {

    private val dao = FakeDao()
    private val repository = TransactionRepository(dao)

    private var nextId = 0

    private fun stored(
        body: String?,
        riyals: String,
        source: String = "SMS",
        merchantRaw: String? = null,
        merchantKey: String? = null,
        categorySource: String? = "AUTOMATIC",
    ) = TransactionEntity(
        id = "t-${nextId++}",
        amountHalalas = Money.ofMajor(riyals).halalas,
        direction = "DEBIT",
        type = "PURCHASE",
        occurredAtMillis = 1_756_000_000_000L,
        accountId = null,
        accountLast4 = null,
        categoryId = "food",
        categorySource = categorySource,
        merchantRaw = merchantRaw,
        merchantKey = merchantKey,
        note = null,
        source = source,
        status = "CONFIRMED",
        fingerprint = "fp-$nextId",
        rawText = body,
        currency = "SAR",
    )

    private val balanceWasStored = "إيداع في بطاقة 2887*\nمبلغ 8500\nفي 04/27\nالصرف المتبقي 32167.58 SAR"

    // ---- repairAmounts -----------------------------------------------------

    @Test
    fun `a stored balance is replaced by the amount the message labels`() = runTest {
        dao.replaceAll(listOf(stored(balanceWasStored, "32167.58")))

        assertEquals(1, repository.repairAmounts())
        assertEquals(Money.ofMajor("8500").halalas, dao.rows.single().amountHalalas)
    }

    @Test
    fun `running it twice changes nothing the second time`() = runTest {
        dao.replaceAll(listOf(stored(balanceWasStored, "32167.58")))

        assertEquals(1, repository.repairAmounts())
        assertEquals(0, repository.repairAmounts())
    }

    /**
     * A hand-typed amount is what the user meant. The DAO excludes MANUAL rows;
     * this is the assertion that the exclusion is load-bearing rather than
     * incidental.
     */
    @Test
    fun `an amount the user typed is never re-derived`() = runTest {
        dao.replaceAll(listOf(stored(balanceWasStored, "32167.58", source = "MANUAL")))

        assertEquals(0, repository.repairAmounts())
        assertEquals(Money.ofMajor("32167.58").halalas, dao.rows.single().amountHalalas)
    }

    /**
     * The parser refuses a foreign amount outright, because this app stores halalas
     * of SAR and reading "4.34 USD" as 4.34 riyals invents a number. The pass now
     * goes through the parser, so it inherits that refusal - reading with the
     * extractor alone would have written 4.34.
     */
    @Test
    fun `a foreign-currency amount is refused, not converted`() = runTest {
        val body = "شراء\nبطاقة:2383 ;فيزا\nمبلغ 4.34 USD\nلدى GOOGLE"
        dao.replaceAll(listOf(stored(body, "16.28")))

        assertEquals(0, repository.repairAmounts())
        assertEquals(Money.ofMajor("16.28").halalas, dao.rows.single().amountHalalas)
    }

    /** Same argument: the parser refuses a zero, so a real figure is never zeroed. */
    @Test
    fun `a zero reading never overwrites a stored amount`() = runTest {
        dao.replaceAll(listOf(stored("شراء\nمبلغ 0.00 SAR\nلدى SHOP", "55.25")))

        assertEquals(0, repository.repairAmounts())
        assertEquals(Money.ofMajor("55.25").halalas, dao.rows.single().amountHalalas)
    }

    @Test
    fun `a row with no body is left alone`() = runTest {
        dao.replaceAll(listOf(stored(null, "55.25")))

        assertEquals(0, repository.repairAmounts())
    }

    // ---- repairNumericParties ---------------------------------------------

    @Test
    fun `an account number standing in for a party is replaced by the name`() = runTest {
        val body = "حوالة صادرة داخلية\nمبلغ 600 SAR\nمن 104*010\nمستفيد BENEFICIARY NAME\nإلى 508*111"
        dao.replaceAll(listOf(stored(body, "600", merchantRaw = "104*010", merchantKey = "104")))

        assertEquals(1, repository.repairNumericParties())
        assertEquals("BENEFICIARY NAME", dao.rows.single().merchantRaw)
    }

    @Test
    fun `a real merchant name is never cleared`() = runTest {
        val body = "شراء إنترنت بـSR 112.25\nعبر2383;فيزا\nلـNINJA RETAIL"
        dao.replaceAll(listOf(stored(body, "112.25", merchantRaw = "NINJA RETAIL", merchantKey = "NINJA RETAIL")))

        assertEquals(0, repository.repairNumericParties())
        assertEquals("NINJA RETAIL", dao.rows.single().merchantRaw)
    }

    /**
     * The restore path re-reads the body. Without one there is nothing to restore
     * from, so clearing would destroy a party permanently.
     */
    @Test
    fun `a numeric party with no body to restore from is left alone`() = runTest {
        dao.replaceAll(listOf(stored(null, "600", merchantRaw = "104*010", merchantKey = "104")))

        assertEquals(0, repository.repairNumericParties())
        assertEquals("104*010", dao.rows.single().merchantRaw)
    }

    @Test
    fun `a party the user filed by hand is left alone`() = runTest {
        val body = "حوالة صادرة داخلية\nمبلغ 600 SAR\nمن 104*010\nمستفيد BENEFICIARY NAME"
        dao.replaceAll(
            listOf(
                stored(body, "600", merchantRaw = "104*010", merchantKey = "104", categorySource = "MANUAL")
            )
        )

        assertEquals(0, repository.repairNumericParties())
        assertEquals("104*010", dao.rows.single().merchantRaw)
    }
}
