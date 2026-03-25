package io.soo.springboot.core.api.security.local

import io.soo.springboot.core.api.security.response.SecurityErrorFields
import io.soo.springboot.core.support.error.AccountStatusDeniedException
import io.soo.springboot.core.domain.LocalLoginPolicyService
import io.soo.springboot.core.support.error.ErrorType
import org.springframework.core.annotation.Order
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.DisabledException
import org.springframework.security.core.AuthenticationException
import org.springframework.stereotype.Component

data class LocalLoginFailureContext(
    val payload: LocalJsonLoginFilter.AuthErrorPayload?,
    val exception: AuthenticationException,
    val loginPolicyResult: LocalLoginPolicyService.FailureResult?,
    val isStatusDenied: Boolean,
)

data class LocalLoginFailureDecision(
    val type: ErrorType,
    val fields: Map<String, Any?>,
)

interface LocalLoginFailurePolicy {
    fun supports(context: LocalLoginFailureContext): Boolean
    fun decide(context: LocalLoginFailureContext): LocalLoginFailureDecision
}

@Component
@Order(100)
class PayloadFailurePolicy : LocalLoginFailurePolicy {
    override fun supports(context: LocalLoginFailureContext): Boolean = context.payload != null

    override fun decide(context: LocalLoginFailureContext): LocalLoginFailureDecision {
        val payload = context.payload ?: error("payload is required")
        return LocalLoginFailureDecision(
            type = payload.type,
            fields = linkedMapOf(
                "userMessage" to payload.userMessage,
                "detail" to payload.detail,
                "extra" to payload.extra.takeIf { it.isNotEmpty() },
            ).filterValues { it != null },
        )
    }
}

@Component
@Order(200)
class DisabledFailurePolicy : LocalLoginFailurePolicy {
    override fun supports(context: LocalLoginFailureContext): Boolean =
        context.exception is DisabledException

    override fun decide(context: LocalLoginFailureContext): LocalLoginFailureDecision =
        LocalLoginFailureDecision(
            type = ErrorType.ACCOUNT_DISABLED,
            fields = SecurityErrorFields.accountDisabled(),
        )
}

@Component
@Order(300)
class StatusDeniedFailurePolicy : LocalLoginFailurePolicy {
    override fun supports(context: LocalLoginFailureContext): Boolean =
        context.isStatusDenied || context.exception is AccountStatusDeniedException

    override fun decide(context: LocalLoginFailureContext): LocalLoginFailureDecision =
        LocalLoginFailureDecision(
            type = ErrorType.LOGIN_DENIED,
            fields = SecurityErrorFields.loginDenied(),
        )
}

@Component
@Order(400)
class LockedFailurePolicy : LocalLoginFailurePolicy {
    override fun supports(context: LocalLoginFailureContext): Boolean =
        context.loginPolicyResult == LocalLoginPolicyService.FailureResult.LOCKED

    override fun decide(context: LocalLoginFailureContext): LocalLoginFailureDecision =
        LocalLoginFailureDecision(
            type = ErrorType.LOGIN_ATTEMPTS_EXCEEDED,
            fields = mapOf("reason" to "LOGIN_ATTEMPTS_EXCEEDED"),
        )
}

@Component
@Order(500)
class BadCredentialsFailurePolicy : LocalLoginFailurePolicy {
    override fun supports(context: LocalLoginFailureContext): Boolean =
        context.exception is BadCredentialsException

    override fun decide(context: LocalLoginFailureContext): LocalLoginFailureDecision {
        val type = when (context.loginPolicyResult) {
            LocalLoginPolicyService.FailureResult.BAD_CREDENTIALS -> ErrorType.LOGIN_BAD_CREDENTIALS
            LocalLoginPolicyService.FailureResult.NOT_FOUND -> ErrorType.LOGIN_ACCOUNT_NOT_FOUND
            else -> ErrorType.UNAUTHORIZED
        }
        return LocalLoginFailureDecision(type = type, fields = SecurityErrorFields.badCredentials())
    }
}

@Component
@Order(1000)
class DefaultFailurePolicy : LocalLoginFailurePolicy {
    override fun supports(context: LocalLoginFailureContext): Boolean = true

    override fun decide(context: LocalLoginFailureContext): LocalLoginFailureDecision =
        LocalLoginFailureDecision(
            type = ErrorType.UNAUTHORIZED,
            fields = mapOf("reason" to "UNAUTHORIZED"),
        )
}

