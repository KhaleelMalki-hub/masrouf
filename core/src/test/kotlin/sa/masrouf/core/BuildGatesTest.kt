package sa.masrouf.core

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

/**
 * The CI workflow, checked against the build file it depends on.
 *
 * `settings.gradle.kts` leaves `:app` out of the build when no Android SDK is
 * present, so that `:core` still builds on a machine without one. On a CI runner
 * that means a GREEN build which ran half the tests and said so in one line of log.
 * The workflow therefore greps for that line and fails.
 *
 * A grep is a copy of a string that lives somewhere else, and this repository has
 * already been bitten twice by a guard that read as present and matched nothing.
 * So the copy is asserted against the original here, in `:core` - the module that
 * still runs when `:app` is the thing being skipped, which is the only place a
 * check on the skip can live and be trusted.
 */
class BuildGatesTest {

    private val workflow = File("../.github/workflows/ci.yml")
    private val settings = File("../settings.gradle.kts")

    @Test
    fun `the workflow runs both suites by name`() {
        val text = workflow.readText()
        assertTrue(":core:test" in text, "CI does not run the core suite")
        assertTrue(":app:testDebugUnitTest" in text, "CI does not run the app suite")
    }

    /**
     * The load-bearing assertion. If someone rewords the message in
     * `settings.gradle.kts`, the workflow's grep stops matching, the skip stops
     * being caught, and nothing anywhere goes red - which is precisely the failure
     * the grep exists to prevent.
     */
    @Test
    fun `the phrase CI greps for is the phrase the build actually prints`() {
        val grepped = Regex("""grep -q "([^"]+)"""").find(workflow.readText())?.groupValues?.get(1)
        assertTrue(grepped != null, "the workflow no longer greps the build log for anything")
        assertTrue(
            grepped in settings.readText(),
            "CI looks for \"$grepped\" in the build log, and settings.gradle.kts " +
                "does not print it. The skip check has been silently disarmed.",
        )
    }

    /** A skipped module must fail the build, not shrink it. */
    @Test
    fun `the workflow exits non-zero when it finds the skip`() {
        val text = workflow.readText()
        val check = text.substringAfter("grep -q").substringBefore("- name:")
        assertTrue("exit 1" in check, "the skip is detected and then tolerated")
    }

    /**
     * And a second, independent check on the same thing, because the first depends
     * on a log line that could be reworded even with this test in place - a rename
     * on BOTH sides passes here and still disarms the grep.
     */
    @Test
    fun `CI also proves each suite left results behind`() {
        val text = workflow.readText()
        assertTrue("core/build/test-results/test" in text, "no evidence check for core")
        assertTrue(
            "app/build/test-results/testDebugUnitTest" in text,
            "no evidence check for app",
        )
    }
}
