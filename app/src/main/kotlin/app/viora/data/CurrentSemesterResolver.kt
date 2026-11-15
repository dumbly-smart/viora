package app.viora.data

import app.viora.domain.ExamWindow
import app.viora.domain.SemesterRollover
import app.viora.domain.SemesterSelection
import app.viora.domain.examSuppressionWindows
import app.viora.domain.suppresses
import app.viora.network.SemesterOption
import app.viora.network.VtopGateway
import java.io.IOException
import java.time.Instant
import java.time.ZoneId

/** Registration for a future term does not end the current term's examinations. */
class CurrentSemesterResolver(
    private val gateway: VtopGateway,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun resolve(options: List<SemesterOption>, cachedIds: Set<String>, savedId: String?): SemesterSelection {
        val zone = ZoneId.of("Asia/Kolkata")
        val today = Instant.ofEpochMilli(clock()).atZone(zone).toLocalDate()
        var confirmed: String? = null
        var discoveryFailed = false
        for (option in options.sortedBy { it.id != savedId }) {
            val exams = try { gateway.exams(option.id) } catch (_: IOException) { discoveryFailed = true; continue }
            val windows = examSuppressionWindows(exams.map {
                ExamWindow(it.startsAt.atZone(zone).toInstant().toEpochMilli(), it.endsAt?.atZone(zone)?.toInstant()?.toEpochMilli(), it.examType)
            }, emptyList(), zone)
            if (windows.any { it.suppresses(today) }) {
                confirmed = option.id
                break
            }
        }
        if (confirmed == null && discoveryFailed && options.any { it.id == savedId }) {
            return SemesterSelection(savedId, false)
        }
        return SemesterRollover.select(options.map { it.id }, cachedIds, savedId, confirmed)
    }
}
