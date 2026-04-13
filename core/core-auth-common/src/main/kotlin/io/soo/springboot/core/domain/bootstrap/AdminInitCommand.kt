package io.soo.springboot.core.domain.bootstrap

data class AdminInitCommand(
    val username: String,
    val rawPassword: String,
    val activeProfiles: List<String>,
    val allowedProfiles: List<String>,
    val allowWeakPassword: Boolean,
) {
    companion object {
        fun of(properties: AdminInitProperties, activeProfiles: List<String>): AdminInitCommand {
            return AdminInitCommand(
                username = properties.username.trim().lowercase(),
                rawPassword = properties.password.trim(),
                activeProfiles = activeProfiles,
                allowedProfiles = properties.allowedProfiles.map { it.trim() }.filter { it.isNotBlank() },
                allowWeakPassword = properties.allowWeakPassword,
            )
        }
    }
}
