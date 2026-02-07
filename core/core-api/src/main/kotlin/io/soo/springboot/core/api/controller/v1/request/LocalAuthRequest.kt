package io.soo.springboot.core.api.controller.v1.request

import io.soo.springboot.core.enums.Gender
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class LoginRequest(
    @field:NotBlank(message = "이메일은 필수입니다.")
    @field:Email(message = "이메일 형식이 올바르지 않습니다.")
    @field:Size(max = 320, message = "이메일은 최대 320자까지 입력할 수 있습니다.")
    val email: String,

    @field:NotBlank(message = "비밀번호는 필수입니다.")
    @field:Size(min = 6, max = 72, message = "비밀번호는 6자 이상 72자 이하로 입력해 주세요.")
    @field:Pattern(
        regexp = """^(?=.{6,72}$)(?=.*[A-Za-z])(?=.*\d)(?=.*[^A-Za-z0-9]).*$""",
        message = "비밀번호는 영문, 숫자, 특수문자 중 3종류 이상을 포함해야 합니다.",
    )
    val password: String,
)

data class SignUpRequest(
    @field:NotBlank(message = "이메일은 필수입니다.")
    @field:Email(message = "이메일 형식이 올바르지 않습니다.")
    @field:Size(max = 320, message = "이메일은 최대 320자까지 입력할 수 있습니다.")
    val email: String,

    @field:NotBlank(message = "비밀번호는 필수입니다.")
    @field:Size(min = 6, max = 72, message = "비밀번호는 6자 이상 72자 이하로 입력해 주세요.")
    @field:Pattern(
        regexp = """^(?=.{6,72}$)(?=.*[A-Za-z])(?=.*\d)(?=.*[^A-Za-z0-9]).*$""",
        message = "비밀번호는 영문, 숫자, 특수문자 중 3종류 이상을 포함해야 합니다.",
    )
    val password: String,

    @field:Size(min = 1, max = 50, message = "이름은 1자 이상 50자 이하로 입력해 주세요.")
    @field:Pattern(
        regexp = """^[가-힣a-zA-Z\s\-\']+$""",
        message = "이름에는 한글/영문/공백/하이픈(-)/아포스트로피(')만 사용할 수 있습니다.",
    )
    val name: String? = null,

    @field:NotNull(message = "성별은 필수입니다.")
    val gender: Gender,

    @field:Size(min = 2, max = 20, message = "닉네임은 2자 이상 20자 이하로 입력해 주세요.")
    @field:Pattern(
        regexp = """^[가-힣a-zA-Z0-9_-]+$""",
        message = "닉네임에는 한글/영문/숫자/언더스코어(_)/하이픈(-)만 사용할 수 있습니다.",
    )
    val nickname: String? = null,

    @field:Size(max = 1000, message = "프로필 이미지 URL은 최대 1000자까지 입력할 수 있습니다.")
    val profileImageUrl: String? = null,

    @field:Size(max = 1000, message = "썸네일 이미지 URL은 최대 1000자까지 입력할 수 있습니다.")
    val thumbnailImageUrl: String? = null,

    @field:Pattern(regexp = """^\d{4}$""", message = "birthyear는 yyyy 형식(4자리)이어야 합니다.")
    val birthyear: String? = null,

    @field:Pattern(
        regexp = """^(0[1-9]|1[0-2])-(0[1-9]|[12]\d|3[01])$""",
        message = "birthday는 MM-DD 형식이어야 합니다."
    )
    val birthday: String? = null,
)
