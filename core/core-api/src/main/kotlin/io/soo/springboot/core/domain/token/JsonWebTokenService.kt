package io.soo.springboot.core.domain.token

import io.soo.springboot.core.domain.denylist.JwtDenylistStore
import io.soo.springboot.core.enums.AuthProvider
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.stereotype.Service
import org.springframework.security.oauth2.jwt.Jwt
import java.time.Duration
import java.time.Instant


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
    private val denylistStore: JwtDenylistStore,
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
    fun refresh(oldRefreshToken: String, deviceId: String): IssuedTokens{
        val (userId, newRefresh) = refreshTokenService.rotate(oldRefreshToken, deviceId)

        val user: UserDetails = userPrincipalLoader.loadByUserId(userId)
        val auth = UsernamePasswordAuthenticationToken(user.username, null, user.authorities)

        val (access, accessExpSec) = accessTokenService.issue(auth, userId)

        return IssuedTokens(
            accessToken = access,
            accessExpiresInSec = accessExpSec,
            refreshToken = newRefresh!!.token,
            refreshExpiresInSec = newRefresh.expiresInSec,
        )
    }

    /**
     * ✅ 로그아웃 시 토큰 무효화
     * - access token: denylist 등록
     * - refresh token: 단건/디바이스/전체 revoke
     */
    fun invalidateTokens(jwt: Jwt, deviceId: String, refreshToken: String?, logoutAll: Boolean) {
        denyAccessToken(jwt)

        val userId = extractUserId(jwt)

        if (logoutAll) {
            if (userId != null) revokeAll(userId)
            return
        }

        val rt = refreshToken.orEmpty().trim()

        // 바디로 refreshToken 보내면 단건 revoke
        if (rt.isNotBlank()) {
            revoke(rt)
            return
        }

        // refreshToken 없으면 현재 디바이스 기준 revoke
        if (userId != null && deviceId.isNotBlank()) {
            revokeByDevice(userId, deviceId)
        }
    }

    private fun denyAccessToken(jwt: Jwt) {
        val jti = jwt.id
        val exp = jwt.expiresAt

        if (jti.isNullOrBlank() || exp == null) return

        val ttl = Duration.between(Instant.now(), exp).coerceAtLeast(Duration.ZERO)
        if (!ttl.isZero) {
            denylistStore.deny(jti, ttl)
        }
    }

    private fun extractUserId(jwt: Jwt): Long? {
        return (jwt.claims["uid"] as? Number)?.toLong()
    }

    fun revoke(token: String) = refreshTokenService.revoke(token)
    fun revokeAll(userId: Long) = refreshTokenService.revokeAllByUser(userId)
    fun revokeByDevice(userId: Long, deviceId: String) = refreshTokenService.revokeByDevice(userId, deviceId)
}
