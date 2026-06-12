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
 * E2 보강 — 관리자 로그인 이력 엔드포인트(`GET /api/v1/auth/admin/login-history`)의 full-boot 컨텍스트 테스트.
 *
 * `AdminLoginHistoryController.listLoginHistory`는 인증된 관리자 본인의 로그인 이력을
 * `LoginHistoryService.findByUserId`(또는 startDate·endDate가 모두 주어지면 `findByUserIdAndDateRange`)로
 * 페이지 조회해 내려준다. 이 경로는 `ApiSecurityConfig`에서 `/api/v1/auth/admin` 하위(ADMIN_API 패턴) →
 * `hasRole("ADMIN")`(=`ROLE_ADMIN`)로 보호된다. Cycle #23의 `AdminHealthContextTest`가 처음 세운
 * 관리자 토큰 하네스(부트스트랩 시드 + admin 로그인)를 그대로 재사용한다.
 *
 * full `@SpringBootTest`로 [CoreApiApplication]을 통째로 띄워 실제 빈으로 인가 + 조회 계약을 핀한다:
 *  1. 익명(토큰 없음) → **401** (`unauthorizedEntryPoint`, `/api` 하위 진입점)
 *  2. 인증된 비관리자(USER 토큰) → **403** (`ROLE_USER`는 `hasRole("ADMIN")` 불충족)
 *  3. 관리자 토큰 → **200 + 본인 로그인 이력 페이지**: 관리자가 같은 앱의 `POST /api/v1/auth/admin/login`으로
 *     로그인하면 `AdminLoginSuccessHandler`가 `LoginHistory(loginType=LOCAL, status=SUCCESS)` 1건을 기록하므로,
 *     본인 userId로 필터된 페이지에 그 SUCCESS 행이 최신순(`createdAt` DESC)으로 노출된다.
 *  4. 관리자 토큰 + 넓은 날짜 범위(startDate·endDate) → **200**: `findByUserIdAndDateRange`
 *     (= `findByUserIdAndCreatedAtBetween`, 양끝 inclusive) 분기를 타고도 같은 행이 잡힌다.
 *
 * 이력은 본인 userId로 필터되므로 ②에서 발급한 카카오 USER 로그인 기록은 관리자 페이지에 섞이지 않는다.
 *
 * 토큰은 위조하지 않는다(하네스는 `AdminHealthContextTest`와 동일):
 *  - 관리자 토큰: 부트스트랩 러너(`AdminInitRunner`)를 `app.bootstrap.admin.*`로 켜 부팅 시 관리자 계정을 시드하고,
 *    같은 앱의 공개 경로 `POST /api/v1/auth/admin/login`으로 진짜 `ROLE_ADMIN` 토큰을 발급(필터는
 *    `@ConditionalOnProperty("app.auth.method.admin.enabled")`라 함께 켠다).
 *  - USER 토큰: 검증된 공개 발급 경로 `POST /oauth2/kakao/token`로 발급(`ROLE_USER`).
 *
 * 외부 upstream인 Kakao `/v2/user/me`([KakaoOAuthClient])만 `@Primary` mockk로 대체해 네트워크 없이 실행한다.
 */
@Tag("context")
@SpringBootTest(classes = [CoreApiApplication::class])
@AutoConfigureMockMvc
@Import(AdminLoginHistoryContextTest.MockKakaoClientConfig::class)
class AdminLoginHistoryContextTest {

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
        every { kakaoOAuthClient.fetchUserMe("ka_at_adminloghist") } returns mapOf(
            "id" to 4_295_008_888L,
            "connected_at" to "2026-05-01T00:00:00Z",
            "kakao_account" to mapOf(
                "email" to "adminloghist-user@triplan.kr",
                "is_email_verified" to true,
                "profile" to mapOf(
                    "nickname" to "유저",
                    "profile_image_url" to "https://example.com/u.jpg",
                ),
            ),
        )
    }

    @Test
    fun `GET admin login-history - anonymous (no token) returns 401`() {
        mockMvc.perform(get("/api/v1/auth/admin/login-history"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `GET admin login-history - authenticated non-admin (USER token) returns 403`() {
        val userToken = issueUserAccessToken()

        mockMvc.perform(
            get("/api/v1/auth/admin/login-history")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $userToken"),
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `GET admin login-history - admin token returns 200 with own SUCCESS login record`() {
        // admin 로그인 자체가 SUCCESS/LOCAL 이력 1건을 남긴다 → 본인 이력 페이지에 그대로 노출.
        val adminToken = issueAdminAccessToken()

        mockMvc.perform(
            get("/api/v1/auth/admin/login-history")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $adminToken"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.result").value("SUCCESS"))
            .andExpect(jsonPath("$.data.content").isArray)
            // 최신순(createdAt DESC) → 방금 만든 SUCCESS/LOCAL 로그인 기록이 맨 앞.
            .andExpect(jsonPath("$.data.content[0].loginType").value("LOCAL"))
            .andExpect(jsonPath("$.data.content[0].status").value("SUCCESS"))
            .andExpect(jsonPath("$.data.content[0].userEmail").isNotEmpty)
            .andExpect(jsonPath("$.data.content[0].userId").isNumber)
    }

    @Test
    fun `GET admin login-history - admin token with wide date range hits range branch and returns the record`() {
        val adminToken = issueAdminAccessToken()

        // startDate·endDate가 모두 주어지면 findByUserIdAndDateRange(createdAt BETWEEN, 양끝 inclusive) 분기.
        mockMvc.perform(
            get("/api/v1/auth/admin/login-history")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $adminToken")
                .param("startDate", "2020-01-01T00:00:00")
                .param("endDate", "2999-12-31T23:59:59"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.result").value("SUCCESS"))
            .andExpect(jsonPath("$.data.content").isArray)
            .andExpect(jsonPath("$.data.content[0].status").value("SUCCESS"))
            .andExpect(jsonPath("$.data.content[0].loginType").value("LOCAL"))
    }

    /** 검증된 공개 발급 경로로 ROLE_USER 액세스 토큰을 발급받는다. */
    private fun issueUserAccessToken(): String {
        val body = mockMvc.perform(
            post("/api/v1/auth/oauth2/kakao/token")
                .header("X-Device-Id", "dev-adminloghist-user")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("kakaoAccessToken" to "ka_at_adminloghist"))),
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
                .header("X-Device-Id", "dev-adminloghist-admin")
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
        private const val ADMIN_USERNAME = "admin-loghist@triplan.test"
        private const val ADMIN_PASSWORD = "Adm1nLogHist!Pwd"

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
