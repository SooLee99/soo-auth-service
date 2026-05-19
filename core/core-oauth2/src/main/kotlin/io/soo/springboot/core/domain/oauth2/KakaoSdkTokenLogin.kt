package io.soo.springboot.core.domain.oauth2

import io.soo.springboot.clients.kakao.KakaoOAuthClient
import io.soo.springboot.clients.kakao.KakaoProviderException
import io.soo.springboot.clients.kakao.KakaoTokenInvalidException
import io.soo.springboot.core.api.security.token.AuthTokenManager
import io.soo.springboot.core.api.security.userdetails.UserPrincipalLoader
import io.soo.springboot.core.domain.authmethod.AuthMethodConfigService
import io.soo.springboot.core.domain.login.LoginHistoryService
import io.soo.springboot.core.enums.AuthMethod
import io.soo.springboot.core.enums.AuthProvider
import io.soo.springboot.core.enums.LoginType
import io.soo.springboot.core.support.error.AccountStatusDeniedException
import io.soo.springboot.core.support.error.CoreException
import io.soo.springboot.core.support.error.ErrorType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.stereotype.Service

/**
 * Kakao JS SDK가 발급한 accessToken을 받아 자체 JWT로 교환하는 SDK-token 흐름의 로그인 유스케이스.
 *
 * Redirect 콜백 흐름(`OAuth2LoginSuccessHandler`)과는 별개 경로이며, triplan 프론트엔드처럼
 * 클라이언트가 직접 Kakao SDK로 accessToken을 받은 뒤 서버에 전달하는 케이스에서 호출된다.
 */
@Service
class KakaoSdkTokenLogin(
    private val authMethodConfigService: AuthMethodConfigService,
    private val kakaoOAuthClient: KakaoOAuthClient,
    private val parsers: OAuth2ParserRegistry,
    private val account: OAuth2AccountService,
    private val principalLoader: UserPrincipalLoader,
    private val authTokenManager: AuthTokenManager,
    private val loginHistoryService: LoginHistoryService,
) {

    data class LoginContext(
        val ipAddress: String?,
        val userAgent: String?,
        val deviceId: String,
    )

    data class IssuedLogin(
        val userId: Long,
        val provider: AuthProvider,
        val accessToken: String,
        val accessExpiresInSec: Long,
        val refreshToken: String,
        val refreshExpiresInSec: Long,
        val email: String,
        val nickname: String?,
        val profileImageUrl: String?,
        val roles: List<String>,
    )

    fun login(kakaoAccessToken: String, context: LoginContext): IssuedLogin {
        authMethodConfigService.assertEnabled(AuthMethod.OAUTH2)

        if (kakaoAccessToken.isBlank()) {
            throw CoreException(ErrorType.KAKAO_TOKEN_INVALID)
        }

        val attrs = try {
            kakaoOAuthClient.fetchUserMe(kakaoAccessToken)
        } catch (e: KakaoTokenInvalidException) {
            throw CoreException(ErrorType.KAKAO_TOKEN_INVALID)
        } catch (e: KakaoProviderException) {
            throw CoreException(ErrorType.KAKAO_PROVIDER_ERROR)
        }

        val profile = parsers.parse(AuthProvider.KAKAO, attrs)

        val userId = try {
            account.signUp(profile)
        } catch (_: AccountStatusDeniedException) {
            throw CoreException(ErrorType.LOGIN_DENIED)
        }

        val principal = principalLoader.loadByUserId(userId)
        val appAuth = UsernamePasswordAuthenticationToken(principal.username, null, principal.authorities)

        val issued = authTokenManager.issue(
            authentication = appAuth,
            userId = userId,
            deviceId = context.deviceId,
            provider = AuthProvider.KAKAO,
        )

        loginHistoryService.recordLoginSuccess(
            userId = userId,
            userEmail = principal.email,
            loginType = LoginType.OAUTH2,
            ipAddress = context.ipAddress,
            userAgent = context.userAgent,
            deviceId = context.deviceId,
        )

        val roles = principal.authorities.map { it.authority }.distinct().sorted()

        return IssuedLogin(
            userId = userId,
            provider = AuthProvider.KAKAO,
            accessToken = issued.accessToken,
            accessExpiresInSec = issued.accessExpiresInSec,
            refreshToken = issued.refreshToken,
            refreshExpiresInSec = issued.refreshExpiresInSec,
            email = principal.email,
            nickname = profile.nickname,
            profileImageUrl = profile.profileImageUrl,
            roles = roles,
        )
    }
}
