package io.soo.springboot.core.domain

import io.mockk.mockk
import io.mockk.verify
import io.soo.springboot.core.domain.local.LocalAccountService
import io.soo.springboot.core.domain.local.UserUniquenessPolicy
import io.soo.springboot.core.domain.phone.verification.PhoneNumberNormalizer
import io.soo.springboot.core.domain.phone.verification.PhoneVerificationService
import io.soo.springboot.core.domain.token.TokenRevocationService
import io.soo.springboot.core.domain.user.UserLifecycleService
import io.soo.springboot.storage.db.core.LocalCredentialRepository
import io.soo.springboot.storage.db.core.UserRepository
import org.junit.jupiter.api.Test
import org.springframework.security.crypto.password.PasswordEncoder

class LocalAccountServiceSoftDeleteTest {
    private val userRepository = mockk<UserRepository>()
    private val localCredentialRepository = mockk<LocalCredentialRepository>()
    private val passwordEncoder = mockk<PasswordEncoder>()
    private val tokenRevocationService = mockk<TokenRevocationService>(relaxed = true)
    private val phoneVerificationService = mockk<PhoneVerificationService>(relaxed = true)
    private val userUniquenessPolicy = mockk<UserUniquenessPolicy>(relaxed = true)
    private val phoneNumberNormalizer = PhoneNumberNormalizer()
    private val userLifecycleService = mockk<UserLifecycleService>(relaxed = true)

    private val service = LocalAccountService(
        userRepository = userRepository,
        localCredentialRepository = localCredentialRepository,
        passwordEncoder = passwordEncoder,
        tokenRevocationService = tokenRevocationService,
        phoneVerificationService = phoneVerificationService,
        userUniquenessPolicy = userUniquenessPolicy,
        phoneNumberNormalizer = phoneNumberNormalizer,
        userLifecycleService = userLifecycleService,
    )

    @Test
    fun `탈퇴를 사용자 수명주기 서비스에 위임`() {
        service.softDeleteUser(userId = 1L, reason = "privacy", actorUserId = 99L)

        verify(exactly = 1) {
            userLifecycleService.softDelete(userId = 1L, reason = "privacy", actorUserId = 99L)
        }
    }
}
