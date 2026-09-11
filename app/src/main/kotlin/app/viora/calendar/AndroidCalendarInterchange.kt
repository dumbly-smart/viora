package app.viora.calendar

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import app.viora.storage.VioraFileStore
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidCalendarInterchange(
    private val context: Context,
    store: ImportedCalendarStore,
) {
    private val repository = CalendarInterchangeRepository(store)
    private val fileStore = VioraFileStore(context.filesDir)
    private val deviceWriter = DeviceCalendarWriter(context)

    suspend fun importFrom(uri: Uri, now: Instant = Instant.now()): CalendarImportSummary = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { input -> readAtMost(input, MAX_IMPORT_BYTES) }
            ?: error("Could not open the selected calendar")
        repository.importIcs(bytes.toString(Charsets.UTF_8), now)
    }

    suspend fun writeTo(uri: Uri, calendarName: String, events: List<CalendarInterchangeEvent>): Int = withContext(Dispatchers.IO) {
        require(events.isNotEmpty()) { "No cached academic events are available" }
        val content = repository.exportIcs(calendarName, events)
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(content.toByteArray(Charsets.UTF_8)) }
            ?: error("Could not create the calendar file")
        events.size
    }

    suspend fun share(calendarName: String, events: List<CalendarInterchangeEvent>): Int {
        require(events.isNotEmpty()) { "No cached academic events are available" }
        val file = withContext(Dispatchers.IO) {
            val content = repository.exportIcs(calendarName, events)
            fileStore.calendarFile("viora-${calendarName.ifBlank { "timetable" }}.ics").also {
                it.writeText(content, Charsets.UTF_8)
            }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "text/calendar"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(share, "Share timetable").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return events.size
    }

    suspend fun exportToDevice(events: List<CalendarInterchangeEvent>): Int = withContext(Dispatchers.IO) {
        require(events.isNotEmpty()) { "No cached academic events are available" }
        deviceWriter.replace(events).getOrThrow()
    }

    companion object { const val MAX_IMPORT_BYTES = 5 * 1024 * 1024 }
}
