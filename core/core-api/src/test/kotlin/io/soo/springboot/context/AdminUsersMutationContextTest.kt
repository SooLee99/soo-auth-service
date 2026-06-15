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
 * E2 보강 — 관리자 사용자 변경 엔드포인트의 full-boot 컨텍스트 테스트.
 *
 * Cycle #25의 `AdminUsersContextTest`는 조회(list·detail) 경로만 핀했고, 변경 경로
 * (update·password-reset·delete·token-revoke)는 픽스처 부담으로 제외했다. 이 테스트가
 * 그 네 변경 엔드포인트(`AdminUsersController`)를 채운다. 모두 `ApiSecurityConfig`의
 * admin 하위(ADMIN_API 패턴) → `hasRole("ADMIN")`로 보호된다. Cycle #23~#27의 관리자
 * 토큰 하네스(부트스트랩 시드 + admin 로그인)와 카카오 USER 발급 하네스를 그대로 재사용한다.
 *
 * 변경 대상 USER는 공개 경로 `POST oauth2/kakao/token`으로 발급해 `data.user.id`를 얻는다
 * (Cycle #27과 동일). 케이스:
 *  1. PATCH update — 익명(토큰 없음) → **401**.
 *  2. PATCH update — 인증된 비관리자(USER 토큰) → **403** (`ROLE_USER`는 `hasRole(ADMIN)` 불충족).
 *  3. PATCH update — 관리자 토큰이 USER의 name·nickname·emailVerified 수정 → **200**, 응답 즉시 반영 +
 *     상세 재조회(read-after-write)로 영속 확인. email·phone은 null로 둬 uniqueness 검증이 short-circuit
 *     (`validateUpdateEmail`/`validateUpdatePhone`의 동일-값 조기 반환)되게 한다.
 *  4. POST password-reset — 관리자가 카카오(OAuth) USER의 비밀번호 리셋 시 **404 `E404`**: OAuth 가입자는
 *     LocalCredential이 없어 `LOCAL_CREDENTIAL_NOT_FOUND`(NOT_FOUND)로 거절된다.
 *  5. POST delete — 관리자가 USER를 소프트 삭제 → **200**, `userStatus=SOFT_DELETED`·`deletedAt` 설정 +
 *     상세 재조회로 영속 확인.
 *  6. POST tokens-revoke — 관리자가 USER의 토큰을 폐기 → **200**, `userId` 일치(유저 자체는 저장하지 않음).
 *  7. POST tokens-revoke — 존재하지 않는 userId → **404 `E404`** (`findByIdIncludingDeleted` null).
 *  8. PATCH update — `userStatus=SOFT_DELETED` 직접 지정 → **400 `E400`** (`INVALID_PARAMETER`): update()는
 *     소프트 삭제를 delete API로만 허용하므로 명시적 SOFT_DELETED 변경은 가드에서 거절된다(#30이 happy-path만 다룸).
 *  9. PATCH update — 다른 유저가 이미 쓰는 email로 변경 → **409 `E409`** (`DUPLICATE_EMAIL`):
 *     `validateUpdateEmail`이 대상 email을 점유한 타 유저(id 불일치)를 감지해 거절한다.
 * 10. POST password-reset — 소프트 삭제된 USER의 비밀번호 리셋 시 **409 `E409`** (`CONFLICT`,
 *     `SOFT_DELETED_USER_PASSWORD_RESET_FORBIDDEN`): `updatePassword`의 SOFT_DELETED 가드는
 *     LocalCredential 조회보다 먼저 실행되므로, OAuth USER라도 소프트 삭제 상태면 404가 아닌 409로 거절된다
 *     (#30 case 4의 404 LOCAL_CREDENTIAL_NOT_FOUND 가드와 대비되는, 더 앞선 상태 가드 브랜치).
 *
 * 토큰은 위조하지 않는다. 외부 upstream Kakao 사용자 조회([KakaoOAuthClient])만 `@Primary` mockk로
 * 대체해 네트워크 없이 실행한다. 변경 테스트마다 고유 카카오 id·email을 써 케이스 순서 독립을 보장한다
 * (Cycle #15 NOTE: 동일 UserEntity 재로그인 시 @Version 낙관락 409 회피).
 */
@Tag("context")
@SpringBootTest(classes = [CoreApiApplication::class])
@AutoConfigureMockMvc
@Import(AdminUsersMutationContextTest.MockKakaoClientConfig::class)
class AdminUsersMutationContextTest {

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
        stubKakaoUser("ka_at_um_403", 4_295_200_001L, "um-403@triplan.kr", "유저403")
        stubKakaoUser("ka_at_um_update", 4_295_200_002L, "um-update@triplan.kr", "유저수정")
        stubKakaoUser("ka_at_um_pwreset", 4_295_200_003L, "um-pwreset@triplan.kr", "유저비번")
        stubKakaoUser("ka_at_um_delete", 4_295_200_004L, "um-delete@triplan.kr", "유저삭제")
        stubKakaoUser("ka_at_um_revoke", 4_295_200_005L, "um-revoke@triplan.kr", "유저폐기")
        stubKakaoUser("ka_at_um_sd", 4_295_200_006L, "um-sd@triplan.kr", "유저상태")
        stubKakaoUser("ka_at_um_dupA", 4_295_200_007L, "um-dup-a@triplan.kr", "유저중복A")
        stubKakaoUser("ka_at_um_dupB", 4_295_200_008L, "um-dup-b@triplan.kr", "유저중복B")
        stubKakaoUser("ka_at_um_sddel", 4_295_200_009L, "um-sddel@triplan.kr", "유저삭제후비번")
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
    fun `PATCH update user - anonymous (no token) returns 401`() {
        mockMvc.perform(
            patch("/api/v1/auth/admin/users/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("name" to "x"))),
        )
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `PATCH update user - authenticated non-admin (USER token) returns 403`() {
        val userToken = issueUserAccessToken("ka_at_um_403", "dev-um-403").accessToken

        mockMvc.perform(
            patch("/api/v1/auth/admin/users/1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $userToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("name" to "x"))),
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `PATCH update user - admin updates name and nickname, re-fetch persists`() {
        val target = issueUserAccessToken("ka_at_um_update", "dev-um-update")
        val targetUserId = target.userId
        val adminToken = issueAdminAccessToken()

        // email·phone을 null로 둬 uniqueness 검증 short-circuit. name·nickname·emailVerified만 변경.
        mockMvc.perform(
            patch("/api/v1/auth/admin/users/$targetUserId")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        mapOf(
                            "name" to "수정된이름",
                            "nickname" to "수정닉",
                            "emailVerified" to true,
                        ),
                    ),
                ),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.result").value("SUCCESS"))
            .andExpect(jsonPath("$.data.userId").value(targetUserId))
            .andExpect(jsonPath("$.data.name").value("수정된이름"))
            .andExpect(jsonPath("$.data.nickname").value("수정닉"))
            .andExpect(jsonPath("$.data.emailVerified").value(true))

        // 상세 재조회로 영속 확인(read-after-write).
        mockMvc.perform(
            get("/api/v1/auth/admin/users/$targetUserId")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $adminToken"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.name").value("수정된이름"))
            .andExpect(jsonPath("$.data.nickname").value("수정닉"))
    }

    @Test
    fun `POST password-reset - admin on OAuth user without LocalCredential returns 404`() {
        val target = issueUserAccessToken("ka_at_um_pwreset", "dev-um-pwreset")
        val targetUserId = target.userId
        val adminToken = issueAdminAccessToken()

        // 카카오(OAuth) 가입자는 LocalCredential이 없어 LOCAL_CREDENTIAL_NOT_FOUND로 거절.
        mockMvc.perform(
            post("/api/v1/auth/admin/users/$targetUserId/password/reset")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("newPassword" to "Reset3d!Pwd99"))),
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.result").value("ERROR"))
            .andExpect(jsonPath("$.error.code").value("E404"))
    }

    @Test
    fun `POST delete - admin soft-deletes a user, re-fetch shows SOFT_DELETED`() {
        val target = issueUserAccessToken("ka_at_um_delete", "dev-um-delete")
        val targetUserId = target.userId
        val adminToken = issueAdminAccessToken()

        mockMvc.perform(
            post("/api/v1/auth/admin/users/$targetUserId/delete")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("reason" to "정책 위반 소프트삭제"))),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.result").value("SUCCESS"))
            .andExpect(jsonPath("$.data.userId").value(targetUserId))
            .andExpect(jsonPath("$.data.userStatus").value("SOFT_DELETED"))
            .andExpect(jsonPath("$.data.deletedAt").isNotEmpty)

        // 상세 재조회(삭제 포함 조회)로 SOFT_DELETED 영속 확인.
        mockMvc.perform(
            get("/api/v1/auth/admin/users/$targetUserId")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $adminToken"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.userStatus").value("SOFT_DELETED"))
    }

    @Test
    fun `POST tokens-revoke - admin revokes a user's tokens returns 200`() {
        val target = issueUserAccessToken("ka_at_um_revoke", "dev-um-revoke")
        val targetUserId = target.userId
        val adminToken = issueAdminAccessToken()

        mockMvc.perform(
            post("/api/v1/auth/admin/users/$targetUserId/tokens/revoke")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $adminToken"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.result").value("SUCCESS"))
            .andExpect(jsonPath("$.data.userId").value(targetUserId))
    }

    @Test
    fun `POST tokens-revoke - admin with non-existent userId returns 404`() {
        val adminToken = issueAdminAccessToken()

        mockMvc.perform(
            post("/api/v1/auth/admin/users/99999999/tokens/revoke")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $adminToken"),
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.result").value("ERROR"))
            .andExpect(jsonPath("$.error.code").value("E404"))
    }

    @Test
    fun `PATCH update user - userStatus SOFT_DELETED is rejected with 400`() {
        val target = issueUserAccessToken("ka_at_um_sd", "dev-um-sd")
        val adminToken = issueAdminAccessToken()

        // update()는 소프트 삭제를 delete API로만 허용 → 명시적 SOFT_DELETED 지정은 INVALID_PARAMETER로 거절.
        mockMvc.perform(
            patch("/api/v1/auth/admin/users/${target.userId}")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("userStatus" to "SOFT_DELETED"))),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.result").value("ERROR"))
            .andExpect(jsonPath("$.error.code").value("E400"))
    }

    @Test
    fun `PATCH update user - email colliding with another user returns 409`() {
        val userA = issueUserAccessToken("ka_at_um_dupA", "dev-um-dupA")
        val userB = issueUserAccessToken("ka_at_um_dupB", "dev-um-dupB")
        val adminToken = issueAdminAccessToken()

        // userA의 email을 userB가 이미 점유한 email로 변경 시도 → validateUpdateEmail이 DUPLICATE_EMAIL로 거절.
        mockMvc.perform(
            patch("/api/v1/auth/admin/users/${userA.userId}")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("email" to "um-dup-b@triplan.kr"))),
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.result").value("ERROR"))
            .andExpect(jsonPath("$.error.code").value("E409"))

        // userB는 멀쩡히 남아 있어야 한다(영속 비손상 확인).
        mockMvc.perform(
            get("/api/v1/auth/admin/users/${userB.userId}")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $adminToken"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.email").value("um-dup-b@triplan.kr"))
    }

    @Test
    fun `POST password-reset - on soft-deleted user returns 409`() {
        val target = issueUserAccessToken("ka_at_um_sddel", "dev-um-sddel")
        val targetUserId = target.userId
        val adminToken = issueAdminAccessToken()

        // 먼저 소프트 삭제(검증된 delete 경로) → userStatus=SOFT_DELETED.
        mockMvc.perform(
            post("/api/v1/auth/admin/users/$targetUserId/delete")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("reason" to "비번리셋전 소프트삭제"))),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.userStatus").value("SOFT_DELETED"))

        // 소프트 삭제 상태에서 비밀번호 리셋 → SOFT_DELETED 가드가 LocalCredential 조회보다 먼저 → 409 CONFLICT.
        mockMvc.perform(
            post("/api/v1/auth/admin/users/$targetUserId/password/reset")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("newPassword" to "Reset3d!Pwd99"))),
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.result").value("ERROR"))
            .andExpect(jsonPath("$.error.code").value("E409"))
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
                .header("X-Device-Id", "dev-um-admin")
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
        private const val ADMIN_USERNAME = "admin-usermut@triplan.test"
        private const val ADMIN_PASSWORD = "Adm1nUsrMut!Pwd9"

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
