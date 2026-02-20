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
import io.soo.springboot.storage.db.core.JpaLocalCredentialRepository
import io.soo.springboot.core.domain.denylist.JwtDenylistStore
import io.soo.springboot.core.domain.token.JsonWebTokenService
import io.soo.springboot.core.enums.Gender


data class LocalSignUpCommand(
    val email: String,
    val password: String,
    val phoneNumber: String,
    val gender: Gender,
    val locale: String = "ko-KR",
    val nickname: String? = null,
    val name: String? = null,
    val profileImageUrl: String? = null,
    val thumbnailImageUrl: String? = null,
    val birthyear: String? = null,
    val birthday: String? = null,
)

@Service
class LocalAccountService(
    private val userJpaRepository: UserJpaRepository,
    private val localAccountRepository: JpaLocalCredentialRepository,
    private val passwordEncoder: PasswordEncoder,
    private val denylistStore: JwtDenylistStore,
    private val jsonWebTokenService: JsonWebTokenService,
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

    /**
     * ✅ logout
     * - access token denylist(jti)
     * - refresh token revoke (단건 or device or all)
     */
    @Transactional
    fun logout(jwt: Jwt, deviceId: String, refreshToken: String?, logoutAll: Boolean) {
        // 1) access token denylist(jti)
        val jti = jwt.id
        val exp = jwt.expiresAt

        if (!jti.isNullOrBlank() && exp != null) {
            val ttl = Duration.between(Instant.now(), exp).coerceAtLeast(Duration.ZERO)
            if (!ttl.isZero) denylistStore.deny(jti, ttl)
        }

        // userId claim
        val uid = (jwt.claims["uid"] as? Number)?.toLong()

        // 2) refresh revoke
        if (logoutAll) {
            if (uid != null) jsonWebTokenService.revokeAll(uid)
            return
        }

        val rt = refreshToken.orEmpty().trim()

        // 바디로 refreshToken을 보내면 단건 revoke
        if (rt.isNotBlank()) {
            jsonWebTokenService.revoke(rt)
            return
        }

        // refreshToken이 없으면 "현재 디바이스" 기준 revoke (권장)
        if (uid != null && deviceId.isNotBlank()) {
            jsonWebTokenService.revokeByDevice(uid, deviceId)
        }
    }
}
