package io.soo.springboot.core.domain

import org.springframework.security.core.Authentication

fun interface UserIdResolver {
    fun resolve(authentication: Authentication): Long
}
