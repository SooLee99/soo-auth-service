package io.soo.springboot.core.domain

import io.soo.springboot.core.api.security.principal.UserPrincipal
import io.soo.springboot.storage.db.core.LocalCredentialJpaRepository
import io.soo.springboot.storage.db.core.UserJpaRepository
import org.springframework.context.annotation.Primary
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Primary
@Service
class DbUserPrincipalLoader(
    private val userRepo: UserJpaRepository,
    private val credentialRepo: LocalCredentialJpaRepository,
) : UserPrincipalLoader, UserDetailsService {

    @Transactional(readOnly = true)
    override fun loadByUserId(userId: Long): UserDetails {
        val user = userRepo.findById(userId).orElseThrow {
            UsernameNotFoundException("User not found: userId=$userId")
        }
        val email = user.email ?: throw UsernameNotFoundException("User email is null: userId=$userId")

        // 로컬 계정이면 credential이 존재, 소셜 계정이면 없을 수 있음
        val passwordHash = credentialRepo.findByUserId(userId)?.passwordHash ?: ""

        return UserPrincipal(
            userId = userId,
            email = email,
            passwordHash = passwordHash,
            role = user.role,
        )
    }

    @Transactional(readOnly = true)
    override fun loadByEmail(email: String): UserDetails {
        val user = userRepo.findByEmail(email)
            ?: throw UsernameNotFoundException("User not found: email=$email")

        return UserPrincipal(
            userId =  user.id,
            email = email,
            passwordHash = credentialRepo.findByUserEmail(email)?.passwordHash ?: "",
            role = user.role,
        )
    }

    /**
     * Spring Security 표준 로그인 흐름(UsernamePasswordAuthenticationToken)에서 사용
     */
    override fun loadUserByUsername(username: String): UserDetails = loadByEmail(username)
}
