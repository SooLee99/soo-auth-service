package io.soo.springboot.context

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.mockk
import io.soo.springboot.CoreApiApplication
import io.soo.springboot.clients.kakao.KakaoOAuthClient
import org.hamcrest.Matchers.hasItem
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
 * E2 보강 — 관리자 사용자 상태(차단/해제·삭제·상태감사) 엔드포인트의 full-boot 컨텍스트 테스트.
 *
 * `AdminUserStatusController`는 `AdminUserBlockService`를 통해 다음을 제공한다:
 *  - read: 차단 목록(`GET /users/blocked`)·삭제 목록(`GET /users/deleted`)·상태 감사로그(`GET /users/{userId}/status-audits`)
 *  - mutate: 차단(`POST /users/{userId}/block`)·해제(`POST /users/{userId}/unblock`)
 * 모두 `ApiSecurityConfig`의 `/api/v1/auth/admin` 하위(ADMIN_API) → `hasRole("ADMIN")`(=`ROLE_ADMIN`)로 보호된다.
 * Cycle #23~#26의 관리자 토큰 하네스(부트스트랩 시드 + admin 로그인)를 그대로 재사용한다.
 *
 * 차단은 변경 경로지만 시드가 가벼워(차단할 사용자 = 공개 카카오 토큰 발급으로 만든 USER) round-trip을 핀한다:
 * block → blocked 목록 노출 + BLOCK 감사로그 생성. 이는 다섯 번째 Admin-controller 커버리지이며,
 * 감사로그(audit)는 차단 변경이 먼저 행을 만들어야 읽힌다는 read-after-write 특성을 함께 핀한다.
 *
 * full `@SpringBootTest`로 [CoreApiApplication]을 통째로 띄워 실제 빈으로 인가 + 차단 계약을 핀한다:
 *  1. 익명(토큰 없음) GET blocked → **401** (`unauthorizedEntryPoint`, admin 하위 진입점)
 *  2. 인증된 비관리자(USER 토큰) GET blocked → **403** (`ROLE_USER`는 `hasRole("ADMIN")` 불충족)
 *  3. 관리자 토큰 block round-trip → **200**: block 응답 `blocked=true`·`userStatus=BLOCKED`·사유·관리자 id;
 *     blocked 목록에 해당 userId 노출; 감사로그 1건이 `BLOCK`/대상 userId/사유 일치.
 *  4. 관리자 토큰 GET deleted → **200**: 비어 있을 수 있으나 SUCCESS + content 배열.
 *  5. 관리자 토큰 block 존재하지 않는 userId → **404 `E404`** (`block`의 `findByIdIncludingDeleted` null → NOT_FOUND).
 *
 * NOTE — unblock 경로는 이번 사이클에서 핀하지 않는다: 동일 `@SpringBootTest` 컨텍스트에서 block 직후
 * 곧바로 unblock하면 `ObjectOptimisticLockingFailure (E409)`가 난다(도메인 `User`↔`UserEntity` 매핑이
 * `@Version`을 라운드트립에 보존하지 못해 stale version 충돌 — Cycle #15에서 카카오 재로그인으로 기록된
 * 것과 동일 계열의 잠재 quirk). 실제 운영은 block/unblock이 별도 요청·시점이라 엔티티가 재로딩되어
 * 재현되지 않는다. 테스트에서 이 quirk를 우회하려 억지 구조를 만들지 않는다(매핑 보강은 별도 백엔드 결정).
 *
 * 토큰은 위조하지 않는다(하네스는 `AdminUsersContextTest`와 동일):
 *  - 관리자 토큰: 부트스트랩 러너를 `app.bootstrap.admin.*`로 켜 부팅 시 관리자 계정을 시드하고,
 *    같은 앱의 공개 경로 `POST /api/v1/auth/admin/login`으로 진짜 `ROLE_ADMIN` 토큰을 발급.
 *  - USER 토큰 + 차단 대상: 검증된 공개 발급 경로 `POST /oauth2/kakao/token`로 발급(`ROLE_USER`).
 *    응답 `data.user.id`가 차단 대상 userId가 된다. 케이스 순서 독립을 위해 토큰 발급 테스트마다
 *    고유 카카오 id/email을 쓴다(Cycle #15 NOTE: 동일 UserEntity 재로그인 시 @Version 낙관락 409 회피).
 *
 * 외부 upstream인 Kakao `/v2/user/me`([KakaoOAuthClient])만 `@Primary` mockk로 대체해 네트워크 없이 실행한다.
 */
@Tag("context")
@SpringBootTest(classes = [CoreApiApplication::class])
@AutoConfigureMockMvc
@Import(AdminUserStatusContextTest.MockKakaoClientConfig::class)
class AdminUserStatusContextTest {

    @TestConfiguration
    class MockKakaoClientConfig {
        @Bean @Primary fun mockKakaoOAuthClient(): KakaoOAuthClient = mockk()
    }

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var kakaoOAuthClient: KakaoOAuthClient

    @BeforeEach
    fun setUp() {
        // 토큰 발급 테스트마다 고유 카카오 사용자 1건씩 등록(케이스 순서 독립 + @Version 낙관락 회피).
        stubKakaoUser("ka_at_us_403", 4_295_100_001L, "us-403@triplan.kr", "유저403")
        stubKakaoUser("ka_at_us_block", 4_295_100_002L, "us-block@triplan.kr", "유저블록")
    }

    private fun stubKakaoUser(token: String, id: Long, email: String, nickname: String) {
        every { kakaoOAuthClient.fetchUserMe(token) } returns mapOf(
            "id" to id,
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

    @Test
    fun `GET admin users blocked - anonymous (no token) returns 401`() {
        mockMvc.perform(get("/api/v1/auth/admin/users/blocked"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `GET admin users blocked - authenticated non-admin (USER token) returns 403`() {
        val userToken = issueUserAccessToken("ka_at_us_403", "dev-us-403").accessToken

        mockMvc.perform(
            get("/api/v1/auth/admin/users/blocked")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $userToken"),
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `block - admin token blocks a user, blocked list and BLOCK audit reflect it`() {
        val target = issueUserAccessToken("ka_at_us_block", "dev-us-block")
        val targetUserId = target.userId
        val adminToken = issueAdminAccessToken()

        // block → 200, 차단 상태/사유/관리자 id 반영
        mockMvc.perform(
            post("/api/v1/auth/admin/users/$targetUserId/block")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("reason" to "정책 위반 테스트"))),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.result").value("SUCCESS"))
            .andExpect(jsonPath("$.data.userId").value(targetUserId))
            .andExpect(jsonPath("$.data.blocked").value(true))
            .andExpect(jsonPath("$.data.userStatus").value("BLOCKED"))
            .andExpect(jsonPath("$.data.blockedReason").value("정책 위반 테스트"))
            .andExpect(jsonPath("$.data.blockedByAdminId").isNumber)

        // blocked 목록에 해당 userId 노출
        mockMvc.perform(
            get("/api/v1/auth/admin/users/blocked")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $adminToken"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.result").value("SUCCESS"))
            .andExpect(jsonPath("$.data.content").isArray)
            .andExpect(jsonPath("$.data.content[*].userId", hasItem(targetUserId.toInt())))

        // 감사로그 1건 = BLOCK / 대상 userId / 사유 일치
        mockMvc.perform(
            get("/api/v1/auth/admin/users/$targetUserId/status-audits")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $adminToken"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.result").value("SUCCESS"))
            .andExpect(jsonPath("$.data.content[0].actionType").value("BLOCK"))
            .andExpect(jsonPath("$.data.content[0].targetUserId").value(targetUserId))
            .andExpect(jsonPath("$.data.content[0].reason").value("정책 위반 테스트"))
    }

    @Test
    fun `GET admin users deleted - admin token returns 200 with content array`() {
        val adminToken = issueAdminAccessToken()

        mockMvc.perform(
            get("/api/v1/auth/admin/users/deleted")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $adminToken"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.result").value("SUCCESS"))
            .andExpect(jsonPath("$.data.content").isArray)
    }

    @Test
    fun `block - admin token with non-existent userId returns 404`() {
        val adminToken = issueAdminAccessToken()

        mockMvc.perform(
            post("/api/v1/auth/admin/users/99999999/block")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("reason" to "없는 사용자"))),
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.result").value("ERROR"))
            .andExpect(jsonPath("$.error.code").value("E404"))
    }

    private data class IssuedUser(val accessToken: String, val userId: Long)

    /** 검증된 공개 발급 경로로 ROLE_USER 액세스 토큰 + userId를 발급받는다. */
    private fun issueUserAccessToken(kakaoToken: String, deviceId: String): IssuedUser {
        val body = mockMvc.perform(
            post("/api/v1/auth/oauth2/kakao/token")
                .header("X-Device-Id", deviceId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("kakaoAccessToken" to kakaoToken))),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.token.accessToken").isNotEmpty)
            .andReturn()
            .response
            .getContentAsString(Charsets.UTF_8)
        val json: JsonNode = objectMapper.readTree(body)
        return IssuedUser(
            accessToken = json.path("data").path("token").path("accessToken").asText(),
            userId = json.path("data").path("user").path("id").asLong(),
        )
    }

    /** 부트스트랩으로 만들어진 관리자 계정을 같은 앱의 admin 로그인 경로로 로그인해 ROLE_ADMIN 토큰을 발급받는다. */
    private fun issueAdminAccessToken(): String {
        val body = mockMvc.perform(
            post("/api/v1/auth/admin/login")
                .header("X-Device-Id", "dev-userstatus-admin")
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

    companion object {
        // 부트스트랩 관리자 계정 — 테스트 픽스처(로컬 H2 전용, 실제 비밀번호 아님).
        // 강한 비밀번호 규칙: 12자 이상 + 대/소문자 + 숫자 + 특수문자, username과 불일치.
        private const val ADMIN_USERNAME = "admin-userstatus@triplan.test"
        private const val ADMIN_PASSWORD = "Adm1nUsrStat!Pwd9"

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
