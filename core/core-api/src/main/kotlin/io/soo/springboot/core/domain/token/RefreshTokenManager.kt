package io.soo.springboot.core.domain.token

import io.soo.springboot.core.enums.AuthProvider
import io.soo.springboot.storage.db.core.RefreshToken
import io.soo.springboot.storage.db.core.RefreshTokenRepository
import io.soo.springboot.core.support.error.CoreException
import io.soo.springboot.core.support.error.ErrorType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.time.temporal.ChronoUnit

data class RefreshTokenIssued(
    val token: String,
    val expiresInSec: Long,
)

data class RefreshTokenRotateResult(
    val userId: Long,
    val serviceId: Long?,
    val issued: RefreshTokenIssued,
)

@Service
class RefreshTokenManager(
    private val repository: RefreshTokenRepository,
) {
    companion object {
        private const val REFRESH_TTL_DAYS = 14L
        private const val TOKEN_BYTES = 32
        private val rng = SecureRandom()
    }

    private fun now(): Instant = Instant.now()

    private fun sha256Hex(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun randomTokenHex(): String {
        val bytes = ByteArray(TOKEN_BYTES)
        rng.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    @Transactional
    fun issue(userId: Long, deviceId: String, provider: AuthProvider, serviceId: Long? = null): RefreshTokenIssued {
        val now = now()
        repository.revokeAllActiveByUserIdAndDeviceId(userId, deviceId, serviceId, now)

        val raw = randomTokenHex()
        val hash = sha256Hex(raw)
        val expiresAt = now.plus(REFRESH_TTL_DAYS, ChronoUnit.DAYS)

        repository.save(
            RefreshToken(
                userId = userId,
                serviceId = serviceId,
                tokenHash = hash,
                expiresAt = expiresAt,
                deviceId = deviceId,
                provider = provider,
                lastAccessedAt = now,
            )
        )

        return RefreshTokenIssued(token = raw, expiresInSec = ChronoUnit.SECONDS.between(now, expiresAt))
    }

    @Transactional
    fun rotate(oldRefreshTokenRaw: String, deviceId: String, expectedServiceId: Long? = null): RefreshTokenRotateResult {
        val now = now()

        if (oldRefreshTokenRaw.isBlank()) {
            throw CoreException(ErrorType.REFRESH_TOKEN_REQUIRED, "refresh token is blank")
        }

        val oldHash = sha256Hex(oldRefreshTokenRaw)
        val old = repository.findByTokenHash(oldHash)
            ?: throw CoreException(ErrorType.INVALID_REFRESH_TOKEN, "refresh token not found")

        if (old.revokedAt != null) {
            throw CoreException(ErrorType.REVOKED_REFRESH_TOKEN, "refresh token is revoked")
        }

        if (old.expiresAt.isBefore(now)) {
            throw CoreException(ErrorType.EXPIRED_REFRESH_TOKEN, "refresh token is expired")
        }

        if (old.deviceId != deviceId) {
            throw CoreException(ErrorType.REFRESH_TOKEN_DEVICE_MISMATCH, "device mismatch")
        }

        if (old.serviceId != expectedServiceId) {
            throw CoreException(ErrorType.INVALID_REFRESH_TOKEN, "service scope mismatch")
        }

        if (old.usedAt != null) {
            throw CoreException(ErrorType.REFRESH_TOKEN_REUSED, "refresh token already used")
        }

        val newRaw = randomTokenHex()
        val newHash = sha256Hex(newRaw)
        val newExpiresAt = now.plus(REFRESH_TTL_DAYS, ChronoUnit.DAYS)

        // old 토큰 used 처리
        repository.save(
            old.copy(
                usedAt = now,
                replacedByHash = newHash,
                lastAccessedAt = now,
            )
        )

        // 새 토큰 저장
        repository.save(
            RefreshToken(
                userId = old.userId,
                serviceId = old.serviceId,
                tokenHash = newHash,
                expiresAt = newExpiresAt,
                deviceId = old.deviceId,
                provider = old.provider,
                lastAccessedAt = now,
            )
        )

        val issued = RefreshTokenIssued(
            token = newRaw,
            expiresInSec = ChronoUnit.SECONDS.between(now, newExpiresAt),
        )

        return RefreshTokenRotateResult(
            userId = old.userId,
            serviceId = old.serviceId,
            issued = issued,
        )
    }

    @Transactional
    fun revoke(rawToken: String, expectedServiceId: Long? = null) {
        if (rawToken.isBlank()) return
        val now = now()

        val hash = sha256Hex(rawToken)
        val token = repository.findByTokenHash(hash) ?: return
        if (token.serviceId != expectedServiceId) {
            throw CoreException(ErrorType.INVALID_REFRESH_TOKEN, "service scope mismatch")
        }

        if (token.revokedAt == null) {
            repository.save(
                token.copy(
                    revokedAt = now,
                    lastAccessedAt = now,
                )
            )
        }
    }

    @Transactional
    fun revokeAllByUser(userId: Long) {
        repository.revokeAllActiveByUserId(userId, null, now())
    }

    @Transactional
    fun revokeAllByUserAndService(userId: Long, serviceId: Long) {
        repository.revokeAllActiveByUserId(userId, serviceId, now())
    }

    @Transactional
    fun revokeByDevice(userId: Long, deviceId: String, serviceId: Long? = null) {
        if (deviceId.isBlank()) return
        repository.revokeAllActiveByUserIdAndDeviceId(userId, deviceId, serviceId, now())
    }
}
