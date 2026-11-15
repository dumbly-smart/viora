package app.viora.data

import app.viora.network.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.lang.reflect.Proxy

class CurrentSemesterResolverTest {
    @Test fun `failed exam discovery preserves saved term instead of rolling into future registration`() = runTest {
        val unused = Proxy.newProxyInstance(VtopGateway::class.java.classLoader, arrayOf(VtopGateway::class.java)) { _, _, _ ->
            error("Unexpected gateway call")
        } as VtopGateway
        val gateway = object : VtopGateway by unused {
            override suspend fun exams(semesterId: String): List<ExamRecord> = throw IOException("Synthetic network failure")
        }
        val result = CurrentSemesterResolver(gateway).resolve(
            listOf(SemesterOption("future", "Future term"), SemesterOption("current", "Current term")), setOf("current"), "current")
        assertEquals("current", result.selectedId)
        assertFalse(result.rolloverDetected)
    }
}
