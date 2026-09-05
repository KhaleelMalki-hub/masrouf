package sa.masrouf.core.capture

import org.junit.jupiter.api.Test
import sa.masrouf.core.model.TransactionType
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The owner's name is configuration, not code.
 *
 * It is a fact about a named person and this repository is public, so the value
 * arrives at runtime from a gitignored file and the default is nothing. That
 * default is the interesting case: on anyone else's clone, and in the seconds
 * before the application configures it, the matcher must claim nobody.
 */
class AccountOwnerTest {

    /**
     * The owner is process-wide state, so a test that sets it and walks away leaves
     * it set for whatever class the JVM runs next - and one of those asserts a
     * transfer's direction on a body that contains the owner's name.
     */
    @org.junit.jupiter.api.AfterEach
    fun clearOwner() = AccountOwner.configure("")


    private val transferToStranger =
        "حوالة صادرة محلية\nمبلغ2850.00SAR\nالى OWNER NAME\nبنكD360 BANK\nلحساب2207"

    private fun typeOf(body: String) = IntentClassifier.classify(body)?.type

    /**
     * The safe direction. An unconfigured matcher demotes nothing, so every
     * outgoing transfer stays spending - the app knows less and claims nothing.
     */
    @Test
    fun `an unconfigured owner matches nobody`() {
        AccountOwner.configure("")

        assertTrue(AccountOwner.nameTokens.isEmpty())
        assertEquals(TransactionType.TRANSFER_OUT, typeOf(transferToStranger))
    }

    @Test
    fun `a configured owner is recognised`() {
        AccountOwner.configure("OWNER|NAME")

        assertEquals(TransactionType.OWN_TRANSFER, typeOf(transferToStranger))
    }

    /** Re-reading the classifier's cache must follow the configuration, not lag it. */
    @Test
    fun `reconfiguring takes effect immediately`() {
        AccountOwner.configure("OWNER|NAME")
        assertEquals(TransactionType.OWN_TRANSFER, typeOf(transferToStranger))

        AccountOwner.configure("SOMEONE|ELSE")
        assertEquals(TransactionType.TRANSFER_OUT, typeOf(transferToStranger))
    }

    /**
     * A half-written property must leave fewer rules, never a rule that matches
     * everything: an entry with no tokens would match every message.
     */
    @Test
    fun `blank and half-written entries are dropped`() {
        AccountOwner.configure(" ; | ; OWNER|NAME ; |")

        assertEquals(listOf(listOf("OWNER", "NAME")), AccountOwner.nameTokens)
    }

    @Test
    fun `both tokens are required, in either order`() {
        AccountOwner.configure("OWNER|NAME")

        assertEquals(TransactionType.TRANSFER_OUT, typeOf("حوالة صادرة محلية\nمبلغ100SAR\nالى OWNER ONLY"))
        assertEquals(TransactionType.OWN_TRANSFER, typeOf("حوالة صادرة محلية\nمبلغ100SAR\nالى NAME OWNER"))
    }
}
