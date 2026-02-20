package io.soo.springboot.core.domain


import io.soo.springboot.core.enums.AuthProvider
import io.soo.springboot.core.enums.Gender
import java.time.Instant


data class OAuth2UserInfo(
    val provider: AuthProvider,
    val providerUserId: String,

    // 공통
    val email: String? = null,
    val emailVerified: Boolean? = null,
    val name: String? = null,
    val nickname: String? = null,
    val locale: String? = null,

    // 프로필 이미지
    val profileImageUrl: String? = null,
    val thumbnailImageUrl: String? = null,

    // 선택 정보 (동의/제공 여부에 따라 null 가능)
    val gender: Gender? = null,
    val birthday: String? = null,   // "MM-DD" 로 정규화
    val birthyear: String? = null,  // "YYYY"
    val ageRange: String? = null,   // "20~29" or "20-29" 등 provider별 원본

    val phoneNumber: String? = null,
    val phoneNumberE164: String? = null,

    // provider별 메타
    val connectedAt: Instant? = null,

    /**
     * provider별로 "동의 필요 여부", "has_xxx" 같은 상태값/추가값을 최대한 담기
     * (DB에는 json으로 저장하거나 필요한 것만 골라 저장)
     */
    val extra: Map<String, Any?> = emptyMap(),

    /**
     * 원본 attrs를 통째로 보관하고 싶다면 여기
     * (주의: 그대로 DB에 박으면 용량/PII/스키마 이슈 -> 보통은 json 컬럼에 저장하거나 로깅 전용)
     */
    val raw: Map<String, Any?> = emptyMap(),
)