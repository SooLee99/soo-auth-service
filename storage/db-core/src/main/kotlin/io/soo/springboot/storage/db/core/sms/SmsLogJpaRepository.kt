package io.soo.springboot.storage.db.core.sms

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDateTime

interface SmsLogJpaRepository : JpaRepository<SmsLogEntity, Long> {
    fun findAllByCreatedAtBetween(
        startDate: LocalDateTime,
        endDate: LocalDateTime,
        pageable: Pageable,
    ): Page<SmsLogEntity>

    fun countByOkTrue(): Long

    fun countByOkFalse(): Long

    fun countByCreatedAtBetween(startDate: LocalDateTime, endDate: LocalDateTime): Long

    fun countByOkTrueAndCreatedAtBetween(startDate: LocalDateTime, endDate: LocalDateTime): Long

    fun countByOkFalseAndCreatedAtBetween(startDate: LocalDateTime, endDate: LocalDateTime): Long
}
