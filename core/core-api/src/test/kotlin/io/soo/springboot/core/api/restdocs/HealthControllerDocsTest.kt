package io.soo.springboot.core.api.restdocs

import io.mockk.every
import io.mockk.mockk
import io.soo.springboot.core.api.controller.v1.HealthController
import io.soo.springboot.core.domain.HealthSnapshotService
import io.soo.springboot.test.api.RestDocsTest
import io.soo.springboot.test.api.RestDocsUtils
import io.soo.springboot.test.api.mockMvcDocument
import jakarta.servlet.http.HttpServletRequest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class HealthControllerDocsTest : RestDocsTest() {
    private val healthSnapshotService = mockk<HealthSnapshotService>()

    @BeforeEach
    fun init() {
        every { healthSnapshotService.publicSummary(any<HttpServletRequest>()) } returns mapOf(
            "status" to "UP",
            "application" to "core-api",
            "uptimeSec" to 10L,
        )
        mockMvc = mockController(HealthController(healthSnapshotService))
    }

    @Test
    fun health() {
        given()
            .`when`()
            .get("/health")
            .then()
            .statusCode(200)
            .apply(
                mockMvcDocument(
                    "health",
                    RestDocsUtils.requestPreprocessor(),
                    RestDocsUtils.responsePreprocessor(),
                )
            )
    }
}
