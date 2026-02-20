package io.soo.springboot.core.domain.token

import org.springframework.stereotype.Component


@Component
interface UserPrincipalLoader {
    fun loadByUserId(userId: Long): UserPrincipal
    fun loadByEmail(email: String): UserPrincipal
}
