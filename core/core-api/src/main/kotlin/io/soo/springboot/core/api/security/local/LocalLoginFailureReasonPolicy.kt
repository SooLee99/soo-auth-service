package io.soo.springboot.core.api.security.local

import io.soo.springboot.core.enums.LoginDenyReason
import io.soo.springboot.core.domain.login.LocalLoginAttemptPolicy
import io.soo.springboot.core.support.error.AccountStatusDeniedException
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

data class LocalLoginFailureReasonContext(
    val loginPolicyResult: LocalLoginAttemptPolicy.FailResult?,
    val statusDenied: AccountStatusDeniedException?,
    val isStatusDenied: Boolean,
)

fun interface LocalLoginFailureReasonPolicy {
    fun resolve(context: LocalLoginFailureReasonContext): String?
}

@Component
@Order(100)
class LockedFailureReasonPolicy : LocalLoginFailureReasonPolicy {
    override fun resolve(context: LocalLoginFailureReasonContext): String? =
        if (context.loginPolicyResult == LocalLoginAttemptPolicy.FailResult.LOCKED) "LOGIN_ATTEMPTS_EXCEEDED" else null
}

@Component
@Order(200)
class BadCredentialsFailureReasonPolicy : LocalLoginFailureReasonPolicy {
    override fun resolve(context: LocalLoginFailureReasonContext): String? =
        if (context.loginPolicyResult == LocalLoginAttemptPolicy.FailResult.BAD_CREDENTIALS) "BAD_CREDENTIALS" else null
}

@Component
@Order(300)
class NotFoundFailureReasonPolicy : LocalLoginFailureReasonPolicy {
    override fun resolve(context: LocalLoginFailureReasonContext): String? =
        if (context.loginPolicyResult == LocalLoginAttemptPolicy.FailResult.NOT_FOUND) "ACCOUNT_NOT_FOUND" else null
}

@Component
@Order(400)
class StatusDeniedFailureReasonPolicy : LocalLoginFailureReasonPolicy {
    override fun resolve(context: LocalLoginFailureReasonContext): String? {
        val denied = context.statusDenied ?: return null
        return when (denied.reason) {
            LoginDenyReason.SOFT_DELETED -> "ACCOUNT_SOFT_DELETED"
            LoginDenyReason.BLOCKED -> "ACCOUNT_BLOCKED"
        }
    }
}

@Component
@Order(1000)
class DefaultFailureReasonPolicy : LocalLoginFailureReasonPolicy {
    override fun resolve(context: LocalLoginFailureReasonContext): String? =
        if (context.isStatusDenied) "ACCOUNT_STATUS_DENIED" else "AUTHENTICATION_FAILED"
}

