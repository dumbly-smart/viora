package app.viora.domain

import app.viora.VioraAppViewModel
import app.viora.database.SlotWithCourse
import java.time.LocalDate

enum class ClassPhase { UPCOMING, LIVE, ENDED }

fun classPhase(startMinute: Int, endMinute: Int, nowMinute: Int): ClassPhase = when {
    nowMinute < startMinute -> ClassPhase.UPCOMING
    nowMinute <= endMinute -> ClassPhase.LIVE
    else -> ClassPhase.ENDED
}

fun focusedSlots(slots: List<SlotWithCourse>, nowMinute: Int): List<SlotWithCourse> {
    val sorted = slots.sortedBy(SlotWithCourse::startMinute)
    val live = sorted.filter { classPhase(it.startMinute, it.endMinute, nowMinute) == ClassPhase.LIVE }
    if (live.isNotEmpty()) return live
    return sorted.firstOrNull { it.startMinute > nowMinute }?.let(::listOf).orEmpty()
}

fun classCheckInKey(date: LocalDate, slotId: String): String =
    "${VioraAppViewModel.CLASS_CHECK_IN_PREFIX}$date:$slotId"
