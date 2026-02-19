package io.soo.springboot.core.domain.token

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.stereotype.Service

data class IssuedTokens(
    val accessToken: String,
    val accessExpiresInSec: Long,
    val refreshToken: String,
    val refreshExpiresInSec: Long,
)

@Service
class JwtService(
    private val accessTokenService: AccessTokenService,
    private val refreshTokenService: RefreshTokenService,
    private val userPrincipalLoader: UserPrincipalLoader,
) {
    /**
     * ✅ 토큰 발급
     * - access token: 15분
     * - refresh token: 14일
     */

    fun issue(authentication: Authentication, userId: Long): IssuedTokens {
        val (access, accessExpSec) = accessTokenService.issue(authentication, userId)
        val refresh = refreshTokenService.issue(userId)

        return IssuedTokens(
            accessToken = access,
            accessExpiresInSec = accessExpSec,
            refreshToken = refresh.token,
            refreshExpiresInSec = refresh.expiresInSec,
        )
    }

    /**
     * ✅ 토큰 refresh
     * - refresh rotate 성공 시: 새 access + 새 refresh 반환
     */
    fun refresh(oldRefreshToken: String): Pair<RotateResult, IssuedTokens?> {
        val (rotate, newRefresh) = refreshTokenService.rotate(oldRefreshToken)

        return when (rotate) {
            is RotateResult.Success -> {
                // userId로 UserDetails 로드
                val user: UserDetails = userPrincipalLoader.loadByUserId(rotate.userId)

                // roles 포함 Authentication 생성
                val auth = UsernamePasswordAuthenticationToken(user.username, null, user.authorities)

                // access token 발급
                val (access, accessExpSec) = accessTokenService.issue(auth, rotate.userId)

                rotate to IssuedTokens(
                    accessToken = access,
                    accessExpiresInSec = accessExpSec,
                    refreshToken = newRefresh!!.token,
                    refreshExpiresInSec = newRefresh.expiresInSec,
                )
            }
            RotateResult.NotFoundOrExpired -> rotate to null
            RotateResult.AlreadyUsed -> rotate to null
        }
    }

    /**
     * ✅ 토큰 revoke
     */
    fun revoke(token: String) = refreshTokenService.revoke(token)
    fun revokeAll(userId: Long) = refreshTokenService.revokeAllByUser(userId)
}
