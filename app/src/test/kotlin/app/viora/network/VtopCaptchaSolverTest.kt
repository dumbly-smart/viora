package app.viora.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class VtopCaptchaSolverTest {
    @Test fun `solver returns exactly six characters from the supported alphabet`() {
        val weights = Array(528) { FloatArray(32) }
        val biases = FloatArray(32).also { it[31] = 1f }
        val solver = VtopCaptchaSolver.fromModel(weights, biases)

        assertEquals("999999", solver.solvePixels(IntArray(200 * 40)))
    }

    @Test fun `solver rejects pixels with unexpected dimensions`() {
        val solver = VtopCaptchaSolver.fromModel(Array(528) { FloatArray(32) }, FloatArray(32))

        assertThrows(IllegalArgumentException::class.java) { solver.solvePixels(IntArray(10)) }
    }

    @Test fun `model dimensions are validated`() {
        assertThrows(IllegalArgumentException::class.java) {
            VtopCaptchaSolver.fromModel(Array(527) { FloatArray(32) }, FloatArray(32))
        }
    }
}
