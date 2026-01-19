package io.soo.springboot.core.domain

import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

import io.soo.springboot.core.api.controller.v1.response.CredentialStatus
import io.soo.springboot.core.api.controller.v1.response.SignUpProfile
import io.soo.springboot.core.api.controller.v1.response.SignUpResult
import io.soo.springboot.core.enums.AuthProvider
import io.soo.springboot.storage.db.core.LocalCredentialEntity
import io.soo.springboot.storage.db.core.LocalCredentialRepository
import io.soo.springboot.storage.db.core.UserAccountEntity
import io.soo.springboot.storage.db.core.UserAccountRepository

@Service
class LocalAuthService(
    private val userAccountRepository: UserAccountRepository,
    private val localCredentialRepository: LocalCredentialRepository,
    private val passwordEncoder: PasswordEncoder,
) {

    /**
     * ✅ 로컬 회원가입만 수행합니다.
     */
    @Transactional
    fun signUp(
        email: String,
        rawPassword: String,
        name: String?,
        nickname: String?,
        profileImageUrl: String?,
        thumbnailImageUrl: String?,
        birthyear: String?,
        birthday: String?,
    ): SignUpResult {
        if (userAccountRepository.findByEmail(email) != null) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "이미 가입된 이메일이 존재합니다.")
        }

        // TODO: 형식 검증 -> 추후 DTO 어노테이션으로 이동
        if (!birthyear.isNullOrBlank()) require(birthyear.matches(Regex("""^\d{4}$"""))) { "birthyear 형식이 올바르지 않습니다." }
        if (!birthday.isNullOrBlank()) require(birthday.matches(Regex("""^(0[1-9]|1[0-2])-(0[1-9]|[12]\d|3[01])$"""))) { "birthday 형식이 올바르지 않습니다." }

        val user = userAccountRepository.save(
            UserAccountEntity(
                email = email,
                emailVerified = false,
                nickname = nickname,
                name = name,
                givenName = null,
                familyName = null,
                locale = null,
                birthyear = birthyear,
                birthday = birthday,
                gender = null,
                ageRange = null,
                phoneNumber = null,
                profileImageUrl = profileImageUrl,
                thumbnailImageUrl = thumbnailImageUrl,
                lastLoginProvider = AuthProvider.LOCAL,
                lastLoginAt = Instant.now(),
            ),
        )

        val credential = localCredentialRepository.save(
            LocalCredentialEntity(
                userId = user.id,
                passwordHash = passwordEncoder.encode(rawPassword),
                passwordUpdatedAt = LocalDateTime.now(),
            ),
        )

        return SignUpResult(
            userId = user.id,
            email = user.email,
            emailVerified = user.emailVerified,
            provider = AuthProvider.LOCAL,
            profile = SignUpProfile(
                name = user.name,
                nickname = user.nickname,
                profileImageUrl = user.profileImageUrl,
                thumbnailImageUrl = user.thumbnailImageUrl,
                birthyear = user.birthyear,
                birthday = user.birthday,
            ),
            credentialStatus = CredentialStatus(
                passwordUpdatedAt = credential.passwordUpdatedAt,
                failedLoginCount = credential.failedLoginCount,
                lockUntil = credential.lockUntil,
            ),
            createdAt = user.createdAt,
        )
    }

    private fun toLocalDateOrNull(birthyear: String?, birthday: String?): LocalDate? {
        if (birthyear.isNullOrBlank() || birthday.isNullOrBlank()) return null
        val parts = birthday.split("-")
        if (parts.size != 2) return null
        val mm = parts[0].toIntOrNull() ?: return null
        val dd = parts[1].toIntOrNull() ?: return null
        val yyyy = birthyear.toIntOrNull() ?: return null
        return runCatching { LocalDate.of(yyyy, mm, dd) }.getOrNull()
    }
}
