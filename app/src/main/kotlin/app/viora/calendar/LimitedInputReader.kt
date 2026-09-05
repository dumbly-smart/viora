package app.viora.calendar

import java.io.ByteArrayOutputStream
import java.io.InputStream

internal fun readAtMost(input: InputStream, maximumBytes: Int): ByteArray {
    require(maximumBytes >= 0) { "Maximum byte count cannot be negative" }
    val output = ByteArrayOutputStream(minOf(maximumBytes, 8 * 1024))
    val buffer = ByteArray(8 * 1024)
    var total = 0
    while (true) {
        val read = input.read(buffer)
        if (read < 0) break
        total += read
        require(total <= maximumBytes) { "Calendar file is larger than 5 MiB" }
        output.write(buffer, 0, read)
    }
    return output.toByteArray()
}
