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
 * E2 보강 — 휴대폰 인증 발급/확인 엔드포인트(`POST /api/v1/auth/local/phone-verifications/request`,
 * `.../confirm`)의 full-boot 컨텍스트 테스트.
 *
 * 두 엔드포인트 모두 공개(ApiSecurityConfig `PUBLIC_ENDPOINTS`)이며, 이 흐름이 발급하는 `phoneVerificationToken`
 * (proof)은 이메일/ID/휴대폰 회원가입을 게이트하는 보안 핵심 경로다. 그동안 full `@SpringBootTest` 커버리지가
 * 없었다 — 이 테스트가 그 공백을 메운다.
 *
 * full `@SpringBootTest`로 [CoreApiApplication]을 통째로 띄워 실제 빈으로 계약을 핀한다
 * (`PhoneVerificationService` → `InMemoryPhoneVerificationStore`(`local` 프로파일), `ApiControllerAdvice`,
 * `ApiResponse` 직렬화):
 *  1. happy 라운드트립 — request(challenge 발급) → confirm(올바른 code) → 200, `phoneVerificationToken` 발급
 *  2. 잘못된 code — confirm 시 발급 code와 다른 6자리 → 400 `INVALID_PHONE_VERIFICATION_CODE`
 *  3. 미존재 verificationId — 임의 UUID → 400 `INVALID_PHONE_VERIFICATION`
 *  4. 폰 번호 불일치 — challenge 발급 폰과 다른 폰으로 확인 → 400 `INVALID_PHONE_VERIFICATION`
 *
 * code는 서버가 `SecureRandom`으로 생성해 SMS notifier로만 내보내므로, 외부 SMS 발송기
 * [PhoneVerificationNotifier]만 `@Primary` mockk로 대체해 발급된 code를 캡처한다(네트워크 없음).
 * 실제 SMS는 발송되지 않으며, code 위조 없이 같은 앱이 발급한 code를 그대로 확인 경로에 태운다.
 */
@Tag("context")
@SpringBootTest(classes = [CoreApiApplication::class])
@AutoConfigureMockMvc
@Import(LocalPhoneVerificationContextTest.MockNotifierConfig::class)
class LocalPhoneVerificationContextTest {

    @TestConfiguration
    class MockNotifierConfig {
        @Bean @Primary fun mockPhoneVerificationNotifier(): PhoneVerificationNotifier = mockk(relaxed = true)
    }

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var notifier: PhoneVerificationNotifier

    private val codeSlot = slot<String>()

    @BeforeEach
    fun setUp() {
        // notifier.sendCode(phone, code, ttl) 호출의 code 인자를 캡처한다 — 확인 단계에서 이 code를 그대로 쓴다.
        every { notifier.sendCode(any(), capture(codeSlot), any()) } returns Unit
    }

    @Test
    fun `request then confirm with the issued code returns a phone verification token`() {
        val phone = "010-1000-2000"
        val verificationId = requestVerification(phone)
        val code = codeSlot.captured

        mockMvc.perform(confirmRequest(phone, verificationId, code))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.result").value("SUCCESS"))
            .andExpect(jsonPath("$.data.phoneVerificationToken").isNotEmpty)
            .andExpect(jsonPath("$.data.expiresInSec").isNumber)
    }

    @Test
    fun `confirm with a wrong code is rejected`() {
        val phone = "010-1000-3000"
        val verificationId = requestVerification(phone)
        val issued = codeSlot.captured
        // 발급된 code와 보장된 다른 6자리 code (둘 다 ^\d{6}$ 통과).
        val wrong = if (issued == "123456") "654321" else "123456"

        mockMvc.perform(confirmRequest(phone, verificationId, wrong))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.result").value("ERROR"))
            .andExpect(jsonPath("$.error.code").value(ErrorType.INVALID_PHONE_VERIFICATION_CODE.code.name))
    }

    @Test
    fun `confirm with an unknown verificationId is rejected`() {
        // 어떤 challenge와도 매칭되지 않는 임의 verificationId.
        mockMvc.perform(confirmRequest("010-1000-4000", "00000000-0000-0000-0000-000000000000", "123456"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.result").value("ERROR"))
            .andExpect(jsonPath("$.error.code").value(ErrorType.INVALID_PHONE_VERIFICATION.code.name))
    }

    @Test
    fun `confirm with a phone number different from the challenge is rejected`() {
        val issuedPhone = "010-1000-5000"
        val verificationId = requestVerification(issuedPhone)
        val code = codeSlot.captured

        // challenge는 issuedPhone으로 발급됐는데 다른 폰으로 확인 시도 → 폰 바인딩 위반(코드 검증 이전 단계).
        mockMvc.perform(confirmRequest("010-9999-8888", verificationId, code))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.result").value("ERROR"))
            .andExpect(jsonPath("$.error.code").value(ErrorType.INVALID_PHONE_VERIFICATION.code.name))
    }

    /** challenge를 발급하고 verificationId를 돌려준다(발급 code는 [codeSlot]에 캡처됨). */
    private fun requestVerification(phone: String): String {
        val body = mockMvc.perform(
            post("/api/v1/auth/local/phone-verifications/request")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("phoneNumber" to phone))),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.verificationId").isNotEmpty)
            .andExpect(jsonPath("$.data.expiresInSec").isNumber)
            .andReturn()
            .response
            .getContentAsString(Charsets.UTF_8)
        return objectMapper.readTree(body).path("data").path("verificationId").asText()
    }

    private fun confirmRequest(phone: String, verificationId: String, code: String) =
        post("/api/v1/auth/local/phone-verifications/confirm")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                objectMapper.writeValueAsString(
                    mapOf(
                        "phoneNumber" to phone,
                        "verificationId" to verificationId,
                        "code" to code,
                    ),
                ),
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

            // dev 기본값은 oauth2만 ON(`AUTH_SMS_ENABLED:false`)이라 SMS 컨트롤러가 @ConditionalOnProperty로
            // 빈 생성되지 않는다. SMS 인증을 켠 상태의 계약을 핀하기 위해 테스트에서 명시적으로 활성화한다.
            registry.add("app.auth.method.sms.enabled") { "true" }

            registry.add("spring.security.oauth2.client.registration.kakao.client-id") { "test-kakao-client" }
            registry.add("spring.security.oauth2.client.registration.kakao.client-secret") { "test-kakao-secret" }

            registry.add("spring.data.redis.host") { System.getenv("REDIS_HOST") ?: "127.0.0.1" }
            registry.add("spring.data.redis.port") { System.getenv("REDIS_PORT") ?: "6379" }
        }
    }
}
