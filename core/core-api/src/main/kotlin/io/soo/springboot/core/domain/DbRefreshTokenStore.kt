package io.soo.springboot.core.domain

import io.soo.springboot.storage.db.core.RefreshTokenEntity
import io.soo.springboot.storage.db.core.RefreshTokenJpaRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant

@Service
class DbRefreshTokenStore(
    private val repo: RefreshTokenJpaRepository,
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
        )
        repo.save(entity)
    }

    @Transactional
    override fun rotate(oldToken: String, newRecord: RefreshTokenRecord): RotateResult {
        val now = Instant.now()
        if (oldToken.isBlank()) return RotateResult.NotFoundOrExpired
        if (newRecord.token.isBlank()) return RotateResult.NotFoundOrExpired
        if (!newRecord.expiresAt.isAfter(now)) return RotateResult.NotFoundOrExpired

        val oldHash = sha256Hex(oldToken)
        val newHash = sha256Hex(newRecord.token)

        // row lock으로 동시 rotate 경쟁 시 1건만 성공하도록 보장
        val old = repo.findForUpdateByHash(oldHash) ?: return RotateResult.NotFoundOrExpired

        // 만료/리보크는 "유효하지 않음" 취급
        if (!old.expiresAt.isAfter(now)) return RotateResult.NotFoundOrExpired
        if (old.revokedAt != null) return RotateResult.NotFoundOrExpired

        // 이미 rotate로 소모된 토큰이면 재사용 감지
        if (old.usedAt != null) return RotateResult.AlreadyUsed

        // old 토큰 소모 처리
        old.usedAt = now
        old.replacedByHash = newHash
        repo.save(old)

        // 새 토큰 저장(유저는 old에서 가져옴)
        val newEntity = RefreshTokenEntity(
            tokenHash = newHash,
            userId = old.userId,
            expiresAt = newRecord.expiresAt,
        )
        repo.save(newEntity)

        return RotateResult.Success(old.userId)
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
