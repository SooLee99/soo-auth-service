package io.soo.springboot.core.domain

import org.springframework.context.annotation.Profile
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant

@Service
@Profile("local-dev", "dev", "staging", "live")
class RedisJwtDenylistStore(
    private val redisTemplate: StringRedisTemplate,
) : JwtDenylistStore {

    companion object {
        private const val KEY_PREFIX = "auth:denylist:jti:"
    }

    override fun deny(jti: String, ttl: Duration) {
        if (jti.isBlank()) return
        if (ttl.isZero || ttl.isNegative) return

        redisTemplate.opsForValue().set("$KEY_PREFIX$jti", "1", ttl)
    }

    override fun isDenied(jti: String): Boolean {
        if (jti.isBlank()) return false
        return redisTemplate.hasKey("$KEY_PREFIX$jti") == true
    }

    override fun purgeExpired(before: Instant): Long {
        // Redis TTL 기반 자동 만료를 사용하므로 별도 purge 작업은 불필요
        return 0L
    }
}
