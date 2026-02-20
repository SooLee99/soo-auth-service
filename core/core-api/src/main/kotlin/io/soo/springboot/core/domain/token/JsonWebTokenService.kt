package io.soo.springboot.core.domain.token

import io.soo.springboot.core.enums.AuthProvider
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
class JsonWebTokenService(
    private val accessTokenService: AccessTokenService,
    private val refreshTokenService: RefreshTokenService,
    private val userPrincipalLoader: UserPrincipalLoader,
) {
    /**
     * ✅ 토큰 발급
     */
    fun issue(authentication: Authentication, userId: Long, deviceId: String, provider: AuthProvider): IssuedTokens {
        val (access, accessExpSec) = accessTokenService.issue(authentication, userId)
        val refresh = refreshTokenService.issue(userId, deviceId, provider)

        return IssuedTokens(
            accessToken = access,
            accessExpiresInSec = accessExpSec,
            refreshToken = refresh.token,
            refreshExpiresInSec = refresh.expiresInSec,
        )
    }

    /**
     * ✅ refresh rotate
     * - old refresh가 유효하면 새 access + 새 refresh 반환
     */
    fun refresh(oldRefreshToken: String, deviceId: String): Pair<RotateResult, IssuedTokens?> {
        val (rotate, newRefresh) = refreshTokenService.rotate(oldRefreshToken, deviceId)

        return when (rotate) {
            is RotateResult.Success -> {
                val user: UserDetails = userPrincipalLoader.loadByUserId(rotate.userId)
                val auth = UsernamePasswordAuthenticationToken(user.username, null, user.authorities)

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
            RotateResult.DeviceMismatch -> rotate to null
        }
    }

    fun revoke(token: String) = refreshTokenService.revoke(token)
    fun revokeAll(userId: Long) = refreshTokenService.revokeAllByUser(userId)
    fun revokeByDevice(userId: Long, deviceId: String) = refreshTokenService.revokeByDevice(userId, deviceId)
}
