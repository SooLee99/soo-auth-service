package io.soo.springboot.core.domain.oauth2

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.soo.springboot.clients.kakao.KakaoOAuthClient
import io.soo.springboot.clients.kakao.KakaoProviderException
import io.soo.springboot.clients.kakao.KakaoTokenInvalidException
import io.soo.springboot.core.api.security.token.AuthTokenManager
import io.soo.springboot.core.api.security.token.IssuedTokens
import io.soo.springboot.core.api.security.userdetails.UserPrincipal
import io.soo.springboot.core.api.security.userdetails.UserPrincipalLoader
import io.soo.springboot.core.domain.authmethod.AuthMethodConfigService
import io.soo.springboot.core.domain.login.LoginHistoryService
import io.soo.springboot.core.enums.AuthMethod
import io.soo.springboot.core.enums.AuthProvider
import io.soo.springboot.core.enums.LoginType
import io.soo.springboot.core.support.error.CoreException
import io.soo.springboot.core.support.error.ErrorType
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/**
 * Kakao SDK 토큰 검증 → 자체 JWT 발급 흐름의 unit 테스트.
 */
@Tag("unit")
class KakaoSdkTokenLoginTest {

    private val authMethodConfigService: AuthMethodConfigService = mockk(relaxed = true)
    private val kakaoOAuthClient: KakaoOAuthClient = mockk()
    private val account: OAuth2AccountService = mockk()
    private val principalLoader: UserPrincipalLoader = mockk()
    private val authTokenManager: AuthTokenManager = mockk()
    private val loginHistoryService: LoginHistoryService = mockk(relaxed = true)

    private val parsers = OAuth2ParserRegistry(parsers = listOf(KakaoParser()))

    private lateinit var sut: KakaoSdkTokenLogin

    @BeforeEach
    fun setUp() {
        sut = KakaoSdkTokenLogin(
            authMethodConfigService = authMethodConfigService,
            kakaoOAuthClient = kakaoOAuthClient,
            parsers = parsers,
            account = account,
            principalLoader = principalLoader,
            authTokenManager = authTokenManager,
            loginHistoryService = loginHistoryService,
        )
    }

    @Test
    fun `정상 토큰이면 user upsert 후 자체 JWT를 발급한다`() {
        // given
        val attrs: Map<String, Any?> = mapOf(
            "id" to 987654321L,
            "kakao_account" to mapOf(
                "email" to "user@triplan.kr",
                "is_email_verified" to true,
                "profile" to mapOf("nickname" to "리스"),
            ),
        )
        every { kakaoOAuthClient.fetchUserMe("ka_at_ok") } returns attrs
        every { account.signUp(any()) } returns 42L

        val principal = UserPrincipal(
            userId = 42L,
            email = "user@triplan.kr",
            passwordHash = null,
            role = "USER",
            provider = AuthProvider.KAKAO,
        )
        every { principalLoader.loadByUserId(42L) } returns principal

        val tokens = IssuedTokens(
            accessToken = "jwt.access.token",
            accessExpiresInSec = 900,
            refreshToken = "jwt.refresh.token",
            refreshExpiresInSec = 60 * 60 * 24 * 30,
        )
        every { authTokenManager.issue(any(), 42L, "dev-1", AuthProvider.KAKAO) } returns tokens

        val context = KakaoSdkTokenLogin.LoginContext(
            ipAddress = "127.0.0.1",
            userAgent = "vitest",
            deviceId = "dev-1",
        )

        // when
        val issued = sut.login("ka_at_ok", context)

        // then
        assertThat(issued.userId).isEqualTo(42L)
        assertThat(issued.provider).isEqualTo(AuthProvider.KAKAO)
        assertThat(issued.accessToken).isEqualTo("jwt.access.token")
        assertThat(issued.refreshToken).isEqualTo("jwt.refresh.token")
        assertThat(issued.email).isEqualTo("user@triplan.kr")
        assertThat(issued.nickname).isEqualTo("리스")

        verify(exactly = 1) {
            authMethodConfigService.assertEnabled(AuthMethod.OAUTH2)
            loginHistoryService.recordLoginSuccess(
                userId = 42L,
                userEmail = "user@triplan.kr",
                loginType = LoginType.OAUTH2,
                ipAddress = "127.0.0.1",
                userAgent = "vitest",
                deviceId = "dev-1",
            )
        }
    }

    @Test
    fun `빈 accessToken 이면 KAKAO_TOKEN_INVALID`() {
        assertThatThrownBy {
            sut.login("", anyContext())
        }
            .isInstanceOf(CoreException::class.java)
            .extracting("errorType")
            .isEqualTo(ErrorType.KAKAO_TOKEN_INVALID)
    }

    @Test
    fun `kakao 401 응답이면 KAKAO_TOKEN_INVALID 로 매핑된다`() {
        every { kakaoOAuthClient.fetchUserMe("ka_at_bad") } throws KakaoTokenInvalidException("401")

        assertThatThrownBy { sut.login("ka_at_bad", anyContext()) }
            .isInstanceOf(CoreException::class.java)
            .extracting("errorType")
            .isEqualTo(ErrorType.KAKAO_TOKEN_INVALID)
    }

    @Test
    fun `kakao 5xx 응답이면 KAKAO_PROVIDER_ERROR 로 매핑된다`() {
        every { kakaoOAuthClient.fetchUserMe("ka_at_kakao_down") } throws KakaoProviderException("502")

        assertThatThrownBy { sut.login("ka_at_kakao_down", anyContext()) }
            .isInstanceOf(CoreException::class.java)
            .extracting("errorType")
            .isEqualTo(ErrorType.KAKAO_PROVIDER_ERROR)
    }

    private fun anyContext() = KakaoSdkTokenLogin.LoginContext(
        ipAddress = null,
        userAgent = null,
        deviceId = "",
    )
}
