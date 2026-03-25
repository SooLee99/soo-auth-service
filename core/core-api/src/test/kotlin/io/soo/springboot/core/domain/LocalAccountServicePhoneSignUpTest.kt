package io.soo.springboot.core.domain

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.soo.springboot.core.domain.auth.TokenRevocationService
import io.soo.springboot.core.enums.AuthProvider
import io.soo.springboot.core.enums.Gender
import io.soo.springboot.core.support.error.CoreException
import io.soo.springboot.core.support.error.ErrorType
import io.soo.springboot.storage.db.core.LocalCredential
import io.soo.springboot.storage.db.core.LocalCredentialRepository
import io.soo.springboot.storage.db.core.User
import io.soo.springboot.storage.db.core.UserRepository
import io.soo.springboot.storage.db.core.UserStatusAuditLogRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.security.crypto.password.PasswordEncoder

class LocalAccountServicePhoneSignUpTest {
    private val userRepository = mockk<UserRepository>()
    private val localCredentialRepository = mockk<LocalCredentialRepository>()
    private val passwordEncoder = mockk<PasswordEncoder>()
    private val tokenRevocationService = mockk<TokenRevocationService>(relaxed = true)
    private val auditRepository = mockk<UserStatusAuditLogRepository>(relaxed = true)

    private val service = LocalAccountService(
        userRepository = userRepository,
        localAccountRepository = localCredentialRepository,
        passwordEncoder = passwordEncoder,
        tokenRevocationService = tokenRevocationService,
        userStatusAuditLogRepository = auditRepository,
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

        val user = service.signUpByPhone("+82 10-1234-5678")

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
            service.signUpByPhone("010-1234-5678")
        }
        assertEquals(ErrorType.DUPLICATE_PHONE_NUMBER, ex.errorType)
    }
}
