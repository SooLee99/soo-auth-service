package io.soo.springboot.core.domain.local

import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.security.crypto.password.PasswordEncoder

import io.soo.springboot.core.enums.AdminUserActionType
import io.soo.springboot.core.enums.Gender
import io.soo.springboot.core.enums.UserStatus
import io.soo.springboot.core.domain.local.phone.login.PhoneLoginContext
import io.soo.springboot.core.domain.local.phone.login.PhoneLoginResolver
import io.soo.springboot.core.domain.local.policy.UserUniquenessPolicy
import io.soo.springboot.core.domain.local.support.PhoneNumberNormalizer
import io.soo.springboot.core.domain.local.support.PhoneAccountFactory
import io.soo.springboot.core.support.error.CoreException
import io.soo.springboot.core.support.error.ErrorType

import io.soo.springboot.core.domain.token.TokenRevocationService
import io.soo.springboot.core.domain.local.phone.PhoneVerificationService
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
    val phoneVerificationToken: String,
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
    private val tokenRevocationService: TokenRevocationService,
    private val phoneVerificationService: PhoneVerificationService,
    private val userStatusAuditLogRepository: UserStatusAuditLogRepository,
    private val userUniquenessPolicy: UserUniquenessPolicy,
    private val phoneNumberNormalizer: PhoneNumberNormalizer,
    private val phoneAccountFactory: PhoneAccountFactory,
    private val phoneLoginResolver: PhoneLoginResolver,
) {
    @Transactional
    fun signup(cmd: LocalSignUpCommand): User {
        phoneVerificationService.consume(cmd.phoneNumber, cmd.phoneVerificationToken)
        val normalizedPhone = phoneNumberNormalizer.normalize(cmd.phoneNumber)

        userUniquenessPolicy.validateSignUp(
            email = cmd.email,
            rawPhone = cmd.phoneNumber,
            normalizedPhone = normalizedPhone,
        )

        // 2) 사용자 정보 저장
        val user = userRepository.save(
            User.createLocal(
                email = cmd.email,
                phoneNumber = normalizedPhone,
                emailVerified = false,
                phoneVerified = false,
                name = cmd.name,
                nickname = cmd.nickname,
                gender = cmd.gender,
                locale = cmd.locale,
                birthyear = cmd.birthyear,
                birthday = cmd.birthday,
                profileImageUrl = cmd.profileImageUrl,
                thumbnailImageUrl = cmd.thumbnailImageUrl,
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
    fun signupPhone(phoneNumber: String, phoneVerificationToken: String): User {
        phoneVerificationService.consume(phoneNumber, phoneVerificationToken)
        val normalizedPhone = phoneNumberNormalizer.normalize(phoneNumber)
        userUniquenessPolicy.validatePhoneAvailable(phoneNumber, normalizedPhone)
        val internalAccount = phoneAccountFactory.create(normalizedPhone)
        val encodedPassword = passwordEncoder.encode(internalAccount.rawPassword)

        val user = userRepository.save(
            User.createLocal(
                email = internalAccount.email,
                phoneNumber = normalizedPhone,
                emailVerified = false,
                phoneVerified = false,
                name = null,
                nickname = null,
                gender = Gender.UNKNOWN,
                locale = "ko-KR",
            )
        )

        localAccountRepository.save(
            LocalCredential(
                userId = user.id,
                userEmail = internalAccount.email,
                passwordHash = encodedPassword,
            )
        )
        return user
    }

    @Transactional
    fun loginByPhone(phoneNumber: String, phoneVerificationToken: String): User {
        phoneVerificationService.consume(phoneNumber, phoneVerificationToken)
        val normalizedPhone = phoneNumberNormalizer.normalize(phoneNumber)

        val activeUser = userRepository.findByPhoneNumber(normalizedPhone)
        val userIncludingDeleted = if (activeUser == null) {
            userRepository.findByPhoneNumberIncludingDeleted(normalizedPhone)
        } else null

        return phoneLoginResolver.resolve(
            PhoneLoginContext(
                activeUser = activeUser,
                userIncludingDeleted = userIncludingDeleted,
            )
        )
    }

    /**
     * ✅ logout
     * - access token denylist(jti)
     * - refresh token revoke (단건 or device or all)
     */
    @Transactional
    fun logout(jwt: Jwt, deviceId: String, refreshToken: String?, logoutAll: Boolean) {
        tokenRevocationService.revokeOnLogout(
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
            user.softDelete(
                at = now,
                retentionUntil = retentionUntil,
                reason = trimmedReason,
                anonymizedEmail = anonymizedEmail,
                anonymizedPhone = anonymizedPhone,
            )
        )

        localAccountRepository.deleteByUserId(userId)
        tokenRevocationService.revokeAllByUserId(userId)
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
}
