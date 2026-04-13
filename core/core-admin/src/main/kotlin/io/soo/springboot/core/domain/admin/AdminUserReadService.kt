package io.soo.springboot.core.domain.admin

import io.soo.springboot.core.enums.AuthProvider
import io.soo.springboot.core.enums.Role
import io.soo.springboot.core.enums.UserStatus
import io.soo.springboot.core.support.error.CoreException
import io.soo.springboot.core.support.error.ErrorType
import io.soo.springboot.storage.db.core.User
import io.soo.springboot.storage.db.core.UserRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AdminUserReadService(
    private val userRepository: UserRepository,
) {
    @Transactional(readOnly = true)
    fun list(
        keyword: String?,
        userStatus: UserStatus?,
        role: Role?,
        authProvider: AuthProvider?,
        pageable: Pageable,
    ): Page<User> {
        return userRepository.searchUsers(
            keyword = keyword,
            userStatus = userStatus,
            role = role,
            authProvider = authProvider,
            pageable = pageable,
        )
    }

    @Transactional(readOnly = true)
    fun getById(userId: Long): User {
        return userRepository.findByIdIncludingDeleted(userId)
            ?: throw CoreException(ErrorType.NOT_FOUND, data = mapOf("userId" to userId))
    }
}
