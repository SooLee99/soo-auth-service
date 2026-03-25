package io.soo.springboot.core.domain.admin.bootstrap

import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

interface AdminPasswordPolicy {
    fun supports(command: AdminBootstrapCommand): Boolean
    fun validate(command: AdminBootstrapCommand)
}

@Component
@Order(100)
class StrongAdminPasswordPolicy : AdminPasswordPolicy {
    override fun supports(command: AdminBootstrapCommand): Boolean = !command.allowWeakPassword

    override fun validate(command: AdminBootstrapCommand) {
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
    override fun supports(command: AdminBootstrapCommand): Boolean = command.allowWeakPassword

    override fun validate(command: AdminBootstrapCommand) = Unit
}
