package io.soo.springboot.core.api.controller.v1.response

import io.soo.springboot.core.domain.SmsStat

data class AdminSmsStatRes(
    val total: Long,
    val ok: Long,
    val fail: Long,
    val rate: Double,
) {
    companion object {
        fun from(stat: SmsStat): AdminSmsStatRes {
            return AdminSmsStatRes(
                total = stat.total,
                ok = stat.ok,
                fail = stat.fail,
                rate = stat.rate,
            )
        }
    }
}
