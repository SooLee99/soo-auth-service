package io.soo.springboot.core.domain.oauth2

import io.soo.springboot.core.domain.OAuth2UserInfo
import io.soo.springboot.core.enums.AuthProvider

interface OAuth2UserInfoParser {
    val provider: AuthProvider
    fun parse(attrs: Map<String, Any?>): OAuth2UserInfo
}

