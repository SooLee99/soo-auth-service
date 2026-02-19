package io.soo.springboot.core.domain

import java.time.Duration
import java.time.Instant
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.security.crypto.password.PasswordEncoder


import io.soo.springboot.core.enums.AuthProvider
import io.soo.springboot.core.support.error.CoreException
import io.soo.springboot.core.support.error.ErrorType

import io.soo.springboot.storage.db.core.UserEntity
import io.soo.springboot.storage.db.core.UserJpaRepository
import io.soo.springboot.storage.db.core.LocalCredentialEntity
import io.soo.springboot.storage.db.core.LocalCredentialJpaRepository
import io.soo.springboot.core.domain.denylist.JwtDenylistStore
import io.soo.springboot.core.domain.token.JwtService

@Service
class LocalAccountService(
    private val userJpaRepository: UserJpaRepository,
    private val localAccountRepository: LocalCredentialJpaRepository,
    private val passwordEncoder: PasswordEncoder,
    private val denylistStore: JwtDenylistStore,
    private val jwtService: JwtService,
) {
    @Transactional
    fun signUp(cmd: LocalSignUpCommand): UserEntity {
        // 1) 중복 이메일/전화번호 검사
        if (userJpaRepository.existsByEmail(cmd.email))
            throw CoreException(ErrorType.DUPLICATE_EMAIL, data = mapOf("email" to cmd.email))
        if (userJpaRepository.existsByPhoneNumber(cmd.phoneNumber))
            throw CoreException(ErrorType.DUPLICATE_PHONE_NUMBER, data = mapOf("phoneNumber" to cmd.phoneNumber))

        // 2) 사용자 정보 저장
        val user = userJpaRepository.save(
            UserEntity(
                email = cmd.email,
                emailVerified = false,
                phoneNumber = cmd.phoneNumber,
                phoneVerified = false,
                name = cmd.name,
                nickname = cmd.nickname,
                gender = cmd.gender,
                locale = cmd.locale,
                birthyear = cmd.birthyear,
                birthday = cmd.birthday,
                profileImageUrl = cmd.profileImageUrl,
                thumbnailImageUrl = cmd.thumbnailImageUrl,
                authProvider = AuthProvider.LOCAL,
            )
        )

        // 3) 로컬 자격증명 저장
        localAccountRepository.save(
            LocalCredentialEntity(
                userId = user.id,
                userEmail = cmd.email,
                passwordHash = passwordEncoder.encode(cmd.password),
            )
        )
        return user
    }

    fun logout(jwt: Jwt, refreshToken: String, isLogoutAll: Boolean) {
        // 1) access token denylist(jti)
        val jti = jwt.id
        val exp = jwt.expiresAt

        // 2) expiration time check
        if (!jti.isNullOrBlank() && exp != null) {
            val ttl = Duration.between(Instant.now(), exp).coerceAtLeast(Duration.ZERO)
            if (!ttl.isZero) denylistStore.deny(jti, ttl)
        }

        // 3) refresh token revoke
        val refreshToken = refreshToken
        if (refreshToken.isNotBlank()) {
            jwtService.revoke(refreshToken)
        }

        // 4) 전체 로그아웃(옵션): 토큰에 uid claim이 있어야 함
        if (isLogoutAll) {
            val uid = (jwt.claims["uid"] as? Number)?.toLong()
            if (uid != null) jwtService.revokeAll(uid)
        }
    }

}
