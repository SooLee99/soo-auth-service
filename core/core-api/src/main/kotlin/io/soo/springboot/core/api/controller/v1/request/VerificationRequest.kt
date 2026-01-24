package io.soo.springboot.core.api.controller.v1.request

import io.soo.springboot.core.enums.VerificationChannel

data class VerificationCodeRequest (
    val channel: VerificationChannel,       // 인증 타입
    val identifier:String                   // 이메일 주소 & 전화번호
)

data class VerificationCodeConfirmRequest(
    val channel: VerificationChannel,
    val identifier: String,
    val code: String,
)
