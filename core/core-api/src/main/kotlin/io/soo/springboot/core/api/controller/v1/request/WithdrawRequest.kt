package io.soo.springboot.core.api.controller.v1.request

import jakarta.validation.constraints.Size

data class WithdrawRequest(
    val reason: String? = "USER_WITHDRAWN",
    @field:Size(min = 6, max = 72)
    val password: String? = null, // 로컬 계정이면 확인용
)
