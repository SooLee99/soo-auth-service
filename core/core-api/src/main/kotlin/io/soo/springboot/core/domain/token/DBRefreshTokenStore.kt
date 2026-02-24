package io.soo.springboot.core.domain.token

import io.soo.springboot.core.support.error.CoreException
import io.soo.springboot.core.support.error.ErrorType
import io.soo.springboot.storage.db.core.JpaRefreshTokenRepository
import io.soo.springboot.storage.db.core.RefreshTokenEntity
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant

@Service
class DBRefreshTokenStore(
    private val repo: JpaRefreshTokenRepository,
) : RefreshTokenStore {

    @Transactional
    override fun save(record: RefreshTokenRecord) {
        val now = Instant.now()
        if (record.token.isBlank()) return
        if (!record.expiresAt.isAfter(now)) return

        val entity = RefreshTokenEntity(
            tokenHash = sha256Hex(record.token),
            userId = record.userId,
            expiresAt = record.expiresAt,
            deviceId = record.deviceId,
            provider = record.provider,
        )
        repo.save(entity)
    }

    @Transactional
    override fun rotate(oldToken: String, newRecord: RefreshTokenRecord): RefreshTokenEntity {
        val now = Instant.now()

        // 입력 검증
        if (oldToken.isBlank()) {
            throw CoreException(ErrorType.REFRESH_TOKEN_REQUIRED, "old refresh token is blank")
        }
        if (newRecord.token.isBlank()) {
            throw CoreException(ErrorType.INVALID_INPUT_VALUE, "new refresh token is blank")
        }
        if (!newRecord.expiresAt.isAfter(now)) {
            throw CoreException(ErrorType.INVALID_INPUT_VALUE, "new refresh token expiresAt must be in the future")
        }

        val oldHash = sha256Hex(oldToken)
        val newHash = sha256Hex(newRecord.token)

        // row lock으로 동시 rotate 경쟁 시 1건만 성공하도록 보장
        val old = repo.findForUpdateByHash(oldHash)
            ?: throw CoreException(ErrorType.INVALID_REFRESH_TOKEN, "refresh token not found")

        // 만료/리보크는 외부적으로는 "유효하지 않음"으로 처리
        if (!old.expiresAt.isAfter(now)) {
            throw CoreException(ErrorType.EXPIRED_REFRESH_TOKEN, "refresh token is expired")
            // 보안을 위해 통일하고 싶으면:
            // throw CoreException(ErrorType.INVALID_REFRESH_TOKEN, "refresh token is expired")
        }

        if (old.revokedAt != null) {
            throw CoreException(ErrorType.REVOKED_REFRESH_TOKEN, "refresh token is revoked")
            // 보안을 위해 통일하고 싶으면:
            // throw CoreException(ErrorType.INVALID_REFRESH_TOKEN, "refresh token is revoked")
        }

        // 이미 rotate로 소모된 토큰이면 재사용 감지
        if (old.usedAt != null) {
            throw CoreException(ErrorType.REFRESH_TOKEN_REUSED, "refresh token already used")
        }

        // old 토큰 소모 처리
        old.usedAt = now
        old.replacedByHash = newHash
        repo.save(old)

        // 새 토큰 저장
        val newEntity = RefreshTokenEntity(
            tokenHash = newHash,
            userId = old.userId,
            expiresAt = newRecord.expiresAt,
            deviceId = old.deviceId,
            provider = old.provider,
        )
        repo.save(newEntity)

        return newEntity
    }

    @Transactional
    override fun revoke(token: String) {
        if (token.isBlank()) return
        val now = Instant.now()
        val hash = sha256Hex(token)
        val entity = repo.findByTokenHash(hash) ?: return

        // 이미 만료/리보크면 무시
        if (entity.revokedAt == null && entity.expiresAt.isAfter(now)) {
            entity.revokedAt = now
            repo.save(entity)
        }
    }

    @Transactional
    override fun revokeAllByUser(userId: Long) {
        val now = Instant.now()
        repo.revokeAllActiveByUser(userId, now)
    }

    @Transactional
    override fun purgeExpired(before: Instant): Long {
        return repo.deleteExpired(before).toLong()
    }

    private fun sha256Hex(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(input.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}