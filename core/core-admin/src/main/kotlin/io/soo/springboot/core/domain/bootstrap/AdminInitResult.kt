package io.soo.springboot.core.domain.bootstrap

sealed interface AdminInitResult {
    data class Skipped(val reason: String) : AdminInitResult

    data class Applied(
        val accountCreated: Boolean,
        val rolePromoted: Boolean,
        val credentialCreated: Boolean,
    ) : AdminInitResult
}
