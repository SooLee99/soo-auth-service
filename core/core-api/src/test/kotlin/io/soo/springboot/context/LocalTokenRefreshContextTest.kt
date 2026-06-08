package io.soo.springboot.context

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.mockk
import io.soo.springboot.CoreApiApplication
import io.soo.springboot.clients.kakao.KakaoOAuthClient
import io.soo.springboot.core.support.error.ErrorType
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
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * E2 보강 — 리프레시 토큰 회전 엔드포인트(`POST /api/v1/auth/local/token/refresh`)의 full-boot 컨텍스트 테스트.
 *
 * `LocalTokenController.refreshTokens`는 공개 엔드포인트(ApiSecurityConfig `PUBLIC_ENDPOINTS`)로,
 * 기존 리프레시 토큰을 받아 새 access + **회전된** refresh를 발급한다.
 * 그동안 이 엔드포인트는 full `@SpringBootTest` 컨텍스트 커버리지가 없었다 — 이 테스트가 그 공백을 메운다.
 *
 * full `@SpringBootTest`로 [CoreApiApplication]을 통째로 띄워, 보안에 민감한 회전 계약을 실제 빈으로 핀한다
 * (`AuthTokenManager.refresh` → `RefreshTokenManager.rotate`, JPA 영속 H2, RS256 재발급):
 *  1. happy 회전 — 유효 refresh → 200, 새 access/refresh 발급 + **새 refresh ≠ 기존 refresh**(회전)
 *  2. 일회용/재사용 탐지 — 한 번 회전에 쓴 refresh를 다시 쓰면 401 `REFRESH_TOKEN_REUSED`
 *  3. 디바이스 바인딩 — 발급 때와 다른 `X-Device-Id`로 회전 시도 → 401 `REFRESH_TOKEN_DEVICE_MISMATCH`
 *  4. 미존재 토큰 — 임의 토큰 → 401 `INVALID_REFRESH_TOKEN`
 *
 * 토큰은 위조하지 않는다. 같은 앱의 공개 발급 경로(`POST /oauth2/kakao/token`)로 진짜 refresh 토큰을 발급받아
 * 그대로 회전 엔드포인트에 태운다 — 발급 → 회전이 한 앱 안에서 end-to-end로 동작하는지까지 핀한다.
 *
 * 외부 upstream인 Kakao `/v2/user/me`([KakaoOAuthClient])만 `@Primary` mockk로 대체해 네트워크 없이 결정적으로 실행한다.
 * JWT 키스토어/Kakao client registration/Redis 부팅 값은 [OAuth2KakaoTokenContextTest]와 동일하게 주입한다.
 */
@Tag("context")
@SpringBootTest(classes = [CoreApiApplication::class])
@AutoConfigureMockMvc
@Import(LocalTokenRefreshContextTest.MockKakaoClientConfig::class)
class LocalTokenRefreshContextTest {

    @TestConfiguration
    class MockKakaoClientConfig {
        @Bean @Primary fun mockKakaoOAuthClient(): KakaoOAuthClient = mockk()
    }

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var kakaoOAuthClient: KakaoOAuthClient

    @BeforeEach
    fun setUp() {
        // 테스트마다 별도의 카카오 사용자를 둔다. 같은 사용자(UserEntity 1행)를 여러 테스트가
        // 반복 로그인하면 OAuth2AccountService.signUp의 기존-사용자 업데이트 경로가 detached merge +
        // @Version에서 ObjectOptimisticLockingFailure(409)를 내므로, 각 테스트를 독립 사용자로 분리한다.
        registerKakaoUser("ka_at_rt_valid", id = 4_295_007_701L, email = "rt-valid@triplan.kr")
        registerKakaoUser("ka_at_rt_reuse", id = 4_295_007_702L, email = "rt-reuse@triplan.kr")
        registerKakaoUser("ka_at_rt_dev", id = 4_295_007_703L, email = "rt-dev@triplan.kr")
    }

    private fun registerKakaoUser(token: String, id: Long, email: String) {
        every { kakaoOAuthClient.fetchUserMe(token) } returns mapOf(
            "id" to id,
            "connected_at" to "2026-05-01T00:00:00Z",
            "kakao_account" to mapOf(
                "email" to email,
                "is_email_verified" to true,
                "profile" to mapOf(
                    "nickname" to "리프레시",
                    "profile_image_url" to "https://example.com/r.jpg",
                ),
            ),
        )
    }

    @Test
    fun `POST token refresh - valid refresh rotates to a new access and a new refresh token`() {
        val deviceId = "dev-rt-1"
        val issued = issueTokens(deviceId, "ka_at_rt_valid")
        val oldRefresh = issued.path("data").path("token").path("refreshToken").asText()

        mockMvc.perform(refreshRequest(deviceId, oldRefresh))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.result").value("SUCCESS"))
            .andExpect(jsonPath("$.data.accessToken").isNotEmpty)
            // access TTL 계약 — 발급 경로(`/oauth2/kakao/token`)와 동일한 AccessTokenIssuer라 900초.
            .andExpect(jsonPath("$.data.accessExpiresInSec").value(900))
            .andExpect(jsonPath("$.data.refreshToken").isNotEmpty)
            .andExpect(jsonPath("$.data.refreshExpiresInSec").isNumber)
            // ✅ 회전 핵심: 새 refresh 토큰은 기존 것과 달라야 한다.
            .andExpect(jsonPath("$.data.refreshToken").value(org.hamcrest.Matchers.not(oldRefresh)))
    }

    @Test
    fun `POST token refresh - reusing an already-rotated refresh token is rejected (one-time use)`() {
        val deviceId = "dev-rt-2"
        val issued = issueTokens(deviceId, "ka_at_rt_reuse")
        val oldRefresh = issued.path("data").path("token").path("refreshToken").asText()

        // 1) 정상 회전 1회 — old refresh가 used 처리된다.
        mockMvc.perform(refreshRequest(deviceId, oldRefresh)).andExpect(status().isOk)

        // 2) 같은(이미 사용된) refresh를 또 쓰면 재사용 탐지 → 401.
        mockMvc.perform(refreshRequest(deviceId, oldRefresh))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.result").value("ERROR"))
            .andExpect(jsonPath("$.error.code").value(ErrorType.REFRESH_TOKEN_REUSED.code.name))
    }

    @Test
    fun `POST token refresh - device id mismatch is rejected`() {
        val issued = issueTokens("dev-rt-3", "ka_at_rt_dev")
        val refresh = issued.path("data").path("token").path("refreshToken").asText()

        // 발급 때와 다른 디바이스로 회전 시도 → 디바이스 바인딩 위반.
        mockMvc.perform(refreshRequest("dev-rt-3-other", refresh))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.result").value("ERROR"))
            .andExpect(jsonPath("$.error.code").value(ErrorType.REFRESH_TOKEN_DEVICE_MISMATCH.code.name))
    }

    @Test
    fun `POST token refresh - unknown refresh token is rejected`() {
        // 어떤 발급 기록과도 매칭되지 않는 임의 토큰.
        mockMvc.perform(refreshRequest("dev-rt-4", "deadbeef".repeat(8)))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.result").value("ERROR"))
            .andExpect(jsonPath("$.error.code").value(ErrorType.INVALID_REFRESH_TOKEN.code.name))
    }

    /** 같은 앱의 공개 발급 경로로 진짜 토큰 쌍을 발급받는다(회전 입력용). */
    private fun issueTokens(deviceId: String, kakaoToken: String): JsonNode {
        val body = mockMvc.perform(
            post("/api/v1/auth/oauth2/kakao/token")
                .header("X-Device-Id", deviceId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("kakaoAccessToken" to kakaoToken))),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.token.refreshToken").isNotEmpty)
            .andReturn()
            .response
            .getContentAsString(Charsets.UTF_8)
        return objectMapper.readTree(body)
    }

    private fun refreshRequest(deviceId: String, refreshToken: String) =
        post("/api/v1/auth/local/token/refresh")
            .header("X-Device-Id", deviceId)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(mapOf("refreshToken" to refreshToken)))

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
