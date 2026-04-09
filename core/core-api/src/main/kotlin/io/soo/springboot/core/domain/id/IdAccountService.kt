package io.soo.springboot.core.domain.id

import io.soo.springboot.core.domain.local.UserUniquenessPolicy
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

data class IdSignUpCommand(
    val loginId: String,
    val password: String,
    val phoneNumber: String,
    val phoneVerificationToken: String,
)

@Service
class IdAccountService(
    private val userRepository: UserRepository,
    private val localCredentialRepository: LocalCredentialRepository,
    private val passwordEncoder: PasswordEncoder,
    private val phoneVerificationService: PhoneVerificationService,
    private val userUniquenessPolicy: UserUniquenessPolicy,
    private val phoneNumberNormalizer: PhoneNumberNormalizer,
) {
    @Transactional
    fun signUp(command: IdSignUpCommand): User {
        val normalizedLoginId = command.loginId.trim().lowercase()
        phoneVerificationService.consumeVerificationToken(command.phoneNumber, command.phoneVerificationToken)

        val normalizedPhone = phoneNumberNormalizer.normalize(command.phoneNumber)
        userUniquenessPolicy.validateLoginIdAvailable(normalizedLoginId)
        userUniquenessPolicy.validatePhoneAvailable(command.phoneNumber, normalizedPhone)

        val user = userRepository.save(
            User.createLocal(
                email = "id-$normalizedLoginId@local.internal",
                loginId = normalizedLoginId,
                phoneNumber = normalizedPhone,
                emailVerified = false,
                phoneVerified = true,
                gender = Gender.UNKNOWN,
                locale = "ko-KR",
            ),
        )

        localCredentialRepository.save(
            LocalCredential(
                userId = user.id,
                userEmail = user.email,
                passwordHash = passwordEncoder.encode(command.password),
            ),
        )
        return user
    }
}
