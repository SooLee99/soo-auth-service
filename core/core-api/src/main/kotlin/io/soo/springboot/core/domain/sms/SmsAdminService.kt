package io.soo.springboot.core.domain.sms

import io.soo.springboot.core.domain.sms.SmsSender
import io.soo.springboot.storage.db.core.sms.SmsLog
import io.soo.springboot.storage.db.core.sms.SmsLogRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class SmsAdminService(
    private val smsLogRepository: SmsLogRepository,
    private val smsSender: SmsSender,
) {
    fun recordSuccessLog(
        smsTo: String,
        smsFrom: String,
        smsText: String,
        provider: String,
    ): SmsLog {
        return smsLogRepository.save(
            smsTo = smsTo,
            smsFrom = smsFrom,
            smsText = smsText,
            ok = true,
            provider = provider,
            code = null,
            msg = null,
        )
    }

    fun recordFailureLog(
        smsTo: String,
        smsFrom: String,
        smsText: String,
        provider: String,
        code: String?,
        msg: String?,
    ): SmsLog {
        return smsLogRepository.save(
            smsTo = smsTo,
            smsFrom = smsFrom,
            smsText = smsText,
            ok = false,
            provider = provider,
            code = code,
            msg = msg,
        )
    }

    fun listLogs(
        pageable: Pageable,
        startDate: LocalDateTime?,
        endDate: LocalDateTime?,
    ): Page<SmsLog> {
        return if (startDate != null && endDate != null) {
            smsLogRepository.findByDateRange(startDate, endDate, pageable)
        } else {
            smsLogRepository.find(pageable)
        }
    }

    fun getStats(startDate: LocalDateTime?, endDate: LocalDateTime?): SmsStat {
        val total: Long
        val ok: Long
        val fail: Long

        if (startDate != null && endDate != null) {
            total = smsLogRepository.countByDateRange(startDate, endDate)
            ok = smsLogRepository.countOkByDateRange(startDate, endDate)
            fail = smsLogRepository.countFailByDateRange(startDate, endDate)
        } else {
            total = smsLogRepository.count()
            ok = smsLogRepository.countOk()
            fail = smsLogRepository.countFail()
        }

        val rate = if (total == 0L) 0.0 else (ok.toDouble() * 100.0) / total.toDouble()

        return SmsStat(
            total = total,
            ok = ok,
            fail = fail,
            rate = rate,
        )
    }

    fun sendSms(to: String, text: String): SmsLog {
        val smsTo = normalizePhone(to)
        val smsText = text.trim()

        try {
            smsSender.send(smsTo, smsText)
            return recordSuccessLog(
                smsTo = smsTo,
                smsFrom = smsSender.from(),
                smsText = smsText,
                provider = smsSender.provider(),
            )
        } catch (e: Exception) {
            val msg = e.message ?: "unknown"
            recordFailureLog(
                smsTo = smsTo,
                smsFrom = smsSender.from(),
                smsText = smsText,
                provider = smsSender.provider(),
                code = null,
                msg = msg,
            )
            throw e
        }
    }

    private fun normalizePhone(phoneNumber: String): String {
        val trimmed = phoneNumber.trim()
        val hasPlusPrefix = trimmed.startsWith("+")
        val digits = trimmed.filter { it.isDigit() }
        return if (hasPlusPrefix) "+$digits" else digits
    }
}
