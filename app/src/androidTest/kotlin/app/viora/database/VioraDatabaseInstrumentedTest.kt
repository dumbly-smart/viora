package app.viora.database

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VioraDatabaseInstrumentedTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun cached_semester_is_readable_without_network() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, VioraDatabase::class.java).allowMainThreadQueries().build()
        db.academicDao().upsertSemester(SemesterEntity("offline", "Offline Semester", true))
        assertEquals("offline", db.academicDao().observeSemesters().first().single().id)
        db.close()
    }

    @Test fun migration_5_6_adds_change_ledger() {
        val name = "migration-5-6.db"
        context.deleteDatabase(name)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(object : SupportSQLiteOpenHelper.Callback(5) {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) = Unit
                override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build(),
        )
        val db = helper.writableDatabase
        VioraDatabase.MIGRATION_5_6.migrate(db)
        val cursor = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='academic_changes'")
        assertTrue(cursor.moveToFirst())
        cursor.close(); helper.close(); context.deleteDatabase(name)
    }

    @Test fun migration_6_7_adds_vtop_exam_end_time_without_erasing_rows() {
        val name = "migration-6-7.db"
        context.deleteDatabase(name)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(object : SupportSQLiteOpenHelper.Callback(6) {
                override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE exams (semesterId TEXT NOT NULL, id TEXT NOT NULL, courseCode TEXT NOT NULL, courseTitle TEXT NOT NULL, examType TEXT NOT NULL, startsEpochMillis INTEGER NOT NULL, venue TEXT NOT NULL, seatNumber TEXT NOT NULL, sourceEpochMillis INTEGER NOT NULL, PRIMARY KEY(semesterId, id))")
                    db.execSQL("INSERT INTO exams VALUES ('semester', 'exam', 'CODE', 'Course', 'Exam', 1000, 'Room', '1', 1000)")
                }
                override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build(),
        )
        val db = helper.writableDatabase
        VioraDatabase.MIGRATION_6_7.migrate(db)
        val cursor = db.query("SELECT id, endsEpochMillis FROM exams")
        assertTrue(cursor.moveToFirst())
        assertEquals("exam", cursor.getString(0))
        assertTrue(cursor.isNull(1))
        cursor.close(); helper.close(); context.deleteDatabase(name)
    }
}
