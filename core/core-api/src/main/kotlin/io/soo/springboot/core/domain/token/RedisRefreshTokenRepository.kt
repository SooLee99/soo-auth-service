package io.soo.springboot.core.domain.token

import com.fasterxml.jackson.databind.ObjectMapper
import io.soo.springboot.storage.db.core.RefreshToken
import io.soo.springboot.storage.db.core.RefreshTokenRepository
import org.springframework.context.annotation.Profile
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Repository
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlin.collections.plusAssign
import kotlin.ranges.coerceAtLeast

@Repository
@Profile("local-dev", "dev", "staging", "live")
class RedisRefreshTokenRepository(
    private val redisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper,
    private val clock: Clock = Clock.systemUTC(),
) : RefreshTokenRepository {

    companion object {
        private const val TOKEN_KEY_PREFIX = "auth:rt:token:"
        private const val USER_INDEX_PREFIX = "auth:rt:user:"
        private const val USER_DEVICE_INDEX_PREFIX = "auth:rt:user-device:"
    }

    override fun save(token: RefreshToken): RefreshToken {
        val now = Instant.now(clock)
        val ttl = durationToExpiry(token.expiresAt, now)
        val tokenKey = tokenKey(token.tokenHash)
        val payload = objectMapper.writeValueAsString(token)

        redisTemplate.opsForValue().set(tokenKey, payload, ttl)

        val userIndex = userIndexKey(token.userId)
        val userDeviceIndex = userDeviceIndexKey(token.userId, token.deviceId)

        redisTemplate.opsForSet().add(userIndex, token.tokenHash)
        redisTemplate.opsForSet().add(userDeviceIndex, token.tokenHash)

        val expireAtSec = ttl.seconds.coerceAtLeast(1)
        redisTemplate.expire(userIndex, expireAtSec, TimeUnit.SECONDS)
        redisTemplate.expire(userDeviceIndex, expireAtSec, TimeUnit.SECONDS)

        return token
    }

    override fun findByTokenHash(hash: String): RefreshToken? {
        if (hash.isBlank()) return null
        return getToken(hash)
    }

    override fun findForUpdateByHash(hash: String): RefreshToken? {
        // Redis에서는 DB row lock과 동일한 lock semantics를 제공하지 않으므로 동일 조회로 처리
        return findByTokenHash(hash)
    }

    override fun findActiveByUserIdAndDeviceId(userId: Long, deviceId: String, now: Instant): List<RefreshToken> {
        if (deviceId.isBlank()) return emptyList()
        val indexKey = userDeviceIndexKey(userId, deviceId)
        return loadFromIndex(indexKey, now)
    }

    override fun findActiveByUserId(userId: Long, now: Instant): List<RefreshToken> {
        return loadFromIndex(userIndexKey(userId), now)
    }

    override fun revokeAllActiveByUserId(userId: Long, now: Instant): Int {
        val activeTokens = findActiveByUserId(userId, now)
        if (activeTokens.isEmpty()) return 0

        activeTokens.forEach { token ->
            save(
                token.copy(
                    revokedAt = now,
                    lastAccessedAt = now,
                )
            )
        }
        return activeTokens.size
    }

    override fun revokeAllActiveByUserIdAndDeviceId(userId: Long, deviceId: String, now: Instant): Int {
        val activeTokens = findActiveByUserIdAndDeviceId(userId, deviceId, now)
        if (activeTokens.isEmpty()) return 0

        activeTokens.forEach { token ->
            save(
                token.copy(
                    revokedAt = now,
                    lastAccessedAt = now,
                )
            )
        }
        return activeTokens.size
    }

    override fun deleteExpired(before: Instant): Int {
        // Redis TTL 자동 만료를 사용하므로 별도 삭제 작업은 필요 없음
        return 0
    }

    private fun loadFromIndex(indexKey: String, now: Instant): List<RefreshToken> {
        val hashes = redisTemplate.opsForSet().members(indexKey).orEmpty()
        if (hashes.isEmpty()) return emptyList()

        val tokens = mutableListOf<RefreshToken>()
        hashes.forEach { hash ->
            val token = getToken(hash)
            if (token == null) {
                redisTemplate.opsForSet().remove(indexKey, hash)
                return@forEach
            }

            if (token.revokedAt == null && token.expiresAt.isAfter(now)) {
                tokens += token
            }
        }
        return tokens
    }

    private fun getToken(hash: String): RefreshToken? {
        val raw = redisTemplate.opsForValue().get(tokenKey(hash)) ?: return null
        val token = objectMapper.readValue(raw, RefreshToken::class.java)
        if (!token.expiresAt.isAfter(Instant.now(clock))) {
            redisTemplate.delete(tokenKey(hash))
            return null
        }
        return token
    }

    private fun tokenKey(hash: String): String = "$TOKEN_KEY_PREFIX$hash"
    private fun userIndexKey(userId: Long): String = "$USER_INDEX_PREFIX$userId"
    private fun userDeviceIndexKey(userId: Long, deviceId: String): String = "$USER_DEVICE_INDEX_PREFIX$userId:$deviceId"

    private fun durationToExpiry(expiresAt: Instant, now: Instant): Duration {
        val ttl = Duration.between(now, expiresAt)
        if (ttl.isNegative || ttl.isZero) return Duration.ofSeconds(1)
        return ttl
    }
}
