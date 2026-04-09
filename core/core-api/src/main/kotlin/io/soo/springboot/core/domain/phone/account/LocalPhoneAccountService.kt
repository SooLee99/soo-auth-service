package io.soo.springboot.core.domain.phone.account

import io.soo.springboot.core.domain.local.UserUniquenessPolicy
import io.soo.springboot.core.domain.phone.login.PhoneLoginContext
import io.soo.springboot.core.domain.phone.login.PhoneLoginResolver
import io.soo.springboot.core.domain.phone.verification.PhoneNumberNormalizer
import io.soo.springboot.core.domain.phone.verification.PhoneVerificationService
import io.soo.springboot.core.enums.Gender
import io.soo.springboot.storage.db.core.LocalCredential
import io.soo.springboot.storage.db.core.LocalCredentialRepository
import io.soo.springboot.storage.db.core.User
import io.soo.springboot.storage.db.core.UserRepository
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class LocalPhoneAccountService(
    private val userRepository: UserRepository,
    private val localCredentialRepository: LocalCredentialRepository,
    private val passwordEncoder: PasswordEncoder,
    private val phoneVerificationService: PhoneVerificationService,
    private val userUniquenessPolicy: UserUniquenessPolicy,
    private val phoneNumberNormalizer: PhoneNumberNormalizer,
    private val phoneAccountFactory: PhoneAccountFactory,
    private val phoneLoginResolver: PhoneLoginResolver,
) {
    @Transactional
    fun signUpWithPhone(phoneNumber: String, phoneVerificationToken: String): User {
        phoneVerificationService.consumeVerificationToken(phoneNumber, phoneVerificationToken)
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
            ),
        )

        localCredentialRepository.save(
            LocalCredential(
                userId = user.id,
                userEmail = internalAccount.email,
                passwordHash = encodedPassword,
            ),
        )
        return user
    }

    @Transactional
    fun loginWithPhone(phoneNumber: String, phoneVerificationToken: String): User {
        phoneVerificationService.consumeVerificationToken(phoneNumber, phoneVerificationToken)
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
            ),
        )
    }
}
