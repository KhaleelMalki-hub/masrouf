package sa.masrouf.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [TransactionEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class MasroufDatabase : RoomDatabase() {

    abstract fun transactions(): TransactionDao

    companion object {
        private const val NAME = "masrouf.db"

        /**
         * Opens the on-device database.
         *
         * No `fallbackToDestructiveMigration`. This is the user's only copy of their
         * own financial history and there is no server to restore it from, so a
         * missing migration must fail loudly at open time rather than silently
         * delete every transaction they have ever recorded.
         */
        fun open(context: Context): MasroufDatabase =
            Room.databaseBuilder(context.applicationContext, MasroufDatabase::class.java, NAME)
                .build()
    }
}
