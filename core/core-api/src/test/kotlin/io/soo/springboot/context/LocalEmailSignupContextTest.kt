package io.soo.springboot.context

import com.fasterxml.jackson.databind.ObjectMapper
import io.soo.springboot.CoreApiApplication
import io.soo.springboot.core.support.error.ErrorType
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * E2 보강 — 이메일 회원가입 엔드포인트(`POST /api/v1/auth/local/email/signup`)의 full-boot 컨텍스트 테스트.
 *
 * `LocalEmailController.signUp`은 공개 엔드포인트이며, `id`/`phone` 가입과 달리 **proof(phoneVerificationToken)를
 * 소비하지 않는다** — `LocalAccountService.signUp`은 `phoneVerificationToken`을 무시하고 이메일·(있으면)폰
 * 유일성만 검증한 뒤 User+LocalCredential을 영속화한다. 즉 happy path는 email+password+gender만으로 통과한다.
 * 이메일 로그인은 별도 컨트롤러가 없으므로(id/phone과 달리), 가입 영속성은 **같은 이메일 재가입 → DUPLICATE_EMAIL**
 * 로 간접 증명한다(중복이 막힌다는 것은 첫 가입이 H2에 영속됐다는 뜻).
 *
 * 이 엔드포인트는 마지막 미커버 local-* 가입 컨트롤러였다. 이 테스트로 모든 local-* signup/login 컨트롤러가
 * full `@SpringBootTest` 커버리지를 갖는다. full-boot로 [CoreApiApplication]을 통째로 띄워 실제 빈으로 계약을 핀한다
 * (`LocalAccountService` → JPA(H2) User/LocalCredential 영속화, `UserUniquenessPolicy`, Bean Validation,
 * `ApiControllerAdvice`, `ApiResponse` 직렬화):
 *  1. happy 가입(email+password+gender, 폰 없음) → 200 + 같은 이메일 재가입 → 409 `DUPLICATE_EMAIL` (영속성·유일성)
 *  2. 다른 이메일로 같은 폰 번호 재사용 가입 → 409 `DUPLICATE_PHONE_NUMBER` (폰 유일성 분기)
 *  3. 약한 비밀번호(영문/숫자/특수문자 3종 미충족) → 400 `INVALID_INPUT_VALUE` (Bean Validation)
 *
 * 외부 호출이 없어 네트워크 의존이 전혀 없다(proof도 불필요). dev 기본값은 oauth2만 ON이라 email 컨트롤러가
 * `@ConditionalOnProperty`로 빈 생성되지 않으므로 `@DynamicPropertySource`로 `app.auth.method.email.enabled=true`를
 * 켠다(Cycle #16 NOTE). `assertEnabled(EMAIL)`은 `auth_method_config`에 행이 없으면 true가 기본이라 admin 시드가
 * 필요 없다. 테스트마다 이메일·폰 번호를 달리해 컨텍스트 공유 H2의 유일성 충돌을 피한다.
 */
@Tag("context")
@SpringBootTest(classes = [CoreApiApplication::class])
@AutoConfigureMockMvc
class LocalEmailSignupContextTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper

    private val password = "Passw0rd!"

    @Test
    fun `email signup persists the account and a duplicate email is rejected`() {
        val email = "email0001@triplan.test"

        // 폰 없이 email+password+gender만으로 가입 → proof 불필요.
        mockMvc.perform(signupRequest(email, password))
            .andExpect(status().isOk)

        // 같은 이메일 재가입 → 첫 가입이 영속됐으므로 유일성 정책이 차단한다.
        mockMvc.perform(signupRequest(email, password))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.result").value("ERROR"))
            .andExpect(jsonPath("$.error.code").value(ErrorType.DUPLICATE_EMAIL.code.name))
    }

    @Test
    fun `signing up with an already-used phone number is rejected`() {
        val phone = "010-4000-0002"

        mockMvc.perform(signupRequest("email0002a@triplan.test", password, phone))
            .andExpect(status().isOk)

        // 다른 이메일이지만 같은 폰 번호 → 폰 유일성 분기에서 차단.
        mockMvc.perform(signupRequest("email0002b@triplan.test", password, phone))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.result").value("ERROR"))
            .andExpect(jsonPath("$.error.code").value(ErrorType.DUPLICATE_PHONE_NUMBER.code.name))
    }

    @Test
    fun `a weak password is rejected by validation`() {
        // "weakpwd" — 6자 이상이지만 숫자·특수문자가 없어 3종 규칙(영문/숫자/특수문자) 미충족.
        mockMvc.perform(signupRequest("email0003@triplan.test", "weakpwd"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.result").value("ERROR"))
            .andExpect(jsonPath("$.error.code").value(ErrorType.INVALID_INPUT_VALUE.code.name))
    }

    private fun signupRequest(email: String, password: String, phone: String? = null): org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder {
        val body = buildMap<String, Any> {
            put("email", email)
            put("password", password)
            put("gender", "MALE")
            if (phone != null) put("phoneNumber", phone)
        }
        return post("/api/v1/auth/local/email/signup")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(body))
    }

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

            // dev 기본값은 oauth2만 ON이라 email 컨트롤러가 @ConditionalOnProperty로 빈 생성되지 않는다.
            // 이메일 가입 계약을 핀하기 위해 명시적으로 활성화한다.
            registry.add("app.auth.method.email.enabled") { "true" }

            registry.add("spring.security.oauth2.client.registration.kakao.client-id") { "test-kakao-client" }
            registry.add("spring.security.oauth2.client.registration.kakao.client-secret") { "test-kakao-secret" }

            registry.add("spring.data.redis.host") { System.getenv("REDIS_HOST") ?: "127.0.0.1" }
            registry.add("spring.data.redis.port") { System.getenv("REDIS_PORT") ?: "6379" }
        }
    }
}
