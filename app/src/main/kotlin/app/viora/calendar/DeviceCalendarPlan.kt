package app.viora.calendar

import java.net.URLEncoder

data class PlannedDeviceCalendarEvent(
    val ownershipUri: String,
    val title: String,
    val details: String,
    val location: String,
    val startMillis: Long,
    val endMillis: Long,
)

data class DeviceCalendarPlan(
    val accountName: String,
    val displayName: String,
    val timeZone: String,
    val events: List<PlannedDeviceCalendarEvent>,
) {
    companion object {
        const val ACCOUNT_NAME = "Viora"
        const val CALENDAR_NAME = "viora-timetable"
        const val DISPLAY_NAME = "Viora timetable"
        const val TIME_ZONE = "Asia/Kolkata"

        fun create(events: List<CalendarInterchangeEvent>): DeviceCalendarPlan = DeviceCalendarPlan(
            accountName = ACCOUNT_NAME,
            displayName = DISPLAY_NAME,
            timeZone = TIME_ZONE,
            events = events.map { event ->
                PlannedDeviceCalendarEvent(
                    ownershipUri = "viora://calendar/${URLEncoder.encode(event.uid, Charsets.UTF_8.name()).replace("+", "%20")}",
                    title = event.title,
                    details = event.details,
                    location = event.location,
                    startMillis = event.startsAt.toEpochMilli(),
                    endMillis = event.endsAt.toEpochMilli(),
                )
            },
        )
    }
}
