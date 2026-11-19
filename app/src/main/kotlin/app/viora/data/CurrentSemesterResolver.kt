package app.viora.data

import app.viora.domain.AcademicDayKind
import app.viora.domain.ExamCalendarDate
import app.viora.domain.ExamWindow
import app.viora.domain.SemesterSelection
import app.viora.domain.finalExamDates
import app.viora.domain.kind
import app.viora.domain.programFatCompletionMillis
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
        val now = clock()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val savedIndex = ids.indexOf(savedId)
        if (savedIndex == 0 || options.isEmpty()) return SemesterSelection(ids.firstOrNull(), false)

        if (savedIndex > 0 && savedId != null) {
            val calendar = calendar(savedId) ?: cachedCalendar
            val exams = exams(savedId, zone) ?: cachedExams
            val fatEnd = programFatCompletionMillis(exams, calendar, zone)
            if (fatEnd == null || now < fatEnd) return SemesterSelection(savedId, false)

            // VTOP lists newer terms ahead of the saved term. Skip an
            // intervening term only if its FAT is already known to be over.
            for (option in options.take(savedIndex).asReversed()) {
                val candidateCalendar = calendar(option.id).orEmpty()
                val candidateEnd = programFatCompletionMillis(emptyList(), candidateCalendar, zone)
                if (candidateEnd == null || now < candidateEnd) return SemesterSelection(option.id, true)
            }
            return SemesterSelection(savedId, false)
        }

        // On first setup use the latest term that has started, while
        // giving an ongoing FAT in the immediately preceding term priority.
        var active: String? = null
        for (option in options) {
            val calendar = calendar(option.id).orEmpty()
            val begins = instructionDate(calendar)
            if (begins != null && begins > today) continue
            val personalExams = if (finalExamDates(emptyList(), calendar, zone) == null) exams(option.id, zone).orEmpty()
                else emptyList()
            val final = finalExamDates(personalExams, calendar, zone)
            val fatEnd = programFatCompletionMillis(personalExams, calendar, zone)
            if (final != null && today >= final.first && (fatEnd == null || now < fatEnd)) {
                active = option.id
                break
            }
            if (active != null) break
            if (begins != null && (final == null || today < final.first)) active = option.id
        }
        val selected = active ?: ids.first()
        return SemesterSelection(selected, active != null && cachedIds.isNotEmpty() && selected !in cachedIds)
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
