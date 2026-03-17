package io.soo.springboot.core.domain

import io.soo.springboot.core.api.controller.v1.request.AdminUserUpdateRequest
import io.soo.springboot.core.enums.AuthProvider
import io.soo.springboot.core.enums.Role
import io.soo.springboot.core.enums.UserStatus
import io.soo.springboot.core.api.security.token.AuthTokenManager
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

@Service
class AdminUserManagementService(
    private val userRepository: UserRepository,
    private val localAccountService: LocalAccountService,
    private val localCredentialRepository: LocalCredentialRepository,
    private val passwordEncoder: PasswordEncoder,
    private val authTokenManager: AuthTokenManager,
) {
    @Transactional(readOnly = true)
    fun listUsers(
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
    fun getUserDetail(userId: Long): User {
        return userRepository.findByIdIncludingDeleted(userId)
            ?: throw CoreException(ErrorType.NOT_FOUND, data = mapOf("userId" to userId))
    }

    @Transactional
    fun updateUser(userId: Long, request: AdminUserUpdateRequest, adminUserId: Long): User {
        val current = userRepository.findByIdIncludingDeleted(userId)
            ?: throw CoreException(ErrorType.NOT_FOUND, data = mapOf("userId" to userId))
        if (request.userStatus == UserStatus.SOFT_DELETED) {
            throw CoreException(
                ErrorType.INVALID_PARAMETER,
                data = mapOf("userStatus" to "SOFT_DELETED", "message" to "소프트 삭제는 delete API를 사용하세요."),
            )
        }

        val updatedEmail = request.email?.trim()?.lowercase() ?: current.email
        val updatedPhone = request.phoneNumber?.trim() ?: current.phoneNumber

        validateUniqueEmail(current.id, current.email, updatedEmail)
        validateUniquePhone(current.id, current.phoneNumber, updatedPhone)

        val now = Instant.now()
        val targetStatus = request.userStatus ?: current.userStatus
        val targetBlocked = request.blocked ?: current.blocked
        val normalizedBlocked = targetBlocked || targetStatus == UserStatus.BLOCKED
        val normalizedStatus = when {
            targetStatus == UserStatus.SOFT_DELETED -> UserStatus.SOFT_DELETED
            normalizedBlocked -> UserStatus.BLOCKED
            else -> UserStatus.ACTIVE
        }

        val blockedAt = when {
            normalizedBlocked && !current.blocked -> now
            normalizedBlocked -> current.blockedAt
            else -> null
        }

        val unblockedAt = when {
            !normalizedBlocked && current.blocked -> now
            !normalizedBlocked -> current.unblockedAt
            else -> null
        }

        val saved = userRepository.save(
            current.copy(
                email = updatedEmail,
                emailVerified = request.emailVerified ?: current.emailVerified,
                phoneNumber = updatedPhone,
                phoneVerified = request.phoneVerified ?: current.phoneVerified,
                name = request.name?.trim() ?: current.name,
                nickname = request.nickname?.trim() ?: current.nickname,
                gender = request.gender ?: current.gender,
                locale = request.locale?.trim() ?: current.locale,
                birthyear = request.birthyear ?: current.birthyear,
                birthday = request.birthday ?: current.birthday,
                profileImageUrl = request.profileImageUrl?.trim() ?: current.profileImageUrl,
                thumbnailImageUrl = request.thumbnailImageUrl?.trim() ?: current.thumbnailImageUrl,
                role = request.role ?: current.role,
                userStatus = normalizedStatus,
                blocked = normalizedBlocked,
                blockedReason = if (normalizedBlocked) request.blockedReason?.trim() ?: current.blockedReason else null,
                blockedAt = blockedAt,
                blockedByAdminId = if (normalizedBlocked) adminUserId else null,
                unblockedAt = unblockedAt,
                unblockedByAdminId = if (!normalizedBlocked && current.blocked) adminUserId else current.unblockedByAdminId,
            )
        )

        return saved
    }

    @Transactional
    fun deleteUser(userId: Long, reason: String?, adminUserId: Long): User {
        localAccountService.softDelete(userId = userId, reason = reason, actorUserId = adminUserId)
        return userRepository.findByIdIncludingDeleted(userId)
            ?: throw CoreException(ErrorType.NOT_FOUND, data = mapOf("userId" to userId))
    }

    @Transactional
    fun resetPassword(userId: Long, newPassword: String): User {
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
        authTokenManager.revokeAll(userId)
        return user
    }

    @Transactional
    fun revokeUserTokens(userId: Long): User {
        val user = userRepository.findByIdIncludingDeleted(userId)
            ?: throw CoreException(ErrorType.NOT_FOUND, data = mapOf("userId" to userId))
        authTokenManager.revokeAll(userId)
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
