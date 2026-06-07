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
 * E2 보강 — 세션 상태 조회 엔드포인트(`GET /api/v1/auth/local/session`)의 full-boot 컨텍스트 테스트.
 *
 * `LocalSessionController.sessionStatus`는 공개 엔드포인트(ApiSecurityConfig `PUBLIC_ENDPOINTS`)이면서도
 * 컨트롤러 안에서 `SecurityContext`의 인증 주체를 들여다봐 익명/인증 두 가지 응답을 갈라낸다.
 * 그동안 이 엔드포인트는 full `@SpringBootTest` 컨텍스트 커버리지가 없었다 — 이 테스트가 그 공백을 메운다.
 *
 * full `@SpringBootTest`로 [CoreApiApplication]을 통째로 띄워 다음을 실제 빈으로 검증한다:
 *  - Security permitAll 체인 — 토큰 없이도 200 (인증 강제 아님), 컨트롤러가 익명을 직접 처리
 *  - 익명 호출 → `authenticated = false`
 *  - **실제로 발급된 앱 JWT를 들고 다시 호출 → resource-server JWT 디코더가 RS256 서명을 검증하고
 *    `@AuthenticationPrincipal` Jwt의 `uid` 클레임 → `userId`로 매핑** (end-to-end)
 *
 * 인증 케이스의 토큰은 위조하지 않는다. 같은 앱의 공개 발급 경로(`POST /oauth2/kakao/token`)로
 * 진짜 토큰을 발급받아 그대로 `Authorization: Bearer`로 다시 태운다 — 발급한 토큰이 같은 앱의
 * resource server에서 그대로 받아들여지는지까지 한 번에 핀한다.
 *
 * 외부 upstream인 Kakao `/v2/user/me`([KakaoOAuthClient])만 `@Primary` mockk로 대체해 네트워크 없이 결정적으로 실행한다.
 * JWT 키스토어/Kakao client registration/Redis 부팅 값은 [OAuth2KakaoTokenContextTest]와 동일하게 주입한다.
 */
@Tag("context")
@SpringBootTest(classes = [CoreApiApplication::class])
@AutoConfigureMockMvc
@Import(LocalSessionContextTest.MockKakaoClientConfig::class)
class LocalSessionContextTest {

    @TestConfiguration
    class MockKakaoClientConfig {
        @Bean @Primary fun mockKakaoOAuthClient(): KakaoOAuthClient = mockk()
    }

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var kakaoOAuthClient: KakaoOAuthClient

    @BeforeEach
    fun setUp() {
        // 세션 테스트 전용 카카오 사용자 1건(앱 JWT 발급을 위해서만 사용).
        every { kakaoOAuthClient.fetchUserMe("ka_at_session") } returns mapOf(
            "id" to 4_295_009_999L,
            "connected_at" to "2026-05-01T00:00:00Z",
            "kakao_account" to mapOf(
                "email" to "session@triplan.kr",
                "is_email_verified" to true,
                "profile" to mapOf(
                    "nickname" to "세션",
                    "profile_image_url" to "https://example.com/s.jpg",
                ),
            ),
        )
    }

    @Test
    fun `GET session - anonymous (no token) returns authenticated false`() {
        // 공개 엔드포인트라 토큰이 없어도 401이 아니라 200 + authenticated=false로 내려와야 한다.
        mockMvc.perform(get("/api/v1/auth/local/session"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.result").value("SUCCESS"))
            .andExpect(jsonPath("$.data.authenticated").value(false))
    }

    @Test
    fun `GET session - valid issued app JWT reflects authenticated principal with uid`() {
        // 1) 같은 앱의 공개 발급 경로로 진짜 액세스 토큰을 발급받는다.
        val tokenJson = issueAccessToken()
        val accessToken = tokenJson.path("data").path("token").path("accessToken").asText()
        val issuedUserId = tokenJson.path("data").path("user").path("id").asLong()

        // 2) 발급된 토큰을 그대로 Bearer로 태워 세션을 조회한다.
        mockMvc.perform(
            get("/api/v1/auth/local/session")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.result").value("SUCCESS"))
            .andExpect(jsonPath("$.data.authenticated").value(true))
            // uid 클레임 → userId, 발급 응답의 user.id와 일치해야 한다.
            .andExpect(jsonPath("$.data.userId").value(issuedUserId))
            .andExpect(jsonPath("$.data.subject").isNotEmpty)
    }

    private fun issueAccessToken(): JsonNode {
        val body = mockMvc.perform(
            post("/api/v1/auth/oauth2/kakao/token")
                .header("X-Device-Id", "dev-session-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("kakaoAccessToken" to "ka_at_session"))),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.token.accessToken").isNotEmpty)
            .andReturn()
            .response
            .getContentAsString(Charsets.UTF_8)
        return objectMapper.readTree(body)
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
