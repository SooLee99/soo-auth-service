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
import java.security.MessageDigest
import java.security.SecureRandom


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
    private val secureRandom = SecureRandom()

    @Transactional
    fun signUp(cmd: LocalSignUpCommand): User {
        val normalizedPhone = normalizePhoneNumber(cmd.phoneNumber)

        // 1) 중복 이메일/전화번호 검사
        if (userRepository.existsByEmail(cmd.email))
            throw CoreException(ErrorType.DUPLICATE_EMAIL, data = mapOf("email" to cmd.email))
        validatePhoneDuplicated(cmd.phoneNumber, normalizedPhone)

        // 2) 사용자 정보 저장
        val user = userRepository.save(
            User(
                email = cmd.email,
                emailVerified = false,
                phoneNumber = normalizedPhone,
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

    @Transactional
    fun signUpByPhone(phoneNumber: String): User {
        val normalizedPhone = normalizePhoneNumber(phoneNumber)
        validatePhoneDuplicated(phoneNumber, normalizedPhone)

        val internalEmail = issueUniqueInternalEmail(normalizedPhone)
        val encodedPassword = passwordEncoder.encode(generateInternalPassword())

        val user = userRepository.save(
            User(
                email = internalEmail,
                emailVerified = false,
                phoneNumber = normalizedPhone,
                phoneVerified = false,
                name = null,
                nickname = null,
                gender = Gender.UNKNOWN,
                locale = "ko-KR",
                authProvider = AuthProvider.LOCAL,
            )
        )

        localAccountRepository.save(
            LocalCredential(
                userId = user.id,
                userEmail = internalEmail,
                passwordHash = encodedPassword,
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
    fun softDelete(userId: Long, reason: String?, actorUserId: Long? = null) {
        val user = userRepository.findByIdIncludingDeleted(userId)
            ?: throw CoreException(ErrorType.NOT_FOUND, mapOf("userId" to userId))
        if (user.userStatus == UserStatus.SOFT_DELETED) return

        val now = Instant.now()
        val retentionUntil = now.atOffset(ZoneOffset.UTC).plusYears(5).toInstant()
        val trimmedReason = reason?.trim()?.takeIf { it.isNotBlank() }
        val anonymizedEmail = buildAnonymizedEmail(userId, now)
        val anonymizedPhone = buildAnonymizedPhone(userId, now)

        userRepository.save(
            user.copy(
                email = anonymizedEmail,
                emailVerified = false,
                phoneNumber = anonymizedPhone,
                phoneNumberE164 = null,
                phoneVerified = false,
                name = null,
                nickname = null,
                gender = Gender.UNKNOWN,
                locale = null,
                birthyear = null,
                birthday = null,
                ageRange = null,
                profileImageUrl = null,
                thumbnailImageUrl = null,
                oauthProviderUserId = null,
                oauthConnectedAt = null,
                oauthExtraJson = null,
                oauthRawJson = null,
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
            actorUserId = actorUserId ?: userId,
            actionType = AdminUserActionType.SOFT_DELETE,
            reason = trimmedReason,
        )
    }

    private fun buildAnonymizedEmail(userId: Long, at: Instant): String {
        return "deleted+${userId}.${at.epochSecond}@deleted.local"
    }

    private fun buildAnonymizedPhone(userId: Long, at: Instant): String {
        return "deleted-${userId}-${at.epochSecond}"
    }

    private fun validatePhoneDuplicated(rawPhone: String, normalizedPhone: String) {
        val duplicated = userRepository.existsByPhoneNumber(rawPhone) ||
            (normalizedPhone != rawPhone && userRepository.existsByPhoneNumber(normalizedPhone))
        if (duplicated) {
            throw CoreException(ErrorType.DUPLICATE_PHONE_NUMBER, data = mapOf("phoneNumber" to rawPhone))
        }
    }

    private fun normalizePhoneNumber(phoneNumber: String): String {
        val trimmed = phoneNumber.trim()
        val hasPlusPrefix = trimmed.startsWith("+")
        val digits = trimmed.filter { it.isDigit() }
        return if (hasPlusPrefix) "+$digits" else digits
    }

    private fun issueUniqueInternalEmail(normalizedPhone: String): String {
        var candidate = buildInternalEmail(normalizedPhone)
        var attempt = 0
        while (userRepository.existsByEmail(candidate) && attempt < 5) {
            attempt += 1
            candidate = buildInternalEmail("$normalizedPhone#$attempt")
        }
        if (userRepository.existsByEmail(candidate)) {
            throw CoreException(ErrorType.INVALID_REQUEST, data = mapOf("reason" to "failed to allocate internal email"))
        }
        return candidate
    }

    private fun buildInternalEmail(seed: String): String {
        val digestBytes = MessageDigest.getInstance("SHA-256").digest(seed.toByteArray(Charsets.UTF_8))
        val hash = digestBytes.joinToString("") { "%02x".format(it) }.take(40)
        return "phone-$hash@local.internal"
    }

    private fun generateInternalPassword(length: Int = 32): String {
        val lower = "abcdefghjkmnpqrstuvwxyz"
        val upper = "ABCDEFGHJKMNPQRSTUVWXYZ"
        val digits = "23456789"
        val symbols = "!@#$%^&*()-_=+[]{}"

        val required = mutableListOf(
            lower.random(secureRandom),
            upper.random(secureRandom),
            digits.random(secureRandom),
            symbols.random(secureRandom),
        )

        val all = lower + upper + digits + symbols
        repeat((length - required.size).coerceAtLeast(0)) {
            required += all.random(secureRandom)
        }
        for (i in required.lastIndex downTo 1) {
            val j = secureRandom.nextInt(i + 1)
            val tmp = required[i]
            required[i] = required[j]
            required[j] = tmp
        }
        return required.joinToString("")
    }

    private fun String.random(random: SecureRandom): Char = this[random.nextInt(this.length)]
}
