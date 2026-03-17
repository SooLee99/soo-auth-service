package io.soo.springboot.core.domain

import io.soo.springboot.core.enums.AuthProvider
import io.soo.springboot.core.enums.Role
import io.soo.springboot.core.enums.UserStatus
import io.soo.springboot.storage.db.core.LocalCredential
import io.soo.springboot.storage.db.core.LocalCredentialRepository
import io.soo.springboot.storage.db.core.User
import io.soo.springboot.storage.db.core.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.core.env.Environment
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
@ConditionalOnProperty(
    prefix = "app.bootstrap.admin",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true,
)
class AdminAccountBootstrapRunner(
    private val properties: AdminBootstrapProperties,
    private val userRepository: UserRepository,
    private val localCredentialRepository: LocalCredentialRepository,
    private val passwordEncoder: PasswordEncoder,
    private val environment: Environment,
) : ApplicationRunner {

    @Transactional
    override fun run(args: ApplicationArguments) {
        val username = properties.username.trim().lowercase()
        val rawPassword = properties.password.trim()
        val activeProfiles = environment.activeProfiles.toList()
        val allowedProfiles = properties.allowedProfiles.map { it.trim() }.filter { it.isNotBlank() }

        if (username.isBlank() || rawPassword.isBlank()) {
            throw IllegalStateException(
                "admin bootstrap enabled but username/password is blank. " +
                    "Set ADMIN_BOOTSTRAP_USERNAME and ADMIN_BOOTSTRAP_PASSWORD.",
            )
        }

        if (!isAllowedProfile(activeProfiles, allowedProfiles)) {
            log.info("admin bootstrap skipped: activeProfiles={}, allowedProfiles={}", activeProfiles, allowedProfiles)
            return
        }

        if (!properties.allowWeakPassword) {
            validateStrongPassword(username, rawPassword)
        }

        val user = userRepository.findByEmail(username)

        if (user == null) {
            val created = userRepository.save(
                User(
                    email = username,
                    phoneNumber = null,
                    name = null,
                    nickname = null,
                    authProvider = AuthProvider.LOCAL,
                    role = Role.ADMIN,
                    userStatus = UserStatus.ACTIVE,
                ),
            )
            localCredentialRepository.save(
                LocalCredential(
                    userId = created.id,
                    userEmail = username,
                    passwordHash = passwordEncoder.encode(rawPassword),
                ),
            )
            log.info("admin bootstrap created account: {}", username)
            return
        }

        val normalizedUser = if (user.role != Role.ADMIN) {
            userRepository.save(user.copy(role = Role.ADMIN))
        } else {
            user
        }

        val credential = localCredentialRepository.findByUserId(normalizedUser.id)
        if (credential == null) {
            localCredentialRepository.save(
                LocalCredential(
                    userId = normalizedUser.id,
                    userEmail = username,
                    passwordHash = passwordEncoder.encode(rawPassword),
                ),
            )
            log.info("admin bootstrap created local credential for existing account: {}", username)
        } else {
            log.info("admin bootstrap skipped create: account already exists ({})", username)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(AdminAccountBootstrapRunner::class.java)
    }

    private fun isAllowedProfile(activeProfiles: List<String>, allowedProfiles: List<String>): Boolean {
        if (allowedProfiles.isEmpty()) return false
        return activeProfiles.any { active -> allowedProfiles.any { allowed -> allowed.equals(active, ignoreCase = true) } }
    }

    private fun validateStrongPassword(username: String, password: String) {
        if (password.length < 12) {
            throw IllegalStateException("admin bootstrap password must be at least 12 characters.")
        }
        if (password.equals(username, ignoreCase = true)) {
            throw IllegalStateException("admin bootstrap password must not be the same as username.")
        }
        val hasUpper = password.any { it.isUpperCase() }
        val hasLower = password.any { it.isLowerCase() }
        val hasDigit = password.any { it.isDigit() }
        val hasSpecial = password.any { !it.isLetterOrDigit() }
        if (!(hasUpper && hasLower && hasDigit && hasSpecial)) {
            throw IllegalStateException(
                "admin bootstrap password must include uppercase, lowercase, digit, and special character.",
            )
        }
    }
}
