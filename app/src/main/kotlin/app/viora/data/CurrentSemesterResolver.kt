package app.viora.data

import app.viora.domain.AcademicDayKind
import app.viora.domain.ExamCalendarDate
import app.viora.domain.ExamWindow
import app.viora.domain.SemesterSelection
import app.viora.domain.finalExamDates
import app.viora.domain.kind
import app.viora.domain.semesterInstructionStart
import app.viora.network.AuthenticationException
import app.viora.network.SemesterOption
import app.viora.network.VtopGateway
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** VTOP can list next-term registration before the current term's FAT ends. */
class CurrentSemesterResolver(
    private val gateway: VtopGateway,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun resolve(
        options: List<SemesterOption>,
        cachedIds: Set<String>,
        savedId: String?,
        explicitlySelectedId: String? = null,
        cachedCalendar: List<ExamCalendarDate> = emptyList(),
        cachedExams: List<ExamWindow> = emptyList(),
    ): SemesterSelection {
        val ids = options.map { it.id }
        if (explicitlySelectedId in ids) return SemesterSelection(explicitlySelectedId, false)
        val zone = ZoneId.of("Asia/Kolkata")
        val today = Instant.ofEpochMilli(clock()).atZone(zone).toLocalDate()
        val savedIndex = ids.indexOf(savedId)
        if (savedIndex == 0 || options.isEmpty()) return SemesterSelection(ids.firstOrNull(), false)

        if (savedIndex > 0 && savedId != null) {
            val calendar = calendar(savedId) ?: cachedCalendar
            val exams = exams(savedId, zone) ?: cachedExams
            val finalDay = finalExamDates(exams, calendar, zone)?.second
                ?: return SemesterSelection(savedId, false)
            if (today <= finalDay) return SemesterSelection(savedId, false)

            // VTOP can list several terms. Wait for instruction in the next
            // published term before displaying its weekly timetable.
            var next: Pair<String, LocalDate>? = null
            for (option in options.take(savedIndex).asReversed()) {
                val candidate = calendar(option.id) ?: continue
                val begins = instructionDate(candidate) ?: continue
                if (begins <= finalDay || begins > today) continue
                val ends = finalExamDates(emptyList(), candidate, zone)?.second
                if (ends != null && today > ends) continue
                if (next == null || begins < next.second) next = option.id to begins
            }
            return SemesterSelection(next?.first ?: savedId, next != null)
        }

        // On first setup prefer the latest term whose instruction has started.
        // Overlapping registration calendars do not override an ongoing FAT.
        for (option in options) {
            val calendar = calendar(option.id) ?: continue
            val begins = instructionDate(calendar) ?: continue
            val ends = finalExamDates(emptyList(), calendar, zone)?.second
            if (begins <= today && (ends == null || today <= ends)) {
                return SemesterSelection(option.id, cachedIds.isNotEmpty() && option.id !in cachedIds)
            }
        }
        return SemesterSelection(ids.first(), false)
    }

    private fun instructionDate(calendar: List<ExamCalendarDate>): LocalDate? =
        semesterInstructionStart(calendar)
            ?: calendar.filter { it.kind() == AcademicDayKind.INSTRUCTIONAL }.minOfOrNull { it.date }

    private suspend fun calendar(id: String): List<ExamCalendarDate>? = try {
        gateway.academicCalendar(id).map { ExamCalendarDate(it.date, it.title, it.dayType) }.takeIf { it.isNotEmpty() }
    } catch (error: IOException) {
        if (error is AuthenticationException) throw error
        null
    }

    private suspend fun exams(id: String, zone: ZoneId): List<ExamWindow>? = try {
        gateway.exams(id).map {
            ExamWindow(it.startsAt.atZone(zone).toInstant().toEpochMilli(),
                it.endsAt?.atZone(zone)?.toInstant()?.toEpochMilli(), it.examType)
        }.takeIf { it.isNotEmpty() }
    } catch (error: IOException) {
        if (error is AuthenticationException) throw error
        null
    }
}

internal suspend fun resolveCurrentSemester(
    graph: app.viora.VioraGraph,
    options: List<SemesterOption>,
): SemesterSelection {
    val dao = graph.database.academicDao()
    val saved = graph.settings.getString(app.viora.VioraGraph.KEY_SEMESTER_ID, null)
    val manual = graph.settings.getString(app.viora.VioraGraph.KEY_MANUAL_SEMESTER_ID, null)
    return CurrentSemesterResolver(graph.gateway).resolve(
        options, dao.semesterSnapshot().map { it.id }.toSet(), saved, manual,
        cachedCalendar = saved?.let { dao.calendarSnapshot(it) }.orEmpty().map {
            ExamCalendarDate(LocalDate.ofEpochDay(it.dateEpochDay), it.title, it.dayType)
        },
        cachedExams = saved?.let { dao.examSnapshot(it) }.orEmpty().map {
            ExamWindow(it.startsEpochMillis, it.endsEpochMillis, it.examType)
        },
    )
}
