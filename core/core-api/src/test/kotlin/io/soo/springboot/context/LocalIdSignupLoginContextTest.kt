package io.soo.springboot.context

import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.soo.springboot.CoreApiApplication
import io.soo.springboot.core.domain.phone.verification.PhoneVerificationNotifier
import io.soo.springboot.core.support.error.ErrorType
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
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * E2 보강 — 아이디 회원가입/로그인 엔드포인트(`POST /api/v1/auth/local/id/signup`,
 * `.../id/login`)의 full-boot 라운드트립 컨텍스트 테스트.
 *
 * 두 엔드포인트 모두 공개(`ApiSecurityConfig` `PUBLIC_ENDPOINTS`)지만, `id/signup`은 휴대폰 인증 흐름이
 * 발급한 `phoneVerificationToken`(proof)을 소비(1회용)해야만 통과한다 — 인증 없는 가입을 막는 보안 핵심
 * 경로다. 그동안 이 두 엔드포인트엔 full `@SpringBootTest` 커버리지가 없었다. 이 테스트가 그 공백을,
 * **위조 없이 같은 앱의 `/phone-verifications/{request,confirm}` 체인으로 실제 proof를 발급해** 메운다.
 * Cycle #20 `LocalPhoneSignupLoginContextTest`와 같은 구조 — 요청 본문/에러 코드만 아이디 흐름으로 교체.
 *
 * full `@SpringBootTest`로 [CoreApiApplication]을 통째로 띄워 실제 빈으로 계약을 핀한다
 * (`IdAccountService` → JPA(H2) User/LocalCredential 영속화, `IdLoginService` → Redis refresh 토큰 발급,
 * `PhoneVerificationService` → `InMemoryPhoneVerificationStore`(`local`), `UserUniquenessPolicy`,
 * `ApiControllerAdvice`, `ApiResponse` 직렬화):
 *  1. happy 라운드트립 — proof 발급 → signup(가입) → 200; 그 아이디/비밀번호로 login → 200 + access/refresh 토큰 발급
 *  2. signup에 미발급 proof 토큰(임의 UUID) → 400 `PHONE_VERIFICATION_REQUIRED` (인증 없이 가입 차단)
 *  3. 가입한 적 없는 아이디로 login → 401 `LOGIN_ACCOUNT_NOT_FOUND`
 *  4. 같은 아이디 중복 signup(다른 폰·새 proof) → 409 `DUPLICATE_LOGIN_ID` (아이디 유일성 정책)
 *  5. 가입한 아이디로 틀린 비밀번호 login → 401 `LOGIN_BAD_CREDENTIALS`
 *
 * proof code는 서버가 `SecureRandom`으로 생성해 SMS notifier로만 내보내므로, 외부 SMS 발송기
 * [PhoneVerificationNotifier]만 `@Primary` mockk로 대체해 발급된 code를 캡처한다(네트워크 없음).
 * dev 기본값은 oauth2만 ON이라 id·SMS 컨트롤러가 `@ConditionalOnProperty`로 빈 생성되지 않으므로
 * `@DynamicPropertySource`로 `app.auth.method.{id,sms}.enabled=true`를 켠다(Cycle #16 NOTE).
 * 테스트마다 아이디·폰 번호를 달리해 컨텍스트 공유 H2의 유일성 충돌을 피한다.
 */
@Tag("context")
@SpringBootTest(classes = [CoreApiApplication::class])
@AutoConfigureMockMvc
@Import(LocalIdSignupLoginContextTest.MockNotifierConfig::class)
class LocalIdSignupLoginContextTest {

    @TestConfiguration
    class MockNotifierConfig {
        @Bean @Primary fun mockPhoneVerificationNotifier(): PhoneVerificationNotifier = mockk(relaxed = true)
    }

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var notifier: PhoneVerificationNotifier

    private val codeSlot = slot<String>()

    private val password = "Passw0rd!"

    @BeforeEach
    fun setUp() {
        // notifier.sendCode(phone, code, ttl) 호출의 code 인자를 캡처한다 — confirm 단계에서 이 code를 그대로 쓴다.
        every { notifier.sendCode(any(), capture(codeSlot), any()) } returns Unit
    }

    @Test
    fun `signup with a verification proof then login issues access and refresh tokens`() {
        val loginId = "iduser0001"
        val phone = "010-3000-0001"

        mockMvc.perform(signupRequest(loginId, password, phone, issueProofToken(phone)))
            .andExpect(status().isOk)

        mockMvc.perform(loginRequest(loginId, password, "id_dev_0001"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.result").value("SUCCESS"))
            .andExpect(jsonPath("$.data.accessToken").isNotEmpty)
            .andExpect(jsonPath("$.data.accessExpiresInSec").isNumber)
            .andExpect(jsonPath("$.data.refreshToken").isNotEmpty)
            .andExpect(jsonPath("$.data.refreshExpiresInSec").isNumber)
    }

    @Test
    fun `signup with an unissued verification token is rejected`() {
        // 어떤 confirm으로도 발급된 적 없는 임의 proof 토큰 → 인증 없는 가입 시도.
        mockMvc.perform(
            signupRequest("iduser0002", password, "010-3000-0002", "00000000-0000-0000-0000-000000000000"),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.result").value("ERROR"))
            .andExpect(jsonPath("$.error.code").value(ErrorType.PHONE_VERIFICATION_REQUIRED.code.name))
    }

    @Test
    fun `login for an id that never signed up is rejected`() {
        mockMvc.perform(loginRequest("iduser0003", password, "id_dev_0003"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.result").value("ERROR"))
            .andExpect(jsonPath("$.error.code").value(ErrorType.LOGIN_ACCOUNT_NOT_FOUND.code.name))
    }

    @Test
    fun `signing up the same id twice is rejected by the uniqueness policy`() {
        val loginId = "iduser0004"

        mockMvc.perform(signupRequest(loginId, password, "010-3000-0004", issueProofToken("010-3000-0004")))
            .andExpect(status().isOk)

        // 새 폰·새 proof지만 같은 아이디 재가입 → proof는 통과하고 아이디 유일성 정책에서 차단.
        mockMvc.perform(signupRequest(loginId, password, "010-3000-0014", issueProofToken("010-3000-0014")))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.result").value("ERROR"))
            .andExpect(jsonPath("$.error.code").value(ErrorType.DUPLICATE_LOGIN_ID.code.name))
    }

    @Test
    fun `login with a wrong password is rejected`() {
        val loginId = "iduser0005"
        val phone = "010-3000-0005"

        mockMvc.perform(signupRequest(loginId, password, phone, issueProofToken(phone)))
            .andExpect(status().isOk)

        mockMvc.perform(loginRequest(loginId, "Wr0ng!pw", "id_dev_0005"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.result").value("ERROR"))
            .andExpect(jsonPath("$.error.code").value(ErrorType.LOGIN_BAD_CREDENTIALS.code.name))
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

            // dev 기본값은 oauth2만 ON이라 id·SMS 컨트롤러가 @ConditionalOnProperty로 빈 생성되지 않는다.
            // id signup/login + 휴대폰 인증(proof) 체인을 켠 상태의 계약을 핀하기 위해 명시적으로 활성화한다.
            registry.add("app.auth.method.id.enabled") { "true" }
            registry.add("app.auth.method.sms.enabled") { "true" }

            registry.add("spring.security.oauth2.client.registration.kakao.client-id") { "test-kakao-client" }
            registry.add("spring.security.oauth2.client.registration.kakao.client-secret") { "test-kakao-secret" }

            registry.add("spring.data.redis.host") { System.getenv("REDIS_HOST") ?: "127.0.0.1" }
            registry.add("spring.data.redis.port") { System.getenv("REDIS_PORT") ?: "6379" }
        }
    }
}
