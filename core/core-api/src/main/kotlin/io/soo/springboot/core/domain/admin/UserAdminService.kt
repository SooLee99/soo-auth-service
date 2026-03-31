package io.soo.springboot.core.domain.admin

import io.soo.springboot.core.enums.Gender
import io.soo.springboot.core.enums.AuthProvider
import io.soo.springboot.core.enums.Role
import io.soo.springboot.core.enums.UserStatus
import io.soo.springboot.core.domain.local.LocalAccountService
import io.soo.springboot.core.domain.token.TokenRevocationService
import io.soo.springboot.core.support.error.CoreException
import io.soo.springboot.core.support.error.ErrorType
import io.soo.springboot.storage.db.core.LocalCredential
import io.soo.springboot.storage.db.core.LocalCredentialRepository
import io.soo.springboot.storage.db.core.User
import io.soo.springboot.storage.db.core.UserRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

data class UserUpdateCommand(
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

@Service
class UserAdminService(
    private val userRepository: UserRepository,
    private val localAccountService: LocalAccountService,
    private val localCredentialRepository: LocalCredentialRepository,
    private val passwordEncoder: PasswordEncoder,
    private val tokenRevocationService: TokenRevocationService,
) {
    @Transactional(readOnly = true)
    fun list(
        keyword: String?,
        userStatus: UserStatus?,
        role: Role?,
        authProvider: AuthProvider?,
        pageable: Pageable,
    ): Page<User> {
        return userRepository.searchUsers(
            keyword = keyword,
            userStatus = userStatus,
            role = role,
            authProvider = authProvider,
            pageable = pageable,
        )
    }

    @Transactional(readOnly = true)
    fun getById(userId: Long): User {
        return userRepository.findByIdIncludingDeleted(userId)
            ?: throw CoreException(ErrorType.NOT_FOUND, data = mapOf("userId" to userId))
    }

    @Transactional
    fun updateUser(userId: Long, command: UserUpdateCommand, adminUserId: Long): User {
        val current = userRepository.findByIdIncludingDeleted(userId)
            ?: throw CoreException(ErrorType.NOT_FOUND, data = mapOf("userId" to userId))
        if (command.userStatus == UserStatus.SOFT_DELETED) {
            throw CoreException(
                ErrorType.INVALID_PARAMETER,
                data = mapOf("userStatus" to "SOFT_DELETED", "message" to "소프트 삭제는 delete API를 사용하세요."),
            )
        }

        val updatedEmail = command.email?.trim()?.lowercase() ?: current.email
        val updatedPhone = command.phoneNumber?.trim() ?: current.phoneNumber

        validateUniqueEmail(current.id, current.email, updatedEmail)
        validateUniquePhone(current.id, current.phoneNumber, updatedPhone)

        val saved = userRepository.save(
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

        return saved
    }

    @Transactional
    fun softDeleteUser(userId: Long, reason: String?, adminUserId: Long): User {
        localAccountService.softDelete(userId = userId, reason = reason, actorUserId = adminUserId)
        return userRepository.findByIdIncludingDeleted(userId)
            ?: throw CoreException(ErrorType.NOT_FOUND, data = mapOf("userId" to userId))
    }

    @Transactional
    fun updatePassword(userId: Long, newPassword: String): User {
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
    fun revokeTokens(userId: Long): User {
        val user = userRepository.findByIdIncludingDeleted(userId)
            ?: throw CoreException(ErrorType.NOT_FOUND, data = mapOf("userId" to userId))
        tokenRevocationService.revokeAllByUserId(userId)
        return user
    }

    private fun validateUniqueEmail(currentUserId: Long, currentEmail: String, targetEmail: String) {
        if (targetEmail == currentEmail) return
        val existed = userRepository.findByEmail(targetEmail)
        if (existed != null && existed.id != currentUserId) {
            throw CoreException(ErrorType.DUPLICATE_EMAIL, data = mapOf("email" to targetEmail))
        }
    }

    private fun validateUniquePhone(currentUserId: Long, currentPhone: String?, targetPhone: String?) {
        if (currentUserId <= 0) return
        if (targetPhone == null || targetPhone == currentPhone) return
        val duplicated = userRepository.existsByPhoneNumber(targetPhone)
        if (duplicated) {
            throw CoreException(ErrorType.DUPLICATE_PHONE_NUMBER, data = mapOf("phoneNumber" to targetPhone))
        }
    }
}
