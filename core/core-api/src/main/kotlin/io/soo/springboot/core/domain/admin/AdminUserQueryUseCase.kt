package io.soo.springboot.core.domain.admin

import io.soo.springboot.core.enums.AuthProvider
import io.soo.springboot.core.enums.Role
import io.soo.springboot.core.enums.UserStatus
import io.soo.springboot.storage.db.core.User
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

interface AdminUserQueryUseCase {
    fun list(
        keyword: String?,
        userStatus: UserStatus?,
        role: Role?,
        authProvider: AuthProvider?,
        pageable: Pageable,
    ): Page<User>

    fun getById(userId: Long): User
}
