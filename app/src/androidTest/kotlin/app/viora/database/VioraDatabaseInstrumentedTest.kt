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
}
