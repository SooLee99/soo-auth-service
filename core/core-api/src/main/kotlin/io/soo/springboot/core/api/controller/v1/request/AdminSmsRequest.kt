package io.soo.springboot.core.api.controller.v1.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class AdminSmsSendReq(
    @field:NotBlank(message = "수신 번호는 필수입니다.")
    @field:Size(max = 20, message = "수신 번호가 너무 깁니다.")
    @field:Pattern(
        regexp = """^\+?\d[\d\s-]{7,18}\d$""",
        message = "수신 번호 형식이 올바르지 않습니다.",
    )
    val to: String,

    @field:NotBlank(message = "메시지는 필수입니다.")
    @field:Size(max = 1000, message = "메시지는 최대 1000자까지 입력할 수 있습니다.")
    val text: String,
)
