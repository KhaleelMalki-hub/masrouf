package sa.masrouf.app

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What leaves the phone, stated once and asserted against the files that ship.
 *
 * The owner asked for the app's data to travel in Android's own backup, which is
 * what makes a new phone possible: 185 categories he filed by hand and 35 merchant
 * rules he taught the app exist in no message and cannot be recaptured from the
 * inbox. That decision reverses the "nothing leaves the device" line this project
 * held until 2026-09-10, so the boundary is now an allow-list rather than a habit,
 * and this test is what keeps the allow-list honest.
 *
 * Two rule files say the same thing because the platform requires two: Android 12
 * reads `data_extraction_rules.xml` and everything from API 26 to 30 reads
 * `backup_content.xml`. One truth in two hand-written copies is the shape that goes
 * wrong silently - the older file is read only by an older phone, which nobody
 * tests on - so the assertion below is IDENTITY between them, not that each is
 * separately plausible.
 */
class BackupRulesTest {

    private val manifest = File("src/main/AndroidManifest.xml").readText()
    private val legacy = File("src/main/res/xml/backup_content.xml").readText()
    private val modern = File("src/main/res/xml/data_extraction_rules.xml").readText()

    /** `<include domain="d" path="p"/>` pairs, in file order. */
    private fun includes(xml: String): List<Pair<String, String>> =
        Regex("""<include\s+domain="([^"]+)"\s+path="([^"]+)"\s*/>""")
            .findAll(xml)
            .map { it.groupValues[1] to it.groupValues[2] }
            .toList()

    @Test
    fun `the manifest opts in and names both rule files and the agent`() {
        assertTrue(
            """android:allowBackup="true"""" in manifest,
            "backup is off; the owner asked for it on",
        )
        assertTrue(
            """android:dataExtractionRules="@xml/data_extraction_rules"""" in manifest,
            "Android 12 and up would fall back to backing up everything",
        )
        assertTrue(
            """android:fullBackupContent="@xml/backup_content"""" in manifest,
            "API 26-30 reads the legacy file and would otherwise back up everything",
        )
        assertTrue(
            """android:backupAgent=".data.MasroufBackupAgent"""" in manifest,
            "without the agent the database is copied mid-checkpoint",
        )
    }

    /**
     * The two files list the same paths.
     *
     * `data_extraction_rules.xml` says everything twice itself - once for the cloud
     * and once for a phone-to-phone transfer - so the modern file's includes are
     * compared as a set against the legacy file's.
     */
    @Test
    fun `the legacy file and the android 12 file back up the same things`() {
        assertEquals(
            includes(legacy).toSet(),
            includes(modern).toSet(),
            "the two backup rule files have drifted",
        )
    }

    @Test
    fun `both transfer routes are described, and the cloud one refuses to go unencrypted`() {
        assertTrue("<cloud-backup" in modern, "no cloud rules means no cloud backup at all")
        assertTrue("<device-transfer>" in modern, "phone-to-phone transfer would be off")
        assertTrue(
            """disableIfNoEncryptionCapabilities="true"""" in modern,
            "twelve years of financial history must not leave the phone in the clear",
        )
    }

    /**
     * The database, its write-ahead log, and the settings - and nothing else.
     *
     * The log is listed because Room runs in WAL mode and a write made since the
     * last checkpoint lives only there; restoring the database without it silently
     * rolls the newest transactions back. The shared-memory file is deliberately
     * absent: SQLite rebuilds it, and a stale one restored beside a fresh database
     * is a liability rather than a saving.
     */
    @Test
    fun `the allow-list is the database, its log and the settings`() {
        val expected = setOf(
            "database" to "masrouf.db",
            "database" to "masrouf.db-wal",
            "sharedpref" to "masrouf.settings.xml",
        )
        assertEquals(expected, includes(legacy).toSet())
        assertTrue(
            includes(legacy).none { it.second.endsWith("-shm") },
            "the shared-memory file is rebuilt, not restored",
        )
    }
}
