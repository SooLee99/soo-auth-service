package io.soo.springboot.core.api.restdocs

import io.mockk.every
import io.mockk.mockk
import io.soo.springboot.core.api.controller.v1.HealthController
import io.soo.springboot.core.domain.health.HealthSnapshotService
import io.soo.springboot.test.api.RestDocsTest
import io.soo.springboot.test.api.RestDocsUtils
import io.soo.springboot.test.api.mockMvcDocument
import io.soo.springboot.test.api.relaxedResponseFields
import io.soo.springboot.test.api.requestHeaders
import jakarta.servlet.http.HttpServletRequest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.restdocs.headers.HeaderDocumentation.headerWithName
import org.springframework.restdocs.payload.JsonFieldType
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath

class HealthControllerDocsTest : RestDocsTest() {
    private val healthSnapshotService = mockk<HealthSnapshotService>()

    @BeforeEach
    fun init() {
        every { healthSnapshotService.publicSummary(any<HttpServletRequest>()) } returns mapOf(
            "status" to "UP",
            "application" to "core-api",
            "uptimeSec" to 10L,
            "links" to mapOf(
                "docs" to mapOf(
                    "swagger" to "https://api.example.com/docs/swagger/index.html",
                    "docs" to "https://api.example.com/docs/index.html",
                )
            ),
        )
        every { healthSnapshotService.adminDetails(any<HttpServletRequest>()) } returns mapOf(
            "status" to "UP",
            "application" to "core-api",
            "profiles" to listOf("dev"),
            "uptimeSec" to 10L,
            "components" to mapOf(
                "database" to mapOf("status" to "UP", "latencyMs" to 2),
                "redis" to mapOf("status" to "UP", "latencyMs" to 1),
            ),
            "system" to mapOf(
                "jvm" to mapOf("heapUsedBytes" to 1000, "heapMaxBytes" to 10000, "processors" to 4),
                "disk" to mapOf("totalBytes" to 100000, "usableBytes" to 90000),
            ),
            "links" to mapOf(
                "docs" to mapOf("swagger" to "https://api.example.com/docs/swagger/index.html", "docs" to "https://api.example.com/docs/index.html"),
                "monitoring" to mapOf("grafana" to "https://api.example.com:3000", "prometheus" to "https://api.example.com/actuator/prometheus", "loki" to "https://api.example.com:3100"),
                "logs" to mapOf("lokiQuery" to "https://api.example.com:3100/loki/api/v1/query_range", "grafanaExplore" to "https://api.example.com:3000/explore"),
            ),
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
                    relaxedResponseFields(
                        fieldWithPath("result").type(JsonFieldType.STRING).description("결과"),
                        fieldWithPath("meta.timestamp").type(JsonFieldType.STRING).description("응답 생성 시각"),
                        fieldWithPath("meta.request.path").type(JsonFieldType.STRING).description("요청 경로"),
                        fieldWithPath("meta.request.method").type(JsonFieldType.STRING).description("요청 메서드"),
                        fieldWithPath("data.status").type(JsonFieldType.STRING).description("헬스 상태"),
                        fieldWithPath("data.application").type(JsonFieldType.STRING).description("애플리케이션 이름"),
                        fieldWithPath("data.uptimeSec").type(JsonFieldType.NUMBER).description("업타임(초)"),
                        fieldWithPath("data.links.docs.swagger").type(JsonFieldType.STRING).description("Swagger UI URL"),
                        fieldWithPath("data.links.docs.docs").type(JsonFieldType.STRING).description("API 문서 URL"),
                    ),
                )
            )
    }

    @Test
    fun adminHealth() {
        given()
            .header("Authorization", "Bearer token")
            .`when`()
            .get("/api/v1/auth/admin/health")
            .then()
            .statusCode(200)
            .apply(
                mockMvcDocument(
                    "auth-admin-health",
                    RestDocsUtils.requestPreprocessor(),
                    RestDocsUtils.responsePreprocessor(),
                    requestHeaders(
                        headerWithName("Authorization").description("Bearer 액세스 토큰"),
                    ),
                    relaxedResponseFields(
                        fieldWithPath("result").type(JsonFieldType.STRING).description("결과"),
                        fieldWithPath("meta.timestamp").type(JsonFieldType.STRING).description("응답 생성 시각"),
                        fieldWithPath("meta.request.path").type(JsonFieldType.STRING).description("요청 경로"),
                        fieldWithPath("meta.request.method").type(JsonFieldType.STRING).description("요청 메서드"),
                        fieldWithPath("data.status").type(JsonFieldType.STRING).description("전체 헬스 상태"),
                        fieldWithPath("data.application").type(JsonFieldType.STRING).description("애플리케이션 이름"),
                        fieldWithPath("data.profiles").type(JsonFieldType.ARRAY).description("활성 프로파일 목록"),
                        fieldWithPath("data.uptimeSec").type(JsonFieldType.NUMBER).description("업타임(초)"),
                        fieldWithPath("data.components.database.status").type(JsonFieldType.STRING).description("DB 상태"),
                        fieldWithPath("data.components.database.latencyMs").type(JsonFieldType.NUMBER).description("DB 응답시간(ms)"),
                        fieldWithPath("data.components.redis.status").type(JsonFieldType.STRING).description("Redis 상태"),
                        fieldWithPath("data.components.redis.latencyMs").type(JsonFieldType.NUMBER).description("Redis 응답시간(ms)"),
                        fieldWithPath("data.system.jvm.heapUsedBytes").type(JsonFieldType.NUMBER).description("JVM 사용 Heap(bytes)"),
                        fieldWithPath("data.system.jvm.heapMaxBytes").type(JsonFieldType.NUMBER).description("JVM 최대 Heap(bytes)"),
                        fieldWithPath("data.system.jvm.processors").type(JsonFieldType.NUMBER).description("가용 CPU 코어 수"),
                        fieldWithPath("data.system.disk.totalBytes").type(JsonFieldType.NUMBER).description("디스크 전체 용량(bytes)"),
                        fieldWithPath("data.system.disk.usableBytes").type(JsonFieldType.NUMBER).description("디스크 사용 가능 용량(bytes)"),
                        fieldWithPath("data.links.docs.swagger").type(JsonFieldType.STRING).description("Swagger UI URL"),
                        fieldWithPath("data.links.docs.docs").type(JsonFieldType.STRING).description("API 문서 URL"),
                        fieldWithPath("data.links.monitoring.grafana").type(JsonFieldType.STRING).description("Grafana URL"),
                        fieldWithPath("data.links.monitoring.prometheus").type(JsonFieldType.STRING).description("Prometheus 메트릭 URL"),
                        fieldWithPath("data.links.monitoring.loki").type(JsonFieldType.STRING).description("Loki URL"),
                        fieldWithPath("data.links.logs.lokiQuery").type(JsonFieldType.STRING).description("Loki Query API URL"),
                        fieldWithPath("data.links.logs.grafanaExplore").type(JsonFieldType.STRING).description("Grafana Explore URL"),
                    ),
                )
            )
    }
}
