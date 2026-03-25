package io.soo.springboot.core.domain.admin.bootstrap

data class AdminBootstrapCommand(
    val username: String,
    val rawPassword: String,
    val activeProfiles: List<String>,
    val allowedProfiles: List<String>,
    val allowWeakPassword: Boolean,
) {
    companion object {
        fun from(properties: AdminBootstrapProperties, activeProfiles: List<String>): AdminBootstrapCommand {
            return AdminBootstrapCommand(
                username = properties.username.trim().lowercase(),
                rawPassword = properties.password.trim(),
                activeProfiles = activeProfiles,
                allowedProfiles = properties.allowedProfiles.map { it.trim() }.filter { it.isNotBlank() },
                allowWeakPassword = properties.allowWeakPassword,
            )
        }
    }
}
