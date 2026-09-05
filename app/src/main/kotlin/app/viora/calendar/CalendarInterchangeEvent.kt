package app.viora.calendar

import java.time.Instant

data class CalendarInterchangeEvent(
    val uid: String,
    val title: String,
    val details: String = "",
    val location: String = "",
    val startsAt: Instant,
    val endsAt: Instant,
    val imported: Boolean = false,
)

data class IcsDecodeResult(
    val events: List<CalendarInterchangeEvent>,
    val skipped: Int,
)
