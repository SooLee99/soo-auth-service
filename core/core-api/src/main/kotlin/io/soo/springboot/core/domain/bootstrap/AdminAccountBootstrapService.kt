package io.soo.springboot.core.domain.bootstrap

import io.soo.springboot.storage.db.core.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AdminAccountBootstrapService(
    private val userRepository: UserRepository,
    private val passwordPolicies: List<AdminPasswordPolicy>,
    private val bootstrapActions: List<AdminBootstrapAction>,
) {
    @Transactional
    fun bootstrap(command: AdminBootstrapCommand): AdminBootstrapResult {
        validateRequiredCredentials(command)

        if (!isAllowedProfile(command.activeProfiles, command.allowedProfiles)) {
            return AdminBootstrapResult.Skipped(
                reason = "activeProfiles=${command.activeProfiles}, allowedProfiles=${command.allowedProfiles}",
            )
        }

        passwordPolicies.firstOrNull { it.supports(command) }?.validate(command)

        var state = AdminBootstrapState(
            command = command,
            user = userRepository.findByEmail(command.username),
        )

        bootstrapActions.forEach { action ->
            state = action.apply(state)
        }

        return AdminBootstrapResult.Applied(
            accountCreated = state.accountCreated,
            rolePromoted = state.rolePromoted,
            credentialCreated = state.credentialCreated,
        )
    }

    private fun validateRequiredCredentials(command: AdminBootstrapCommand) {
        if (command.username.isBlank() || command.rawPassword.isBlank()) {
            throw IllegalStateException(
                "admin bootstrap enabled but username/password is blank. " +
                    "Set ADMIN_BOOTSTRAP_USERNAME and ADMIN_BOOTSTRAP_PASSWORD.",
            )
        }
    }

    private fun isAllowedProfile(activeProfiles: List<String>, allowedProfiles: List<String>): Boolean {
        if (allowedProfiles.isEmpty()) return false
        return activeProfiles.any { active -> allowedProfiles.any { allowed -> allowed.equals(active, ignoreCase = true) } }
    }
}
