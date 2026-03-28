package io.soo.springboot.core.domain

import io.soo.springboot.core.domain.local.phone.sms.SmsSend
import io.soo.springboot.storage.db.core.SmsLog
import io.soo.springboot.storage.db.core.SmsLogRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class SmsAdmin(
    private val smsLogRepository: SmsLogRepository,
    private val smsSend: SmsSend,
) {
    fun ok(
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

    fun fail(
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

    fun logs(
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

    fun stat(startDate: LocalDateTime?, endDate: LocalDateTime?): SmsStat {
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

    fun send(to: String, text: String): SmsLog {
        val smsTo = normalizePhone(to)
        val smsText = text.trim()

        try {
            smsSend.send(smsTo, smsText)
            return ok(
                smsTo = smsTo,
                smsFrom = smsSend.from(),
                smsText = smsText,
                provider = smsSend.provider(),
            )
        } catch (e: Exception) {
            val msg = e.message ?: "unknown"
            fail(
                smsTo = smsTo,
                smsFrom = smsSend.from(),
                smsText = smsText,
                provider = smsSend.provider(),
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
