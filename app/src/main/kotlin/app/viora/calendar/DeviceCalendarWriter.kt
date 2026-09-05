package app.viora.calendar

import android.Manifest
import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat

class DeviceCalendarWriter(private val context: Context) {
    fun replace(events: List<CalendarInterchangeEvent>): Result<Int> = runCatching {
        requireCalendarPermission()
        val plan = DeviceCalendarPlan.create(events)
        val calendarId = findCalendarId() ?: createCalendar(plan)
        val operations = arrayListOf<ContentProviderOperation>().apply {
            add(
                ContentProviderOperation.newDelete(CalendarContract.Events.CONTENT_URI)
                    .withSelection("${CalendarContract.Events.CALENDAR_ID} = ?", arrayOf(calendarId.toString()))
                    .build(),
            )
            plan.events.forEach { event ->
                add(
                    ContentProviderOperation.newInsert(CalendarContract.Events.CONTENT_URI)
                        .withValue(CalendarContract.Events.CALENDAR_ID, calendarId)
                        .withValue(CalendarContract.Events.TITLE, event.title)
                        .withValue(CalendarContract.Events.DESCRIPTION, event.details)
                        .withValue(CalendarContract.Events.EVENT_LOCATION, event.location)
                        .withValue(CalendarContract.Events.DTSTART, event.startMillis)
                        .withValue(CalendarContract.Events.DTEND, event.endMillis)
                        .withValue(CalendarContract.Events.EVENT_TIMEZONE, plan.timeZone)
                        .withValue(CalendarContract.Events.CUSTOM_APP_PACKAGE, context.packageName)
                        .withValue(CalendarContract.Events.CUSTOM_APP_URI, event.ownershipUri)
                        .build(),
                )
            }
        }
        context.contentResolver.applyBatch(CalendarContract.AUTHORITY, operations)
        plan.events.size
    }

    private fun findCalendarId(): Long? {
        val projection = arrayOf(CalendarContract.Calendars._ID)
        val selection = "${CalendarContract.Calendars.ACCOUNT_NAME} = ? AND ${CalendarContract.Calendars.ACCOUNT_TYPE} = ? AND ${CalendarContract.Calendars.NAME} = ?"
        val args = arrayOf(DeviceCalendarPlan.ACCOUNT_NAME, CalendarContract.ACCOUNT_TYPE_LOCAL, DeviceCalendarPlan.CALENDAR_NAME)
        return context.contentResolver.query(CalendarContract.Calendars.CONTENT_URI, projection, selection, args, null)?.use { cursor ->
            cursor.takeIf { it.moveToFirst() }?.getLong(0)
        }
    }

    private fun createCalendar(plan: DeviceCalendarPlan): Long {
        val values = ContentValues().apply {
            put(CalendarContract.Calendars.ACCOUNT_NAME, plan.accountName)
            put(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            put(CalendarContract.Calendars.NAME, DeviceCalendarPlan.CALENDAR_NAME)
            put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, plan.displayName)
            put(CalendarContract.Calendars.CALENDAR_COLOR, 0xff3f51b5.toInt())
            put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.CAL_ACCESS_OWNER)
            put(CalendarContract.Calendars.OWNER_ACCOUNT, plan.accountName)
            put(CalendarContract.Calendars.VISIBLE, 1)
            put(CalendarContract.Calendars.SYNC_EVENTS, 1)
            put(CalendarContract.Calendars.CALENDAR_TIME_ZONE, plan.timeZone)
        }
        val uri = CalendarContract.Calendars.CONTENT_URI.buildUpon()
            .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, plan.accountName)
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            .build()
        val inserted = requireNotNull(context.contentResolver.insert(uri, values)) { "Could not create Viora timetable calendar" }
        return ContentUris.parseId(inserted)
    }

    private fun requireCalendarPermission() {
        check(ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED) {
            "Calendar permission is required"
        }
    }
}
