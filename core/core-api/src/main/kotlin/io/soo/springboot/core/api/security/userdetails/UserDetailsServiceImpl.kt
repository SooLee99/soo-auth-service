package io.soo.springboot.core.api.security.userdetails

import io.soo.springboot.core.domain.UserStatusPolicy
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
    private val userStatusPolicy: UserStatusPolicy,
) : UserDetailsService {

    override fun loadUserByUsername(username: String): UserDetails {
        val email = username.trim().lowercase()

        // 1) 로컬 자격증명 조회 (이메일로 로그인)
        val cred = localCredentialRepository.findByUserEmail(email)
            ?: throw UsernameNotFoundException("User not found by email: $email")

        // 2)  user 상태/권한 확인이 필요하면 UserEntity도 조회
        val user = userRepository.findByIdIncludingDeleted(cred.userId)
            ?: throw UsernameNotFoundException("User not found by id: ${cred.userId}")

        userStatusPolicy.validateLoginAllowed(user)

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
