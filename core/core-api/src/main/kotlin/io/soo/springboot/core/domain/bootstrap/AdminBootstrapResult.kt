package io.soo.springboot.core.domain.bootstrap

sealed interface AdminBootstrapResult {
    data class Skipped(val reason: String) : AdminBootstrapResult

    data class Applied(
        val accountCreated: Boolean,
        val rolePromoted: Boolean,
        val credentialCreated: Boolean,
    ) : AdminBootstrapResult
}
