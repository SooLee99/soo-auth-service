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
 * E2 보강 — 로그아웃 엔드포인트(`POST /api/v1/auth/local/logout`)의 full-boot 컨텍스트 테스트.
 *
 * `LocalTokenController.logoutUser`는 PUBLIC_ENDPOINTS가 아니며 `ApiSecurityConfig`에서 명시적으로
 * `requestMatchers("/api/v1/auth/local/logout").authenticated()`로 막혀 있다 — 익명 호출은 401이어야 한다.
 * 로그아웃은 `TokenRevocationService.revokeOnLogout`을 통해 (1) access JWT의 jti denylist 등록,
 * (2) refresh 토큰 폐기를 수행하는 보안상 민감한 경로지만, 그동안 full `@SpringBootTest` 컨텍스트
 * 커버리지가 없었다 — 이 테스트가 그 공백을 메운다.
 *
 * full `@SpringBootTest`로 [CoreApiApplication]을 통째로 띄워, refresh 토큰 폐기 계약을 실제 빈으로 핀한다
 * (`LocalAccountService.logout` → `TokenRevocationService` → `RefreshTokenManager.revoke*`, JPA 영속 H2).
 * 핵심 검증은 "로그아웃 후 해당 refresh 토큰을 재사용하면 회전 엔드포인트가 거부한다"는 것 —
 * 즉 폐기가 실제로 영속되었음을 같은 앱의 공개 회전 경로(`POST /api/v1/auth/local/token/refresh`)로 증명한다:
 *  1. 익명(토큰 없음) → 401 (authenticated() 게이트)
 *  2. refreshToken을 body에 담아 로그아웃 → 200 OK, 그 refresh를 재사용하면 401 `REVOKED_REFRESH_TOKEN`
 *     (특정 토큰 폐기 분기 — `revoke(rt)`)
 *  3. logoutAll=true 로그아웃(body에 refreshToken 없음) → 200 OK, refresh 재사용 401 `REVOKED_REFRESH_TOKEN`
 *     (사용자 전체 폐기 분기 — `revokeAllByUserId`)
 *  4. body 없이 로그아웃(refreshToken·logoutAll 모두 미지정) → 200 OK, refresh 재사용 401 `REVOKED_REFRESH_TOKEN`
 *     (디바이스 스코프 폐기 분기 — JWT의 uid + X-Device-Id로 `revokeByUserIdAndDeviceId`)
 *
 * 토큰은 위조하지 않는다. 같은 앱의 공개 발급 경로(`POST /oauth2/kakao/token`)로 진짜 access/refresh를 발급받아
 * access는 `Authorization: Bearer`로 로그아웃 게이트를 통과시키고, refresh는 폐기 증명에 사용한다.
 *
 * 외부 upstream인 Kakao `/v2/user/me`([KakaoOAuthClient])만 `@Primary` mockk로 대체해 네트워크 없이 결정적으로 실행한다.
 * 같은 사용자를 한 컨텍스트 안에서 여러 번 로그인하면 `OAuth2AccountService.signUp`의 기존-사용자 업데이트가
 * `@Version` 낙관적 락(409)을 내므로, 토큰을 발급하는 각 테스트는 **서로 다른 카카오 사용자**를 쓴다.
 */
@Tag("context")
@SpringBootTest(classes = [CoreApiApplication::class])
@AutoConfigureMockMvc
@Import(LocalLogoutContextTest.MockKakaoClientConfig::class)
class LocalLogoutContextTest {

    @TestConfiguration
    class MockKakaoClientConfig {
        @Bean @Primary fun mockKakaoOAuthClient(): KakaoOAuthClient = mockk()
    }

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var kakaoOAuthClient: KakaoOAuthClient

    @BeforeEach
    fun setUp() {
        // 토큰을 발급하는 테스트마다 독립된 카카오 사용자를 둔다(같은 UserEntity 반복 로그인 시 @Version 409 회피).
        registerKakaoUser("ka_logout_body", id = 4_295_008_801L, email = "logout-body@triplan.kr")
        registerKakaoUser("ka_logout_all", id = 4_295_008_802L, email = "logout-all@triplan.kr")
        registerKakaoUser("ka_logout_device", id = 4_295_008_803L, email = "logout-device@triplan.kr")
    }

    private fun registerKakaoUser(token: String, id: Long, email: String) {
        every { kakaoOAuthClient.fetchUserMe(token) } returns mapOf(
            "id" to id,
            "connected_at" to "2026-05-01T00:00:00Z",
            "kakao_account" to mapOf(
                "email" to email,
                "is_email_verified" to true,
                "profile" to mapOf(
                    "nickname" to "로그아웃",
                    "profile_image_url" to "https://example.com/l.jpg",
                ),
            ),
        )
    }

    @Test
    fun `POST logout - anonymous request without a token is rejected with 401`() {
        // 로그아웃은 PUBLIC_ENDPOINTS가 아니고 ApiSecurityConfig에서 명시적으로 authenticated() → 토큰 없이는 막힌다.
        mockMvc.perform(
            post("/api/v1/auth/local/logout")
                .header("X-Device-Id", "dev-logout-anon")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(emptyMap<String, Any>())),
        )
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `POST logout - with the refresh token in the body revokes that refresh token`() {
        val deviceId = "dev-logout-1"
        val issued = issueTokens(deviceId, "ka_logout_body")
        val accessToken = issued.path("data").path("token").path("accessToken").asText()
        val refreshToken = issued.path("data").path("token").path("refreshToken").asText()

        // 1) 로그아웃 — refreshToken을 body에 담아 특정 토큰 폐기 분기를 탄다.
        mockMvc.perform(
            post("/api/v1/auth/local/logout")
                .header("X-Device-Id", deviceId)
                .header("Authorization", "Bearer $accessToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("refreshToken" to refreshToken))),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.result").value("SUCCESS"))
            .andExpect(jsonPath("$.data.result").value("OK"))

        // 2) ✅ 폐기 증명: 로그아웃된 refresh로 회전을 시도하면 거부된다(폐기 영속).
        mockMvc.perform(refreshRequest(deviceId, refreshToken))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.result").value("ERROR"))
            .andExpect(jsonPath("$.error.code").value(ErrorType.REVOKED_REFRESH_TOKEN.code.name))
    }

    @Test
    fun `POST logout - with logoutAll revokes the refresh token even without passing it`() {
        val deviceId = "dev-logout-2"
        val issued = issueTokens(deviceId, "ka_logout_all")
        val accessToken = issued.path("data").path("token").path("accessToken").asText()
        val refreshToken = issued.path("data").path("token").path("refreshToken").asText()

        // logoutAll=true → body에 refreshToken을 넘기지 않아도 JWT의 uid로 사용자 전체 refresh를 폐기한다.
        mockMvc.perform(
            post("/api/v1/auth/local/logout")
                .header("X-Device-Id", deviceId)
                .header("Authorization", "Bearer $accessToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("logoutAll" to true))),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.result").value("OK"))

        mockMvc.perform(refreshRequest(deviceId, refreshToken))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error.code").value(ErrorType.REVOKED_REFRESH_TOKEN.code.name))
    }

    @Test
    fun `POST logout - with no body revokes by user id and device id`() {
        val deviceId = "dev-logout-3"
        val issued = issueTokens(deviceId, "ka_logout_device")
        val accessToken = issued.path("data").path("token").path("accessToken").asText()
        val refreshToken = issued.path("data").path("token").path("refreshToken").asText()

        // body 없이(refreshToken·logoutAll 미지정) → JWT의 uid + X-Device-Id 스코프로 폐기하는 분기를 탄다.
        mockMvc.perform(
            post("/api/v1/auth/local/logout")
                .header("X-Device-Id", deviceId)
                .header("Authorization", "Bearer $accessToken"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.result").value("OK"))

        mockMvc.perform(refreshRequest(deviceId, refreshToken))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error.code").value(ErrorType.REVOKED_REFRESH_TOKEN.code.name))
    }

    /** 같은 앱의 공개 발급 경로로 진짜 access/refresh 토큰 쌍을 발급받는다. */
    private fun issueTokens(deviceId: String, kakaoToken: String): JsonNode {
        val body = mockMvc.perform(
            post("/api/v1/auth/oauth2/kakao/token")
                .header("X-Device-Id", deviceId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("kakaoAccessToken" to kakaoToken))),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.token.accessToken").isNotEmpty)
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
