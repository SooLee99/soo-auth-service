package io.soo.springboot.core.api.controller.v1.request

import jakarta.validation.constraints.NotNull

data class AdminAuthMethodUpdateRequest(
    @field:NotNull(message = "enabled 값은 필수입니다.")
    val enabled: Boolean?,
)
