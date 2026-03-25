package io.soo.springboot.core.domain

import io.soo.springboot.core.enums.AuthProvider
import io.soo.springboot.core.enums.LoginDenyReason
import io.soo.springboot.core.enums.UserStatus
import io.soo.springboot.core.support.error.AccountStatusDeniedException
import io.soo.springboot.storage.db.core.User
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Instant

class UserStatusPolicyTest {
    private val policy = UserStatusPolicy()

    @Test
    fun `ACTIVE 사용자 로그인 허용`() {
        val user = user(status = UserStatus.ACTIVE)
        assertDoesNotThrow { policy.validateLoginAllowed(user) }
    }

    @Test
    fun `BLOCKED 사용자 로그인 거부`() {
        val user = user(
            status = UserStatus.BLOCKED,
            blocked = true,
            blockedReason = "abuse",
            blockedAt = Instant.now(),
            blockedByAdminId = 999L,
        )

        val ex = assertThrows(AccountStatusDeniedException::class.java) {
            policy.validateLoginAllowed(user)
        }
        assertEquals(LoginDenyReason.BLOCKED, ex.reason)
    }

    @Test
    fun `SOFT_DELETED 사용자 로그인 거부`() {
        val user = user(
            status = UserStatus.SOFT_DELETED,
            deletionReason = "user_request",
            deletedAt = Instant.now(),
        )

        val ex = assertThrows(AccountStatusDeniedException::class.java) {
            policy.validateLoginAllowed(user)
        }
        assertEquals(LoginDenyReason.SOFT_DELETED, ex.reason)
    }

    private fun user(
        status: UserStatus,
        blocked: Boolean = false,
        blockedReason: String? = null,
        blockedAt: Instant? = null,
        blockedByAdminId: Long? = null,
        deletionReason: String? = null,
        deletedAt: Instant? = null,
    ): User {
        return User(
            id = 1L,
            email = "user@example.com",
            phoneNumber = null,
            name = null,
            nickname = null,
            authProvider = AuthProvider.LOCAL,
            userStatus = status,
            blocked = blocked,
            blockedReason = blockedReason,
            blockedAt = blockedAt,
            blockedByAdminId = blockedByAdminId,
            deletedAt = deletedAt,
            deletionReason = deletionReason,
        )
    }
}
