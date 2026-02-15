package io.soo.springboot.core.domain

import org.springframework.security.core.userdetails.UserDetails

interface UserPrincipalLoader {
    fun loadByUserId(userId: Long): UserDetails
    fun loadByEmail(email: String): UserDetails
}
