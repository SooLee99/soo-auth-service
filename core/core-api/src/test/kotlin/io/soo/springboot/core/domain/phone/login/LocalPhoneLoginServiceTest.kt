package io.soo.springboot.core.domain.phone.login

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.soo.springboot.core.domain.login.LoginHistoryService
import io.soo.springboot.core.domain.phone.account.LocalPhoneAccountService
import io.soo.springboot.core.enums.AuthProvider
import io.soo.springboot.core.enums.LoginType
import io.soo.springboot.storage.db.core.User
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LocalPhoneLoginServiceTest {
    private val localPhoneAccountService = mockk<LocalPhoneAccountService>()
    private val tokenIssuer = mockk<LocalLoginTokenIssuer>()
    private val loginHistoryService = mockk<LoginHistoryService>(relaxed = true)

    private val service = LocalPhoneLoginService(
        localPhoneAccountService = localPhoneAccountService,
        localLoginTokenIssuer = tokenIssuer,
        loginHistoryService = loginHistoryService,
    )

    @Test
    fun `전화 로그인 성공 시 토큰 발급과 이력 저장`() {
        val user = User(
            id = 1L,
            email = "user@example.com",
            phoneNumber = "01012345678",
            name = null,
            nickname = null,
            authProvider = AuthProvider.LOCAL,
        )
        val tokens = LocalIssuedTokens(
            accessToken = "access-token",
            accessExpiresInSec = 3600,
            refreshToken = "refresh-token",
            refreshExpiresInSec = 1209600,
        )

        every { localPhoneAccountService.loginWithPhone("+821012345678", "verified-token") } returns user
        every { tokenIssuer.issue(user, "device-001") } returns tokens

        val result = service.login(
            LocalPhoneLoginCommand(
                phoneNumber = "+821012345678",
                phoneVerificationToken = "verified-token",
                deviceId = "device-001",
                ipAddress = "127.0.0.1",
                userAgent = "JUnit",
            ),
        )

        assertEquals(tokens, result)
        verify(exactly = 1) {
            loginHistoryService.recordLoginSuccess(
                1L,
                "user@example.com",
                LoginType.LOCAL,
                "127.0.0.1",
                "JUnit",
                "device-001",
            )
        }
    }
}
