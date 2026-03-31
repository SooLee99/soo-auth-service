package io.soo.springboot.core.domain.local.phone.login

import io.soo.springboot.core.domain.UserStatusPolicy
import io.soo.springboot.core.enums.AuthProvider
import io.soo.springboot.core.support.error.CoreException
import io.soo.springboot.core.support.error.ErrorType
import io.soo.springboot.storage.db.core.User
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service

data class PhoneLoginContext(
    val activeUser: User?,
    val userIncludingDeleted: User?,
)

interface PhoneLoginPolicy {
    fun supports(context: PhoneLoginContext): Boolean
    fun resolve(context: PhoneLoginContext): User
}

@Service
class PhoneLoginResolver(
    private val policies: List<PhoneLoginPolicy>,
) {
    fun resolve(context: PhoneLoginContext): User {
        val policy = policies.firstOrNull { it.supports(context) }
            ?: throw CoreException(ErrorType.INVALID_CREDENTIALS)
        return policy.resolve(context)
    }
}

@Component
@Order(100)
class LocalUserPhoneLoginPolicy(
    private val userStatusPolicy: UserStatusPolicy,
) : PhoneLoginPolicy {
    override fun supports(context: PhoneLoginContext): Boolean {
        return context.activeUser?.authProvider == AuthProvider.LOCAL
    }

    override fun resolve(context: PhoneLoginContext): User {
        val user = context.activeUser ?: throw CoreException(ErrorType.INVALID_CREDENTIALS)
        userStatusPolicy.validateLoginAllowed(user)
        return user
    }
}

@Component
@Order(200)
class NonLocalUserPhoneLoginPolicy(
    private val userStatusPolicy: UserStatusPolicy,
) : PhoneLoginPolicy {
    override fun supports(context: PhoneLoginContext): Boolean {
        val active = context.activeUser ?: return false
        return active.authProvider != AuthProvider.LOCAL
    }

    override fun resolve(context: PhoneLoginContext): User {
        val user = context.activeUser ?: throw CoreException(ErrorType.INVALID_CREDENTIALS)
        userStatusPolicy.validateLoginAllowed(user)
        throw CoreException(ErrorType.INVALID_CREDENTIALS)
    }
}

@Component
@Order(300)
class DeletedPhoneLoginPolicy(
    private val userStatusPolicy: UserStatusPolicy,
) : PhoneLoginPolicy {
    override fun supports(context: PhoneLoginContext): Boolean {
        return context.activeUser == null && context.userIncludingDeleted != null
    }

    override fun resolve(context: PhoneLoginContext): User {
        val user = context.userIncludingDeleted ?: throw CoreException(ErrorType.INVALID_CREDENTIALS)
        userStatusPolicy.validateLoginAllowed(user)
        throw CoreException(ErrorType.INVALID_CREDENTIALS)
    }
}

@Component
@Order(400)
class MissingPhoneLoginPolicy : PhoneLoginPolicy {
    override fun supports(context: PhoneLoginContext): Boolean {
        return context.activeUser == null && context.userIncludingDeleted == null
    }

    override fun resolve(context: PhoneLoginContext): User {
        throw CoreException(ErrorType.INVALID_CREDENTIALS)
    }
}
