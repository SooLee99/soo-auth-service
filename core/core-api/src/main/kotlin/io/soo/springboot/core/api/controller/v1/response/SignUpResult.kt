package io.soo.springboot.core.api.controller.v1.response

import io.soo.springboot.core.enums.AuthProvider
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * ✅ 회원가입 응답(민감정보 제외)
 * - 프론트에서 "가입 완료" 화면 구성에 바로 사용 가능
 * - 운영/디버깅 편의를 위해 credential의 기본 상태도 함께 반환(원치 않으면 제거 가능)
 */
data class SignUpResult(
    val userId: Long,
    val email: String?,
    val emailVerified: Boolean?,
    val provider: AuthProvider,
    val profile: SignUpProfile,
    val credentialStatus: CredentialStatus,
    val createdAt: LocalDateTime,
)

/**
 * ✅ 사용자 공용 프로필 정보(민감정보 제외)
 */
data class SignUpProfile(
    val name: String?,
    val nickname: String?,
    val profileImageUrl: String?,
    val thumbnailImageUrl: String?,
    val birthyear: String?,
    val birthday: String?,
)

/**
 * ✅ 로컬 자격증명 상태(보안상 안전한 범위만)
 * - passwordHash는 절대 반환하지 않음
 */
data class CredentialStatus(
    val passwordUpdatedAt: LocalDateTime,
    val failedLoginCount: Int,
    val lockUntil: LocalDateTime?,
)

