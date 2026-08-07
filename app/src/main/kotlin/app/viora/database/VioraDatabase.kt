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
        MarkEntity::class,
        GradeEntity::class,
        AcademicSummaryEntity::class,
    ],
    version = 4,
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
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build().also { instance = it }
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

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE `attendance_new` (`semesterId` TEXT NOT NULL, `id` TEXT NOT NULL, `courseCode` TEXT NOT NULL, `courseTitle` TEXT NOT NULL, `courseType` TEXT NOT NULL, `faculty` TEXT NOT NULL, `attended` INTEGER NOT NULL, `held` INTEGER NOT NULL, `sourceEpochMillis` INTEGER NOT NULL, PRIMARY KEY(`semesterId`, `id`))")
                db.execSQL("INSERT INTO attendance_new SELECT semesterId, courseCode, courseCode, courseTitle, '', '', attended, held, sourceEpochMillis FROM attendance")
                db.execSQL("DROP TABLE attendance")
                db.execSQL("ALTER TABLE attendance_new RENAME TO attendance")
                db.execSQL("CREATE INDEX `index_attendance_semesterId` ON `attendance` (`semesterId`)")
                db.execSQL("CREATE INDEX `index_attendance_courseCode` ON `attendance` (`courseCode`)")
                db.execSQL("CREATE TABLE `marks` (`semesterId` TEXT NOT NULL, `id` TEXT NOT NULL, `courseCode` TEXT NOT NULL, `courseTitle` TEXT NOT NULL, `courseType` TEXT NOT NULL, `title` TEXT NOT NULL, `maxMarks` REAL, `weightagePercent` REAL, `status` TEXT NOT NULL, `scoredMark` REAL, `weightageMark` REAL, `sourceEpochMillis` INTEGER NOT NULL, PRIMARY KEY(`semesterId`, `id`))")
                db.execSQL("CREATE INDEX `index_marks_semesterId` ON `marks` (`semesterId`)")
                db.execSQL("CREATE INDEX `index_marks_courseCode` ON `marks` (`courseCode`)")
                db.execSQL("CREATE TABLE `grades` (`semesterId` TEXT NOT NULL, `courseCode` TEXT NOT NULL, `courseTitle` TEXT NOT NULL, `courseType` TEXT NOT NULL, `credits` REAL, `total` REAL, `grading` TEXT NOT NULL, `grade` TEXT NOT NULL, `sourceEpochMillis` INTEGER NOT NULL, PRIMARY KEY(`semesterId`, `courseCode`))")
                db.execSQL("CREATE INDEX `index_grades_semesterId` ON `grades` (`semesterId`)")
                db.execSQL("CREATE TABLE `academic_summaries` (`id` TEXT NOT NULL, `gpa` REAL, `cgpa` REAL, `registeredCredits` REAL, `earnedCredits` REAL, `gradeCounts` TEXT NOT NULL, `sourceEpochMillis` INTEGER NOT NULL, PRIMARY KEY(`id`))")
            }
        }
    }
}
