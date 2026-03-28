package io.soo.springboot.storage.db.core

import org.springframework.stereotype.Repository
import org.springframework.context.annotation.Profile
import java.time.Instant

@Repository
@Profile("local", "test")
class RefreshTokenRepositoryImpl(
    private val jpaRepository: RefreshTokenJpaRepository
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

    override fun findActiveByUserIdAndDeviceId(userId: Long, deviceId: String, serviceId: Long?, now: Instant): List<RefreshToken> {
        return jpaRepository.findActiveByUserIdAndDeviceId(userId, deviceId, serviceId, now)
            .map { it.toModel() }
    }

    override fun findActiveByUserId(userId: Long, serviceId: Long?, now: Instant): List<RefreshToken> {
        return jpaRepository.findActiveByUserId(userId, serviceId, now)
            .map { it.toModel() }
    }

    override fun revokeAllActiveByUserId(userId: Long, serviceId: Long?, now: Instant): Int {
        return jpaRepository.revokeAllActiveByUserId(userId, serviceId, now)
    }

    override fun revokeAllActiveByUserIdAndDeviceId(userId: Long, deviceId: String, serviceId: Long?, now: Instant): Int {
        return jpaRepository.revokeAllActiveByUserIdAndDeviceId(userId, deviceId, serviceId, now)
    }

    override fun deleteExpired(before: Instant): Int {
        return jpaRepository.deleteExpired(before)
    }

    private fun RefreshToken.toEntity(): RefreshTokenEntity {
        return RefreshTokenEntity(
            userId = userId,
            serviceId = serviceId,
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
            serviceId = serviceId,
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
