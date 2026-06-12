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
 * E2 보강 — 관리자 사용자 조회 엔드포인트(`GET /api/v1/auth/admin/users`,
 * `GET /api/v1/auth/admin/users/{userId}`)의 full-boot 컨텍스트 테스트.
 *
 * `AdminUsersController`는 `AdminUserReadService`를 통해 사용자 목록(페이징·필터)과 단건 상세를 조회한다.
 * 이 경로는 `ApiSecurityConfig`에서 `/api/v1/auth/admin` 하위(ADMIN_API 패턴) → `hasRole("ADMIN")`
 * (=`ROLE_ADMIN`)로 보호된다. Cycle #23/#24의 관리자 토큰 하네스(부트스트랩 시드 + admin 로그인)를
 * 그대로 재사용한다. read 경로만 핀하고, 변경 경로(update/password-reset/delete/token-revoke)는
 * 픽스처 부담이 커 이번 사이클 범위에서 제외한다.
 *
 * full `@SpringBootTest`로 [CoreApiApplication]을 통째로 띄워 실제 빈으로 인가 + 조회 계약을 핀한다:
 *  1. 익명(토큰 없음) → **401** (`unauthorizedEntryPoint`, `/api` 하위 진입점)
 *  2. 인증된 비관리자(USER 토큰) → **403** (`ROLE_USER`는 `hasRole("ADMIN")` 불충족)
 *  3. 관리자 토큰 + `role=ADMIN` 필터 목록 → **200**: 부트스트랩으로 시드된 관리자(이메일=부트스트랩 username,
 *     role=ADMIN)가 페이지에 노출된다. ADMIN은 부트스트랩 1명뿐이라 필터 결과 맨 앞이 그 관리자다.
 *  4. 관리자 토큰 + 단건 상세(`/users/{adminId}`) → **200**: 목록에서 뽑은 관리자 userId로 상세 조회 시
 *     userId·email·role(ADMIN)이 일치한다.
 *  5. 관리자 토큰 + 존재하지 않는 userId → **404 `E404`** (`getById`의 `NOT_FOUND`).
 *
 * 토큰은 위조하지 않는다(하네스는 `AdminLoginHistoryContextTest`와 동일):
 *  - 관리자 토큰: 부트스트랩 러너를 `app.bootstrap.admin.*`로 켜 부팅 시 관리자 계정을 시드하고,
 *    같은 앱의 공개 경로 `POST /api/v1/auth/admin/login`으로 진짜 `ROLE_ADMIN` 토큰을 발급.
 *  - USER 토큰: 검증된 공개 발급 경로 `POST /oauth2/kakao/token`로 발급(`ROLE_USER`).
 *
 * 외부 upstream인 Kakao `/v2/user/me`([KakaoOAuthClient])만 `@Primary` mockk로 대체해 네트워크 없이 실행한다.
 */
@Tag("context")
@SpringBootTest(classes = [CoreApiApplication::class])
@AutoConfigureMockMvc
@Import(AdminUsersContextTest.MockKakaoClientConfig::class)
class AdminUsersContextTest {

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
        every { kakaoOAuthClient.fetchUserMe("ka_at_adminusers") } returns mapOf(
            "id" to 4_295_009_999L,
            "connected_at" to "2026-05-01T00:00:00Z",
            "kakao_account" to mapOf(
                "email" to "adminusers-user@triplan.kr",
                "is_email_verified" to true,
                "profile" to mapOf(
                    "nickname" to "유저",
                    "profile_image_url" to "https://example.com/u.jpg",
                ),
            ),
        )
    }

    @Test
    fun `GET admin users - anonymous (no token) returns 401`() {
        mockMvc.perform(get("/api/v1/auth/admin/users"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `GET admin users - authenticated non-admin (USER token) returns 403`() {
        val userToken = issueUserAccessToken()

        mockMvc.perform(
            get("/api/v1/auth/admin/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $userToken"),
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `GET admin users - admin token with role=ADMIN filter returns 200 with the seeded admin`() {
        val adminToken = issueAdminAccessToken()

        mockMvc.perform(
            get("/api/v1/auth/admin/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $adminToken")
                .param("role", "ADMIN"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.result").value("SUCCESS"))
            .andExpect(jsonPath("$.data.content").isArray)
            // ADMIN은 부트스트랩 1명뿐 → 필터 결과 맨 앞이 그 관리자.
            .andExpect(jsonPath("$.data.content[0].role").value("ADMIN"))
            .andExpect(jsonPath("$.data.content[0].email").value(ADMIN_USERNAME))
            .andExpect(jsonPath("$.data.content[0].userId").isNumber)
    }

    @Test
    fun `GET admin user detail - admin token returns 200 with matching userId and role`() {
        val adminToken = issueAdminAccessToken()
        val adminUserId = resolveAdminUserId(adminToken)

        mockMvc.perform(
            get("/api/v1/auth/admin/users/$adminUserId")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $adminToken"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.result").value("SUCCESS"))
            .andExpect(jsonPath("$.data.userId").value(adminUserId))
            .andExpect(jsonPath("$.data.email").value(ADMIN_USERNAME))
            .andExpect(jsonPath("$.data.role").value("ADMIN"))
    }

    @Test
    fun `GET admin user detail - admin token with non-existent userId returns 404`() {
        val adminToken = issueAdminAccessToken()

        mockMvc.perform(
            get("/api/v1/auth/admin/users/99999999")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $adminToken"),
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.result").value("ERROR"))
            .andExpect(jsonPath("$.error.code").value("E404"))
    }

    /** 관리자 목록(role=ADMIN)에서 시드된 관리자 userId를 뽑아낸다. */
    private fun resolveAdminUserId(adminToken: String): Long {
        val body = mockMvc.perform(
            get("/api/v1/auth/admin/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $adminToken")
                .param("role", "ADMIN"),
        )
            .andExpect(status().isOk)
            .andReturn()
            .response
            .getContentAsString(Charsets.UTF_8)
        return objectMapper.readTree(body).path("data").path("content").path(0).path("userId").asLong()
    }

    /** 검증된 공개 발급 경로로 ROLE_USER 액세스 토큰을 발급받는다. */
    private fun issueUserAccessToken(): String {
        val body = mockMvc.perform(
            post("/api/v1/auth/oauth2/kakao/token")
                .header("X-Device-Id", "dev-adminusers-user")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("kakaoAccessToken" to "ka_at_adminusers"))),
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
                .header("X-Device-Id", "dev-adminusers-admin")
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
        private const val ADMIN_USERNAME = "admin-users@triplan.test"
        private const val ADMIN_PASSWORD = "Adm1nUsers!Pwd9"

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
