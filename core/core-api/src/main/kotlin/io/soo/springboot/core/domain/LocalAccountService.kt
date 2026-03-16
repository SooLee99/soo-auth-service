package io.soo.springboot.core.domain

import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.security.crypto.password.PasswordEncoder

import io.soo.springboot.core.enums.AdminUserActionType
import io.soo.springboot.core.enums.Gender
import io.soo.springboot.core.enums.AuthProvider
import io.soo.springboot.core.enums.UserStatus
import io.soo.springboot.core.support.error.ErrorType
import io.soo.springboot.core.support.error.CoreException

import io.soo.springboot.core.api.security.token.AuthTokenManager
import io.soo.springboot.storage.db.core.LocalCredential
import io.soo.springboot.storage.db.core.LocalCredentialRepository
import io.soo.springboot.storage.db.core.User
import io.soo.springboot.storage.db.core.UserRepository
import io.soo.springboot.storage.db.core.UserStatusAuditLogRepository
import java.time.Instant
import java.time.ZoneOffset


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

@Service
class LocalAccountService(
    private val userRepository: UserRepository,
    private val localAccountRepository: LocalCredentialRepository,
    private val passwordEncoder: PasswordEncoder,
    private val authTokenManager: AuthTokenManager,
    private val userStatusAuditLogRepository: UserStatusAuditLogRepository,
) {
    @Transactional
    fun signUp(cmd: LocalSignUpCommand): User {
        // 1) 중복 이메일/전화번호 검사
        if (userRepository.existsByEmail(cmd.email))
            throw CoreException(ErrorType.DUPLICATE_EMAIL, data = mapOf("email" to cmd.email))
        if (userRepository.existsByPhoneNumber(cmd.phoneNumber))
            throw CoreException(ErrorType.DUPLICATE_PHONE_NUMBER, data = mapOf("phoneNumber" to cmd.phoneNumber))

        // 2) 사용자 정보 저장
        val user = userRepository.save(
            User(
                email = cmd.email,
                emailVerified = false,
                phoneNumber = cmd.phoneNumber,
                phoneVerified = false,
                name = cmd.name,
                nickname = cmd.nickname,
                gender = cmd.gender,
                locale = cmd.locale,
                birthyear = cmd.birthyear,
                birthday = cmd.birthday,
                profileImageUrl = cmd.profileImageUrl,
                thumbnailImageUrl = cmd.thumbnailImageUrl,
                authProvider = AuthProvider.LOCAL,
            )
        )

        // 3) 로컬 자격증명 저장
        localAccountRepository.save(
            LocalCredential(
                userId = user.id,
                userEmail = cmd.email,
                passwordHash = passwordEncoder.encode(cmd.password),
            )
        )
        return user
    }

    /**
     * ✅ logout
     * - access token denylist(jti)
     * - refresh token revoke (단건 or device or all)
     */
    @Transactional
    fun logout(jwt: Jwt, deviceId: String, refreshToken: String?, logoutAll: Boolean) {
        authTokenManager.invalidateTokens(
            jwt = jwt,
            deviceId = deviceId,
            refreshToken = refreshToken,
            logoutAll = logoutAll,
        )
    }

    /**
     * 소프트 회원 탈퇴:
     * - 물리 삭제 없이 상태를 SOFT_DELETED로 변경
     * - retentionUntil = 탈퇴 시각 + 5년
     * - 탈퇴 즉시 로그인 불가
     */
    @Transactional
    fun softDelete(userId: Long, reason: String?) {
        val user = userRepository.findByIdIncludingDeleted(userId)
            ?: throw CoreException(ErrorType.NOT_FOUND, mapOf("userId" to userId))
        if (user.userStatus == UserStatus.SOFT_DELETED) return

        val now = Instant.now()
        val retentionUntil = now.atOffset(ZoneOffset.UTC).plusYears(5).toInstant()
        val trimmedReason = reason?.trim()?.takeIf { it.isNotBlank() }

        userRepository.save(
            user.copy(
                userStatus = UserStatus.SOFT_DELETED,
                blocked = false,
                blockedReason = null,
                blockedAt = null,
                blockedByAdminId = null,
                unblockedAt = null,
                unblockedByAdminId = null,
                deletedAt = now,
                deletionReason = trimmedReason,
                retentionUntil = retentionUntil,
            )
        )

        localAccountRepository.deleteByUserId(userId)
        authTokenManager.revokeAll(userId)
        userStatusAuditLogRepository.save(
            targetUserId = userId,
            actorUserId = userId,
            actionType = AdminUserActionType.SOFT_DELETE,
            reason = trimmedReason,
        )
    }
}
