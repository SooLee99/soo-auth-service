package io.soo.springboot.core.api.security.userdetails

import io.soo.springboot.core.enums.AuthProvider
import io.soo.springboot.core.enums.Role
import io.soo.springboot.storage.db.core.LocalCredentialRepository
import io.soo.springboot.storage.db.core.UserRepository
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service

@Service
class DbUserPrincipalLoader(
    private val userRepository: UserRepository,
    private val localCredentialRepository: LocalCredentialRepository,
) : UserPrincipalLoader {

    override fun loadByUserId(userId: Long): UserPrincipal {
        val user = userRepository.findById(userId)
            ?: throw UsernameNotFoundException("User not found by id: $userId")

        return toUserPrincipal(user.id, user.email, user.role, user.authProvider)
    }

    override fun loadByEmail(email: String): UserPrincipal {
        val user = userRepository.findByEmail(email)
            ?: throw UsernameNotFoundException("User not found by email: $email")

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
            role = role, // 실제 타입에 맞게 수정
            provider = provider,
        )
    }
}