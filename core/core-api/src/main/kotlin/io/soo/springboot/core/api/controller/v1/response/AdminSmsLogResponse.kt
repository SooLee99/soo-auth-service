package io.soo.springboot.core.api.controller.v1.response

import io.soo.springboot.storage.db.core.sms.SmsLog
import java.time.LocalDateTime

data class AdminSmsLogResponse(
    val id: Long,
    val to: String,
    val from: String,
    val text: String,
    val ok: Boolean,
    val provider: String,
    val code: String?,
    val msg: String?,
    val at: LocalDateTime,
) {
    companion object {
        fun from(log: SmsLog): AdminSmsLogResponse {
            return AdminSmsLogResponse(
                id = log.id,
                to = log.smsTo,
                from = log.smsFrom,
                text = log.smsText,
                ok = log.ok,
                provider = log.provider,
                code = log.code,
                msg = log.msg,
                at = log.createdAt,
            )
        }
    }
}
