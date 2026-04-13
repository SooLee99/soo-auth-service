package io.soo.springboot.storage.db.core.authmethod

import io.soo.springboot.core.enums.AuthMethod

data class AuthMethodConfig(
    val method: AuthMethod,
    val enabled: Boolean,
) {
    companion object {
        fun from(entity: AuthMethodConfigEntity): AuthMethodConfig {
            return AuthMethodConfig(
                method = entity.method,
                enabled = entity.enabled,
            )
        }
    }
}
