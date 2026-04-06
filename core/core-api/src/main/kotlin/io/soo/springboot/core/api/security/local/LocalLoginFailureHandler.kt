package io.soo.springboot.core.api.security.local

import io.soo.springboot.core.api.security.response.SecurityErrorResponseWriter
import io.soo.springboot.core.support.error.AccountStatusDeniedException
import io.soo.springboot.core.enums.LoginType
import io.soo.springboot.core.enums.LoginDenyReason
import io.soo.springboot.core.domain.login.LocalLoginAttemptPolicy
import io.soo.springboot.core.domain.login.LoginHistoryService
import io.soo.springboot.storage.db.core.UserRepository
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.authentication.AuthenticationFailureHandler
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class LocalLoginFailureHandler(
    private val writer: SecurityErrorResponseWriter,
    private val localLoginPolicyService: LocalLoginAttemptPolicy,
    private val loginHistoryService: LoginHistoryService,
    private val userRepository: UserRepository,
    private val failurePolicies: List<LocalLoginFailurePolicy>,
) : AuthenticationFailureHandler {

    override fun onAuthenticationFailure(
        request: HttpServletRequest,
        response: HttpServletResponse,
        exception: AuthenticationException,
    ) {
        val payload = request.getAttribute(LocalJsonLoginFilter.ATTR_AUTH_ERROR)
                as? LocalJsonLoginFilter.AuthErrorPayload

        val ip = request.remoteAddr
        val ua = request.getHeader("User-Agent")
        val deviceId = request.getHeader("X-Device-Id")?.trim().orEmpty()
        val normalizedEmail =
            request.getAttribute(LocalJsonLoginFilter.ATTR_NORMALIZED_EMAIL) as? String ?: "unknown"

        val statusDenied = exception as? AccountStatusDeniedException
        val isStatusDenied = statusDenied != null
        val result = if (isStatusDenied) {
            null
        } else {
            localLoginPolicyService.recordFailureByEmail(
                normalizedEmail = normalizedEmail,
                now = LocalDateTime.now(),
            )
        }

        // 로그인 실패 기록 (userId는 조회 가능할 때만)
        val user = userRepository.findByEmailIncludingDeleted(normalizedEmail)
        if (user != null) {
            val failureReason = when (result) {
                LocalLoginAttemptPolicy.FailResult.LOCKED -> "LOGIN_ATTEMPTS_EXCEEDED"
                LocalLoginAttemptPolicy.FailResult.BAD_CREDENTIALS -> "BAD_CREDENTIALS"
                LocalLoginAttemptPolicy.FailResult.NOT_FOUND -> "ACCOUNT_NOT_FOUND"
                null -> when (statusDenied?.reason) {
                    LoginDenyReason.SOFT_DELETED -> "ACCOUNT_SOFT_DELETED"
                    LoginDenyReason.BLOCKED -> "ACCOUNT_BLOCKED"
                    else -> if (isStatusDenied) "ACCOUNT_STATUS_DENIED" else "AUTHENTICATION_FAILED"
                }
            }

            loginHistoryService.recordLoginFailure(
                userId = user.id,
                userEmail = normalizedEmail,
                loginType = LoginType.LOCAL,
                ipAddress = ip,
                userAgent = ua,
                deviceId = deviceId,
                failureReason = failureReason,
            )
        }

        val context = LocalLoginFailureContext(
            payload = payload,
            exception = exception,
            loginPolicyResult = result,
            isStatusDenied = isStatusDenied,
        )
        val decision = failurePolicies.first { it.match(context) }.decide(context)

        writer.writeError(
            response = response,
            request = request,
            type = decision.type,
            fields = decision.fields,
        )
    }
}
