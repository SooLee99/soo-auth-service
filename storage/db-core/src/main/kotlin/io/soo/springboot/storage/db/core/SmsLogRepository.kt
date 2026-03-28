package io.soo.springboot.storage.db.core

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import java.time.LocalDateTime

interface SmsLogRepository {
    fun save(
        smsTo: String,
        smsFrom: String,
        smsText: String,
        ok: Boolean,
        provider: String,
        code: String?,
        msg: String?,
    ): SmsLog

    fun find(pageable: Pageable): Page<SmsLog>

    fun findByDateRange(startDate: LocalDateTime, endDate: LocalDateTime, pageable: Pageable): Page<SmsLog>

    fun count(): Long

    fun countOk(): Long

    fun countFail(): Long

    fun countByDateRange(startDate: LocalDateTime, endDate: LocalDateTime): Long

    fun countOkByDateRange(startDate: LocalDateTime, endDate: LocalDateTime): Long

    fun countFailByDateRange(startDate: LocalDateTime, endDate: LocalDateTime): Long
}
