package io.soo.springboot.core.domain.bootstrap

import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

interface AdminInitPasswordRule {
    fun match(command: AdminInitCommand): Boolean
    fun check(command: AdminInitCommand)
}

@Component
@Order(100)
class StrongAdminInitPasswordRule : AdminInitPasswordRule {
    override fun match(command: AdminInitCommand): Boolean = !command.allowWeakPassword

    override fun check(command: AdminInitCommand) {
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
class NoopAdminInitPasswordRule : AdminInitPasswordRule {
    override fun match(command: AdminInitCommand): Boolean = command.allowWeakPassword

    override fun check(command: AdminInitCommand) = Unit
}
