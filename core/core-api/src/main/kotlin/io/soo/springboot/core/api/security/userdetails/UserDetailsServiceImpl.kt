package io.soo.springboot.core.api.security.userdetails

import io.soo.springboot.core.domain.token.UserPrincipal
import io.soo.springboot.storage.db.core.JpaLocalCredentialRepository
import io.soo.springboot.storage.db.core.UserJpaRepository
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service

@Service
class UserDetailsServiceImpl(
    private val jpaLocalCredentialRepository: JpaLocalCredentialRepository,
    private val userJpaRepository: UserJpaRepository,
) : UserDetailsService {

    override fun loadUserByUsername(username: String): UserDetails {
        val email = username.trim().lowercase()

        // 1) 로컬 자격증명 조회 (이메일로 로그인)
        val cred = jpaLocalCredentialRepository.findByUserEmail(email)
            ?: throw UsernameNotFoundException("User not found by email: $email")

        // 2)  user 상태/권한 확인이 필요하면 UserEntity도 조회
        val user = userJpaRepository.findById(cred.userId)
            .orElseThrow { UsernameNotFoundException("User not found by id: ${cred.userId}") }

        // 3) UserDetails 반환
        return UserPrincipal(
            userId = user.id,
            email = email,
            passwordHash = cred.passwordHash,
            role = user.role,
            provider = user.authProvider,
        )
    }
}
