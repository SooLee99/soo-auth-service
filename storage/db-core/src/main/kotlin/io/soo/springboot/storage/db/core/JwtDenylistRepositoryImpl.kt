package io.soo.springboot.storage.db.core


import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
class JwtDenylistRepositoryImpl(
    private val jpaRepository: JpaJwtDenylistRepository
) : JwtDenylistRepository {

    override fun save(jti: String, expiresAt: Instant) {
        jpaRepository.save(JwtDenylistEntity(jti = jti, expiresAt = expiresAt))
    }

    override fun existsByJtiAndExpiresAtAfter(jti: String, now: Instant): Boolean {
        return jpaRepository.existsByJtiAndExpiresAtAfter(jti, now)
    }

    override fun findByJti(jti: String): JwtDenylistEntry? {
        return jpaRepository.findByJti(jti)?.let {
            JwtDenylistEntry(jti = it.jti, expiresAt = it.expiresAt)
        }
    }

    override fun deleteExpired(before: Instant): Int {
        return jpaRepository.deleteExpired(before)
    }
}