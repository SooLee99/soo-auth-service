package io.soo.springboot.core.domain.bootstrap

sealed interface AdminBootResult {
    data class Skipped(val reason: String) : AdminBootResult

    data class Applied(
        val accountCreated: Boolean,
        val rolePromoted: Boolean,
        val credentialCreated: Boolean,
    ) : AdminBootResult
}
