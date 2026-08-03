package app.viora.model

import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime

data class Course(
    val id: String,
    val code: String,
    val title: String,
    val faculty: String,
)

data class ClassSlot(
    val id: String,
    val courseId: String,
    val day: DayOfWeek,
    val start: LocalTime,
    val end: LocalTime,
    val venue: String,
    val type: ClassType,
)

enum class ClassType { THEORY, LAB, PROJECT, UNKNOWN }

data class Deadline(
    val id: String,
    val courseId: String,
    val title: String,
    val dueAt: LocalDateTime,
    val submitted: Boolean,
)

data class Exam(
    val id: String,
    val courseId: String,
    val type: String,
    val startsAt: LocalDateTime,
    val venue: String?,
    val seatNumber: String?,
)
