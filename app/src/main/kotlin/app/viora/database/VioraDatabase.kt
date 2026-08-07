package app.viora.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        SemesterEntity::class,
        CourseEntity::class,
        ClassSlotEntity::class,
        SyncResourceEntity::class,
        AttendanceEntity::class,
    ],
    version = 2,
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
            ).addMigrations(MIGRATION_1_2).build().also { instance = it }
        }

        fun closeAndForget() = synchronized(this) {
            instance?.close()
            instance = null
        }


        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `attendance` (
                        `semesterId` TEXT NOT NULL,
                        `courseCode` TEXT NOT NULL,
                        `courseTitle` TEXT NOT NULL,
                        `attended` INTEGER NOT NULL,
                        `held` INTEGER NOT NULL,
                        `sourceEpochMillis` INTEGER NOT NULL,
                        PRIMARY KEY(`semesterId`, `courseCode`)
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_attendance_semesterId` ON `attendance` (`semesterId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_attendance_courseCode` ON `attendance` (`courseCode`)")
            }
        }
    }
}
