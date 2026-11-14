package app.viora

import kotlin.math.abs

internal data class TimelineViewportCourse(
    val key: String,
    val top: Int,
    val height: Int,
)

internal fun focusedTimelineCourseKey(
    viewportStart: Int,
    viewportEnd: Int,
    courses: List<TimelineViewportCourse>,
): String? {
    val viewportCenter = (viewportStart + viewportEnd) / 2
    return courses.minByOrNull { course -> abs((course.top + course.height / 2) - viewportCenter) }?.key
}
