package io.soo.springboot.core.domain.admin

import io.soo.springboot.core.domain.local.policy.UserUniquenessPolicy
import io.soo.springboot.core.domain.token.TokenRevocationService
import io.soo.springboot.core.domain.user.UserLifecycleService
import io.soo.springboot.core.enums.UserStatus
import io.soo.springboot.core.support.error.CoreException
import io.soo.springboot.core.support.error.ErrorType
import io.soo.springboot.storage.db.core.LocalCredential
import io.soo.springboot.storage.db.core.LocalCredentialRepository
import io.soo.springboot.storage.db.core.User
import io.soo.springboot.storage.db.core.UserRepository
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class UserAdminCommandService(
    private val userRepository: UserRepository,
    private val localCredentialRepository: LocalCredentialRepository,
    private val passwordEncoder: PasswordEncoder,
    private val tokenRevocationService: TokenRevocationService,
    private val userUniquenessPolicy: UserUniquenessPolicy,
    private val userLifecycleService: UserLifecycleService,
) : AdminUserCommandUseCase {
    @Transactional
    override fun update(userId: Long, command: UserUpdateCommand, adminUserId: Long): User {
        val current = userRepository.findByIdIncludingDeleted(userId)
            ?: throw CoreException(ErrorType.NOT_FOUND, data = mapOf("userId" to userId))
        if (command.userStatus == UserStatus.SOFT_DELETED) {
            throw CoreException(
                ErrorType.INVALID_PARAMETER,
                data = mapOf("userStatus" to "SOFT_DELETED", "message" to "소프트 삭제는 delete API를 사용하세요."),
            )
        }

        val nextEmail = command.email?.trim()?.lowercase() ?: current.email
        val nextPhone = command.phoneNumber?.trim() ?: current.phoneNumber

        userUniquenessPolicy.validateUpdateEmail(current.id, current.email, nextEmail)
        userUniquenessPolicy.validateUpdatePhone(current.id, current.phoneNumber, nextPhone)

        return userRepository.save(
            current.updateByAdmin(
                email = command.email,
                phoneNumber = command.phoneNumber,
                name = command.name,
                nickname = command.nickname,
                gender = command.gender,
                locale = command.locale,
                birthyear = command.birthyear,
                birthday = command.birthday,
                profileImageUrl = command.profileImageUrl,
                thumbnailImageUrl = command.thumbnailImageUrl,
                role = command.role,
                userStatus = command.userStatus,
                blocked = command.blocked,
                blockedReason = command.blockedReason,
                emailVerified = command.emailVerified,
                phoneVerified = command.phoneVerified,
                adminUserId = adminUserId,
                now = Instant.now(),
            )
        )
    }

    @Transactional
    override fun softDelete(userId: Long, reason: String?, adminUserId: Long): User {
        userLifecycleService.softDelete(userId = userId, reason = reason, actorUserId = adminUserId)
        return userRepository.findByIdIncludingDeleted(userId)
            ?: throw CoreException(ErrorType.NOT_FOUND, data = mapOf("userId" to userId))
    }

    @Transactional
    override fun updatePassword(userId: Long, newPassword: String): User {
        val user = userRepository.findByIdIncludingDeleted(userId)
            ?: throw CoreException(ErrorType.NOT_FOUND, data = mapOf("userId" to userId))
        if (user.userStatus == UserStatus.SOFT_DELETED) {
            throw CoreException(ErrorType.CONFLICT, data = mapOf("reason" to "SOFT_DELETED_USER_PASSWORD_RESET_FORBIDDEN"))
        }

        val credential = localCredentialRepository.findByUserId(userId)
            ?: throw CoreException(ErrorType.NOT_FOUND, data = mapOf("reason" to "LOCAL_CREDENTIAL_NOT_FOUND", "userId" to userId))

        localCredentialRepository.save(
            LocalCredential(
                id = credential.id,
                userId = credential.userId,
                userEmail = credential.userEmail,
                passwordHash = passwordEncoder.encode(newPassword),
            )
        )
        tokenRevocationService.revokeAllByUserId(userId)
        return user
    }

    @Transactional
    override fun revokeTokens(userId: Long): User {
        val user = userRepository.findByIdIncludingDeleted(userId)
            ?: throw CoreException(ErrorType.NOT_FOUND, data = mapOf("userId" to userId))
        tokenRevocationService.revokeAllByUserId(userId)
        return user
    }
}
