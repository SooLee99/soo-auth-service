package io.soo.springboot.core.domain

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.soo.springboot.core.api.security.token.AuthTokenManager
import io.soo.springboot.core.enums.AdminUserActionType
import io.soo.springboot.core.enums.AuthProvider
import io.soo.springboot.core.enums.UserStatus
import io.soo.springboot.storage.db.core.LocalCredentialRepository
import io.soo.springboot.storage.db.core.User
import io.soo.springboot.storage.db.core.UserRepository
import io.soo.springboot.storage.db.core.UserStatusAuditLog
import io.soo.springboot.storage.db.core.UserStatusAuditLogRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.Instant

class LocalAccountServiceSoftDeleteTest {
    private val userRepository = mockk<UserRepository>()
    private val localCredentialRepository = mockk<LocalCredentialRepository>()
    private val passwordEncoder = mockk<PasswordEncoder>()
    private val authTokenManager = mockk<AuthTokenManager>(relaxed = true)
    private val auditRepository = mockk<UserStatusAuditLogRepository>()

    private val service = LocalAccountService(
        userRepository = userRepository,
        localAccountRepository = localCredentialRepository,
        passwordEncoder = passwordEncoder,
        authTokenManager = authTokenManager,
        userStatusAuditLogRepository = auditRepository,
    )

    @Test
    fun `탈퇴 처리 성공 및 보관 만료일 저장`() {
        every { userRepository.findByIdIncludingDeleted(1L) } returns activeUser()
        val saveSlot = slot<User>()
        every { userRepository.save(capture(saveSlot)) } answers { saveSlot.captured }
        every {
            auditRepository.save(1L, 1L, AdminUserActionType.SOFT_DELETE, any())
        } returns UserStatusAuditLog(1L, 1L, 1L, AdminUserActionType.SOFT_DELETE, "privacy", Instant.now())

        service.softDelete(1L, "privacy")

        val saved = saveSlot.captured
        assertEquals(UserStatus.SOFT_DELETED, saved.userStatus)
        assertNotNull(saved.deletedAt)
        assertEquals("privacy", saved.deletionReason)
        assertNotNull(saved.retentionUntil)
        verify(exactly = 1) { authTokenManager.revokeAll(1L) }
    }

    private fun activeUser() = User(
        id = 1L,
        email = "user@example.com",
        phoneNumber = null,
        name = null,
        nickname = null,
        authProvider = AuthProvider.LOCAL,
        userStatus = UserStatus.ACTIVE,
    )
}
