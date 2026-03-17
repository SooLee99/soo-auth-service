package io.soo.springboot.core.api.controller.v1.request

import io.soo.springboot.core.enums.Gender
import io.soo.springboot.core.enums.Role
import io.soo.springboot.core.enums.UserStatus
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class AdminUserUpdateRequest(
    @field:Email(message = "이메일 형식이 올바르지 않습니다.")
    @field:Size(max = 320, message = "이메일은 최대 320자입니다.")
    val email: String? = null,

    @field:Size(max = 20, message = "휴대폰 번호가 너무 깁니다.")
    @field:Pattern(
        regexp = """^\+?\d[\d\s-]{7,18}\d$""",
        message = "휴대폰 번호 형식이 올바르지 않습니다.",
    )
    val phoneNumber: String? = null,

    @field:Size(min = 1, max = 50, message = "이름은 1자 이상 50자 이하로 입력해 주세요.")
    val name: String? = null,

    @field:Size(min = 2, max = 20, message = "닉네임은 2자 이상 20자 이하로 입력해 주세요.")
    val nickname: String? = null,

    val gender: Gender? = null,

    @field:Pattern(
        regexp = """^[a-zA-Z]{2,3}(-[a-zA-Z]{4})?(-[a-zA-Z]{2}|\d{3})?$""",
        message = "locale 형식이 올바르지 않습니다. (예: ko-KR, en-US)",
    )
    val locale: String? = null,

    @field:Pattern(regexp = """^\d{4}$""", message = "birthyear는 yyyy 형식(4자리)이어야 합니다.")
    val birthyear: String? = null,

    @field:Pattern(
        regexp = """^(0[1-9]|1[0-2])-(0[1-9]|[12]\d|3[01])$""",
        message = "birthday는 MM-DD 형식이어야 합니다.",
    )
    val birthday: String? = null,

    @field:Size(max = 1000, message = "프로필 이미지 URL은 최대 1000자까지 입력할 수 있습니다.")
    val profileImageUrl: String? = null,

    @field:Size(max = 1000, message = "썸네일 이미지 URL은 최대 1000자까지 입력할 수 있습니다.")
    val thumbnailImageUrl: String? = null,

    val role: Role? = null,
    val userStatus: UserStatus? = null,
    val blocked: Boolean? = null,
    val blockedReason: String? = null,
    val emailVerified: Boolean? = null,
    val phoneVerified: Boolean? = null,
)

data class AdminUserDeleteRequest(
    @field:Size(max = 500, message = "삭제 사유는 500자 이하여야 합니다.")
    val reason: String? = null,
)
