package io.soo.springboot.core.domain.token

import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

import io.soo.springboot.core.api.config.AppJwtProperties


data class IssuedRefreshToken(
    val token: String,
    val expiresInSec: Long,
    val expiresAt: Instant,
)

@Service
class RefreshTokenService(
    private val props: AppJwtProperties,
    private val refreshStore: RefreshTokenStore,
) {

    fun issue(userId: Long): IssuedRefreshToken {
        val now = Instant.now()
        val expiresIn = props.refreshTtlSeconds
        val expiresAt = now.plusSeconds(expiresIn)
        val token = newOpaqueToken()

        refreshStore.save(RefreshTokenRecord(token, userId, expiresAt))
        return IssuedRefreshToken(token, expiresIn, expiresAt)
    }

    /**
     * oldRefreshToken을 소모(used 처리)하고 새 refresh 발급 + 저장
     * - 성공 시 RotateResult.Success(userId) + 새 refresh 반환
     */
    fun rotate(oldRefreshToken: String): Pair<RotateResult, IssuedRefreshToken?> {
        val now = Instant.now()
        val expiresIn = props.refreshTtlSeconds
        val expiresAt = now.plusSeconds(expiresIn)
        val newToken = newOpaqueToken()

        val rotate = refreshStore.rotate(
            oldToken = oldRefreshToken,
            newRecord = RefreshTokenRecord(
                token = newToken,
                userId = -1, // store가 oldToken으로 userId 찾아서 Success(userId)로 반환
                expiresAt = expiresAt
            )
        )

        return when (rotate) {
            is RotateResult.Success -> rotate to IssuedRefreshToken(newToken, expiresIn, expiresAt)
            RotateResult.NotFoundOrExpired -> rotate to null
            RotateResult.AlreadyUsed -> rotate to null
        }
    }

    fun revoke(token: String) = refreshStore.revoke(token)

    fun revokeAllByUser(userId: Long) = refreshStore.revokeAllByUser(userId)

    private fun newOpaqueToken(): String =
        UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "")
}
