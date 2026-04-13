package io.soo.springboot.core.domain.admin

import io.soo.springboot.core.enums.Gender
import io.soo.springboot.core.enums.Role
import io.soo.springboot.core.enums.UserStatus

data class AdminUserUpdate(
    val email: String? = null,
    val phoneNumber: String? = null,
    val name: String? = null,
    val nickname: String? = null,
    val gender: Gender? = null,
    val locale: String? = null,
    val birthyear: String? = null,
    val birthday: String? = null,
    val profileImageUrl: String? = null,
    val thumbnailImageUrl: String? = null,
    val role: Role? = null,
    val userStatus: UserStatus? = null,
    val blocked: Boolean? = null,
    val blockedReason: String? = null,
    val emailVerified: Boolean? = null,
    val phoneVerified: Boolean? = null,
)
