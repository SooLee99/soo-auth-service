package io.soo.springboot.core.api.security

import io.soo.springboot.storage.db.core.LocalCredentialRepository
import io.soo.springboot.storage.db.core.UserRepository
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service

@Service
class UserDetailsServiceImpl(
    private val localCredentialRepository: LocalCredentialRepository,
    private val userRepository: UserRepository,
) : UserDetailsService {

    override fun loadUserByUsername(username: String): UserDetails {
        val email = username.trim().lowercase()

        // 1) 로컬 자격증명 조회 (이메일로 로그인)
        val cred = localCredentialRepository.findByUserEmail(email)
            ?: throw UsernameNotFoundException("User not found by email: $email")

        // 2)  user 상태/권한 확인이 필요하면 UserEntity도 조회
        val user = userRepository.findById(cred.userId)
            .orElseThrow { UsernameNotFoundException("User not found by id: ${cred.userId}") }

        // 3) UserDetails 반환
        return UserPrincipal(
            userId = user.id,
            email = email,
            passwordHash = cred.passwordHash,
        )
    }
}
