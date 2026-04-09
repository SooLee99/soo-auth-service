package io.soo.springboot.storage.db.core.login

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDateTime

interface LoginHistoryJpaRepository : JpaRepository<LoginHistoryEntity, Long> {
    fun findByUserId(userId: Long, pageable: Pageable): Page<LoginHistoryEntity>
    fun findByUserIdAndCreatedAtBetween(
        userId: Long,
        startDate: LocalDateTime,
        endDate: LocalDateTime,
        pageable: Pageable,
    ): Page<LoginHistoryEntity>
}
