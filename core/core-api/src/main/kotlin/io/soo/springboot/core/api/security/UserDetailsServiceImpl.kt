package io.soo.springboot.core.api.security

import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service

@Service
class UserDetailsServiceImpl(
    private val passwordEncoder: PasswordEncoder,
) : UserDetailsService {

    private val testEmail = "a1@a.com"
    private val testPasswordHash: String = passwordEncoder.encode("password")

    override fun loadUserByUsername(username: String): UserDetails {
        val email = username.trim().lowercase()

        if (email != testEmail) {
            throw UsernameNotFoundException("User not found by email: $email")
        }

        return UserPrincipal(
            userId = 1,
            email = email,
            passwordHash = testPasswordHash,
        )
    }
}
