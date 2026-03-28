package io.soo.springboot.core.domain.bootstrap

import io.soo.springboot.core.enums.AuthProvider
import io.soo.springboot.core.enums.Role
import io.soo.springboot.core.enums.UserStatus
import io.soo.springboot.storage.db.core.LocalCredential
import io.soo.springboot.storage.db.core.LocalCredentialRepository
import io.soo.springboot.storage.db.core.User
import io.soo.springboot.storage.db.core.UserRepository
import org.springframework.core.annotation.Order
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component

data class AdminBootCtx(
    val command: AdminBootCmd,
    val user: User?,
    val accountCreated: Boolean = false,
    val rolePromoted: Boolean = false,
    val credentialCreated: Boolean = false,
)

interface AdminBootStep {
    fun apply(state: AdminBootCtx): AdminBootCtx
}

@Component
@Order(100)
class CreateAdminStep(
    private val userRepository: UserRepository,
) : AdminBootStep {
    override fun apply(state: AdminBootCtx): AdminBootCtx {
        if (state.user != null) return state
        val created = userRepository.save(
            User(
                email = state.command.username,
                phoneNumber = null,
                name = null,
                nickname = null,
                authProvider = AuthProvider.LOCAL,
                role = Role.ADMIN,
                userStatus = UserStatus.ACTIVE,
            ),
        )
        return state.copy(user = created, accountCreated = true)
    }
}

@Component
@Order(200)
class PromoteRoleStep(
    private val userRepository: UserRepository,
) : AdminBootStep {
    override fun apply(state: AdminBootCtx): AdminBootCtx {
        val current = state.user ?: return state
        if (current.role == Role.ADMIN) return state
        val promoted = userRepository.save(current.copy(role = Role.ADMIN))
        return state.copy(user = promoted, rolePromoted = true)
    }
}

@Component
@Order(300)
class EnsureCredStep(
    private val localCredentialRepository: LocalCredentialRepository,
    private val passwordEncoder: PasswordEncoder,
) : AdminBootStep {
    override fun apply(state: AdminBootCtx): AdminBootCtx {
        val current = state.user ?: return state
        val credential = localCredentialRepository.findByUserId(current.id)
        if (credential != null) return state

        localCredentialRepository.save(
            LocalCredential(
                userId = current.id,
                userEmail = state.command.username,
                passwordHash = passwordEncoder.encode(state.command.rawPassword),
            ),
        )
        return state.copy(credentialCreated = true)
    }
}
