package io.soo.springboot.core.api.security.userdetails

interface UserPrincipalLoader {
    fun loadByUserId(userId: Long): UserPrincipal
    fun loadByEmail(email: String): UserPrincipal
}
