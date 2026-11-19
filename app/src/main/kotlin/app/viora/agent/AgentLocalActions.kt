package app.viora.agent

import app.viora.VioraGraph
import app.viora.auth.SessionResolution
import app.viora.database.CourseMaterialEntity
import app.viora.domain.AcademicClassCalendar
import app.viora.domain.ExamCalendarDate
import app.viora.domain.ExamWindow
import app.viora.domain.sameCourseCode
import app.viora.domain.semesterInstructionStart
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

internal data class AgentMaterial(val material: CourseMaterialEntity, val course: String)
internal data class AgentDownloadPlan(val files: List<AgentMaterial>, val note: String)
internal data class AgentDownloadResult(val files: List<AgentMaterial>, val summary: String)

/** Results stay in the native agent view and are never sent back to the model. */
internal class AgentLocalActions(private val graph: VioraGraph) {
    private val dao get() = graph.database.academicDao()
    private val zone = ZoneId.of("Asia/Kolkata")
    private val dateTime = DateTimeFormatter.ofPattern("EEE, d MMM · h:mm a")
    private fun format(millis: Long) = Instant.ofEpochMilli(millis).atZone(zone).format(dateTime)
    private fun semester() = graph.settings.getString(VioraGraph.KEY_SEMESTER_ID, null)
        ?: throw AgentFailure("Sync a semester in Viora first.")

    suspend fun read(action: AgentAction.Read): String {
        val id = semester()
        val now = System.currentTimeMillis()
        fun matches(code: String, title: String) = matchesCourse(action.course, code, title)
        val courses = dao.courses(id).filter { matches(it.code, it.title) }
        val records = when (action.data) {
            AgentData.EXAMS -> dao.examSnapshot(id).filter {
                matches(it.courseCode, it.courseTitle) && (it.endsEpochMillis ?: it.startsEpochMillis) >= now
            }.sortedBy { it.startsEpochMillis }.map {
                "${it.courseTitle} · ${it.examType}\n${format(it.startsEpochMillis)} · ${it.venue}"
            }
            AgentData.ATTENDANCE -> dao.attendanceSnapshot(id).filter { matches(it.courseCode, it.courseTitle) }.map {
                "${it.courseTitle} · ${it.courseType}\n${it.attended}/${it.held} classes attended"
            }
            AgentData.MARKS -> dao.markSnapshot(id).filter { matches(it.courseCode, it.courseTitle) }.map {
                "${it.courseTitle} · ${it.title}\n${it.scoredMark ?: it.status} / ${it.maxMarks ?: "—"}"
            }
            AgentData.GRADES -> dao.gradeSnapshot(id).filter { matches(it.courseCode, it.courseTitle) }.map {
                "${it.courseTitle} · ${it.grade}"
            }
            AgentData.ASSIGNMENTS -> dao.assignmentSnapshot(id).filter { matches(it.courseCode, it.courseTitle) }
                .sortedBy { it.dueEpochMillis ?: Long.MAX_VALUE }.map {
                    "${it.title} · ${it.courseTitle}\n${it.dueEpochMillis?.let(::format) ?: "No due date"} · ${it.status}"
                }
            AgentData.CALENDAR -> {
                val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
                dao.calendarSnapshot(id).filter { it.dateEpochDay >= today.toEpochDay() && it.title.isNotBlank() }
                    .sortedBy { it.dateEpochDay }.map {
                        "${LocalDate.ofEpochDay(it.dateEpochDay)} · ${it.title}"
                    }
            }
            AgentData.MESSAGES -> dao.messageSnapshot().filter { row ->
                courses.any { sameCourseCode(it.code, row.courseCode) }
            }.sortedByDescending { it.postedEpochMillis }.map { "${it.subject}\n${it.body.take(240)}" }
            AgentData.MATERIALS -> courses.distinctBy { it.code }.flatMap { course ->
                dao.materialSnapshot(id, course.code).map { "${course.title} · ${it.title}" }
            }
            AgentData.SCHEDULE -> {
                val calendar = dao.calendarSnapshot(id).map {
                    ExamCalendarDate(LocalDate.ofEpochDay(it.dateEpochDay), it.title, it.dayType)
                }
                val exams = dao.examSnapshot(id).map { ExamWindow(it.startsEpochMillis, it.endsEpochMillis, it.examType) }
                val rules = AcademicClassCalendar(calendar, exams, zone)
                val slots = dao.timetableSnapshot(id).filter { matches(it.code, it.title) }
                val date = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
                (0L..6L).flatMap { offset ->
                    val day = date.plusDays(offset)
                    val order = rules.timetableDay(day)?.value
                    slots.filter { it.dayOfWeek == order }.sortedBy { it.startMinute }.mapNotNull {
                        val start = day.atStartOfDay(zone).plusMinutes(it.startMinute.toLong()).toInstant().toEpochMilli()
                        if (start < now) null else "${it.title} · ${it.type}\n${format(start)} · ${it.venue}"
                    }
                }
            }
        }
        if (records.isEmpty()) return "Viora found no matching ${action.data.name.lowercase()} in the current semester."
        return records.take(12).joinToString("\n\n").let { shown ->
            if (records.size <= 12) shown else "$shown\n\nShowing 12 of ${records.size} matches."
        }
    }

    suspend fun prepareDownloads(action: AgentAction.Download, progress: (String) -> Unit): AgentDownloadPlan {
        val id = semester()
        val courses = dao.courses(id).filter { matchesCourse(action.course, it.code, it.title) }
            .distinctBy { it.code to it.faculty }
            .filter { action.faculty.isBlank() || facultyMatches(it.faculty, action.faculty) }
        if (courses.isEmpty()) throw AgentFailure("No enrolled course matches those course or teacher details.")
        if (action.faculty.isNotBlank() && courses.map { it.faculty.trim().lowercase() }.distinct().size > 1) {
            throw AgentFailure("More than one teacher matches. Ask again with their full name or a course code.")
        }
        val assessment = normalizeAssessment(action.assessment)
        if (action.assessment.isNotBlank() && assessment == null) throw AgentFailure("Choose CAT-1, CAT-2, or FAT for an exam-based material search.")
        if (graph.sessions.ensureActive() != SessionResolution.Ready) {
            throw AgentFailure("VTOP sign-in needs attention. Open Viora and reconnect before downloading.")
        }
        val termStart = if (assessment != null) semesterInstructionStart(dao.calendarSnapshot(id).map {
            ExamCalendarDate(LocalDate.ofEpochDay(it.dateEpochDay), it.title, it.dayType)
        }) else null
        val messages = if (termStart == null) emptyList() else try {
            graph.gateway.classMessages()
                .filter { it.postedAt?.toLocalDate()?.let { date -> date >= termStart } == true }
        } catch (_: java.io.IOException) {
            emptyList()
        }
        val selected = linkedMapOf<String, AgentMaterial>()
        var unavailable = 0
        var noPortion = 0
        for (course in courses) {
            coroutineContext.ensureActive()
            progress("Finding materials in VTOP…")
            val records = try {
                graph.gateway.courseMaterials(id, course.code, course.title, course.faculty.takeIf { action.faculty.isNotBlank() }.orEmpty())
            } catch (_: java.io.IOException) {
                unavailable++
                continue
            }
            val portion = messages.asSequence().filter { sameCourseCode(it.courseCode, course.code) }
                .filter { course.faculty.isNotBlank() && facultyMatches(it.faculty, course.faculty) }
                .sortedByDescending { it.postedAt }
                .map {
                    assessmentModules("${it.subject} ${it.body}", assessment.orEmpty()).ifEmpty {
                        assessmentModules("${it.subject}: ${it.body}", assessment.orEmpty())
                    }
                }
                .firstOrNull { it.isNotEmpty() }.orEmpty()
            if (assessment != null && portion.isEmpty()) noPortion++
            for (row in records) {
                if (row.downloadPath.isBlank()) continue
                if (action.faculty.isNotBlank() && !facultyMatches(row.faculty, action.faculty)) continue
                val teacherModules = portion.takeIf { facultyMatches(row.faculty, course.faculty) }.orEmpty()
                if (assessment != null && !matchesAssessment(row.title, row.fileName, assessment, teacherModules)) continue
                val now = System.currentTimeMillis()
                val material = CourseMaterialEntity(id, row.id, course.code, row.title, row.fileName, row.downloadPath,
                    row.postedAt?.atZone(zone)?.toInstant()?.toEpochMilli(), now)
                selected.putIfAbsent(row.id, AgentMaterial(material, course.title))
            }
        }
        if (selected.isEmpty()) {
            throw AgentFailure(if (assessment != null) "Viora found no files with a verified $assessment label or matching modules in a VTOP faculty announcement."
                else "VTOP returned no matching files. ${if (unavailable > 0) "$unavailable course lookup(s) failed." else ""}".trim())
        }
        val note = buildList {
            if (noPortion > 0) add("For $noPortion course entries without a verified portion, only explicitly $assessment-labelled files were included.")
            if (unavailable > 0) add("VTOP could not load materials for $unavailable course entries.")
        }.joinToString(" ")
        return AgentDownloadPlan(selected.values.toList(), note)
    }

    suspend fun download(plan: AgentDownloadPlan, progress: (String) -> Unit): AgentDownloadResult {
        val saved = mutableListOf<AgentMaterial>()
        for ((index, entry) in plan.files.withIndex()) {
            coroutineContext.ensureActive()
            progress("Downloading ${index + 1} of ${plan.files.size}…")
            if (graph.materialManager.download(entry.material, entry.course).isSuccess) saved += entry
        }
        val summary = "${saved.size} of ${plan.files.size} matching files saved in Viora. ${plan.note}".trim()
        return AgentDownloadResult(saved, summary)
    }

    suspend fun openMaterial(entry: AgentMaterial) {
        if (graph.materialManager.open(entry.material, entry.course, share = false).isFailure) {
            throw AgentFailure("Viora couldn’t open this file.")
        }
    }

}

internal fun matchesCourse(query: String, code: String, title: String) =
    query.isBlank() || sameCourseCode(code, query) || title.contains(query, true)

internal fun facultyMatches(faculty: String, query: String): Boolean {
    val words = Regex("[\\p{L}\\p{N}]+")
    val expected = words.findAll(query.lowercase()).map { it.value }.filterNot { it in setOf("dr", "prof", "professor", "mr", "ms") }.toSet()
    val actual = words.findAll(faculty.lowercase()).map { it.value }.toSet()
    return expected.isNotEmpty() && actual.containsAll(expected)
}

internal fun normalizeAssessment(value: String): String? =
    when (value.uppercase().filter(Char::isLetterOrDigit)) {
        "CAT1", "CATI" -> "CAT-1"
        "CAT2", "CATII" -> "CAT-2"
        "FAT", "FINALASSESSMENTTEST" -> "FAT"
        else -> null
    }

private fun assessmentPattern(assessment: String): String = when (assessment) {
    "CAT-1" -> """\bCAT[\s_-]*(?:1|I)\b"""
    "CAT-2" -> """\bCAT[\s_-]*(?:2|II)\b"""
    "FAT" -> """\b(?:FAT|Final Assessment Test)\b"""
    else -> "(?!)"
}

internal fun assessmentModules(text: String, assessment: String): Set<Int> {
    val portion = Regex("""${assessmentPattern(assessment)}\s+(?:portions?|syllabus)\s*[:–-]\s*modules?\s+([\d,\s–-]+(?:and\s+[\d\s–-]+)?)""",
        RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1)?.trim()?.trimEnd(',') ?: return emptySet()
    val parts = portion.split(Regex("""\s*(?:,|and)\s*""", RegexOption.IGNORE_CASE))
    val modules = mutableSetOf<Int>()
    for (part in parts) {
        val range = part.split(Regex("""\s*[-–]\s*"""))
        if (range.size !in 1..2) return emptySet()
        val first = range.first().toIntOrNull() ?: return emptySet()
        val last = range.last().toIntOrNull() ?: return emptySet()
        if (first !in 1..30 || last !in first..30) return emptySet()
        modules += first..last
    }
    return modules
}

internal fun matchesAssessment(title: String, fileName: String, assessment: String, modules: Set<Int>): Boolean {
    val text = "$title $fileName"
    if (Regex(assessmentPattern(assessment), RegexOption.IGNORE_CASE).containsMatchIn(text)) return true
    return Regex("""\bmodule[\s_-]+(?:module[\s_-]+)?(\d+)\b""", RegexOption.IGNORE_CASE)
        .findAll(text).any { it.groupValues[1].toIntOrNull() in modules }
}
