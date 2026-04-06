package io.soo.springboot.core.domain.phone.account

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.soo.springboot.core.domain.local.UserUniquenessPolicy
import io.soo.springboot.core.domain.phone.login.PhoneLoginResolver
import io.soo.springboot.core.domain.phone.verification.PhoneNumberNormalizer
import io.soo.springboot.core.domain.phone.verification.PhoneVerificationService
import io.soo.springboot.core.enums.AuthProvider
import io.soo.springboot.core.enums.Gender
import io.soo.springboot.core.support.error.CoreException
import io.soo.springboot.core.support.error.ErrorType
import io.soo.springboot.storage.db.core.LocalCredential
import io.soo.springboot.storage.db.core.LocalCredentialRepository
import io.soo.springboot.storage.db.core.User
import io.soo.springboot.storage.db.core.UserRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.security.crypto.password.PasswordEncoder

class LocalPhoneAccountServiceTest {
    private val userRepository = mockk<UserRepository>()
    private val localCredentialRepository = mockk<LocalCredentialRepository>()
    private val passwordEncoder = mockk<PasswordEncoder>()
    private val phoneVerificationService = mockk<PhoneVerificationService>(relaxed = true)
    private val userUniquenessPolicy = UserUniquenessPolicy(userRepository)
    private val phoneNumberNormalizer = PhoneNumberNormalizer()
    private val phoneAccountFactory = PhoneAccountFactory(userRepository)
    private val phoneLoginResolver = mockk<PhoneLoginResolver>(relaxed = true)

    private val service = LocalPhoneAccountService(
        userRepository = userRepository,
        localCredentialRepository = localCredentialRepository,
        passwordEncoder = passwordEncoder,
        phoneVerificationService = phoneVerificationService,
        userUniquenessPolicy = userUniquenessPolicy,
        phoneNumberNormalizer = phoneNumberNormalizer,
        phoneAccountFactory = phoneAccountFactory,
        phoneLoginResolver = phoneLoginResolver,
    )

    @Test
    fun `전화번호 전용 회원가입 성공`() {
        every { userRepository.existsByPhoneNumber(any()) } returns false
        every { userRepository.existsByEmail(any()) } returns false
        every { passwordEncoder.encode(any()) } returns "encoded-password"

        val savedUser = slot<User>()
        every { userRepository.save(capture(savedUser)) } answers { savedUser.captured.copy(id = 1L) }

        val savedCredential = slot<LocalCredential>()
        every { localCredentialRepository.save(capture(savedCredential)) } answers { savedCredential.captured.copy(id = 10L) }

        val user = service.signUpWithPhone("+82 10-1234-5678", "verified-phone-token")

        assertEquals(1L, user.id)
        assertEquals("+821012345678", savedUser.captured.phoneNumber)
        assertEquals(Gender.UNKNOWN, savedUser.captured.gender)
        assertEquals(AuthProvider.LOCAL, savedUser.captured.authProvider)
        assertTrue(savedUser.captured.email.startsWith("phone-"))
        assertTrue(savedUser.captured.email.endsWith("@local.internal"))

        assertEquals(1L, savedCredential.captured.userId)
        assertEquals(savedUser.captured.email, savedCredential.captured.userEmail)
        assertEquals("encoded-password", savedCredential.captured.passwordHash)

        verify(exactly = 1) { passwordEncoder.encode(any()) }
    }

    @Test
    fun `중복 전화번호면 예외`() {
        every { userRepository.existsByPhoneNumber(any()) } returns true

        val ex = org.junit.jupiter.api.Assertions.assertThrows(CoreException::class.java) {
            service.signUpWithPhone("010-1234-5678", "verified-phone-token")
        }
        assertEquals(ErrorType.DUPLICATE_PHONE_NUMBER, ex.errorType)
    }
}
