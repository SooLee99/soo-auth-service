package io.soo.springboot.core.domain.bootstrap

data class AdminBootCmd(
    val username: String,
    val rawPassword: String,
    val activeProfiles: List<String>,
    val allowedProfiles: List<String>,
    val allowWeakPassword: Boolean,
) {
    companion object {
        fun of(properties: AdminBootProps, activeProfiles: List<String>): AdminBootCmd {
            return AdminBootCmd(
                username = properties.username.trim().lowercase(),
                rawPassword = properties.password.trim(),
                activeProfiles = activeProfiles,
                allowedProfiles = properties.allowedProfiles.map { it.trim() }.filter { it.isNotBlank() },
                allowWeakPassword = properties.allowWeakPassword,
            )
        }
    }
}
