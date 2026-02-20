package io.soo.springboot.core.domain.token

import io.soo.springboot.core.enums.AuthProvider
import io.soo.springboot.storage.db.core.JpaLocalCredentialRepository
import io.soo.springboot.storage.db.core.UserJpaRepository
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service

@Service
class JpaUserPrincipalLoader(
    private val userJpaRepository: UserJpaRepository,
    private val jpaLocalCredentialRepository: JpaLocalCredentialRepository,
) : UserPrincipalLoader {

    override fun loadByUserId(userId: Long): UserPrincipal {
        val user = userJpaRepository.findById(userId)
            .orElseThrow { UsernameNotFoundException("User not found by id: $userId") }

        val provider: AuthProvider = user.authProvider ?: AuthProvider.LOCAL

        // LOCAL이면 자격증명에서 passwordHash를 채우고, OAuth2면 null 허용
        val passwordHash: String? = if (provider == AuthProvider.LOCAL) {
            val email = user.email?.trim()?.lowercase()
            if (email.isNullOrBlank()) null
            else jpaLocalCredentialRepository.findByUserEmail(email)?.passwordHash
        } else null

        return UserPrincipal(
            userId = user.id,
            email = user.email,
            passwordHash = passwordHash,
            role = user.role,
            provider = provider,
        )
    }

    override fun loadByEmail(email: String): UserPrincipal {
        val normalized = email.trim().lowercase()
        if (normalized.isBlank()) throw UsernameNotFoundException("Email is blank")

        val cred = jpaLocalCredentialRepository.findByUserEmail(normalized)
            ?: throw UsernameNotFoundException("User not found by email: $normalized")

        val user = userJpaRepository.findById(cred.userId)
            .orElseThrow { UsernameNotFoundException("User not found by id: ${cred.userId}") }

        val provider: AuthProvider = user.authProvider ?: AuthProvider.LOCAL

        return UserPrincipal(
            userId = user.id,
            email = normalized,
            passwordHash = cred.passwordHash,
            role = user.role,
            provider = provider,
        )
    }
}