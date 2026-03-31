package io.soo.springboot.core.domain.bootstrap

import io.soo.springboot.core.enums.Role
import io.soo.springboot.storage.db.core.LocalCredential
import io.soo.springboot.storage.db.core.LocalCredentialRepository
import io.soo.springboot.storage.db.core.User
import io.soo.springboot.storage.db.core.UserRepository
import org.springframework.core.annotation.Order
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component

data class AdminBootstrapState(
    val command: AdminBootstrapCommand,
    val user: User?,
    val accountCreated: Boolean = false,
    val rolePromoted: Boolean = false,
    val credentialCreated: Boolean = false,
)

interface AdminBootstrapAction {
    fun apply(state: AdminBootstrapState): AdminBootstrapState
}

@Component
@Order(100)
class CreateAdminStep(
    private val userRepository: UserRepository,
) : AdminBootstrapAction {
    override fun apply(state: AdminBootstrapState): AdminBootstrapState {
        if (state.user != null) return state
        val created = userRepository.save(
            User.createLocal(
                email = state.command.username,
                phoneNumber = null,
                name = null,
                nickname = null,
                role = Role.ADMIN,
            ),
        )
        return state.copy(user = created, accountCreated = true)
    }
}

@Component
@Order(200)
class PromoteRoleStep(
    private val userRepository: UserRepository,
) : AdminBootstrapAction {
    override fun apply(state: AdminBootstrapState): AdminBootstrapState {
        val current = state.user ?: return state
        if (current.role == Role.ADMIN) return state
        val promoted = userRepository.save(current.promoteToAdmin())
        return state.copy(user = promoted, rolePromoted = true)
    }
}

@Component
@Order(300)
class EnsureCredStep(
    private val localCredentialRepository: LocalCredentialRepository,
    private val passwordEncoder: PasswordEncoder,
) : AdminBootstrapAction {
    override fun apply(state: AdminBootstrapState): AdminBootstrapState {
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
