package io.soo.springboot.core.domain.token

import io.soo.springboot.core.enums.AuthProvider
import io.soo.springboot.storage.db.core.RefreshTokenEntity
import io.soo.springboot.storage.db.core.JpaRefreshTokenRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.time.temporal.ChronoUnit

data class RefreshTokenIssued(
    val token: String,          // 클라이언트에 반환되는 원문
    val expiresInSec: Long,
)


@Service
class RefreshTokenService(
    private val repo: JpaRefreshTokenRepository,
) {
    companion object {
        private const val REFRESH_TTL_DAYS = 14L
        private const val TOKEN_BYTES = 32
        private val rng = SecureRandom()
    }

    fun refreshTtlSeconds(): Long = ChronoUnit.DAYS.duration.seconds * REFRESH_TTL_DAYS

    private fun now(): Instant = Instant.now()

    private fun sha256Hex(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun randomTokenHex(): String {
        val bytes = ByteArray(TOKEN_BYTES)
        rng.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) } // 64 hex chars
    }

    /**
     * ✅ issue: (userId, deviceId) 기준으로 기존 활성 refresh 토큰 revoke 후 새로 발급
     */
    @Transactional
    fun issue(userId: Long, deviceId: String, provider: AuthProvider): RefreshTokenIssued {
        val now = now()

        // 정책: 한 디바이스당 활성 refresh 1개
        repo.revokeAllActiveByUserIdAndDeviceId(userId, deviceId, now)

        val raw = randomTokenHex()
        val hash = sha256Hex(raw)

        val expiresAt = now.plus(REFRESH_TTL_DAYS, ChronoUnit.DAYS)

        repo.save(
            RefreshTokenEntity(
                userId = userId,
                tokenHash = hash,
                expiresAt = expiresAt,
                deviceId = deviceId,
                provider = provider,
                lastAccessedAt = now,
            )
        )

        return RefreshTokenIssued(token = raw, expiresInSec = ChronoUnit.SECONDS.between(now, expiresAt))
    }

    /**
     * ✅ rotate: old refresh 1회용, 새 refresh 발급
     * - 토큰 재사용 방지: usedAt 체크 + replacedByHash 기록
     * - deviceId 일치 검증(요청 헤더 기반)
     */
    @Transactional
    fun rotate(oldRefreshTokenRaw: String, deviceId: String): Pair<RotateResult, RefreshTokenIssued?> {
        val now = now()
        if (oldRefreshTokenRaw.isBlank()) return RotateResult.NotFoundOrExpired to null

        val oldHash = sha256Hex(oldRefreshTokenRaw)
        val old = repo.findByTokenHash(oldHash) ?: return RotateResult.NotFoundOrExpired to null

        // 만료/폐기 체크
        if (old.revokedAt != null || old.expiresAt.isBefore(now)) return RotateResult.NotFoundOrExpired to null

        // 디바이스 검증
        if (old.deviceId != deviceId) return RotateResult.DeviceMismatch to null

        // 재사용 방지: 이미 usedAt이 찍혔으면 재사용
        if (old.usedAt != null) return RotateResult.AlreadyUsed to null

        // 새 refresh 생성
        val newRaw = randomTokenHex()
        val newHash = sha256Hex(newRaw)
        val newExpiresAt = now.plus(REFRESH_TTL_DAYS, ChronoUnit.DAYS)

        // old 토큰을 used 처리 + replacedByHash 기록
        old.usedAt = now
        old.replacedByHash = newHash
        old.lastAccessedAt = now
        repo.save(old)

        // 새 토큰 저장 (같은 user/device/provider 유지)
        repo.save(
            RefreshTokenEntity(
                userId = old.userId,
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

        return RotateResult.Success(old.userId) to issued
    }

    /**
     * ✅ 단건 revoke (클라이언트 원문 토큰 입력)
     */
    @Transactional
    fun revoke(rawToken: String) {
        if (rawToken.isBlank()) return
        val now = now()

        val hash = sha256Hex(rawToken)
        val e = repo.findByTokenHash(hash) ?: return

        if (e.revokedAt == null) {
            e.revokedAt = now
            e.lastAccessedAt = now
            repo.save(e)
        }
    }

    /**
     * ✅ 유저 전체 revoke
     */
    @Transactional
    fun revokeAllByUser(userId: Long) {
        repo.revokeAllActiveByUserId(userId, now())
    }

    /**
     * ✅ 특정 디바이스 revoke
     */
    @Transactional
    fun revokeByDevice(userId: Long, deviceId: String) {
        if (deviceId.isBlank()) return
        repo.revokeAllActiveByUserIdAndDeviceId(userId, deviceId, now())
    }
}
