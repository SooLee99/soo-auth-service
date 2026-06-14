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
 * E2 보강 — 관리자 SMS(발송·로그·통계) 엔드포인트의 full-boot 컨텍스트 테스트.
 *
 * `AdminSmsController`는 `SmsAdminService`를 통해 다음을 제공한다:
 *  - mutate: 발송(`POST /sms/send`) — `SmsSender.send` 후 성공/실패 로그를 적재
 *  - read:   로그 목록(`GET /sms/logs`)·통계(`GET /sms/stats`)
 * 모두 `ApiSecurityConfig`의 admin 하위(`/api/v1/auth/admin` 경로, ADMIN_API) → `hasRole("ADMIN")`(=`ROLE_ADMIN`)로 보호된다.
 * Cycle #23~#27의 관리자 토큰 하네스(부트스트랩 시드 + admin 로그인)를 그대로 재사용한다.
 *
 * 발송은 변경 경로지만 `local` 프로파일에선 `solapi.sms.enabled` 미설정 → `LoggingSmsSender`(no-op 로그)가 활성이라
 * 네트워크 없이 성공한다. 따라서 SMS 게이트웨이 mock 없이 발송 round-trip을 핀한다:
 * send → 200(`ok=true`/`provider=LOG`/번호 정규화) → 로그 목록에 노출 → read-after-write.
 * 이는 여섯 번째이자 마지막 Admin-controller 커버리지로, 이로써 모든 Admin 컨트롤러가 full-boot로 핀된다.
 *
 * full `@SpringBootTest`로 [CoreApiApplication]을 통째로 띄워 실제 빈으로 인가 + SMS 계약을 핀한다:
 *  1. 익명(토큰 없음) GET logs → **401** (`unauthorizedEntryPoint`, admin 하위 진입점)
 *  2. 인증된 비관리자(USER 토큰) GET logs → **403** (`ROLE_USER`는 `hasRole("ADMIN")` 불충족)
 *  3. 관리자 토큰 GET logs → **200** + content 배열(비어 있을 수 있음)
 *  4. 관리자 토큰 GET stats → **200** + total/ok/fail/rate 필드
 *  5. 관리자 토큰 POST send round-trip → **200**: 발송 응답 `ok=true`·`provider=LOG`·정규화된 번호·`id` 숫자;
 *     이후 GET logs content에 방금 발송한 text 노출(read-after-write).
 *  6. 관리자 토큰 POST send + 잘못된 번호 → **400 `E400`** (`@Valid` `@Pattern` 위반 → `INVALID_INPUT_VALUE`).
 *
 * SMS 컨트롤러는 `@ConditionalOnProperty("app.auth.method.sms.enabled", matchIfMissing=true)`지만 dev 기본값이
 * present-and-false(`AUTH_SMS_ENABLED:false`)라 매핑되지 않으므로(Cycle #16 NOTE) `@DynamicPropertySource`로
 * `app.auth.method.sms.enabled=true`를 명시한다(없으면 404).
 *
 * 토큰은 위조하지 않는다(하네스는 `AdminUserStatusContextTest`와 동일):
 *  - 관리자 토큰: 부트스트랩 러너를 `app.bootstrap.admin.*`로 켜 부팅 시 관리자 계정을 시드하고,
 *    같은 앱의 공개 경로 `POST /api/v1/auth/admin/login`으로 진짜 `ROLE_ADMIN` 토큰을 발급.
 *  - USER 토큰: 검증된 공개 발급 경로 `POST /oauth2/kakao/token`로 발급(`ROLE_USER`).
 *
 * 외부 upstream인 Kakao `/v2/user/me`([KakaoOAuthClient])만 `@Primary` mockk로 대체해 네트워크 없이 실행한다.
 */
@Tag("context")
@SpringBootTest(classes = [CoreApiApplication::class])
@AutoConfigureMockMvc
@Import(AdminSmsContextTest.MockKakaoClientConfig::class)
class AdminSmsContextTest {

    @TestConfiguration
    class MockKakaoClientConfig {
        @Bean @Primary fun mockKakaoOAuthClient(): KakaoOAuthClient = mockk()
    }

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var kakaoOAuthClient: KakaoOAuthClient

    @BeforeEach
    fun setUp() {
        // 토큰 발급 테스트마다 고유 카카오 사용자(케이스 순서 독립 + @Version 낙관락 회피, Cycle #15 NOTE).
        stubKakaoUser("ka_at_sms_403", 4_295_200_001L, "sms-403@triplan.kr", "유저403")
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
    fun `GET admin sms logs - anonymous (no token) returns 401`() {
        mockMvc.perform(get("/api/v1/auth/admin/sms/logs"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `GET admin sms logs - authenticated non-admin (USER token) returns 403`() {
        val userToken = issueUserAccessToken("ka_at_sms_403", "dev-sms-403").accessToken

        mockMvc.perform(
            get("/api/v1/auth/admin/sms/logs")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $userToken"),
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `GET admin sms logs - admin token returns 200 with content array`() {
        val adminToken = issueAdminAccessToken()

        mockMvc.perform(
            get("/api/v1/auth/admin/sms/logs")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $adminToken"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.result").value("SUCCESS"))
            .andExpect(jsonPath("$.data.content").isArray)
    }

    @Test
    fun `GET admin sms stats - admin token returns 200 with stat fields`() {
        val adminToken = issueAdminAccessToken()

        mockMvc.perform(
            get("/api/v1/auth/admin/sms/stats")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $adminToken"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.result").value("SUCCESS"))
            .andExpect(jsonPath("$.data.total").isNumber)
            .andExpect(jsonPath("$.data.ok").isNumber)
            .andExpect(jsonPath("$.data.fail").isNumber)
            .andExpect(jsonPath("$.data.rate").isNumber)
    }

    @Test
    fun `send - admin token sends sms (LoggingSmsSender) and logs reflect it`() {
        val adminToken = issueAdminAccessToken()
        val text = "관리자 테스트 발송 RAW round-trip"

        // send → 200, LoggingSmsSender 성공(ok=true/provider=LOG), 번호 정규화
        mockMvc.perform(
            post("/api/v1/auth/admin/sms/send")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("to" to "010-1234-5678", "text" to text))),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.result").value("SUCCESS"))
            .andExpect(jsonPath("$.data.id").isNumber)
            .andExpect(jsonPath("$.data.to").value("01012345678"))
            .andExpect(jsonPath("$.data.text").value(text))
            .andExpect(jsonPath("$.data.ok").value(true))
            .andExpect(jsonPath("$.data.provider").value("LOG"))

        // 로그 목록에 방금 발송한 text 노출(read-after-write; createdAt DESC 정렬과 무관하게 hasItem으로 검증)
        mockMvc.perform(
            get("/api/v1/auth/admin/sms/logs")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $adminToken"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.result").value("SUCCESS"))
            .andExpect(jsonPath("$.data.content").isArray)
            .andExpect(jsonPath("$.data.content[*].text", hasItem(text)))
            .andExpect(jsonPath("$.data.content[*].ok", hasItem(true)))
    }

    @Test
    fun `send - admin token with invalid phone number returns 400`() {
        val adminToken = issueAdminAccessToken()

        mockMvc.perform(
            post("/api/v1/auth/admin/sms/send")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $adminToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("to" to "123", "text" to "잘못된 번호"))),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.result").value("ERROR"))
            .andExpect(jsonPath("$.error.code").value("E400"))
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
                .header("X-Device-Id", "dev-sms-admin")
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
        private const val ADMIN_USERNAME = "admin-sms@triplan.test"
        private const val ADMIN_PASSWORD = "Adm1nSms!Pwd987"

        @JvmStatic
        @DynamicPropertySource
        fun props(registry: DynamicPropertyRegistry) {
            val ksPassword = System.getenv("JWT_KEYSTORE_PASSWORD") ?: "devjwtpass"
            registry.add("app.security.jwt.keystore.password") { ksPassword }
            registry.add("app.security.jwt.keystore.key-password") {
                System.getenv("JWT_KEY_PASSWORD") ?: ksPassword
            }
            registry.add("app.security.jwt.keystore.alias") { System.getenv("JWT_KEY_ALIAS") ?: "jwt" }

            // admin 로그인 필터·부트스트랩 러너 + SMS 컨트롤러 매핑(present-and-false 기본값 → 명시 활성).
            registry.add("app.auth.method.admin.enabled") { "true" }
            registry.add("app.auth.method.sms.enabled") { "true" }
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
