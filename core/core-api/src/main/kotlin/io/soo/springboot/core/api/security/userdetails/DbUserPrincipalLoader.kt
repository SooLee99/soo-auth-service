package io.soo.springboot.core.api.security.userdetails

import io.soo.springboot.core.enums.AuthProvider
import io.soo.springboot.core.enums.Role
import io.soo.springboot.core.domain.AccountStatusDeniedException
import io.soo.springboot.core.domain.UserStatusPolicy
import io.soo.springboot.core.support.error.CoreException
import io.soo.springboot.core.support.error.ErrorType
import io.soo.springboot.storage.db.core.LocalCredentialRepository
import io.soo.springboot.storage.db.core.UserRepository
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service

@Service
class DbUserPrincipalLoader(
    private val userRepository: UserRepository,
    private val localCredentialRepository: LocalCredentialRepository,
    private val userStatusPolicy: UserStatusPolicy,
) : UserPrincipalLoader {

    override fun loadByUserId(userId: Long): UserPrincipal {
        val user = userRepository.findByIdIncludingDeleted(userId)
            ?: throw UsernameNotFoundException("User not found by id: $userId")
        try {
            userStatusPolicy.validateLoginAllowed(user)
        } catch (_: AccountStatusDeniedException) {
            throw CoreException(ErrorType.LOGIN_DENIED)
        }

        return toUserPrincipal(user.id, user.email, user.role, user.authProvider)
    }

    override fun loadByEmail(email: String): UserPrincipal {
        val user = userRepository.findByEmailIncludingDeleted(email)
            ?: throw UsernameNotFoundException("User not found by email: $email")
        try {
            userStatusPolicy.validateLoginAllowed(user)
        } catch (_: AccountStatusDeniedException) {
            throw CoreException(ErrorType.LOGIN_DENIED)
        }

        return toUserPrincipal(user.id, user.email, user.role, user.authProvider)
    }

    private fun toUserPrincipal(
        userId: Long,
        email: String,
        role: Role,
        provider: AuthProvider,
    ): UserPrincipal {
        val passwordHash: String? =
            if (provider == AuthProvider.LOCAL) {
                val cred = localCredentialRepository.findByUserId(userId)
                    ?: throw UsernameNotFoundException("LocalCredential not found by userId: $userId")
                cred.passwordHash
            } else {
                null
            }

        return UserPrincipal(
            userId = userId,
            email = email,
            passwordHash = passwordHash,
            role = role,
            provider = provider,
        )
    }
}
