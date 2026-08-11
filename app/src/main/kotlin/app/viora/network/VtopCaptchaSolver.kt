package app.viora.network

import android.content.res.AssetManager
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.IOException
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Solves VTOP's six-character text CAPTCHA locally using a bundled linear model. */
class VtopCaptchaSolver private constructor(
    private val weights: Array<FloatArray>,
    private val biases: FloatArray,
) {
    fun solve(dataUri: String): String {
        val match = DATA_URI.matchEntire(dataUri.trim())
            ?: throw IOException("VTOP returned an invalid CAPTCHA image")
        val imageBytes = runCatching { Base64.decode(match.groupValues[2], Base64.DEFAULT) }
            .getOrElse { throw IOException("VTOP returned malformed CAPTCHA image data", it) }
        val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            ?: throw IOException("VTOP returned an unsupported CAPTCHA image")
        val pixels = IntArray(TARGET_WIDTH * TARGET_HEIGHT)
        val scaled = if (bitmap.width == TARGET_WIDTH && bitmap.height == TARGET_HEIGHT) {
            bitmap
        } else {
            android.graphics.Bitmap.createScaledBitmap(bitmap, TARGET_WIDTH, TARGET_HEIGHT, false)
        }
        try {
            scaled.getPixels(pixels, 0, TARGET_WIDTH, 0, 0, TARGET_WIDTH, TARGET_HEIGHT)
        } finally {
            if (scaled !== bitmap) scaled.recycle()
            bitmap.recycle()
        }
        return solvePixels(pixels)
    }

    internal fun solvePixels(pixels: IntArray): String {
        require(pixels.size == TARGET_WIDTH * TARGET_HEIGHT) { "CAPTCHA must be 200x40 pixels" }
        val saturation = IntArray(pixels.size)
        pixels.forEachIndexed { index, color ->
            val red = color shr 16 and 0xff
            val green = color shr 8 and 0xff
            val blue = color and 0xff
            val low = min(red, min(green, blue))
            val high = max(red, max(green, blue))
            saturation[index] = if (high > 0) ((high - low) * 255f / high).roundToInt() else 0
        }

        return buildString(CHAR_COUNT) {
            repeat(CHAR_COUNT) { characterIndex ->
                val left = (characterIndex + 1) * 25 + 2
                val top = 8 + 5 * (characterIndex % 2)
                val right = (characterIndex + 2) * 25 + 1
                val bottom = 35 - 5 * ((characterIndex + 1) % 2)
                val input = FloatArray(INPUT_SIZE)
                var average = 0f
                var position = 0
                for (y in top until bottom) {
                    for (x in left until right) {
                        val value = saturation[y * TARGET_WIDTH + x].toFloat()
                        input[position++] = value
                        average += value
                    }
                }
                average /= INPUT_SIZE
                var bestClass = 0
                var bestScore = Float.NEGATIVE_INFINITY
                for (candidate in biases.indices) {
                    var score = biases[candidate]
                    for (i in input.indices) {
                        val bit = if (input[i] > average) 1f else 0f
                        score += bit * weights[i][candidate]
                    }
                    if (score > bestScore) {
                        bestScore = score
                        bestClass = candidate
                    }
                }
                append(LABELS[bestClass])
            }
        }.also { answer ->
            if (answer.length != CHAR_COUNT || answer.any { it !in LABELS }) {
                throw IOException("CAPTCHA solver produced an invalid answer")
            }
        }
    }

    companion object {
        private const val MODEL_ASSET = "vtop_captcha_model.bin"
        private const val MODEL_MAGIC = 0x56544331 // VTC1
        private const val TARGET_WIDTH = 200
        private const val TARGET_HEIGHT = 40
        private const val INPUT_SIZE = 528
        private const val OUTPUT_SIZE = 32
        private const val CHAR_COUNT = 6
        private const val LABELS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        private val DATA_URI = Regex("^data:(image/(?:png|jpe?g));base64,(.+)$", RegexOption.IGNORE_CASE)

        fun fromAssets(assets: AssetManager): VtopCaptchaSolver =
            DataInputStream(BufferedInputStream(assets.open(MODEL_ASSET))).use { input ->
                if (input.readInt() != MODEL_MAGIC) throw IOException("Invalid CAPTCHA model")
                val rows = input.readInt()
                val columns = input.readInt()
                if (rows != INPUT_SIZE || columns != OUTPUT_SIZE) {
                    throw IOException("Unsupported CAPTCHA model dimensions: ${rows}x$columns")
                }
                val weights = Array(rows) { FloatArray(columns) }
                for (row in 0 until rows) for (column in 0 until columns) {
                    weights[row][column] = input.readFloat()
                }
                val biases = FloatArray(columns) { input.readFloat() }
                VtopCaptchaSolver(weights, biases)
            }

        internal fun fromModel(weights: Array<FloatArray>, biases: FloatArray): VtopCaptchaSolver {
            require(weights.size == INPUT_SIZE && weights.all { it.size == OUTPUT_SIZE })
            require(biases.size == OUTPUT_SIZE)
            return VtopCaptchaSolver(weights, biases)
        }
    }
}
