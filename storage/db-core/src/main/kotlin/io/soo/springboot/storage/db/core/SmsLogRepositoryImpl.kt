package io.soo.springboot.storage.db.core

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class SmsLogRepositoryImpl(
    private val jpaRepository: SmsLogJpaRepository,
) : SmsLogRepository {

    override fun save(
        smsTo: String,
        smsFrom: String,
        smsText: String,
        ok: Boolean,
        provider: String,
        code: String?,
        msg: String?,
    ): SmsLog {
        val entity = SmsLogEntity(
            smsTo = smsTo,
            smsFrom = smsFrom,
            smsText = smsText,
            ok = ok,
            provider = provider,
            code = code,
            msg = msg,
        )
        return SmsLog.from(jpaRepository.save(entity))
    }

    override fun find(pageable: Pageable): Page<SmsLog> {
        return jpaRepository.findAll(pageable).map { SmsLog.from(it) }
    }

    override fun findByDateRange(startDate: LocalDateTime, endDate: LocalDateTime, pageable: Pageable): Page<SmsLog> {
        return jpaRepository.findAllByCreatedAtBetween(startDate, endDate, pageable).map { SmsLog.from(it) }
    }

    override fun count(): Long {
        return jpaRepository.count()
    }

    override fun countOk(): Long {
        return jpaRepository.countByOkTrue()
    }

    override fun countFail(): Long {
        return jpaRepository.countByOkFalse()
    }

    override fun countByDateRange(startDate: LocalDateTime, endDate: LocalDateTime): Long {
        return jpaRepository.countByCreatedAtBetween(startDate, endDate)
    }

    override fun countOkByDateRange(startDate: LocalDateTime, endDate: LocalDateTime): Long {
        return jpaRepository.countByOkTrueAndCreatedAtBetween(startDate, endDate)
    }

    override fun countFailByDateRange(startDate: LocalDateTime, endDate: LocalDateTime): Long {
        return jpaRepository.countByOkFalseAndCreatedAtBetween(startDate, endDate)
    }
}
