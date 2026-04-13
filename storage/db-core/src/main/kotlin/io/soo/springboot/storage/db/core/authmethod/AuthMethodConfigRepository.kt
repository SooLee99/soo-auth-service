package io.soo.springboot.storage.db.core.authmethod

import io.soo.springboot.core.enums.AuthMethod

interface AuthMethodConfigRepository {
    fun findByMethod(method: AuthMethod): AuthMethodConfig?
    fun findAll(): List<AuthMethodConfig>
    fun save(method: AuthMethod, enabled: Boolean): AuthMethodConfig
}
