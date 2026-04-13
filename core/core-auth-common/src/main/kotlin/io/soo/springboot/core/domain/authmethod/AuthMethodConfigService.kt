package io.soo.springboot.core.domain.authmethod

import io.soo.springboot.core.enums.AuthMethod
import io.soo.springboot.core.support.error.CoreException
import io.soo.springboot.core.support.error.ErrorType
import io.soo.springboot.storage.db.core.authmethod.AuthMethodConfigRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

data class AuthMethodState(
    val method: AuthMethod,
    val enabled: Boolean,
)

@Service
class AuthMethodConfigService(
    private val authMethodConfigRepository: AuthMethodConfigRepository,
) {
    @Transactional(readOnly = true)
    fun list(): List<AuthMethodState> {
        val persisted = authMethodConfigRepository.findAll().associateBy { it.method }
        return AuthMethod.entries.map { method ->
            val config = persisted[method]
            AuthMethodState(
                method = method,
                enabled = config?.enabled ?: true,
            )
        }
    }

    @Transactional(readOnly = true)
    fun isEnabled(method: AuthMethod): Boolean {
        return authMethodConfigRepository.findByMethod(method)?.enabled ?: true
    }

    @Transactional
    fun setEnabled(method: AuthMethod, enabled: Boolean): AuthMethodState {
        val saved = authMethodConfigRepository.save(method, enabled)
        return AuthMethodState(saved.method, saved.enabled)
    }

    @Transactional(readOnly = true)
    fun assertEnabled(method: AuthMethod) {
        if (!isEnabled(method)) {
            throw CoreException(
                ErrorType.AUTH_METHOD_DISABLED,
                data = mapOf("method" to method.name),
            )
        }
    }
}
