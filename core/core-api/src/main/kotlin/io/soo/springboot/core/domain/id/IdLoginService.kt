package io.soo.springboot.core.domain.id

import io.soo.springboot.core.domain.UserStatusPolicy
import io.soo.springboot.core.domain.login.LocalLoginAttemptPolicy
import io.soo.springboot.core.domain.login.LoginHistoryService
import io.soo.springboot.core.domain.phone.login.LocalIssuedTokens
import io.soo.springboot.core.domain.phone.login.LocalLoginTokenIssuer
import io.soo.springboot.core.enums.LoginType
import io.soo.springboot.core.support.error.CoreException
import io.soo.springboot.core.support.error.ErrorType
import io.soo.springboot.storage.db.core.LocalCredentialRepository
import io.soo.springboot.storage.db.core.UserRepository
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

data class IdLoginCommand(
    val loginId: String,
    val password: String,
    val deviceId: String,
    val ipAddress: String?,
    val userAgent: String?,
)

@Service
class IdLoginService(
    private val userRepository: UserRepository,
    private val localCredentialRepository: LocalCredentialRepository,
    private val passwordEncoder: PasswordEncoder,
    private val userStatusPolicy: UserStatusPolicy,
    private val loginAttemptPolicy: LocalLoginAttemptPolicy,
    private val loginHistoryService: LoginHistoryService,
    private val localLoginTokenIssuer: LocalLoginTokenIssuer,
) {
    @Transactional
    fun login(command: IdLoginCommand): LocalIssuedTokens {
        val normalizedLoginId = command.loginId.trim().lowercase()
        val user = userRepository.findByLoginId(normalizedLoginId)
        val userIncludingDeleted = if (user == null) {
            userRepository.findByLoginIdIncludingDeleted(normalizedLoginId)
        } else {
            null
        }

        if (user == null) {
            if (userIncludingDeleted != null) {
                userStatusPolicy.validateLoginAllowed(userIncludingDeleted)
            }
            throw CoreException(ErrorType.LOGIN_ACCOUNT_NOT_FOUND)
        }

        userStatusPolicy.validateLoginAllowed(user)

        val credential = localCredentialRepository.findByUserId(user.id)
            ?: throw CoreException(ErrorType.LOGIN_ACCOUNT_NOT_FOUND)

        if (!passwordEncoder.matches(command.password, credential.passwordHash)) {
            val result = loginAttemptPolicy.recordFailureByEmail(user.email)

            val failureReason = when (result) {
                LocalLoginAttemptPolicy.FailResult.LOCKED -> "LOGIN_ATTEMPTS_EXCEEDED"
                LocalLoginAttemptPolicy.FailResult.BAD_CREDENTIALS -> "BAD_CREDENTIALS"
                LocalLoginAttemptPolicy.FailResult.NOT_FOUND -> "ACCOUNT_NOT_FOUND"
            }
            loginHistoryService.recordLoginFailure(
                userId = user.id,
                userEmail = user.email,
                loginType = LoginType.LOCAL,
                ipAddress = command.ipAddress,
                userAgent = command.userAgent,
                deviceId = command.deviceId,
                failureReason = failureReason,
            )

            val errorType = when (result) {
                LocalLoginAttemptPolicy.FailResult.LOCKED -> ErrorType.LOGIN_ATTEMPTS_EXCEEDED
                LocalLoginAttemptPolicy.FailResult.BAD_CREDENTIALS -> ErrorType.LOGIN_BAD_CREDENTIALS
                LocalLoginAttemptPolicy.FailResult.NOT_FOUND -> ErrorType.LOGIN_ACCOUNT_NOT_FOUND
            }
            throw CoreException(errorType)
        }

        val issued = localLoginTokenIssuer.issue(user, command.deviceId)

        loginHistoryService.recordLoginSuccess(
            userId = user.id,
            userEmail = user.email,
            loginType = LoginType.LOCAL,
            ipAddress = command.ipAddress,
            userAgent = command.userAgent,
            deviceId = command.deviceId,
        )
        return issued
    }
}
