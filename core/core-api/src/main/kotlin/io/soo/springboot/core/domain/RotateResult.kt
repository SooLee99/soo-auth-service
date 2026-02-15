package io.soo.springboot.core.domain

sealed class RotateResult {
    data class Success(val userId: Long) : RotateResult()
    data object NotFoundOrExpired : RotateResult()
    data object AlreadyUsed : RotateResult()
}
