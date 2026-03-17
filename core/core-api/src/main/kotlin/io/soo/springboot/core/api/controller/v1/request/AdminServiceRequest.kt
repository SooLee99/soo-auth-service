package io.soo.springboot.core.api.controller.v1.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class AdminServiceCreateRequest(
    @field:NotBlank(message = "서비스 코드는 필수입니다.")
    @field:Size(max = 64, message = "서비스 코드는 최대 64자입니다.")
    @field:Pattern(
        regexp = "^[A-Za-z0-9][A-Za-z0-9_-]{1,63}$",
        message = "서비스 코드는 영문/숫자/_(언더스코어)/-(하이픈)만 허용됩니다."
    )
    val serviceCode: String,

    @field:NotBlank(message = "서비스 이름은 필수입니다.")
    @field:Size(max = 120, message = "서비스 이름은 최대 120자입니다.")
    val serviceName: String,
)

