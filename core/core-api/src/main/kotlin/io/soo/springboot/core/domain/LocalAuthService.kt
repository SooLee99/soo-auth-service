package io.soo.springboot.core.domain

import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

import io.soo.springboot.storage.db.core.*
import org.springframework.session.FindByIndexNameSessionRepository
import org.springframework.session.Session
import io.soo.springboot.core.api.controller.v1.response.CredentialStatus
import io.soo.springboot.core.api.controller.v1.response.SignUpProfile
import io.soo.springboot.core.api.controller.v1.response.SignUpResult
import io.soo.springboot.core.enums.AuthProvider
import io.soo.springboot.storage.db.core.LocalCredentialEntity
import io.soo.springboot.storage.db.core.LocalCredentialRepository
import io.soo.springboot.storage.db.core.OAuthIdentityRepository
import io.soo.springboot.storage.db.core.UserAccountEntity
import io.soo.springboot.storage.db.core.UserAccountRepository
import io.soo.springboot.storage.db.core.UserDeviceRepository

@Service
class LocalAuthService(
    private val userAccountRepository: UserAccountRepository,
    private val localCredentialRepository: LocalCredentialRepository,
    private val oauthIdentityRepository: OAuthIdentityRepository,
    private val userDeviceRepository: UserDeviceRepository,
    private val sessionRepository: FindByIndexNameSessionRepository<out Session>,
    private val sessionMapService: UserSessionMapService,
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

    @Transactional
    fun withdrawBySession(
        sessionId: String,
        reason: String?,
        passwordForLocal: String?,
    ) {
        val binding = sessionMapService.findActive(sessionId)
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Session revoked or not mapped")

        val userId = binding.userId
        val now = LocalDateTime.now()
        val withdrawReason = reason ?: "USER_WITHDRAWN"

        val user = userAccountRepository.findById(userId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")
        }

        // 이미 탈퇴한 계정이면 멱등 처리
        if (user.isDeleted()) {
            safeLogoutAllSessions(userId, withdrawReason)
            return
        }

        // ✅ 로컬 계정이면 비밀번호 확인, OAuth 로그인 유저는 passwordForLocal 없이 통과
        val localCredential = localCredentialRepository.findByUserId(userId)
        if (localCredential != null && !passwordForLocal.isNullOrBlank()) {
            if (!passwordEncoder.matches(passwordForLocal, localCredential.passwordHash)) {
                throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Password mismatch")
            }
        } else if (localCredential != null && passwordForLocal.isNullOrBlank()) {
           throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Password required")
        }

        // 1) ✅ 모든 세션 즉시 로그아웃 + 세션매핑 revoke
        safeLogoutAllSessions(userId, withdrawReason)

        // 2) ✅ user_account 소프트딜리트 + 익명화(권장)
        user.softDelete(reason = withdrawReason, at = now)

        // PII 익명화(정책에 맞게 조정)
        user.email = null
        user.emailVerified = false
        user.name = null
        user.givenName = null
        user.familyName = null
        user.nickname = null
        user.locale = null
        user.gender = null
        user.ageRange = null
        user.birthyear = null
        user.birthday = null
        user.phoneNumber = null
        user.profileImageUrl = null
        user.thumbnailImageUrl = null

        userAccountRepository.save(user)

        // 3) ✅ OAuth identity 소프트딜리트
        oauthIdentityRepository.softDeleteByUserId(userId, now, withdrawReason)

        // 4) ✅ 로컬 credential 소프트딜리트(+ 해시 무력화 옵션)
        localCredentialRepository.softDeleteByUserId(userId, now, withdrawReason)

        // 5) ✅ 디바이스 레코드 비활성화(또는 소프트딜리트)
        userDeviceRepository.markWithdrawnByUserId(userId, now, withdrawReason)
    }

    private fun safeLogoutAllSessions(userId: Long, reason: String) {
        val sessionIds = sessionMapService.activeSessionIds(userId, deviceId = null)
        sessionIds.forEach { sid ->
            sessionRepository.deleteById(sid)
            sessionMapService.revokeSession(sid, reason)
        }
    }
}
