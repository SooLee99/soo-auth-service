package io.soo.springboot.core.domain.token

import io.soo.springboot.core.enums.AuthProvider
import io.soo.springboot.storage.db.core.JpaLocalCredentialRepository
import io.soo.springboot.storage.db.core.UserJpaRepository
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service

@Service
abstract class DbUserPrincipalLoader(
    private val userJpaRepository: UserJpaRepository,
    private val jpaLocalCredentialRepository: JpaLocalCredentialRepository,
) : UserPrincipalLoader {

    override fun loadByUserId(userId: Long): UserPrincipal {
        val user = userJpaRepository.findById(userId)
            .orElseThrow { UsernameNotFoundException("User not found by id: $userId") }

        val provider = user.authProvider ?: AuthProvider.LOCAL
        val email = user.email
        val passwordHash: String? =
            if (provider == AuthProvider.LOCAL) {
                val cred = jpaLocalCredentialRepository.findByUserId(userId)
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