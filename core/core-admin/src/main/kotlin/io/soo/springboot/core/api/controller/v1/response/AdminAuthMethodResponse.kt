package io.soo.springboot.core.api.controller.v1.response

import io.soo.springboot.core.domain.authmethod.AuthMethodState

data class AdminAuthMethodResponse(
    val method: String,
    val enabled: Boolean,
) {
    companion object {
        fun from(state: AuthMethodState): AdminAuthMethodResponse {
            return AdminAuthMethodResponse(
                method = state.method.name,
                enabled = state.enabled,
            )
        }
    }
}
