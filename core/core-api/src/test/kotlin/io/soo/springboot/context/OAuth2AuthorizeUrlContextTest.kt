package io.soo.springboot.context

import io.mockk.mockk
import io.soo.springboot.CoreApiApplication
import io.soo.springboot.clients.kakao.KakaoOAuthClient
import io.soo.springboot.core.support.error.ErrorType
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * E2 보강 — OAuth2 authorize-url 발급 엔드포인트(`GET /api/v1/auth/oauth2/{provider}/authorize-url`)의
 * full-boot 컨텍스트 테스트.
 *
 * `OAuth2AccountController`에는 두 개의 엔드포인트가 있는데, `POST /kakao/token`은
 * [OAuth2KakaoTokenContextTest]가 이미 full-boot로 핀하고 있었으나, redirect 흐름의 시작점인
 * **`GET /{provider}/authorize-url`은 full `@SpringBootTest` 컨텍스트 커버리지가 없었다.**
 * 이 테스트가 그 공백을 메운다.
 *
 * full `@SpringBootTest`로 [CoreApiApplication]을 통째로 띄워 다음을 실제 빈으로 검증한다:
 *  - Security permitAll 체인 — `GET /api/v1/auth/oauth2/{provider}/authorize-url`은 공개(ApiSecurityConfig L147)
 *  - `@PathVariable provider` 바인딩 → `data = "/oauth2/authorization/{provider}"`
 *  - `returnUrl` 상대경로 검증(open-redirect 방어): 절대/프로토콜-상대 URL은 거부
 *  - 세션 속성 기록(RETURN_URL / DEVICE_ID)
 *  - **컨트롤러가 `ApiResponse.error(...)`를 *throw*가 아니라 *return*하므로
 *    (ApiControllerAdvice는 @ExceptionHandler 전용) 검증 실패는 HTTP 200 + result=ERROR로 내려온다** —
 *    이 contract quirk를 핀한다.
 *
 * 이 엔드포인트는 외부 Kakao 호출도, 토큰 발급(Redis)도 하지 않는다(경로 문자열만 구성).
 * 다만 full 컨텍스트 부팅을 위해 외부 upstream [KakaoOAuthClient]만 `@Primary` mockk로 대체하고,
 * JWT 키스토어/Kakao client registration/Redis 부팅 값은 [OAuth2KakaoTokenContextTest]와 동일하게 주입한다.
 */
@Tag("context")
@SpringBootTest(classes = [CoreApiApplication::class])
@AutoConfigureMockMvc
@Import(OAuth2AuthorizeUrlContextTest.MockKakaoClientConfig::class)
class OAuth2AuthorizeUrlContextTest {

    @TestConfiguration
    class MockKakaoClientConfig {
        @Bean @Primary fun mockKakaoOAuthClient(): KakaoOAuthClient = mockk()
    }

    @Autowired private lateinit var mockMvc: MockMvc

    @Test
    fun `GET authorize-url - no returnUrl returns kakao authorization path (public)`() {
        mockMvc.perform(get("/api/v1/auth/oauth2/kakao/authorize-url"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.result").value("SUCCESS"))
            .andExpect(jsonPath("$.data").value("/oauth2/authorization/kakao"))
            .andExpect(jsonPath("$.error").doesNotExist())
    }

    @Test
    fun `GET authorize-url - relative returnUrl is accepted (SUCCESS, not rejected)`() {
        // 상대경로 returnUrl은 open-redirect 가드를 통과해 정상 응답으로 내려와야 한다
        // (절대/프로토콜-상대 URL의 ERROR 케이스와 대비되는 happy-path).
        mockMvc.perform(
            get("/api/v1/auth/oauth2/kakao/authorize-url")
                .param("returnUrl", "/saved")
                .header("X-Device-Id", "dev-au-1"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.result").value("SUCCESS"))
            .andExpect(jsonPath("$.data").value("/oauth2/authorization/kakao"))
            .andExpect(jsonPath("$.error").doesNotExist())
    }

    @Test
    fun `GET authorize-url - provider path variable is reflected in the path`() {
        mockMvc.perform(get("/api/v1/auth/oauth2/naver/authorize-url"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.result").value("SUCCESS"))
            .andExpect(jsonPath("$.data").value("/oauth2/authorization/naver"))
    }

    @Test
    fun `GET authorize-url - absolute returnUrl is rejected as INVALID_REQUEST (HTTP 200 error envelope)`() {
        // 컨트롤러가 ApiResponse.error를 return(throw 아님) → HTTP는 200, 바디만 result=ERROR.
        mockMvc.perform(
            get("/api/v1/auth/oauth2/kakao/authorize-url")
                .param("returnUrl", "https://evil.example/phish"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.result").value("ERROR"))
            .andExpect(jsonPath("$.error.code").value(ErrorType.INVALID_REQUEST.code.name))
            .andExpect(jsonPath("$.data").doesNotExist())
    }

    @Test
    fun `GET authorize-url - protocol-relative returnUrl is rejected (open-redirect guard)`() {
        mockMvc.perform(
            get("/api/v1/auth/oauth2/kakao/authorize-url")
                .param("returnUrl", "//evil.example"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.result").value("ERROR"))
            .andExpect(jsonPath("$.error.code").value(ErrorType.INVALID_REQUEST.code.name))
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
