package io.soo.springboot.core.domain.local

import io.soo.springboot.core.domain.phone.verification.PhoneNumberNormalizer
import io.soo.springboot.core.domain.token.TokenRevocationService
import io.soo.springboot.core.domain.user.UserLifecycleService
import io.soo.springboot.core.enums.Gender
import io.soo.springboot.storage.db.core.LocalCredential
import io.soo.springboot.storage.db.core.LocalCredentialRepository
import io.soo.springboot.storage.db.core.User
import io.soo.springboot.storage.db.core.UserRepository
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

data class LocalSignUpCommand(
    val email: String,
    val password: String,
    val phoneNumber: String? = null,
    val phoneVerificationToken: String? = null,
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
    private val userRepository: UserRepository,
    private val localCredentialRepository: LocalCredentialRepository,
    private val passwordEncoder: PasswordEncoder,
    private val tokenRevocationService: TokenRevocationService,
    private val userUniquenessPolicy: UserUniquenessPolicy,
    private val phoneNumberNormalizer: PhoneNumberNormalizer,
    private val userLifecycleService: UserLifecycleService,
) {
    @Transactional
    fun signUpByEmail(
        email: String,
        password: String,
        phoneNumber: String?,
        phoneVerificationToken: String?,
        gender: Gender,
        locale: String,
        nickname: String?,
        name: String?,
        profileImageUrl: String?,
        thumbnailImageUrl: String?,
        birthyear: String?,
        birthday: String?,
    ): User {
        return signUp(
            LocalSignUpCommand(
                email = email,
                password = password,
                phoneNumber = phoneNumber,
                phoneVerificationToken = phoneVerificationToken,
                gender = gender,
                locale = locale,
                nickname = nickname,
                name = name,
                profileImageUrl = profileImageUrl,
                thumbnailImageUrl = thumbnailImageUrl,
                birthyear = birthyear,
                birthday = birthday,
            ),
        )
    }

    @Transactional
    fun signUp(command: LocalSignUpCommand): User {
        val normalizedPhone = command.phoneNumber?.let(phoneNumberNormalizer::normalize)

        userUniquenessPolicy.validateSignUp(
            email = command.email,
            rawPhone = command.phoneNumber,
            normalizedPhone = normalizedPhone,
        )

        val user = userRepository.save(
            User.createLocal(
                email = command.email,
                phoneNumber = normalizedPhone,
                emailVerified = false,
                phoneVerified = false,
                name = command.name,
                nickname = command.nickname,
                gender = command.gender,
                locale = command.locale,
                birthyear = command.birthyear,
                birthday = command.birthday,
                profileImageUrl = command.profileImageUrl,
                thumbnailImageUrl = command.thumbnailImageUrl,
            ),
        )

        localCredentialRepository.save(
            LocalCredential(
                userId = user.id,
                userEmail = command.email,
                passwordHash = passwordEncoder.encode(command.password),
            ),
        )
        return user
    }

    @Transactional
    fun logout(jwt: Jwt, deviceId: String, refreshToken: String?, logoutAll: Boolean) {
        tokenRevocationService.revokeOnLogout(
            jwt = jwt,
            deviceId = deviceId,
            refreshToken = refreshToken,
            logoutAll = logoutAll,
        )
    }

    @Transactional
    fun softDeleteUser(userId: Long, reason: String?, actorUserId: Long? = null) {
        userLifecycleService.softDelete(userId = userId, reason = reason, actorUserId = actorUserId)
    }
}
