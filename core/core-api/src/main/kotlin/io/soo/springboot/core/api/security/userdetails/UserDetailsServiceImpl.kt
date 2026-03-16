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

        // 1) ACTIVE 사용자 우선 조회 (탈퇴 계정은 로그인 식별 대상에서 제외)
        val activeUser = userRepository.findByEmail(email)

        if (activeUser != null) {
            userStatusPolicy.validateLoginAllowed(activeUser)

            // 2) 자격증명은 userId 기준으로 조회해 탈퇴 후 재가입 충돌을 방지
            val cred = localCredentialRepository.findByUserId(activeUser.id)
                ?: throw UsernameNotFoundException("Credential not found by userId: ${activeUser.id}")

            // 3) UserDetails 반환
            return UserPrincipal(
                userId = activeUser.id,
                email = email,
                passwordHash = cred.passwordHash,
                role = activeUser.role,
                provider = activeUser.authProvider,
            )
        }

        // 탈퇴 계정이면 상태 기반 거부를 유지
        val maybeDeletedUser = userRepository.findByEmailIncludingDeleted(email)
            ?: throw UsernameNotFoundException("User not found by email: $email")
        userStatusPolicy.validateLoginAllowed(maybeDeletedUser)

        throw UsernameNotFoundException("Active user not found by email: $email")
    }
}
