package io.soo.springboot.storage.db.core

import io.mockk.every
import io.mockk.mockk
import io.soo.springboot.core.enums.AuthProvider
import io.soo.springboot.core.enums.UserStatus
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.Optional

class UserRepositoryImplTest {
    private val jpaRepository = mockk<UserJpaRepository>()
    private val repository = UserRepositoryImpl(jpaRepository)

    @Test
    fun `소프트 탈퇴 사용자는 일반 ID 조회에서 제외`() {
        every { jpaRepository.findById(1L) } returns Optional.of(softDeletedEntity())

        val result = repository.findById(1L)

        assertNull(result)
    }

    private fun softDeletedEntity(): UserEntity {
        return UserEntity(
            email = "user@example.com",
            authProvider = AuthProvider.LOCAL,
            userStatus = UserStatus.SOFT_DELETED,
            blocked = false,
        ).also { it.id = 1L }
    }
}
