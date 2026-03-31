package io.soo.springboot.core.domain.local.phone.login

import io.mockk.mockk
import io.mockk.verify
import io.soo.springboot.core.domain.UserStatusPolicy
import io.soo.springboot.core.enums.AuthProvider
import io.soo.springboot.core.enums.UserStatus
import io.soo.springboot.core.support.error.CoreException
import io.soo.springboot.core.support.error.ErrorType
import io.soo.springboot.storage.db.core.User
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class PhoneLoginResolverTest {
    private val userStatusPolicy = mockk<UserStatusPolicy>(relaxed = true)

    private val resolver = PhoneLoginResolver(
        listOf(
            LocalUserPhoneLoginPolicy(userStatusPolicy),
            NonLocalUserPhoneLoginPolicy(userStatusPolicy),
            DeletedPhoneLoginPolicy(userStatusPolicy),
            MissingPhoneLoginPolicy(),
        )
    )

    @Test
    fun `active local user면 로그인 허용`() {
        val user = sampleUser(authProvider = AuthProvider.LOCAL)
        val result = resolver.resolve(PhoneLoginContext(activeUser = user, userIncludingDeleted = null))
        assertEquals(user, result)
        verify(exactly = 1) { userStatusPolicy.validateLoginAllowed(user) }
    }

    @Test
    fun `active non-local user면 로그인 거부`() {
        val user = sampleUser(authProvider = AuthProvider.KAKAO)

        val ex = assertThrows(CoreException::class.java) {
            resolver.resolve(PhoneLoginContext(activeUser = user, userIncludingDeleted = null))
        }

        assertEquals(ErrorType.INVALID_CREDENTIALS, ex.errorType)
        verify(exactly = 1) { userStatusPolicy.validateLoginAllowed(user) }
    }

    @Test
    fun `사용자 없으면 로그인 거부`() {
        val ex = assertThrows(CoreException::class.java) {
            resolver.resolve(PhoneLoginContext(activeUser = null, userIncludingDeleted = null))
        }
        assertEquals(ErrorType.INVALID_CREDENTIALS, ex.errorType)
    }

    private fun sampleUser(authProvider: AuthProvider): User {
        return User(
            id = 1L,
            email = "user@example.com",
            phoneNumber = "01012345678",
            name = null,
            nickname = null,
            authProvider = authProvider,
            userStatus = UserStatus.ACTIVE,
        )
    }
}
