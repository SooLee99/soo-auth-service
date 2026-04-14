package io.soo.springboot.core.api.config

import org.springframework.security.config.annotation.web.builders.HttpSecurity

interface OAuth2SecurityConfigurer {
    fun configure(http: HttpSecurity)
}
