package io.soo.springboot.storage.db.core

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
@Profile("local", "test")
class RefreshTokenRepositoryImpl(
    private val jpaRepository: RefreshTokenJpaRepository,
) : RefreshTokenRepository {

    override fun save(token: RefreshToken): RefreshToken {
        val entity = token.toEntity()
        val saved = jpaRepository.save(entity)
        return saved.toModel()
    }

    override fun findByTokenHash(hash: String): RefreshToken? {
        return jpaRepository.findByTokenHash(hash)?.toModel()
    }

    override fun findForUpdateByHash(hash: String): RefreshToken? {
        return jpaRepository.findForUpdateByHash(hash)?.toModel()
    }

    override fun findActiveByUserIdAndDeviceId(userId: Long, deviceId: String, now: Instant): List<RefreshToken> {
        return jpaRepository.findActiveByUserIdAndDeviceId(userId, deviceId, now)
            .map { it.toModel() }
    }

    override fun findActiveByUserId(userId: Long, now: Instant): List<RefreshToken> {
        return jpaRepository.findActiveByUserId(userId, now)
            .map { it.toModel() }
    }

    override fun revokeAllActiveByUserId(userId: Long, now: Instant): Int {
        return jpaRepository.revokeAllActiveByUserId(userId, now)
    }

    override fun revokeAllActiveByUserIdAndDeviceId(userId: Long, deviceId: String, now: Instant): Int {
        return jpaRepository.revokeAllActiveByUserIdAndDeviceId(userId, deviceId, now)
    }

    override fun deleteExpired(before: Instant): Int {
        return jpaRepository.deleteExpired(before)
    }

    private fun RefreshToken.toEntity(): RefreshTokenEntity {
        return RefreshTokenEntity(
            userId = userId,
            tokenHash = tokenHash,
            deviceId = deviceId,
            provider = provider,
            expiresAt = expiresAt,
            lastAccessedAt = lastAccessedAt,
        ).also { e ->
            if (id > 0) e.id = id
            e.usedAt = usedAt
            e.revokedAt = revokedAt
            e.replacedByHash = replacedByHash
        }
    }

    private fun RefreshTokenEntity.toModel(): RefreshToken {
        return RefreshToken(
            id = id ?: 0L,
            userId = userId,
            tokenHash = tokenHash,
            deviceId = deviceId,
            provider = provider,
            expiresAt = expiresAt,
            createdAt = createdAt,
            usedAt = usedAt,
            revokedAt = revokedAt,
            replacedByHash = replacedByHash,
            lastAccessedAt = lastAccessedAt,
        )
    }
}
