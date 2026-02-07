package io.soo.springboot.core.domain

import io.soo.springboot.core.enums.Gender

data class LocalSignUpCommand(
    val email: String,
    val password: String,
    val phoneNumber: String,
    val gender: Gender,
    val locale: String = "ko-KR",
    val nickname: String? = null,
    val name: String? = null,
    val profileImageUrl: String? = null,
    val thumbnailImageUrl: String? = null,
    val birthyear: String? = null,
    val birthday: String? = null,
)
