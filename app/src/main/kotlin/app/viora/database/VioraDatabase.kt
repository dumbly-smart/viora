package app.viora.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [SemesterEntity::class, CourseEntity::class, ClassSlotEntity::class, SyncResourceEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class VioraDatabase : RoomDatabase() {
    abstract fun academicDao(): AcademicDao

    companion object {
        @Volatile private var instance: VioraDatabase? = null

        fun get(context: Context): VioraDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                VioraDatabase::class.java,
                "viora.db",
            ).build().also { instance = it }
        }

        fun closeAndForget() = synchronized(this) {
            instance?.close()
            instance = null
        }
    }
}
