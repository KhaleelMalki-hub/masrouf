package sa.masrouf.app.data

import android.app.backup.BackupAgent
import android.app.backup.BackupDataInput
import android.app.backup.BackupDataOutput
import android.app.backup.FullBackupDataOutput
import android.database.sqlite.SQLiteDatabase
import android.os.ParcelFileDescriptor
import android.util.Log

/**
 * Folds the write-ahead log back into the database before the backup copies it.
 *
 * Room runs in WAL mode, so at any moment the committed history is spread across
 * two files: `masrouf.db` and `masrouf.db-wal`. Both are in the backup, which is
 * enough for correctness - SQLite validates the log's frames and replays it - but
 * it is not enough for safety. The system copies the two files one after the other,
 * and if SQLite happens to be checkpointing in between, the main file is read while
 * pages are being written into it. There is no error for that. It restores.
 *
 * A `TRUNCATE` checkpoint before the copy closes it: everything moves into the main
 * file, the log is emptied, and what the backup reads is one consistent file plus
 * an empty one. It also makes the payload as small as it can be, which matters
 * against Auto Backup's 25 MB ceiling - a dataset over it is not backed up at all,
 * and the system says so only to `onQuotaExceeded`.
 *
 * The checkpoint is best-effort by design. If the database cannot be opened - it
 * does not exist yet on a fresh install, or another process holds it - the backup
 * still runs on the files as they are, which is the behaviour there would be
 * without this class. Refusing to back up because the optimisation failed would
 * trade a small risk for a certain one.
 *
 * Only full backup is declared, so the key/value pair below is never called. They
 * are abstract on [BackupAgent] and have to be here.
 */
class MasroufBackupAgent : BackupAgent() {

    override fun onFullBackup(data: FullBackupDataOutput) {
        checkpoint()
        super.onFullBackup(data)
    }

    private fun checkpoint() {
        val file = getDatabasePath(MasroufDatabase.NAME)
        if (!file.exists()) return
        try {
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
                db.execSQL("PRAGMA wal_checkpoint(TRUNCATE)")
            }
        } catch (e: RuntimeException) {
            // Any failure here leaves both files in the backup, which is the state
            // this class exists to improve on rather than to depend on.
            Log.w(TAG, "could not checkpoint before backup; copying the log as it stands", e)
        }
    }

    override fun onBackup(
        oldState: ParcelFileDescriptor?,
        data: BackupDataOutput?,
        newState: ParcelFileDescriptor?,
    ) = Unit

    override fun onRestore(
        data: BackupDataInput?,
        appVersionCode: Int,
        newState: ParcelFileDescriptor?,
    ) = Unit

    private companion object {
        const val TAG = "MasroufBackup"
    }
}
