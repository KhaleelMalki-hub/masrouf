package sa.masrouf.app

import org.junit.jupiter.api.Test
import sa.masrouf.app.data.CURRENT_MAINTENANCE_VERSION
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The shape of the repair set, which decides what an old install runs and when.
 *
 * These used to be an `if (done < n)` ladder. An install several versions behind
 * ran them in sequence, and because most repairs reuse an existing pass that meant
 * the same full-table scan four times over - eight scans where four would do,
 * growing by one with every fix. They are idempotent, so what matters is that each
 * runs once and that they run in dependency order.
 *
 * Nothing else can catch a mistake here. A repair declared in the wrong position
 * changes what runs before what, silently, on a machine that is not this one - and
 * the only symptom is a history that comes out slightly wrong on an upgrade path
 * nobody re-treads.
 */
class MaintenanceOrderTest {

    private val repairs = MasroufApp.Repair.entries

    /**
     * Declaration order is dependency order, and the set is executed in it. The
     * argument, in four lines: nothing may read a body holding a credential, so the
     * purge is first; a repair reads what the purge leaves; a retype reads the
     * amounts and parties a repair corrects; and filing reads the merchant and the
     * type everything before it corrects, so it is last.
     */
    @Test
    fun `repairs are declared in the order they must run`() {
        assertEquals(
            listOf(
                MasroufApp.Repair.PURGE_REJECTED,
                MasroufApp.Repair.REPAIR_AMOUNTS,
                MasroufApp.Repair.REPAIR_PARTIES,
                MasroufApp.Repair.BACKFILL_BALANCES,
                MasroufApp.Repair.REPARSE_BODIES,
                MasroufApp.Repair.RETYPE_SALARY,
                MasroufApp.Repair.RETYPE_OWN_MONEY,
                MasroufApp.Repair.REFILE_ALL,
            ),
            repairs,
        )
    }

    /** Filing reads what every other repair writes, so nothing may follow it. */
    @Test
    fun `filing is last`() {
        assertEquals(MasroufApp.Repair.REFILE_ALL, repairs.last())
    }

    /** A credential must not survive to be read by the repairs that follow. */
    @Test
    fun `the purge is first`() {
        assertEquals(MasroufApp.Repair.PURGE_REJECTED, repairs.first())
    }

    /**
     * A repair introduced above the current version could never run: the set is
     * `done < introducedIn`, and `done` is stamped to the current version after
     * every launch. Off by one and the fix ships dead.
     */
    @Test
    fun `no repair is introduced beyond the current version`() {
        val beyond = repairs.filter { it.introducedIn > CURRENT_MAINTENANCE_VERSION }

        assertEquals(emptyList(), beyond, "these would never run")
    }

    /**
     * The version an install stamps must be at least the newest repair, or the
     * newest fix is skipped on every machine that is already current.
     */
    @Test
    fun `the current version covers the newest repair`() {
        assertTrue(CURRENT_MAINTENANCE_VERSION >= repairs.maxOf { it.introducedIn })
    }

    /**
     * A fresh install runs nothing: `done` starts at 0, so every repair is selected
     * - correctly, since the set is empty of rows. The assertion that matters is
     * that selecting them all is CHEAP, which it is only because each is idempotent
     * against an empty table. Recorded here because the invariant it rests on is
     * written nowhere else: every repair corrects what an OLDER pipeline stored,
     * and the current pipeline does not produce it.
     */
    @Test
    fun `an install at the current version needs no repair`() {
        val needed = repairs.filter { CURRENT_MAINTENANCE_VERSION < it.introducedIn }

        assertEquals(emptyList(), needed)
    }

    /** Each repair appears once. A duplicate would scan the table twice again. */
    @Test
    fun `no repair is declared twice`() {
        assertEquals(repairs.size, repairs.toSet().size)
    }
}
