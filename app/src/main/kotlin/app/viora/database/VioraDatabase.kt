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
        DigitalAssignmentEntity::class,
        ExamEntity::class,
        NotificationLedgerEntity::class,
    ],
    version = 3,
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
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
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

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `digital_assignments` (`semesterId` TEXT NOT NULL, `id` TEXT NOT NULL, `courseCode` TEXT NOT NULL, `title` TEXT NOT NULL, `dueEpochMillis` INTEGER, `lastUpload` TEXT NOT NULL, `status` TEXT NOT NULL, `sourceEpochMillis` INTEGER NOT NULL, PRIMARY KEY(`semesterId`, `id`))",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_digital_assignments_semesterId` ON `digital_assignments` (`semesterId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_digital_assignments_dueEpochMillis` ON `digital_assignments` (`dueEpochMillis`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `exams` (`semesterId` TEXT NOT NULL, `id` TEXT NOT NULL, `courseCode` TEXT NOT NULL, `courseTitle` TEXT NOT NULL, `examType` TEXT NOT NULL, `startsEpochMillis` INTEGER NOT NULL, `venue` TEXT NOT NULL, `seatNumber` TEXT NOT NULL, `sourceEpochMillis` INTEGER NOT NULL, PRIMARY KEY(`semesterId`, `id`))",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_exams_semesterId` ON `exams` (`semesterId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_exams_startsEpochMillis` ON `exams` (`startsEpochMillis`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `notification_ledger` (`key` TEXT NOT NULL, `notifiedEpochMillis` INTEGER NOT NULL, PRIMARY KEY(`key`))",
                )
            }
        }
    }
}
