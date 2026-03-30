package io.soo.springboot.core.domain.bootstrap

import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

interface AdminPasswordPolicy {
    fun match(command: AdminBootstrapCommand): Boolean
    fun check(command: AdminBootstrapCommand)
}

@Component
@Order(100)
class StrongAdminPasswordPolicy : AdminPasswordPolicy {
    override fun match(command: AdminBootstrapCommand): Boolean = !command.allowWeakPassword

    override fun check(command: AdminBootstrapCommand) {
        val username = command.username
        val password = command.rawPassword

        if (password.length < 12) {
            throw IllegalStateException("admin bootstrap password must be at least 12 characters.")
        }
        if (password.equals(username, ignoreCase = true)) {
            throw IllegalStateException("admin bootstrap password must not be the same as username.")
        }
        val hasUpper = password.any { it.isUpperCase() }
        val hasLower = password.any { it.isLowerCase() }
        val hasDigit = password.any { it.isDigit() }
        val hasSpecial = password.any { !it.isLetterOrDigit() }
        if (!(hasUpper && hasLower && hasDigit && hasSpecial)) {
            throw IllegalStateException(
                "admin bootstrap password must include uppercase, lowercase, digit, and special character.",
            )
        }
    }
}

@Component
@Order(200)
class NoopAdminPasswordPolicy : AdminPasswordPolicy {
    override fun match(command: AdminBootstrapCommand): Boolean = command.allowWeakPassword

    override fun check(command: AdminBootstrapCommand) = Unit
}
