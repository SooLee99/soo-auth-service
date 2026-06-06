package io.soo.springboot.context

import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.mockk
import io.soo.springboot.CoreApiApplication
import io.soo.springboot.clients.kakao.KakaoOAuthClient
import io.soo.springboot.clients.kakao.KakaoProviderException
import io.soo.springboot.clients.kakao.KakaoTokenInvalidException
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
 * E2 — Kakao SDK 토큰 교환 엔드포인트(`POST /api/v1/auth/oauth2/kakao/token`)의 happy-path 컨텍스트 테스트.
 *
 * full `@SpringBootTest`로 [CoreApiApplication] 컨텍스트를 통째로 띄워, 다음을 실제 빈으로 검증한다:
 *  - Security permitAll 체인(공개 엔드포인트) + 요청 역직렬화/검증
 *  - JPA 계정 생성(H2 in-memory, `local` 프로파일) — `OAuth2AccountService.signUp`
 *  - RS256 access token 서명(`Rs256JwtCodecConfig` + classpath `keys/jwt-dev.p12`)
 *  - Redis refresh token 발급(`RefreshTokenManager`/`AuthTokenManager`) — 127.0.0.1:6379
 *  - 로그인 이력 기록(`LoginHistoryService`)
 *
 * 외부 upstream인 Kakao `/v2/user/me` 호출([KakaoOAuthClient])만 `@Primary` mockk 빈으로 대체해
 * 네트워크 없이 결정적으로 실행한다. (이 엔드포인트는 토큰을 *발급*하는 공개 경로이므로,
 * travel 쪽 인증 엔드포인트와 달리 테스트에서 JWT를 위조할 필요가 없다.)
 *
 * JWT 키스토어 비밀번호는 레포에 커밋된 dev 픽스처(`keys/jwt-dev.p12`)용 값이며, 운영 비밀이 아니다.
 * 로컬 셸에 env가 있으면 그 값을, 없으면 dev 기본값을 쓴다.
 */
@Tag("context")
@SpringBootTest(classes = [CoreApiApplication::class])
@AutoConfigureMockMvc
@Import(OAuth2KakaoTokenContextTest.MockKakaoClientConfig::class)
class OAuth2KakaoTokenContextTest {

    /**
     * Kakao `/v2/user/me` 호출만 mockk로 대체. `@Primary`로 실 빈(`RestClientKakaoOAuthClient`)의
     * 주입 지점을 가로챈다(나머지 컨텍스트는 실제 빈).
     */
    @TestConfiguration
    class MockKakaoClientConfig {
        @Bean @Primary fun mockKakaoOAuthClient(): KakaoOAuthClient = mockk()
    }

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var kakaoOAuthClient: KakaoOAuthClient

    @BeforeEach
    fun setUp() {
        // 유효 토큰("ka_at_ok") → KakaoParser가 그대로 소비할 수 있는 /v2/user/me attrs 1건.
        every { kakaoOAuthClient.fetchUserMe("ka_at_ok") } returns mapOf(
            "id" to 4_295_001_234L,
            "connected_at" to "2026-05-01T00:00:00Z",
            "kakao_account" to mapOf(
                "email" to "rise@triplan.kr",
                "is_email_verified" to true,
                "profile" to mapOf(
                    "nickname" to "리스",
                    "profile_image_url" to "https://example.com/p.jpg",
                ),
            ),
        )
        // 위조/만료 토큰 → 클라이언트가 토큰 무효 예외를 던지는 상황.
        every { kakaoOAuthClient.fetchUserMe("ka_at_bad") } throws
            KakaoTokenInvalidException("kakao 401")
        // 카카오 5xx/네트워크 장애.
        every { kakaoOAuthClient.fetchUserMe("ka_at_down") } throws
            KakaoProviderException("kakao 5xx")
    }

    @Test
    fun `POST kakao token - valid SDK token issues app JWT + refresh and returns user`() {
        mockMvc.perform(
            post("/api/v1/auth/oauth2/kakao/token")
                .header("X-Device-Id", "dev-ctx-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("kakaoAccessToken" to "ka_at_ok"))),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.result").value("SUCCESS"))
            .andExpect(jsonPath("$.data.token.accessToken").isNotEmpty)
            .andExpect(jsonPath("$.data.token.expiresIn").value(900))
            .andExpect(jsonPath("$.data.token.refreshToken").isNotEmpty)
            .andExpect(jsonPath("$.data.user.id").isNumber)
            .andExpect(jsonPath("$.data.user.provider").value("KAKAO"))
            .andExpect(jsonPath("$.data.user.email").value("rise@triplan.kr"))
            .andExpect(jsonPath("$.data.user.roles").isArray)
    }

    @Test
    fun `POST kakao token - invalid kakao token is rejected with 401`() {
        mockMvc.perform(
            post("/api/v1/auth/oauth2/kakao/token")
                .header("X-Device-Id", "dev-ctx-2")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("kakaoAccessToken" to "ka_at_bad"))),
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.result").value("ERROR"))
            .andExpect(jsonPath("$.error.code").value(ErrorType.KAKAO_TOKEN_INVALID.code.name))
    }

    @Test
    fun `POST kakao token - kakao provider error is mapped to 502`() {
        mockMvc.perform(
            post("/api/v1/auth/oauth2/kakao/token")
                .header("X-Device-Id", "dev-ctx-3")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("kakaoAccessToken" to "ka_at_down"))),
        )
            .andExpect(status().isBadGateway)
            .andExpect(jsonPath("$.result").value("ERROR"))
            .andExpect(jsonPath("$.error.code").value(ErrorType.KAKAO_PROVIDER_ERROR.code.name))
    }

    @Test
    fun `POST kakao token - blank token fails request validation`() {
        mockMvc.perform(
            post("/api/v1/auth/oauth2/kakao/token")
                .header("X-Device-Id", "dev-ctx-4")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("kakaoAccessToken" to ""))),
        )
            .andExpect(status().is4xxClientError)
            .andExpect(jsonPath("$.result").value("ERROR"))
    }

    companion object {
        /**
         * full 컨텍스트 부팅에 필요한 외부 의존 값 주입.
         *  - JWT 키스토어: 커밋된 dev 픽스처(`keys/jwt-dev.p12`)용 비밀번호 (운영 비밀 아님; env 있으면 우선).
         *  - Kakao OAuth2 client registration: redirect 흐름용이라 SDK-token 경로엔 안 쓰이지만,
         *    빈 client-id면 부팅이 실패하므로 더미 값으로 채운다.
         *  - Redis: 로컬 docker(triplan-redis) 기본 포트.
         */
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
