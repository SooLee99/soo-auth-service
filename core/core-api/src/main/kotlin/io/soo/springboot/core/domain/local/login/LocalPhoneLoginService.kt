package io.soo.springboot.core.domain.local.login

import io.soo.springboot.core.domain.LoginHistoryService
import io.soo.springboot.core.domain.local.LocalAccountService
import io.soo.springboot.core.enums.LoginType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

data class LocalPhoneLoginCommand(
    val phoneNumber: String,
    val phoneVerificationToken: String,
    val deviceId: String,
    val ipAddress: String?,
    val userAgent: String?,
)

@Service
class LocalPhoneLoginService(
    private val localAccountService: LocalAccountService,
    private val localLoginTokenIssuer: LocalLoginTokenIssuer,
    private val loginHistoryService: LoginHistoryService,
) {
    @Transactional
    fun login(command: LocalPhoneLoginCommand): LocalIssuedTokens {
        val user = localAccountService.loginWithPhone(
            phoneNumber = command.phoneNumber,
            phoneVerificationToken = command.phoneVerificationToken,
        )

        val tokens = localLoginTokenIssuer.issue(user = user, deviceId = command.deviceId)

        loginHistoryService.recordLoginSuccess(
            userId = user.id,
            userEmail = user.email,
            loginType = LoginType.LOCAL,
            ipAddress = command.ipAddress,
            userAgent = command.userAgent,
            deviceId = command.deviceId,
        )

        return tokens
    }
}
