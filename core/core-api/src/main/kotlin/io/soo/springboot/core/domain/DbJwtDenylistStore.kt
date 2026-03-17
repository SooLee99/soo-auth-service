package io.soo.springboot.core.domain

import org.springframework.dao.DataIntegrityViolationException
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration
import java.time.Instant

import io.soo.springboot.storage.db.core.JwtDenylistRepository


@Service
@Profile("local", "test")
class DbJwtDenylistStore(
    private val repo: JwtDenylistRepository,
    private val clock: Clock = Clock.systemUTC(),
) : JwtDenylistStore {

    @Transactional
    override fun deny(jti: String, ttl: Duration) {
        if (jti.isBlank()) return

        val now = Instant.now(clock)
        if (ttl.isZero || ttl.isNegative) return

        // expiresAt 계산 (오버플로 방지용 try)
        val expiresAt = try {
            now.plus(ttl)
        } catch (e: Exception) {
            // ttl이 비정상적으로 커서 overflow 나는 경우 방어
            Instant.MAX
        }

        // expiresAt이 now와 같으면 의미 없음
        if (!expiresAt.isAfter(now)) return

        // 기본: insert 시도
        try {
            repo.save(jti = jti, expiresAt = expiresAt)
            return
        } catch (_: DataIntegrityViolationException) {
            // 유니크 충돌: 이미 존재 -> expiresAt을 더 큰 값으로 갱신(짧아지지 않게)
        }

        val existing = repo.findByJti(jti)
        if (existing == null) {
            // 극히 드물게 레이스로 find가 null이면 재시도
            repo.save(jti = jti, expiresAt = expiresAt)
            return
        }

        if (existing.expiresAt.isBefore(expiresAt)) {
            repo.save(jti, expiresAt)
        }
    }

    @Transactional(readOnly = true)
    override fun isDenied(jti: String): Boolean {
        if (jti.isBlank()) return false
        val now = Instant.now(clock)
        return repo.existsByJtiAndExpiresAtAfter(jti, now)
    }

    @Transactional
    override fun purgeExpired(before: Instant): Long {
        return repo.deleteExpired(before).toLong()
    }
}
