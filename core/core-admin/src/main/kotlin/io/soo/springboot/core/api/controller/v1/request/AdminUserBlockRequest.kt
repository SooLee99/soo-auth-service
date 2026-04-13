package io.soo.springboot.core.api.controller.v1.request

import jakarta.validation.constraints.Size

data class AdminUserBlockRequest(
    @field:Size(max = 500, message = "차단 사유는 500자 이하여야 합니다.")
    val reason: String? = null,
)
