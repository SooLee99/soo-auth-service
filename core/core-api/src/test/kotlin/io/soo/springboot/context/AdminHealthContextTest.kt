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
 * E2 보강 — 관리자 헬스 엔드포인트(`GET /api/v1/auth/admin/health`)의 full-boot 컨텍스트 테스트.
 *
 * `AdminHealthController.getAdminHealth`는 `HealthSnapshotService.adminDetails`를 그대로 내려준다.
 * 이 경로는 `ApiSecurityConfig`에서 `/api/v1/auth/admin` 하위(ADMIN_API 패턴) → `hasRole("ADMIN")`(=`ROLE_ADMIN`)로 보호된다.
 * Cycle #19의 `HealthContextTest`는 공개 `/health`가 `adminDetails`-전용 내부 정보(`profiles`/`build`/
 * `components`/`system`/`links`)를 **숨긴다**는 것을 핀했다. 이 테스트는 그 **대조(contrast)** 다 —
 * 관리자 토큰으로는 바로 그 내부 정보가 **노출**됨을 핀해, 정보 노출 경계를 양방향으로 못박는다.
 *
 * full `@SpringBootTest`로 [CoreApiApplication]을 통째로 띄워 실제 빈으로 인가 계약을 핀한다:
 *  1. 익명(토큰 없음) → **401** (`unauthorizedEntryPoint`, `/api` 하위 진입점)
 *  2. 인증된 비관리자(USER 토큰) → **403** (`restAccessDeniedHandler`; `ROLE_USER`는 `hasRole("ADMIN")` 불충족)
 *  3. 관리자 토큰 → **200 + `adminDetails`**: `profiles`(local 포함)·`build`·`components`(db/redis)·`system`·`links`
 *     모두 노출 (Cycle #19에서 `/health`가 `doesNotExist`로 숨긴 바로 그 필드들).
 *
 * 토큰은 위조하지 않는다.
 *  - **관리자 토큰**: 앱 부트스트랩 러너(`AdminInitRunner`)가 부팅 시 관리자 계정을 만들도록
 *    `app.bootstrap.admin.{enabled,username,password}`를 `@DynamicPropertySource`로 주입하고
 *    (active profile `local`은 기본 `allowed-profiles`(local,local-dev)에 포함), 같은 앱의 공개 경로
 *    `POST /api/v1/auth/admin/login`(JSON email/password)으로 진짜 `ROLE_ADMIN` 액세스 토큰을 발급받는다.
 *    이 필터는 `@ConditionalOnProperty("app.auth.method.admin.enabled")`라 함께 켠다.
 *  - **USER 토큰**: 검증된 공개 발급 경로 `POST /oauth2/kakao/token`로 발급(`ROLE_USER`).
 *
 * 외부 upstream인 Kakao `/v2/user/me`([KakaoOAuthClient])만 `@Primary` mockk로 대체해 네트워크 없이 실행한다.
 * JWT 키스토어/Kakao registration/Redis 부팅 값은 [LocalSessionContextTest]와 동일하게 주입한다.
 */
@Tag("context")
@SpringBootTest(classes = [CoreApiApplication::class])
@AutoConfigureMockMvc
@Import(AdminHealthContextTest.MockKakaoClientConfig::class)
class AdminHealthContextTest {

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
        every { kakaoOAuthClient.fetchUserMe("ka_at_adminhealth") } returns mapOf(
            "id" to 4_295_007_777L,
            "connected_at" to "2026-05-01T00:00:00Z",
            "kakao_account" to mapOf(
                "email" to "adminhealth-user@triplan.kr",
                "is_email_verified" to true,
                "profile" to mapOf(
                    "nickname" to "유저",
                    "profile_image_url" to "https://example.com/u.jpg",
                ),
            ),
        )
    }

    @Test
    fun `GET admin health - anonymous (no token) returns 401`() {
        mockMvc.perform(get("/api/v1/auth/admin/health"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `GET admin health - authenticated non-admin (USER token) returns 403`() {
        val userToken = issueUserAccessToken()

        mockMvc.perform(
            get("/api/v1/auth/admin/health")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $userToken"),
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `GET admin health - admin token returns 200 with admin-only internals exposed`() {
        val adminToken = issueAdminAccessToken()

        // Cycle #19에서 /health가 doesNotExist로 숨긴 바로 그 필드들이, 관리자 경로에서는 노출된다.
        mockMvc.perform(
            get("/api/v1/auth/admin/health")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $adminToken"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.result").value("SUCCESS"))
            .andExpect(jsonPath("$.data.status").isNotEmpty)
            // 관리자 전용 내부 정보 — 모두 present.
            .andExpect(jsonPath("$.data.profiles").isArray)
            .andExpect(jsonPath("$.data.profiles[0]").value("local"))
            .andExpect(jsonPath("$.data.build").exists())
            .andExpect(jsonPath("$.data.components").exists())
            .andExpect(jsonPath("$.data.components.database").exists())
            .andExpect(jsonPath("$.data.components.redis").exists())
            .andExpect(jsonPath("$.data.system").exists())
            .andExpect(jsonPath("$.data.links").exists())
    }

    /** 검증된 공개 발급 경로로 ROLE_USER 액세스 토큰을 발급받는다. */
    private fun issueUserAccessToken(): String {
        val body = mockMvc.perform(
            post("/api/v1/auth/oauth2/kakao/token")
                .header("X-Device-Id", "dev-adminhealth-user")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("kakaoAccessToken" to "ka_at_adminhealth"))),
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
                .header("X-Device-Id", "dev-adminhealth-admin")
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
        return parseAdminToken(body)
    }

    private fun parseAdminToken(body: String): String {
        val json: JsonNode = objectMapper.readTree(body)
        return json.path("data").path("accessToken").asText()
    }

    companion object {
        // 부트스트랩 관리자 계정 — 테스트 픽스처(로컬 H2 전용, 실제 비밀번호 아님).
        // 강한 비밀번호 규칙: 12자 이상 + 대/소문자 + 숫자 + 특수문자, username과 불일치.
        private const val ADMIN_USERNAME = "admin-health@triplan.test"
        private const val ADMIN_PASSWORD = "Adm1nHealth!Pwd"

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
            // active profile은 기본 local → 기본 allowed-profiles(local,local-dev)에 포함되어 부팅 시 시드된다.

            registry.add("spring.security.oauth2.client.registration.kakao.client-id") { "test-kakao-client" }
            registry.add("spring.security.oauth2.client.registration.kakao.client-secret") { "test-kakao-secret" }

            registry.add("spring.data.redis.host") { System.getenv("REDIS_HOST") ?: "127.0.0.1" }
            registry.add("spring.data.redis.port") { System.getenv("REDIS_PORT") ?: "6379" }
        }
    }
}
