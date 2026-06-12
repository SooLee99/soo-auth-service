package io.soo.springboot.context

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.mockk
import io.soo.springboot.CoreApiApplication
import io.soo.springboot.clients.kakao.KakaoOAuthClient
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * 보강 — 헬스 엔드포인트(`GET /health`)의 full-boot 컨텍스트 테스트.
 *
 * `HealthController.getHealth`는 [io.soo.springboot.core.domain.health.HealthSnapshotService.publicSummary]만
 * 반환한다. 이 엔드포인트는 `ApiSecurityConfig.PUBLIC_ENDPOINTS`에 없고 `/api` 경로 패턴에도 걸리지 않아
 * 최종적으로 `anyRequest().authenticated()`로 떨어진다 — 즉 **인증이 필요한 경로**다.
 * 그동안 이 엔드포인트는 full `@SpringBootTest` 컨텍스트 커버리지가 없었다 — 이 테스트가 그 공백을 메운다.
 *
 * full `@SpringBootTest`로 [CoreApiApplication]을 통째로 띄워 두 가지를 실제 빈으로 검증한다:
 *  1. **익명 접근 차단** — 토큰 없이 `GET /health` → 인증 게이트가 200/본문 노출을 막는다.
 *  2. **정보 노출 경계(security boundary)** — 실제로 발급된 앱 JWT를 들고 호출하면 200이되,
 *     `publicSummary`만 내려오고 admin 전용 민감정보(`adminDetails`에만 있는
 *     `profiles`·`build`·`components`(DB url/pool, redis)·`system`(jvm/disk)·monitoring/database/logs 링크)는
 *     **절대 포함되지 않아야** 한다. 이 경계가 깨지면 비-admin 사용자에게 내부 인프라 정보가 새어 나간다.
 *
 * 인증 케이스의 토큰은 위조하지 않는다. 같은 앱의 공개 발급 경로(`POST /oauth2/kakao/token`)로
 * 진짜 토큰을 발급받아 그대로 `Authorization: Bearer`로 다시 태운다.
 *
 * 외부 upstream인 Kakao `/v2/user/me`([KakaoOAuthClient])만 `@Primary` mockk로 대체해 네트워크 없이 실행한다.
 * JWT 키스토어/Kakao client registration/Redis 부팅 값은 [LocalSessionContextTest]와 동일하게 주입한다.
 */
@Tag("context")
@SpringBootTest(classes = [CoreApiApplication::class])
@AutoConfigureMockMvc
@Import(HealthContextTest.MockKakaoClientConfig::class)
class HealthContextTest {

    @TestConfiguration
    class MockKakaoClientConfig {
        @Bean @Primary fun mockKakaoOAuthClient(): KakaoOAuthClient = mockk()
    }

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var kakaoOAuthClient: KakaoOAuthClient

    @BeforeEach
    fun setUp() {
        // 헬스 테스트 전용 카카오 사용자 1건(앱 JWT 발급을 위해서만 사용). 다른 컨텍스트 테스트와 겹치지 않는 고유 id/email.
        every { kakaoOAuthClient.fetchUserMe("ka_at_health") } returns mapOf(
            "id" to 4_295_007_777L,
            "connected_at" to "2026-05-01T00:00:00Z",
            "kakao_account" to mapOf(
                "email" to "health@triplan.kr",
                "is_email_verified" to true,
                "profile" to mapOf(
                    "nickname" to "헬스",
                    "profile_image_url" to "https://example.com/h.jpg",
                ),
            ),
        )
    }

    @Test
    fun `GET health - anonymous (no token) is blocked from the public summary`() {
        // /health 는 PUBLIC_ENDPOINTS 에 없고 /api/** 도 아니므로 anyRequest().authenticated() 로 떨어진다.
        // 익명은 publicSummary 본문(200)을 받아선 안 된다 — 인증 게이트가 막아야 한다.
        mockMvc.perform(get("/health"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `GET health - authenticated returns public summary WITHOUT admin-only internals`() {
        // 1) 같은 앱의 공개 발급 경로로 진짜 액세스 토큰을 발급받는다.
        val accessToken = issueAccessToken()

        // 2) 발급된 토큰을 그대로 Bearer 로 태워 헬스를 조회한다.
        mockMvc.perform(
            get("/health").header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.result").value("SUCCESS"))
            // publicSummary 가 노출하는 안전한 필드는 존재해야 한다.
            .andExpect(jsonPath("$.data.status").isNotEmpty)
            .andExpect(jsonPath("$.data.application").isNotEmpty)
            .andExpect(jsonPath("$.data.version").exists())
            .andExpect(jsonPath("$.data.uptimeSec").exists())
            .andExpect(jsonPath("$.data.links.docs").exists())
            // ── 정보 노출 경계: adminDetails 에만 있어야 할 내부 정보는 절대 내려오면 안 된다 ──
            .andExpect(jsonPath("$.data.profiles").doesNotExist())     // active profiles
            .andExpect(jsonPath("$.data.build").doesNotExist())        // buildTime/gitCommitId/gitBranch
            .andExpect(jsonPath("$.data.components").doesNotExist())   // database(url/pool)·redis 상세
            .andExpect(jsonPath("$.data.system").doesNotExist())       // jvm heap/disk
            .andExpect(jsonPath("$.data.links.monitoring").doesNotExist()) // grafana/prometheus/loki
            .andExpect(jsonPath("$.data.links.database").doesNotExist())   // phpMyAdmin
            .andExpect(jsonPath("$.data.links.logs").doesNotExist())       // lokiQuery/grafanaExplore
    }

    private fun issueAccessToken(): String {
        val body = mockMvc.perform(
            post("/api/v1/auth/oauth2/kakao/token")
                .header("X-Device-Id", "dev-health-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("kakaoAccessToken" to "ka_at_health"))),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.token.accessToken").isNotEmpty)
            .andReturn()
            .response
            .getContentAsString(Charsets.UTF_8)
        val json: JsonNode = objectMapper.readTree(body)
        return json.path("data").path("token").path("accessToken").asText()
    }

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun props(registry: DynamicPropertyRegistry) {
            val ksPassword = System.getenv("JWT_KEYSTORE_PASSWORD") ?: "devjwtpass"
            registry.add("app.security.jwt.keystore.password") { ksPassword }
            registry.add("app.security.jwt.keystore.key-password") {
                System.getenv("JWT_KEY_PASSWORD") ?: ksPassword
            }
            registry.add("app.security.jwt.keystore.alias") { System.getenv("JWT_KEY_ALIAS") ?: "jwt" }

            registry.add("spring.security.oauth2.client.registration.kakao.client-id") { "test-kakao-client" }
            registry.add("spring.security.oauth2.client.registration.kakao.client-secret") { "test-kakao-secret" }

            registry.add("spring.data.redis.host") { System.getenv("REDIS_HOST") ?: "127.0.0.1" }
            registry.add("spring.data.redis.port") { System.getenv("REDIS_PORT") ?: "6379" }
        }
    }
}
