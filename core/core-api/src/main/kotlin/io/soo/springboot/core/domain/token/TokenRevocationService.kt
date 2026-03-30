package io.soo.springboot.core.domain.token

import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant

@Service
class TokenRevocationService(
    private val refreshTokenManager: RefreshTokenManager,
    private val denylistStore: JwtDenylistStore,
) {
    fun revokeOnLogout(jwt: Jwt, deviceId: String, refreshToken: String?, logoutAll: Boolean) {
        val exp = jwt.expiresAt
        if (jwt.id.isNullOrBlank() || exp == null) return

        val ttl = Duration.between(Instant.now(), exp).coerceAtLeast(Duration.ZERO)
        if (!ttl.isZero) {
            denylistStore.deny(jwt.id, ttl)
        }

        val userId = extractUserId(jwt)
        if (logoutAll) {
            if (userId != null) revokeAllByUserId(userId)
            return
        }

        val rt = refreshToken.orEmpty().trim()
        if (rt.isNotBlank()) {
            revoke(rt)
            return
        }

        if (userId != null && deviceId.isNotBlank()) {
            revokeByUserIdAndDeviceId(userId, deviceId)
        }
    }

    fun revoke(token: String) = refreshTokenManager.revoke(token)
    fun revokeAllByUserId(userId: Long) = refreshTokenManager.revokeAllByUserId(userId)
    fun revokeByUserIdAndDeviceId(userId: Long, deviceId: String) = refreshTokenManager.revokeByUserIdAndDeviceId(userId, deviceId)

    private fun extractUserId(jwt: Jwt): Long? {
        return (jwt.claims["uid"] as? Number)?.toLong()
    }
}

