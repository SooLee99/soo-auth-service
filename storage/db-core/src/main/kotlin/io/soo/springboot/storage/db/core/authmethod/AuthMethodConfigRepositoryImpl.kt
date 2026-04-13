package io.soo.springboot.storage.db.core.authmethod

import io.soo.springboot.core.enums.AuthMethod
import org.springframework.stereotype.Repository

@Repository
class AuthMethodConfigRepositoryImpl(
    private val jpaRepository: AuthMethodConfigJpaRepository,
) : AuthMethodConfigRepository {
    override fun findByMethod(method: AuthMethod): AuthMethodConfig? {
        return jpaRepository.findByMethod(method)?.let(AuthMethodConfig::from)
    }

    override fun findAll(): List<AuthMethodConfig> {
        return jpaRepository.findAll().map(AuthMethodConfig::from)
    }

    override fun save(method: AuthMethod, enabled: Boolean): AuthMethodConfig {
        val existing = jpaRepository.findByMethod(method)
        val entity = if (existing == null) {
            AuthMethodConfigEntity(
                method = method,
                enabled = enabled,
            )
        } else {
            existing.enabled = enabled
            existing
        }
        return AuthMethodConfig.from(jpaRepository.save(entity))
    }
}
