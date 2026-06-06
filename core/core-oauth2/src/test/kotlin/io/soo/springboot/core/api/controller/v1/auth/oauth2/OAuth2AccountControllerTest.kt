package io.soo.springboot.core.api.controller.v1.auth.oauth2

import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.mockk
import io.soo.springboot.core.api.controller.ApiControllerAdvice
import io.soo.springboot.core.domain.authmethod.AuthMethodConfigService
import io.soo.springboot.core.domain.oauth2.KakaoSdkTokenLogin
import io.soo.springboot.core.enums.AuthProvider
import io.soo.springboot.core.support.error.CoreException
import io.soo.springboot.core.support.error.ErrorType
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

/**
 * `POST /api/v1/auth/oauth2/kakao/token` 컨트롤러 레이어 통합 테스트.
 *
 * standalone MockMvc로 컨트롤러 + [ApiControllerAdvice]만 띄워, DB/Redis 없이도
 * 요청 역직렬화 → 응답 JSON 규약 → CoreException 에러 매핑을 검증한다.
 * (전체 Spring 컨텍스트 happy-path 검증은 로컬 DB/Redis가 필요해 `@Tag("context")`로 별도 분리 예정)
 */
@Tag("unit")
class OAuth2AccountControllerTest {

    private val authMethodConfigService: AuthMethodConfigService = mockk(relaxed = true)
    private val kakaoSdkTokenLogin: KakaoSdkTokenLogin = mockk()

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        val controller = OAuth2AccountController(
            authMethodConfigService = authMethodConfigService,
            kakaoSdkTokenLogin = kakaoSdkTokenLogin,
        )
        // java.time(LocalDateTime/Instant) + Kotlin data class 직렬화를 위해 모듈을 모두 등록한다.
        val objectMapper = ObjectMapper().findAndRegisterModules()
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(ApiControllerAdvice())
            .setMessageConverters(MappingJackson2HttpMessageConverter(objectMapper))
            .build()
    }

    @Test
    fun `정상 토큰이면 200과 LoginSuccessResponse 규약대로 토큰·유저를 반환한다`() {
        every { kakaoSdkTokenLogin.login(eq("ka_at_ok"), any()) } returns KakaoSdkTokenLogin.IssuedLogin(
            userId = 42L,
            provider = AuthProvider.KAKAO,
            accessToken = "jwt.access.token",
            accessExpiresInSec = 900,
            refreshToken = "jwt.refresh.token",
            refreshExpiresInSec = 60L * 60 * 24 * 30,
            email = "user@triplan.kr",
            nickname = "리스",
            profileImageUrl = null,
            roles = listOf("USER"),
        )

        mockMvc.perform(
            post("/api/v1/auth/oauth2/kakao/token")
                .header("X-Device-Id", "dev-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"kakaoAccessToken":"ka_at_ok"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.result").value("SUCCESS"))
            .andExpect(jsonPath("$.data.token.accessToken").value("jwt.access.token"))
            .andExpect(jsonPath("$.data.token.expiresIn").value(900))
            .andExpect(jsonPath("$.data.token.refreshToken").value("jwt.refresh.token"))
            .andExpect(jsonPath("$.data.user.id").value(42))
            .andExpect(jsonPath("$.data.user.provider").value("KAKAO"))
            .andExpect(jsonPath("$.data.user.email").value("user@triplan.kr"))
            .andExpect(jsonPath("$.data.user.roles[0]").value("USER"))
    }

    @Test
    fun `kakao 토큰이 유효하지 않으면 401과 에러코드를 반환한다`() {
        every { kakaoSdkTokenLogin.login(eq("ka_at_bad"), any()) } throws
            CoreException(ErrorType.KAKAO_TOKEN_INVALID)

        mockMvc.perform(
            post("/api/v1/auth/oauth2/kakao/token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"kakaoAccessToken":"ka_at_bad"}"""),
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.result").value("ERROR"))
            .andExpect(jsonPath("$.error.code").value(ErrorType.KAKAO_TOKEN_INVALID.code.name))
    }

    @Test
    fun `kakao 제공자 오류면 502와 에러코드를 반환한다`() {
        every { kakaoSdkTokenLogin.login(eq("ka_at_down"), any()) } throws
            CoreException(ErrorType.KAKAO_PROVIDER_ERROR)

        mockMvc.perform(
            post("/api/v1/auth/oauth2/kakao/token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"kakaoAccessToken":"ka_at_down"}"""),
        )
            .andExpect(status().isBadGateway)
            .andExpect(jsonPath("$.result").value("ERROR"))
            .andExpect(jsonPath("$.error.code").value(ErrorType.KAKAO_PROVIDER_ERROR.code.name))
    }
}
