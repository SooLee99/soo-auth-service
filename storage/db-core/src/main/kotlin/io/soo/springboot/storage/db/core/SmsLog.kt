package io.soo.springboot.storage.db.core

import java.time.LocalDateTime

data class SmsLog(
    val id: Long,
    val smsTo: String,
    val smsFrom: String,
    val smsText: String,
    val ok: Boolean,
    val provider: String,
    val code: String?,
    val msg: String?,
    val createdAt: LocalDateTime,
) {
    companion object {
        fun from(entity: SmsLogEntity): SmsLog {
            return SmsLog(
                id = entity.id,
                smsTo = entity.smsTo,
                smsFrom = entity.smsFrom,
                smsText = entity.smsText,
                ok = entity.ok,
                provider = entity.provider,
                code = entity.code,
                msg = entity.msg,
                createdAt = entity.createdAt,
            )
        }
    }
}
