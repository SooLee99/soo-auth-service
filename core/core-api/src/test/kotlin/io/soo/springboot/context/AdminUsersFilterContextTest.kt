package io.soo.springboot.context

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.mockk
import io.soo.springboot.CoreApiApplication
import io.soo.springboot.clients.kakao.KakaoOAuthClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
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
 * E2 보강 — 관리자 사용자 목록 조회(`GET /api/v1/auth/admin/users`)의 검색 필터 분기 full-boot 컨텍스트 테스트.
 *
 * Cycle #25의 `AdminUsersContextTest`는 `role=ADMIN` 필터만 사용해, `AdminUserReadService.list` →
 * `UserRepository.searchUsers`의 나머지 세 분기(`keyword`·`userStatus`·`authProvider`)가 한 번도 실행되지
 * 않았다. `searchUsers`의 `Specification`은 인자별로 predicate를 누적한다:
 *  - `keyword`(non-blank): email·name·nickname·phoneNumber에 대한 lowercase LIKE OR
 *  - `userStatus`: equal
 *  - `role`: equal (Cycle #25에서 커버)
 *  - `authProvider`: equal
 *
 * 이 테스트는 같은 컨텍스트에 부트스트랩 관리자(authProvider=LOCAL/role=ADMIN/status=ACTIVE)와
 * 직접 발급한 카카오 USER(authProvider=KAKAO/role=USER/status=ACTIVE)만 존재한다는 점을 이용해
 * 나머지 세 필터 분기를 결정적으로 핀한다:
 *  1. `authProvider=KAKAO` → 카카오 USER만 노출, LOCAL 관리자는 제외.
 *  2. `keyword=<카카오 USER의 고유 이메일 조각>` → 그 USER가 결과에 포함(LIKE 매칭).
 *  3. `keyword=<아무 사용자와도 매칭되지 않는 고유 문자열>` → 빈 결과.
 *  4. `userStatus=BLOCKED` → 이 컨텍스트엔 BLOCKED 사용자가 없으므로 빈 결과(equal 분기, status별 분리 확인).
 *
 * 관리자 인가 게이트(`hasRole("ADMIN")`)는 Cycle #25에서 이미 핀했으므로 여기선 필터 계약에 집중한다.
 * 하네스(부트스트랩 시드 + admin 로그인, 카카오 USER 발급)는 `AdminUsersContextTest`와 동일하다.
 * 외부 upstream인 Kakao [KakaoOAuthClient]만 `@Primary` mockk로 대체해 네트워크 없이 실행한다.
 */
@Tag("context")
@SpringBootTest(classes = [CoreApiApplication::class])
@AutoConfigureMockMvc
@Import(AdminUsersFilterContextTest.MockKakaoClientConfig::class)
class AdminUsersFilterContextTest {

    @TestConfiguration
    class MockKakaoClientConfig {
        @Bean @Primary fun mockKakaoOAuthClient(): KakaoOAuthClient = mockk()
    }

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var kakaoOAuthClient: KakaoOAuthClient

    @BeforeEach
    fun setUp() {
        // authProvider 필터용 카카오 USER.
        stubKakao("ka_at_kf_provider", 4_295_010_001L, "kf-provider@triplan.kr", "필터유저A")
        // keyword 필터(매칭)용 카카오 USER — 고유 이메일 조각을 keyword로 검색한다.
        stubKakao("ka_at_kf_keyword", 4_295_010_002L, KEYWORD_TARGET_EMAIL, "필터유저B")
    }

    @Test
    fun `GET admin users - authProvider=KAKAO filter returns only KAKAO users (LOCAL admin excluded)`() {
        val adminToken = issueAdminAccessToken()
        val kakaoEmail = issueUserAccessTokenEmail("ka_at_kf_provider", "dev-kf-provider")

        val data = listUsers(adminToken, "authProvider" to "KAKAO")
        val content = data.path("content")
        assertTrue(content.isArray && content.size() > 0, "KAKAO 필터 결과는 비어 있지 않아야 한다")

        val emails = content.map { it.path("email").asText() }
        // 모든 항목이 KAKAO여야 하고, 방금 발급한 카카오 USER가 포함되어야 한다.
        content.forEach { assertEquals("KAKAO", it.path("authProvider").asText()) }
        assertTrue(emails.contains(kakaoEmail), "발급한 카카오 USER가 KAKAO 필터 결과에 있어야 한다")
        // LOCAL 부트스트랩 관리자는 KAKAO 필터에서 제외되어야 한다.
        assertTrue(!emails.contains(ADMIN_USERNAME), "LOCAL 관리자는 KAKAO 필터 결과에서 제외되어야 한다")
    }

    @Test
    fun `GET admin users - keyword filter matches the user's email fragment`() {
        val adminToken = issueAdminAccessToken()
        val kakaoEmail = issueUserAccessTokenEmail("ka_at_kf_keyword", "dev-kf-keyword")

        val data = listUsers(adminToken, "keyword" to KEYWORD_FRAGMENT)
        val content = data.path("content")
        val emails = content.map { it.path("email").asText() }
        assertTrue(emails.contains(kakaoEmail), "keyword(LIKE)로 이메일 조각이 매칭되어야 한다")
        // LIKE 매칭이므로 그 조각을 포함하지 않는 관리자 이메일은 결과에 없어야 한다.
        assertTrue(!emails.contains(ADMIN_USERNAME), "조각과 무관한 관리자는 keyword 결과에 없어야 한다")
    }

    @Test
    fun `GET admin users - keyword with no match returns empty content`() {
        val adminToken = issueAdminAccessToken()

        mockMvc.perform(
            get("/api/v1/auth/admin/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $adminToken")
                .param("keyword", "no-such-user-zzz-9f3a1c"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.result").value("SUCCESS"))
            .andExpect(jsonPath("$.data.content").isArray)
            .andExpect(jsonPath("$.data.content[0]").doesNotExist())
    }

    @Test
    fun `GET admin users - userStatus=BLOCKED filter returns empty content (no blocked users)`() {
        val adminToken = issueAdminAccessToken()

        mockMvc.perform(
            get("/api/v1/auth/admin/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $adminToken")
                .param("userStatus", "BLOCKED"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.result").value("SUCCESS"))
            .andExpect(jsonPath("$.data.content").isArray)
            // 이 컨텍스트엔 BLOCKED 사용자가 없다 → equal(userStatus=BLOCKED) 분기는 빈 결과.
            .andExpect(jsonPath("$.data.content[0]").doesNotExist())
    }

    /** `GET /users`를 필터 파라미터와 함께 호출하고 `data`(Page) 노드를 반환한다. */
    private fun listUsers(adminToken: String, vararg params: Pair<String, String>): JsonNode {
        var request = get("/api/v1/auth/admin/users")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $adminToken")
        params.forEach { (k, v) -> request = request.param(k, v) }
        val body = mockMvc.perform(request)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.result").value("SUCCESS"))
            .andReturn()
            .response
            .getContentAsString(Charsets.UTF_8)
        return objectMapper.readTree(body).path("data")
    }

    /** 검증된 공개 발급 경로로 ROLE_USER 액세스 토큰을 발급받고, 그 USER의 이메일을 반환한다. */
    private fun issueUserAccessTokenEmail(kakaoToken: String, deviceId: String): String {
        mockMvc.perform(
            post("/api/v1/auth/oauth2/kakao/token")
                .header("X-Device-Id", deviceId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("kakaoAccessToken" to kakaoToken))),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.token.accessToken").isNotEmpty)
        // setUp의 stub에서 이 토큰에 매핑한 이메일.
        return kakaoEmailByToken.getValue(kakaoToken)
    }

    /** 부트스트랩으로 만들어진 관리자 계정을 admin 로그인 경로로 로그인해 ROLE_ADMIN 토큰을 발급받는다. */
    private fun issueAdminAccessToken(): String {
        val body = mockMvc.perform(
            post("/api/v1/auth/admin/login")
                .header("X-Device-Id", "dev-adminusersfilter-admin")
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
        return objectMapper.readTree(body).path("data").path("accessToken").asText()
    }

    private val kakaoEmailByToken = mutableMapOf<String, String>()

    /** 카카오 `/v2/user/me` 응답 stub을 등록하고 토큰→이메일 매핑을 기록한다. */
    private fun stubKakao(token: String, kakaoId: Long, email: String, nickname: String) {
        kakaoEmailByToken[token] = email
        every { kakaoOAuthClient.fetchUserMe(token) } returns mapOf(
            "id" to kakaoId,
            "connected_at" to "2026-05-01T00:00:00Z",
            "kakao_account" to mapOf(
                "email" to email,
                "is_email_verified" to true,
                "profile" to mapOf(
                    "nickname" to nickname,
                    "profile_image_url" to "https://example.com/u.jpg",
                ),
            ),
        )
    }

    companion object {
        // 부트스트랩 관리자 계정 — 테스트 픽스처(로컬 H2 전용, 실제 비밀번호 아님).
        // 강한 비밀번호 규칙: 12자 이상 + 대/소문자 + 숫자 + 특수문자, username과 불일치.
        private const val ADMIN_USERNAME = "admin-usersfilter@triplan.test"
        private const val ADMIN_PASSWORD = "Adm1nUsrFlt!Pwd9"

        // keyword(매칭)용: 고유 이메일과 그 안에서 LIKE로 잡을 조각.
        private const val KEYWORD_FRAGMENT = "kf-keyword-uniq"
        private const val KEYWORD_TARGET_EMAIL = "$KEYWORD_FRAGMENT@triplan.kr"

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
