package io.soo.springboot.core.domain.token

sealed class RotateResult {
    data class Success(val userId: Long) : RotateResult()
    data object NotFoundOrExpired : RotateResult()
    data object AlreadyUsed : RotateResult()
}
