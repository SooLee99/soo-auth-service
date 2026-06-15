package io.soo.springboot.context

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.soo.springboot.CoreApiApplication
import io.soo.springboot.clients.kakao.KakaoOAuthClient
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * E2 보강 — 관리자 사용자 변경의 휴대폰 유일성 가드(`AdminUserService.update` →
 * `UserUniquenessPolicy.validateUpdatePhone`) full-boot 컨텍스트 테스트.
 *
 * Cycle #31의 `AdminUsersMutationContextTest`는 `update`의 DUPLICATE_EMAIL 가드(이메일 충돌)는 핀했지만,
 * 같은 구조의 **DUPLICATE_PHONE_NUMBER 가드(휴대폰 충돌)**는 미커버였다 — 카카오 USER는 기본적으로 폰이 없어
 * 충돌 대상 폰을 가진 유저가 필요하기 때문이다. 이 테스트가 그 마지막 `update`/uniqueness 분기를 채운다.
 *
 * 충돌 대상 폰을 가진 유저는 **위조 없이** 같은 앱의 `/phone-verifications/{request,confirm}` → `/phone/signup`
 * 체인으로 실제 발급해 만든다(Cycle #20 `LocalPhoneSignupLoginContextTest` 패턴). `PhoneNumberNormalizer`가
 * 폰을 숫자만 남겨 정규화(예: `010-3000-0002`→`01030000002`)해 저장하므로, PATCH로 충돌을 트리거하려면
 * 같은 **정규화된 숫자열**을 보내야 한다(`validateUpdatePhone`은 정규화 없이 `existsByPhoneNumber(targetPhone)`을
 * 그대로 조회하기 때문).
 *
 * 케이스(모두 `hasRole("ADMIN")` 보호, 네트워크 없음):
 *  1. happy — 관리자가 카카오 USER(폰 null)의 phoneNumber를 **미사용** 폰으로 변경 → **200**,
 *     응답 즉시 반영 + 상세 재조회(read-after-write)로 영속 확인. `validateUpdatePhone`의 성공 분기
 *     (`existsByPhoneNumber`=false → throw 없이 통과)와 실제 폰 영속을 핀한다(#30 happy-path는 폰을 null로
 *     둬 동일-값 short-circuit만 탔다).
 *  2. duplicate — 폰 가입 유저(userB, phoneB)를 만든 뒤, 관리자가 카카오 USER(userA, 폰 null)의 phoneNumber를
 *     userB의 **정규화 폰**으로 변경 시도 → **409 `E409`** (`DUPLICATE_PHONE_NUMBER`):
 *     `validateUpdatePhone`이 대상 폰을 점유한 타 유저를 `existsByPhoneNumber`로 감지해 거절한다.
 *     userB가 멀쩡히 남아 있는지(영속 비손상) 재조회로 확인한다.
 *
 * 토큰은 위조하지 않는다. 외부 upstream만 `@Primary` mockk로 대체한다 — 카카오 사용자 조회([KakaoOAuthClient]),
 * SMS 발송기([PhoneVerificationNotifier], 발급 code 캡처). 관리자 토큰은 부트스트랩 시드 + admin 로그인 하네스
 * (Cycle #23~#31)로 발급한다. 변경 테스트마다 고유 카카오 id·email·폰을 써 컨텍스트 공유 H2의 순서 독립을 보장한다.
 */
@Tag("context")
@SpringBootTest(classes = [CoreApiApplication::class])
@AutoConfigureMockMvc
@Import(AdminUserUpdatePhoneContextTest.MockExternalConfig::class)
class AdminUserUpdatePhoneContextTest {

    @TestConfiguration
    class MockExternalConfig {
        @Bean @Primary fun mockKakaoOAuthClient(): KakaoOAuthClient = mockk()
        @Bean @Primary fun mockPhoneVerificationNotifier(): PhoneVerificationNotifier = mockk(relaxed = true)
    }

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var kakaoOAuthClient: KakaoOAuthClient
    @Autowired private lateinit var notifier: PhoneVerificationNotifier

    private val codeSlot = slot<String>()

    @BeforeEach
    fun setUp() {
        stubKakaoUser("ka_at_uph_happy", 4_295_300_001L, "uph-happy@triplan.kr", "유저폰happy")
        stubKakaoUser("ka_at_uph_dup", 4_295_300_002L, "uph-dup@triplan.kr", "유저폰dup")
        // notifier.sendCode(phone, code, ttl) 의 code 인자를 캡처 — confirm에서 그대로 쓴다.
        every { notifier.sendCode(any(), capture(codeSlot), any()) } returns Unit
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
    fun `PATCH update - admin sets a fresh phone, re-fetch persists`() {
        val target = issueUserAccessToken("ka_at_uph_happy", "dev-uph-happy")
        val targetUserId = target.userId
        val adminToken = issueAdminAccessToken()
        val freshPhone = "01099990001" // 누구도 점유하지 않은 정규화 폰.

        mockMvc.perform(
            patch("/api/v1/auth/admin/users/$targetUserId")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("phoneNumber" to freshPhone))),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.result").value("SUCCESS"))
            .andExpect(jsonPath("$.data.userId").value(targetUserId))
            .andExpect(jsonPath("$.data.phoneNumber").value(freshPhone))

        // 상세 재조회로 영속 확인(read-after-write).
        mockMvc.perform(
            get("/api/v1/auth/admin/users/$targetUserId")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $adminToken"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.phoneNumber").value(freshPhone))
    }

    @Test
    fun `PATCH update - phone colliding with another user returns 409`() {
        // 충돌 대상: 폰 가입 유저(userB)를 같은 앱의 검증된 phone-signup 체인으로 만든다.
        val phoneB = "010-3000-0002"
        val normalizedPhoneB = "01030000002" // PhoneNumberNormalizer: 숫자만.
        mockMvc.perform(
            post("/api/v1/auth/local/phone/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        mapOf("phoneNumber" to phoneB, "phoneVerificationToken" to issueProofToken(phoneB)),
                    ),
                ),
        )
            .andExpect(status().isOk)

        val userA = issueUserAccessToken("ka_at_uph_dup", "dev-uph-dup")
        val adminToken = issueAdminAccessToken()

        // userA(폰 null)의 phoneNumber를 userB가 점유한 정규화 폰으로 변경 시도 → DUPLICATE_PHONE_NUMBER.
        mockMvc.perform(
            patch("/api/v1/auth/admin/users/${userA.userId}")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("phoneNumber" to normalizedPhoneB))),
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.result").value("ERROR"))
            .andExpect(jsonPath("$.error.code").value("E409"))

        // userA의 폰은 그대로 null이어야 한다(거절 후 비변경 확인).
        mockMvc.perform(
            get("/api/v1/auth/admin/users/${userA.userId}")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $adminToken"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.phoneNumber").doesNotExist())
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
                .header("X-Device-Id", "dev-uph-admin")
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
        private const val ADMIN_USERNAME = "admin-uphone@triplan.test"
        private const val ADMIN_PASSWORD = "Adm1nUphone!Pwd9"

        @JvmStatic
        @DynamicPropertySource
        fun props(registry: DynamicPropertyRegistry) {
            val ksPassword = System.getenv("JWT_KEYSTORE_PASSWORD") ?: "devjwtpass"
            registry.add("app.security.jwt.keystore.password") { ksPassword }
            registry.add("app.security.jwt.keystore.key-password") {
                System.getenv("JWT_KEY_PASSWORD") ?: ksPassword
            }
            registry.add("app.security.jwt.keystore.alias") { System.getenv("JWT_KEY_ALIAS") ?: "jwt" }

            // admin 로그인 필터 + 부트스트랩 러너를 켠다.
            registry.add("app.auth.method.admin.enabled") { "true" }
            registry.add("app.bootstrap.admin.enabled") { "true" }
            registry.add("app.bootstrap.admin.username") { ADMIN_USERNAME }
            registry.add("app.bootstrap.admin.password") { ADMIN_PASSWORD }

            // 폰 인증/가입 컨트롤러는 dev 기본값(present-and-false)이라 @ConditionalOnProperty로 unmapped → 켠다.
            registry.add("app.auth.method.sms.enabled") { "true" }

            registry.add("spring.security.oauth2.client.registration.kakao.client-id") { "test-kakao-client" }
            registry.add("spring.security.oauth2.client.registration.kakao.client-secret") { "test-kakao-secret" }

            registry.add("spring.data.redis.host") { System.getenv("REDIS_HOST") ?: "127.0.0.1" }
            registry.add("spring.data.redis.port") { System.getenv("REDIS_PORT") ?: "6379" }
        }
    }
}
