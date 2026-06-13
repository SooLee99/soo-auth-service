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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * E2 보강 — 관리자 인증수단 설정 엔드포인트(`GET /api/v1/auth/admin/auth-methods`,
 * `PATCH /api/v1/auth/admin/auth-methods/{method}`)의 full-boot 컨텍스트 테스트.
 *
 * `AdminAuthMethodController`는 `AuthMethodConfigService`를 통해 인증수단(EMAIL·ID·SMS·OAUTH2) 설정을
 * 목록 조회(`list`)하고 단건 토글(`setEnabled`)한다. 이 경로는 `ApiSecurityConfig`에서
 * `/api/v1/auth/admin` 하위(ADMIN_API 패턴) → `hasRole("ADMIN")`(=`ROLE_ADMIN`)로 보호된다.
 * Cycle #23/#24/#25의 관리자 토큰 하네스(부트스트랩 시드 + admin 로그인)를 그대로 재사용한다.
 * 네 번째 Admin 컨트롤러 커버리지이며, 읽기(목록)와 가벼운 토글(PATCH round-trip)을 함께 핀한다.
 *
 * full `@SpringBootTest`로 [CoreApiApplication]을 통째로 띄워 실제 빈으로 인가 + 조회/토글 계약을 핀한다:
 *  1. 익명(토큰 없음) GET → **401** (`unauthorizedEntryPoint`, `/api` 하위 진입점)
 *  2. 인증된 비관리자(USER 토큰) GET → **403** (`ROLE_USER`는 `hasRole("ADMIN")` 불충족)
 *  3. 관리자 토큰 GET 목록 → **200**: `AuthMethod.entries` 순서대로 4건
 *     (EMAIL·ID·SMS·OAUTH2). `auth_method_config` 테이블이 비어 있으면 전부 `enabled=true` 기본값.
 *  4. 관리자 토큰 PATCH round-trip: `/{EMAIL}`을 `enabled=false`로 토글 → 200 (method=EMAIL/enabled=false),
 *     GET 재조회로 영속 확인 → 다시 `enabled=true`로 복원해 공유 H2 상태를 깨끗이 되돌린다(케이스 순서 독립성).
 *  5. 관리자 토큰 PATCH + 알 수 없는 method(`BOGUS`) → **400 `E400`** (`parseMethod`의 `INVALID_PARAMETER`).
 *
 * 토큰은 위조하지 않는다(하네스는 `AdminUsersContextTest`와 동일):
 *  - 관리자 토큰: 부트스트랩 러너를 `app.bootstrap.admin.*`로 켜 부팅 시 관리자 계정을 시드하고,
 *    같은 앱의 공개 경로 `POST /api/v1/auth/admin/login`으로 진짜 `ROLE_ADMIN` 토큰을 발급.
 *  - USER 토큰: 검증된 공개 발급 경로 `POST /oauth2/kakao/token`로 발급(`ROLE_USER`).
 *
 * 외부 upstream인 Kakao `/v2/user/me`([KakaoOAuthClient])만 `@Primary` mockk로 대체해 네트워크 없이 실행한다.
 */
@Tag("context")
@SpringBootTest(classes = [CoreApiApplication::class])
@AutoConfigureMockMvc
@Import(AdminAuthMethodContextTest.MockKakaoClientConfig::class)
class AdminAuthMethodContextTest {

    @TestConfiguration
    class MockKakaoClientConfig {
        @Bean @Primary fun mockKakaoOAuthClient(): KakaoOAuthClient = mockk()
    }

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var kakaoOAuthClient: KakaoOAuthClient

    @BeforeEach
    fun setUp() {
        // 비관리자 USER 토큰 발급용 카카오 사용자 1건.
        every { kakaoOAuthClient.fetchUserMe("ka_at_authmethods") } returns mapOf(
            "id" to 4_295_008_888L,
            "connected_at" to "2026-05-01T00:00:00Z",
            "kakao_account" to mapOf(
                "email" to "authmethods-user@triplan.kr",
                "is_email_verified" to true,
                "profile" to mapOf(
                    "nickname" to "유저",
                    "profile_image_url" to "https://example.com/u.jpg",
                ),
            ),
        )
    }

    @Test
    fun `GET admin auth-methods - anonymous (no token) returns 401`() {
        mockMvc.perform(get("/api/v1/auth/admin/auth-methods"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `GET admin auth-methods - authenticated non-admin (USER token) returns 403`() {
        val userToken = issueUserAccessToken()

        mockMvc.perform(
            get("/api/v1/auth/admin/auth-methods")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $userToken"),
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `GET admin auth-methods - admin token returns 200 with all four methods`() {
        val adminToken = issueAdminAccessToken()

        mockMvc.perform(
            get("/api/v1/auth/admin/auth-methods")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $adminToken"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.result").value("SUCCESS"))
            .andExpect(jsonPath("$.data").isArray)
            // list()는 AuthMethod.entries 순서(EMAIL·ID·SMS·OAUTH2) 4건을 반환.
            .andExpect(jsonPath("$.data.length()").value(4))
            .andExpect(jsonPath("$.data[0].method").value("EMAIL"))
            // 설정 테이블이 비어 있으면 enabled 기본값 true.
            .andExpect(jsonPath("$.data[0].enabled").value(true))
            .andExpect(jsonPath("$.data[3].method").value("OAUTH2"))
            .andExpect(jsonPath("$.data[3].enabled").value(true))
    }

    @Test
    fun `PATCH admin auth-method - admin token toggles EMAIL and persists, then restores`() {
        val adminToken = issueAdminAccessToken()

        // EMAIL을 false로 토글 → 200, 응답에 반영.
        mockMvc.perform(
            patch("/api/v1/auth/admin/auth-methods/EMAIL")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("enabled" to false))),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.result").value("SUCCESS"))
            .andExpect(jsonPath("$.data.method").value("EMAIL"))
            .andExpect(jsonPath("$.data.enabled").value(false))

        // GET 재조회로 영속 확인 — EMAIL enabled=false.
        mockMvc.perform(
            get("/api/v1/auth/admin/auth-methods")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $adminToken"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data[0].method").value("EMAIL"))
            .andExpect(jsonPath("$.data[0].enabled").value(false))

        // 공유 H2 상태를 깨끗이 복원 — EMAIL을 다시 true로.
        mockMvc.perform(
            patch("/api/v1/auth/admin/auth-methods/EMAIL")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("enabled" to true))),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.enabled").value(true))
    }

    @Test
    fun `PATCH admin auth-method - admin token with unknown method returns 400`() {
        val adminToken = issueAdminAccessToken()

        mockMvc.perform(
            patch("/api/v1/auth/admin/auth-methods/BOGUS")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("enabled" to true))),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.result").value("ERROR"))
            .andExpect(jsonPath("$.error.code").value("E400"))
    }

    /** 검증된 공개 발급 경로로 ROLE_USER 액세스 토큰을 발급받는다. */
    private fun issueUserAccessToken(): String {
        val body = mockMvc.perform(
            post("/api/v1/auth/oauth2/kakao/token")
                .header("X-Device-Id", "dev-authmethods-user")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("kakaoAccessToken" to "ka_at_authmethods"))),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.token.accessToken").isNotEmpty)
            .andReturn()
            .response
            .getContentAsString(Charsets.UTF_8)
        return objectMapper.readTree(body).path("data").path("token").path("accessToken").asText()
    }

    /** 부트스트랩으로 만들어진 관리자 계정을 같은 앱의 admin 로그인 경로로 로그인해 ROLE_ADMIN 토큰을 발급받는다. */
    private fun issueAdminAccessToken(): String {
        val body = mockMvc.perform(
            post("/api/v1/auth/admin/login")
                .header("X-Device-Id", "dev-authmethods-admin")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        mapOf("email" to ADMIN_USERNAME, "password" to ADMIN_PASSWORD),
                    ),
                ),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.accessToken").isNotEmpty)
            .andReturn()
            .response
            .getContentAsString(Charsets.UTF_8)
        val json: JsonNode = objectMapper.readTree(body)
        return json.path("data").path("accessToken").asText()
    }

    companion object {
        // 부트스트랩 관리자 계정 — 테스트 픽스처(로컬 H2 전용, 실제 비밀번호 아님).
        // 강한 비밀번호 규칙: 12자 이상 + 대/소문자 + 숫자 + 특수문자, username과 불일치.
        private const val ADMIN_USERNAME = "admin-authmethods@triplan.test"
        private const val ADMIN_PASSWORD = "Adm1nAuthM!Pwd9"

        @JvmStatic
        @DynamicPropertySource
        fun props(registry: DynamicPropertyRegistry) {
            val ksPassword = System.getenv("JWT_KEYSTORE_PASSWORD") ?: "devjwtpass"
            registry.add("app.security.jwt.keystore.password") { ksPassword }
            registry.add("app.security.jwt.keystore.key-password") {
                System.getenv("JWT_KEY_PASSWORD") ?: ksPassword
            }
            registry.add("app.security.jwt.keystore.alias") { System.getenv("JWT_KEY_ALIAS") ?: "jwt" }

            // admin 로그인 필터(@ConditionalOnProperty)와 부트스트랩 러너를 켠다.
            registry.add("app.auth.method.admin.enabled") { "true" }
            registry.add("app.bootstrap.admin.enabled") { "true" }
            registry.add("app.bootstrap.admin.username") { ADMIN_USERNAME }
            registry.add("app.bootstrap.admin.password") { ADMIN_PASSWORD }

            registry.add("spring.security.oauth2.client.registration.kakao.client-id") { "test-kakao-client" }
            registry.add("spring.security.oauth2.client.registration.kakao.client-secret") { "test-kakao-secret" }

            registry.add("spring.data.redis.host") { System.getenv("REDIS_HOST") ?: "127.0.0.1" }
            registry.add("spring.data.redis.port") { System.getenv("REDIS_PORT") ?: "6379" }
        }
    }
}
