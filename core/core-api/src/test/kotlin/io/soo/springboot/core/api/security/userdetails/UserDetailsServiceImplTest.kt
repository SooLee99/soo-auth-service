package io.soo.springboot.core.api.security.userdetails

import io.mockk.every
import io.mockk.mockk
import io.soo.springboot.core.domain.AccountStatusDeniedException
import io.soo.springboot.core.domain.UserStatusPolicy
import io.soo.springboot.core.enums.AuthProvider
import io.soo.springboot.core.enums.UserStatus
import io.soo.springboot.storage.db.core.LocalCredential
import io.soo.springboot.storage.db.core.LocalCredentialRepository
import io.soo.springboot.storage.db.core.User
import io.soo.springboot.storage.db.core.UserRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Instant

class UserDetailsServiceImplTest {
    private val localCredentialRepository = mockk<LocalCredentialRepository>()
    private val userRepository = mockk<UserRepository>()
    private val userStatusPolicy = UserStatusPolicy()
    private val service = UserDetailsServiceImpl(localCredentialRepository, userRepository, userStatusPolicy)

    @Test
    fun `정상 사용자 로그인 principal 로딩 성공`() {
        every { localCredentialRepository.findByUserEmail("user@example.com") } returns credential()
        every { userRepository.findByIdIncludingDeleted(1L) } returns user(status = UserStatus.ACTIVE)

        val principal = service.loadUserByUsername("user@example.com") as UserPrincipal
        assertEquals(1L, principal.userId)
        assertEquals("user@example.com", principal.email)
    }

    @Test
    fun `차단 사용자 로그인 거부`() {
        every { localCredentialRepository.findByUserEmail("user@example.com") } returns credential()
        every { userRepository.findByIdIncludingDeleted(1L) } returns user(
            status = UserStatus.BLOCKED,
            blocked = true,
            blockedAt = Instant.now(),
            blockedByAdminId = 9L,
        )

        assertThrows(AccountStatusDeniedException::class.java) {
            service.loadUserByUsername("user@example.com")
        }
    }

    @Test
    fun `소프트 탈퇴 사용자 로그인 거부`() {
        every { localCredentialRepository.findByUserEmail("user@example.com") } returns credential()
        every { userRepository.findByIdIncludingDeleted(1L) } returns user(
            status = UserStatus.SOFT_DELETED,
            deletedAt = Instant.now(),
            deletionReason = "user_request",
        )

        assertThrows(AccountStatusDeniedException::class.java) {
            service.loadUserByUsername("user@example.com")
        }
    }

    private fun credential() = LocalCredential(
        userId = 1L,
        userEmail = "user@example.com",
        passwordHash = "{bcrypt}hash",
    )

    private fun user(
        status: UserStatus,
        blocked: Boolean = false,
        blockedAt: Instant? = null,
        blockedByAdminId: Long? = null,
        deletedAt: Instant? = null,
        deletionReason: String? = null,
    ) = User(
        id = 1L,
        email = "user@example.com",
        phoneNumber = null,
        name = null,
        nickname = null,
        authProvider = AuthProvider.LOCAL,
        userStatus = status,
        blocked = blocked,
        blockedAt = blockedAt,
        blockedByAdminId = blockedByAdminId,
        deletedAt = deletedAt,
        deletionReason = deletionReason,
    )
}
