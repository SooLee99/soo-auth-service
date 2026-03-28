package io.soo.springboot.core.domain

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.soo.springboot.core.domain.admin.UserBlock
import io.soo.springboot.core.enums.AdminUserActionType
import io.soo.springboot.core.enums.AuthProvider
import io.soo.springboot.core.enums.UserStatus
import io.soo.springboot.storage.db.core.User
import io.soo.springboot.storage.db.core.UserRepository
import io.soo.springboot.storage.db.core.UserStatusAuditLog
import io.soo.springboot.storage.db.core.UserStatusAuditLogRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import java.time.Instant

class UserBlockTest {
    private val userRepository = mockk<UserRepository>()
    private val auditRepository = mockk<UserStatusAuditLogRepository>()
    private val service = UserBlock(userRepository, auditRepository)

    @Test
    fun `관리자 차단 성공`() {
        every { userRepository.findByIdIncludingDeleted(1L) } returns activeUser()
        val saveSlot = slot<User>()
        every { userRepository.save(capture(saveSlot)) } answers { saveSlot.captured }
        every {
            auditRepository.save(1L, 100L, AdminUserActionType.BLOCK, any())
        } returns UserStatusAuditLog(1L, 1L, 100L, AdminUserActionType.BLOCK, "abuse", Instant.now())

        val result = service.block(1L, 100L, "abuse")

        assertEquals(UserStatus.BLOCKED, result.userStatus)
        assertEquals(true, result.blocked)
        assertEquals("abuse", result.blockedReason)
    }

    @Test
    fun `관리자 차단 해제 성공`() {
        every { userRepository.findByIdIncludingDeleted(1L) } returns activeUser().copy(
            userStatus = UserStatus.BLOCKED,
            blocked = true,
            blockedAt = Instant.now(),
            blockedByAdminId = 100L,
        )
        val saveSlot = slot<User>()
        every { userRepository.save(capture(saveSlot)) } answers { saveSlot.captured }
        every {
            auditRepository.save(1L, 100L, AdminUserActionType.UNBLOCK, null)
        } returns UserStatusAuditLog(1L, 1L, 100L, AdminUserActionType.UNBLOCK, null, Instant.now())

        val result = service.unblock(1L, 100L)
        assertEquals(UserStatus.ACTIVE, result.userStatus)
        assertEquals(false, result.blocked)
    }

    @Test
    fun `차단 목록 조회 성공`() {
        val pageable = PageRequest.of(0, 20)
        every { userRepository.blocked(pageable) } returns PageImpl(listOf(
            activeUser().copy(userStatus = UserStatus.BLOCKED, blocked = true, blockedReason = "policy_violation")
        ))

        val page = service.blocked(pageable)
        assertEquals(1, page.totalElements)
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
