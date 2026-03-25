package io.soo.springboot.core.domain.admin.bootstrap

sealed interface AdminBootstrapResult {
    data class Skipped(val reason: String) : AdminBootstrapResult

    data class Applied(
        val accountCreated: Boolean,
        val rolePromoted: Boolean,
        val credentialCreated: Boolean,
    ) : AdminBootstrapResult
}
