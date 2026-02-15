package io.soo.springboot.storage.db.core

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface JwtDenylistJpaRepository : JpaRepository<JwtDenylistEntity, Long> {

    fun existsByJtiAndExpiresAtAfter(jti: String, now: Instant): Boolean

    fun findByJti(jti: String): JwtDenylistEntity?

    @Modifying
    @Query("delete from JwtDenylistEntity e where e.expiresAt < :before")
    fun deleteExpired(@Param("before") before: Instant): Int
}
