package io.soo.springboot.context

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.mockk
import io.soo.springboot.CoreApiApplication
import io.soo.springboot.clients.kakao.KakaoOAuthClient
import io.soo.springboot.core.enums.UserStatus
import io.soo.springboot.storage.db.core.UserRepository
import org.assertj.core.api.Assertions.assertThat
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * E2 보강 — 계정 탈퇴 엔드포인트(`POST /api/v1/auth/local/withdraw`)의 full-boot 컨텍스트 테스트.
 *
 * `LocalUserController.withdrawUser`는 인증 주체(JWT `uid`)를 받아 `LocalAccountService.softDeleteUser`로
 * 본인 계정을 soft-delete 하는 보안상 민감한 경로다. `ApiSecurityConfig`에서 PUBLIC_ENDPOINTS에 없고
 * `/api/` 하위 경로 → `authenticated()`에 걸리므로 익명 호출은 막혀야 한다. 그동안 이 엔드포인트는 full
 * `@SpringBootTest` 컨텍스트 커버리지가 없었다 — 이 테스트가 그 공백을 메운다.
 *
 * full `@SpringBootTest`로 [CoreApiApplication]을 통째로 띄워 다음을 실제 빈으로 검증한다:
 *  - 익명(토큰 없음) → 401 (Security 인증 강제)
 *  - **같은 앱의 공개 발급 경로(`POST /oauth2/kakao/token`)로 진짜 토큰을 발급받아** Bearer로 태워
 *    탈퇴 → 200 `result=OK`, 그리고 [UserRepository]로 재조회해 **soft-delete가 실제로 영속**됐는지
 *    (`userStatus=SOFT_DELETED`, `deletedAt` 채움, 이메일 익명화) 확인 — DB 라운드트립 증명
 *  - 멱등성 — 이미 soft-delete된 계정에 같은 토큰으로 재탈퇴 → `UserLifecycleService.softDelete`의
 *    early-return 분기를 타고 여전히 200 OK (한 번 탈퇴가 영속됐음을 간접 증명)
 *
 * 토큰은 위조하지 않는다. 외부 upstream인 Kakao `/v2/user/me`([KakaoOAuthClient])만 `@Primary` mockk로
 * 대체해 네트워크 없이 결정적으로 실행한다. JWT 키스토어/Kakao client registration/Redis 부팅 값은
 * [LocalSessionContextTest]·[OAuth2KakaoTokenContextTest]와 동일하게 주입한다.
 *
 * 주의(Cycle #15 NOTE) — 한 컨텍스트 안에서 같은 UserEntity 로그인을 반복하면 `@Version` 낙관적 락 409가
 * 날 수 있으므로, 토큰을 발급하는 각 테스트는 자기만의 distinct kakao id/email을 쓴다.
 */
@Tag("context")
@SpringBootTest(classes = [CoreApiApplication::class])
@AutoConfigureMockMvc
@Import(LocalWithdrawContextTest.MockKakaoClientConfig::class)
class LocalWithdrawContextTest {

    @TestConfiguration
    class MockKakaoClientConfig {
        @Bean @Primary fun mockKakaoOAuthClient(): KakaoOAuthClient = mockk()
    }

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var kakaoOAuthClient: KakaoOAuthClient
    @Autowired private lateinit var userRepository: UserRepository

    @Test
    fun `POST withdraw - anonymous (no token) returns 401`() {
        // 탈퇴는 PUBLIC_ENDPOINTS가 아니고 /api/ 하위 경로 → authenticated() 이므로 토큰 없이는 막혀야 한다.
        mockMvc.perform(
            post("/api/v1/auth/local/withdraw")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"),
        )
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `POST withdraw - valid token soft-deletes the user and persists`() {
        stubKakaoUser("ka_at_withdraw_a", kakaoId = 4_295_010_001L, email = "withdraw-a@triplan.kr")
        val tokenJson = issueAccessToken("ka_at_withdraw_a", deviceId = "dev-withdraw-a")
        val accessToken = tokenJson.path("data").path("token").path("accessToken").asText()
        val userId = tokenJson.path("data").path("user").path("id").asLong()

        // 탈퇴(사유 포함) → 200, result=OK
        mockMvc.perform(
            post("/api/v1/auth/local/withdraw")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("reason" to "더 이상 사용하지 않음"))),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.result").value("SUCCESS"))
            .andExpect(jsonPath("$.data.result").value("OK"))

        // DB 재조회 — soft-delete가 실제로 영속됐는지 (삭제 포함 조회).
        val user = userRepository.findByIdIncludingDeleted(userId)
        assertThat(user).isNotNull
        assertThat(user!!.userStatus).isEqualTo(UserStatus.SOFT_DELETED)
        assertThat(user.deletedAt).isNotNull
        // 이메일 익명화: deleted+<userId>.<epoch>@deleted.local
        assertThat(user.email).endsWith("@deleted.local")
        assertThat(user.email).isNotEqualTo("withdraw-a@triplan.kr")
    }

    @Test
    fun `POST withdraw - idempotent second withdraw with same token still returns OK`() {
        stubKakaoUser("ka_at_withdraw_b", kakaoId = 4_295_010_002L, email = "withdraw-b@triplan.kr")
        val accessToken = issueAccessToken("ka_at_withdraw_b", deviceId = "dev-withdraw-b")
            .path("data").path("token").path("accessToken").asText()

        // 1차 탈퇴 — soft-delete 영속
        mockMvc.perform(
            post("/api/v1/auth/local/withdraw")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.result").value("OK"))

        // 2차 탈퇴(같은 토큰) — 이미 SOFT_DELETED라 softDelete가 early-return,
        // 컨트롤러는 여전히 200 OK (멱등). 한 번 탈퇴가 영속됐음을 간접 증명.
        mockMvc.perform(
            post("/api/v1/auth/local/withdraw")
                .header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.result").value("OK"))
    }

    private fun stubKakaoUser(kakaoAccessToken: String, kakaoId: Long, email: String) {
        every { kakaoOAuthClient.fetchUserMe(kakaoAccessToken) } returns mapOf(
            "id" to kakaoId,
            "connected_at" to "2026-05-01T00:00:00Z",
            "kakao_account" to mapOf(
                "email" to email,
                "is_email_verified" to true,
                "profile" to mapOf(
                    "nickname" to "탈퇴테스트",
                    "profile_image_url" to "https://example.com/w.jpg",
                ),
            ),
        )
    }

    private fun issueAccessToken(kakaoAccessToken: String, deviceId: String): JsonNode {
        val body = mockMvc.perform(
            post("/api/v1/auth/oauth2/kakao/token")
                .header("X-Device-Id", deviceId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("kakaoAccessToken" to kakaoAccessToken))),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.token.accessToken").isNotEmpty)
            .andReturn()
            .response
            .getContentAsString(Charsets.UTF_8)
        return objectMapper.readTree(body)
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

            registry.add("spring.security.oauth2.client.registration.kakao.client-id") { "test-kakao-client" }
            registry.add("spring.security.oauth2.client.registration.kakao.client-secret") { "test-kakao-secret" }

            registry.add("spring.data.redis.host") { System.getenv("REDIS_HOST") ?: "127.0.0.1" }
            registry.add("spring.data.redis.port") { System.getenv("REDIS_PORT") ?: "6379" }
        }
    }
}
