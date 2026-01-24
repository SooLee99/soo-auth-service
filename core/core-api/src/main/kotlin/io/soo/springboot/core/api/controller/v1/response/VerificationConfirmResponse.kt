package io.soo.springboot.core.api.controller.v1.response

import io.soo.springboot.core.enums.VerificationStatus

data class VerificationCodeResponse(
    val status: VerificationStatus,
    val retryAfterSeconds: Long? = null,
)

data class VerificationCodeConfirmResponse(
    val verified: Boolean,
    val status: VerificationStatus,
    val retryAfterSeconds: Long? = null,
    val remainingAttempts: Int? = null,
)
