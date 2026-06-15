package io.soo.springboot.context

import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.soo.springboot.CoreApiApplication
import io.soo.springboot.core.domain.phone.verification.PhoneVerificationNotifier
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
 * E2 보강 — 관리자 비밀번호 재설정(`POST /api/v1/auth/admin/users/{userId}/password/reset`)의
 * **성공 분기(happy path)** full-boot 컨텍스트 테스트.
 *
 * `AdminUserService.updatePassword`의 가드 분기는 그동안 부분만 핀됐다:
 *  - SOFT_DELETED 가드(→ 409) — Cycle #33
 *  - LocalCredential 없음(OAuth 가입자, → 404) — Cycle #30
 * 그러나 **실제 비밀번호를 교체하는 성공 분기**(`localCredentialRepository.findByUserId` → 새 해시 저장 →
 * `tokenRevocationService.revokeAllByUserId`)는 한 번도 실행된 적이 없다. #30이 그 이유를 기록했다:
 * 하네스의 변경 대상은 모두 카카오 OAuth USER(=LocalCredential 없음)였고, 관리자 자신의 비밀번호를 바꾸면
 * 공유 H2의 관리자 credential이 변형돼 다른 테스트의 admin 로그인이 깨지기 때문이다.
 *
 * 이 테스트는 그 공백을, **id 회원가입 체인으로 LocalCredential을 가진 별도의 LOCAL 유저를 만들어** 메운다
 * (#21 `LocalIdSignupLoginContextTest`의 검증된 proof→signup 체인 재사용 + #30 admin-token 하네스 재사용).
 * 성공 분기를 **read-after-write로 실제 로그인 경로를 통해** 검증한다 — 위조 없이, 같은 앱이 발급한 proof로.
 *
 * full `@SpringBootTest`로 [CoreApiApplication]을 통째로 띄워 실제 빈으로 계약을 핀한다:
 *  1. happy 라운드트립 — id-signup으로 LOCAL 유저 생성(원래 비번으로 로그인 200 = 기준선) → 관리자 검색으로
 *     userId 확보 → 관리자가 그 유저의 비밀번호를 새 값으로 리셋(200, 응답 userId 일치) →
 *     **신 비번 로그인 → 200 + 토큰 발급**. 신 비번 로그인 성공은 곧 저장된 해시가 교체됐다는 read-after-write
 *     증명이다(이전 credential은 원래 비번의 해시였으므로). 이로써 성공 분기(credential 조회 → 새 해시 저장 →
 *     전체 토큰 폐기)가 실제 로그인 경로를 통해 end-to-end로 핀된다.
 *  2. 존재하지 않는 userId 비밀번호 리셋 → **404 `E404`**: `updatePassword`의 첫 가드
 *     (`findByIdIncludingDeleted` null)를 핀한다(#30의 credential-없음 404·#33의 SOFT_DELETED 409와 구분되는,
 *     더 앞선 user-not-found 분기).
 *
 * proof code는 서버가 `SecureRandom`으로 생성해 SMS notifier로만 내보내므로, 외부 SMS 발송기
 * [PhoneVerificationNotifier]만 `@Primary` mockk로 대체해 발급된 code를 캡처한다(네트워크 없음).
 * 관리자 토큰은 위조하지 않는다 — 부트스트랩 시드 + 같은 앱의 admin 로그인으로 실제 ROLE_ADMIN 토큰을 받는다.
 * dev 기본값은 oauth2만 ON이라 id·SMS·admin 경로는 `@DynamicPropertySource`로 명시 활성화한다(Cycle #16 NOTE).
 *
 * NOTE(@Version latent quirk — Cycle #15/#27 부류). "구 비번 로그인 → 401" 추가 단언은 의도적으로 제외했다.
 * 비밀번호 리셋은 `LocalCredential`을 (id만 보존하고 @Version은 재구성한) 새 객체로 save한다. 그 직후 같은
 * credential에 대한 **실패 로그인**은 `LocalLoginAttemptPolicy.recordFailureByEmail`이 credential을 다시
 * mutate+save하는데, 재구성된 stale @Version 때문에 `ObjectOptimisticLockingFailure(E409)`로 깨진다 —
 * #15(kakao 재로그인)·#27(block→unblock)이 기록한 도메인↔엔티티 @Version 미보존 매핑과 같은 latent prod
 * 이슈다(실제 운영의 분리된 트랜잭션에선 엔티티가 새로 로드돼 재현되지 않음). 테스트는 이 quirk를 우회하려
 * contort하지 않는다(성공 로그인 경로는 credential을 save하지 않아 충돌하지 않으며, 성공 분기 핀에는 신 비번
 * 로그인 성공만으로 충분하다). @Version 매핑 하드닝은 별도의 백엔드 결정이지 테스트 관심사가 아니다.
 */
@Tag("context")
@SpringBootTest(classes = [CoreApiApplication::class])
@AutoConfigureMockMvc
@Import(AdminUserPasswordResetContextTest.MockNotifierConfig::class)
class AdminUserPasswordResetContextTest {

    @TestConfiguration
    class MockNotifierConfig {
        @Bean @Primary fun mockPhoneVerificationNotifier(): PhoneVerificationNotifier = mockk(relaxed = true)
    }

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var notifier: PhoneVerificationNotifier

    private val codeSlot = slot<String>()

    private val originalPassword = "Passw0rd!"
    private val newPassword = "Reset3d!Pwd99"

    @BeforeEach
    fun setUp() {
        // notifier.sendCode(phone, code, ttl) 호출의 code 인자를 캡처한다 — confirm 단계에서 이 code를 그대로 쓴다.
        every { notifier.sendCode(any(), capture(codeSlot), any()) } returns Unit
    }

    @Test
    fun `admin password-reset replaces the hash - old password fails, new password logs in`() {
        val loginId = "pwresetuser01"
        val phone = "010-3100-0001"

        // LocalCredential을 가진 LOCAL 유저 생성(검증된 proof→id-signup 체인).
        mockMvc.perform(signupRequest(loginId, originalPassword, phone, issueProofToken(phone)))
            .andExpect(status().isOk)

        // 기준선: 원래 비밀번호로 로그인 가능.
        mockMvc.perform(loginRequest(loginId, originalPassword, "dev-pwrst-0001"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.accessToken").isNotEmpty)

        // 관리자 토큰 발급 + 검색으로 대상 userId 확보(email = id-<loginId>@local.internal 이 keyword LIKE에 매칭).
        val adminToken = issueAdminAccessToken()
        val targetUserId = findUserIdByKeyword(adminToken, loginId)

        // 관리자가 비밀번호 리셋 → 성공 분기(credential 조회 → 새 해시 저장 → 전체 토큰 폐기).
        mockMvc.perform(
            post("/api/v1/auth/admin/users/$targetUserId/password/reset")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("newPassword" to newPassword))),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.result").value("SUCCESS"))
            .andExpect(jsonPath("$.data.userId").value(targetUserId))

        // read-after-write: 신 비밀번호로 로그인 성공 + 토큰 발급(새 해시가 실제로 영속됐다는 증명 —
        // 직전 credential은 원래 비번의 해시였으므로 신 비번이 통과하려면 해시가 교체됐어야 한다).
        mockMvc.perform(loginRequest(loginId, newPassword, "dev-pwrst-0001c"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.result").value("SUCCESS"))
            .andExpect(jsonPath("$.data.accessToken").isNotEmpty)
            .andExpect(jsonPath("$.data.refreshToken").isNotEmpty)
    }

    @Test
    fun `admin password-reset for a non-existent userId returns 404`() {
        val adminToken = issueAdminAccessToken()

        // updatePassword의 첫 가드: findByIdIncludingDeleted null → NOT_FOUND.
        mockMvc.perform(
            post("/api/v1/auth/admin/users/99999999/password/reset")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("newPassword" to newPassword))),
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.result").value("ERROR"))
            .andExpect(jsonPath("$.error.code").value("E404"))
    }

    /** 관리자 검색(keyword LIKE)으로 대상 유저의 userId를 찾는다. loginId는 email(`id-<loginId>@local.internal`)에 포함돼 매칭된다. */
    private fun findUserIdByKeyword(adminToken: String, keyword: String): Long {
        val body = mockMvc.perform(
            get("/api/v1/auth/admin/users")
                .param("keyword", keyword)
                .header(HttpHeaders.AUTHORIZATION, "Bearer $adminToken"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.content[0].userId").isNumber)
            .andReturn()
            .response
            .getContentAsString(Charsets.UTF_8)
        return objectMapper.readTree(body).path("data").path("content").path(0).path("userId").asLong()
    }

    /**
     * `/phone-verifications/request` → notifier로 캡처한 code로 `/confirm` → 발급된 `phoneVerificationToken`을
     * 돌려준다. 위조 없이 같은 앱이 발급한 proof를 그대로 signup에 태운다.
     */
    private fun issueProofToken(phone: String): String {
        val requestBody = mockMvc.perform(
            post("/api/v1/auth/local/phone-verifications/request")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("phoneNumber" to phone))),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.verificationId").isNotEmpty)
            .andReturn()
            .response
            .getContentAsString(Charsets.UTF_8)
        val verificationId = objectMapper.readTree(requestBody).path("data").path("verificationId").asText()
        val code = codeSlot.captured

        val confirmBody = mockMvc.perform(
            post("/api/v1/auth/local/phone-verifications/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        mapOf("phoneNumber" to phone, "verificationId" to verificationId, "code" to code),
                    ),
                ),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.phoneVerificationToken").isNotEmpty)
            .andReturn()
            .response
            .getContentAsString(Charsets.UTF_8)
        return objectMapper.readTree(confirmBody).path("data").path("phoneVerificationToken").asText()
    }

    private fun signupRequest(loginId: String, password: String, phone: String, proofToken: String) =
        post("/api/v1/auth/local/id/signup")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                objectMapper.writeValueAsString(
                    mapOf(
                        "loginId" to loginId,
                        "password" to password,
                        "phoneNumber" to phone,
                        "phoneVerificationToken" to proofToken,
                    ),
                ),
            )

    private fun loginRequest(loginId: String, password: String, deviceId: String) =
        post("/api/v1/auth/local/id/login")
            .header("X-Device-Id", deviceId)
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                objectMapper.writeValueAsString(mapOf("loginId" to loginId, "password" to password)),
            )

    /** 부트스트랩으로 만들어진 관리자 계정을 같은 앱의 admin 로그인 경로로 로그인해 ROLE_ADMIN 토큰을 발급받는다. */
    private fun issueAdminAccessToken(): String {
        val body = mockMvc.perform(
            post("/api/v1/auth/admin/login")
                .header("X-Device-Id", "dev-pwrst-admin")
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
        private const val ADMIN_USERNAME = "admin-pwreset@triplan.test"
        private const val ADMIN_PASSWORD = "Adm1nPwRst!Pwd9"

        @JvmStatic
        @DynamicPropertySource
        fun props(registry: DynamicPropertyRegistry) {
            val ksPassword = System.getenv("JWT_KEYSTORE_PASSWORD") ?: "devjwtpass"
            registry.add("app.security.jwt.keystore.password") { ksPassword }
            registry.add("app.security.jwt.keystore.key-password") {
                System.getenv("JWT_KEY_PASSWORD") ?: ksPassword
            }
            registry.add("app.security.jwt.keystore.alias") { System.getenv("JWT_KEY_ALIAS") ?: "jwt" }

            // dev 기본값은 oauth2만 ON이라 id·SMS 컨트롤러가 @ConditionalOnProperty로 빈 생성되지 않는다.
            // id signup/login + 휴대폰 인증(proof) 체인 + admin 로그인/부트스트랩을 명시 활성화한다.
            registry.add("app.auth.method.id.enabled") { "true" }
            registry.add("app.auth.method.sms.enabled") { "true" }
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
