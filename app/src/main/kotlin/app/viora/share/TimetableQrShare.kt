package app.viora.share

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.content.FileProvider
import app.viora.database.SlotWithCourse
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.time.DayOfWeek

class TimetableQrShare(private val context: Context) {
    suspend fun share(semesterName: String, slots: List<SlotWithCourse>): Result<Unit> = runCatching {
        require(slots.isNotEmpty()) { "No timetable is cached" }
        val payload = TimetableQrPayload.encode(semesterName, slots)
        val file = withContext(Dispatchers.IO) {
            val matrix = MultiFormatWriter().encode(
                payload,
                BarcodeFormat.QR_CODE,
                1024,
                1024,
                mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 2),
            )
            val bitmap = Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.RGB_565)
            for (y in 0 until matrix.height) for (x in 0 until matrix.width) bitmap.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
            val directory = File(context.filesDir, "shared").apply { mkdirs() }
            File(directory, "viora-timetable-qr.png").also { output -> FileOutputStream(output).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle() }
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, payload)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Share timetable QR").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

object TimetableQrPayload {
    private const val MAX_CHARS = 2000
    fun encode(semesterName: String, slots: List<SlotWithCourse>): String {
        val header = "VIORA TIMETABLE | ${clean(semesterName, 60)}\n"
        val lines = slots.sortedWith(compareBy<SlotWithCourse> { it.dayOfWeek }.thenBy { it.startMinute }).map { slot ->
            "${DayOfWeek.of(slot.dayOfWeek).name.take(3)}|${time(slot.startMinute)}-${time(slot.endMinute)}|${clean(slot.code, 14)}|${clean(slot.title, 40)}|${clean(slot.venue, 20)}"
        }
        val output = StringBuilder(header)
        for (line in lines) {
            if (output.length + line.length + 1 > MAX_CHARS) { output.append("…more slots in Viora"); break }
            output.append(line).append('\n')
        }
        return output.toString().trimEnd()
    }
    private fun clean(value: String, max: Int) = value.replace('|', '/').replace(Regex("\\s+"), " ").trim().map { if (it.code in 32..126) it else '?' }.joinToString("").take(max)
    private fun time(minutes: Int) = "%02d:%02d".format(minutes / 60, minutes % 60)
}
