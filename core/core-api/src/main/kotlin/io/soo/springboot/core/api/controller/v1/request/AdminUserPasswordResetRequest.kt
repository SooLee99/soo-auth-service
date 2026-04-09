package io.soo.springboot.core.api.controller.v1.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class AdminUserPasswordResetRequest(
    @field:NotBlank(message = "새 비밀번호는 필수입니다.")
    @field:Size(min = 8, max = 72, message = "비밀번호는 8자 이상 72자 이하여야 합니다.")
    @field:Pattern(
        regexp = """^(?=.{8,72}$)(?=.*[A-Za-z])(?=.*\d)(?=.*[^A-Za-z0-9]).*$""",
        message = "비밀번호는 영문, 숫자, 특수문자를 포함해야 합니다.",
    )
    val newPassword: String,
)
