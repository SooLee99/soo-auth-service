package io.soo.springboot.core.domain

import io.soo.springboot.core.enums.AuthProvider

data class OAuthUserInfo(
    val provider: AuthProvider,
    val providerUserId: String,

    // 공통
    val email: String? = null,
    val emailVerified: Boolean? = null,

    // 사람 정보
    val nickname: String? = null,
    val name: String? = null,
    val givenName: String? = null,
    val familyName: String? = null,
    val locale: String? = null,

    // 민감/선택 정보(동의 필요)
    val gender: String? = null,
    val ageRange: String? = null,     // e.g. "20~29" or "20-29"
    val birthday: String? = null,     // e.g. "MM-DD"
    val birthyear: String? = null,    // e.g. "1995"
    val phoneNumber: String? = null,

    // 프로필 이미지
    val profileImageUrl: String? = null,
    val thumbnailImageUrl: String? = null,

    // Kakao 특화
    val ci: String? = null,
)
