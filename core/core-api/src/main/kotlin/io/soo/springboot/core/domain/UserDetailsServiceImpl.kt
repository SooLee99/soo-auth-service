package io.soo.springboot.core.domain

import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service

import io.soo.springboot.storage.db.core.LocalCredentialRepository
import io.soo.springboot.storage.db.core.UserAccountRepository

@Service
class UserDetailsServiceImpl(
    private val userAccountRepository: UserAccountRepository,
    private val localCredentialRepository: LocalCredentialRepository,
) : UserDetailsService {

    override fun loadUserByUsername(username: String): UserDetails {
        val email = username.trim().lowercase()
        val user = userAccountRepository.findByEmail(email)
            ?: throw UsernameNotFoundException("User not found by email: $email")

        val cred = localCredentialRepository.findByUserId(user.id)
            ?: throw UsernameNotFoundException("Local credential not found: userId=${user.id}")

        return UserPrincipal(
            userId = user.id,
            email = email,
            passwordHash = cred.passwordHash,
        )
    }
}
