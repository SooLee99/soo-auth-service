package io.soo.springboot.core.domain.token

import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails
import io.soo.springboot.core.enums.AuthProvider

data class UserPrincipal(
    val userId: Long,
    val email: String?,
    val passwordHash: String?,
    val role: Any,
    val provider: AuthProvider,
) : UserDetails {

    override fun getAuthorities(): MutableCollection<out GrantedAuthority> =
        mutableListOf(SimpleGrantedAuthority("ROLE_$role"))

    override fun getPassword(): String? = passwordHash
    override fun getUsername(): String = email ?: userId.toString()

    override fun isAccountNonExpired() = true
    override fun isAccountNonLocked() = true
    override fun isCredentialsNonExpired() = true
    override fun isEnabled() = true
}