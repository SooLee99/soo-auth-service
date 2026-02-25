package io.soo.springboot.core.domain.token

import io.soo.springboot.core.enums.AuthProvider
import io.soo.springboot.storage.db.core.LocalCredentialRepository
import io.soo.springboot.storage.db.core.UserRepository
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service

@Service
abstract class DbUserPrincipalLoader(
    private val userRepository: UserRepository,
    private val localCredentialRepository: LocalCredentialRepository,
) : UserPrincipalLoader {

    override fun loadByUserId(userId: Long): UserPrincipal {
        val user = userRepository.findById(userId) ?: throw UsernameNotFoundException("User not found by id: $userId")

        val provider = user.authProvider
        val email = user.email
        val passwordHash: String? =
            if (provider == AuthProvider.LOCAL) {
                val cred = localCredentialRepository.findByUserId(userId)
                    ?: throw UsernameNotFoundException("LocalCredential not found by userId: $userId")
                cred.passwordHash
            } else {
                null
            }

        return UserPrincipal(
            userId = user.id,
            email = email,
            passwordHash = passwordHash,
            role = user.role,
            provider = provider,
        )
    }
}