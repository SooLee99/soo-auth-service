package io.soo.springboot.core.api.security.auth

import org.springframework.security.core.Authentication

fun interface UserIdResolver {
    fun resolve(authentication: Authentication): Long
}