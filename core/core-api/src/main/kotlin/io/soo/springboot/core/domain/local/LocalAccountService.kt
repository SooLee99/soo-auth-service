package io.soo.springboot.core.domain.local

import io.soo.springboot.core.domain.local.phone.PhoneVerificationService
import io.soo.springboot.core.domain.local.phone.login.PhoneLoginContext
import io.soo.springboot.core.domain.local.phone.login.PhoneLoginResolver
import io.soo.springboot.core.domain.local.policy.UserUniquenessPolicy
import io.soo.springboot.core.domain.local.support.PhoneAccountFactory
import io.soo.springboot.core.domain.local.support.PhoneNumberNormalizer
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
    val phoneNumber: String,
    val phoneVerificationToken: String,
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
    private val phoneVerificationService: PhoneVerificationService,
    private val userUniquenessPolicy: UserUniquenessPolicy,
    private val phoneNumberNormalizer: PhoneNumberNormalizer,
    private val phoneAccountFactory: PhoneAccountFactory,
    private val phoneLoginResolver: PhoneLoginResolver,
    private val userLifecycleService: UserLifecycleService,
) {
    @Transactional
    fun signUp(command: LocalSignUpCommand): User {
        phoneVerificationService.consume(command.phoneNumber, command.phoneVerificationToken)
        val normalizedPhone = phoneNumberNormalizer.normalize(command.phoneNumber)

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
            )
        )

        localCredentialRepository.save(
            LocalCredential(
                userId = user.id,
                userEmail = command.email,
                passwordHash = passwordEncoder.encode(command.password),
            )
        )
        return user
    }

    @Transactional
    fun signUpWithPhone(phoneNumber: String, phoneVerificationToken: String): User {
        phoneVerificationService.consume(phoneNumber, phoneVerificationToken)
        val normalizedPhone = phoneNumberNormalizer.normalize(phoneNumber)
        userUniquenessPolicy.validatePhoneAvailable(phoneNumber, normalizedPhone)

        val internalAccount = phoneAccountFactory.create(normalizedPhone)
        val encodedPassword = passwordEncoder.encode(internalAccount.rawPassword)

        val user = userRepository.save(
            User.createLocal(
                email = internalAccount.email,
                phoneNumber = normalizedPhone,
                emailVerified = false,
                phoneVerified = false,
                name = null,
                nickname = null,
                gender = Gender.UNKNOWN,
                locale = "ko-KR",
            )
        )

        localCredentialRepository.save(
            LocalCredential(
                userId = user.id,
                userEmail = internalAccount.email,
                passwordHash = encodedPassword,
            )
        )
        return user
    }

    @Transactional
    fun loginWithPhone(phoneNumber: String, phoneVerificationToken: String): User {
        phoneVerificationService.consume(phoneNumber, phoneVerificationToken)
        val normalizedPhone = phoneNumberNormalizer.normalize(phoneNumber)

        val activeUser = userRepository.findByPhoneNumber(normalizedPhone)
        val userIncludingDeleted = if (activeUser == null) {
            userRepository.findByPhoneNumberIncludingDeleted(normalizedPhone)
        } else {
            null
        }

        return phoneLoginResolver.resolve(
            PhoneLoginContext(
                activeUser = activeUser,
                userIncludingDeleted = userIncludingDeleted,
            )
        )
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

    // backward compatibility while endpoints/tests are being migrated
    @Transactional
    fun signup(cmd: LocalSignUpCommand): User = signUp(cmd)

    @Transactional
    fun signupPhone(phoneNumber: String, phoneVerificationToken: String): User = signUpWithPhone(phoneNumber, phoneVerificationToken)

    @Transactional
    fun loginByPhone(phoneNumber: String, phoneVerificationToken: String): User = loginWithPhone(phoneNumber, phoneVerificationToken)

    @Transactional
    fun softDelete(userId: Long, reason: String?, actorUserId: Long? = null) = softDeleteUser(userId, reason, actorUserId)
}
