package app.viora

import android.content.Context
import app.viora.auth.SessionManager
import app.viora.data.TimetableRepository
import app.viora.data.AttendanceRepository
import app.viora.data.DigitalAssignmentRepository
import app.viora.data.ExamRepository
import app.viora.data.ResultsRepository
import app.viora.data.AcademicExtrasRepository
import app.viora.data.CourseMaterialManager
import app.viora.database.VioraDatabase
import app.viora.network.HttpVtopGateway
import app.viora.network.IsolatedCookieJar
import app.viora.network.VioraHttpClient
import app.viora.network.VtopCaptchaSolver
import app.viora.security.AndroidKeystoreCipher
import app.viora.security.CredentialVault
import app.viora.security.EncryptedCookieStore
import app.viora.security.EncryptedPreferencesBlobStore
import app.viora.sync.TimetableSyncCoordinator
import app.viora.notifications.VioraNotifications
import app.viora.auth.LocalAccountManager
import app.viora.share.TimetableQrShare

class VioraGraph(context: Context) {
    private val appContext = context.applicationContext
    val secureBlobs = EncryptedPreferencesBlobStore(
        appContext,
        AndroidKeystoreCipher("viora.local.v1"),
    )
    val credentials = CredentialVault(secureBlobs)
    private val cookieStore = EncryptedCookieStore(secureBlobs)
    private val cookieJar = IsolatedCookieJar(cookieStore)
    val gateway = HttpVtopGateway(
        VioraHttpClient.create(cookieJar),
        cookieJar,
        VtopCaptchaSolver.fromAssets(appContext.assets),
    )
    val database = VioraDatabase.get(appContext)
    val timetable = TimetableRepository(database.academicDao(), gateway)
    val attendance = AttendanceRepository(database.academicDao(), gateway)
    val assignments = DigitalAssignmentRepository(database.academicDao(), gateway)
    val exams = ExamRepository(database.academicDao(), gateway)
    val results = ResultsRepository(database.academicDao(), gateway)
    val extras = AcademicExtrasRepository(database.academicDao(), gateway)
    val materialManager = CourseMaterialManager(appContext, gateway)
    val timetableQr = TimetableQrShare(appContext)
    val notifications = VioraNotifications(appContext, database.academicDao())
    val sessions = SessionManager(gateway, credentials)
    val account = LocalAccountManager(appContext, gateway, credentials, secureBlobs)
    val timetableSync = TimetableSyncCoordinator(sessions, timetable)
    val settings = appContext.getSharedPreferences(SETTINGS_NAME, Context.MODE_PRIVATE)

    companion object {
        const val SETTINGS_NAME = "viora_local_settings"
        const val KEY_CONFIGURED = "configured"
        const val KEY_SEMESTER_ID = "active_semester_id"
        const val KEY_SEMESTER_NAME = "active_semester_name"
    }
}
