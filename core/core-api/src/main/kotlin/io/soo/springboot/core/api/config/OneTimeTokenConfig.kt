package io.soo.springboot.core.api.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jdbc.core.JdbcOperations
import org.springframework.security.authentication.ott.JdbcOneTimeTokenService
import org.springframework.security.authentication.ott.OneTimeTokenService

@Configuration
class OneTimeTokenConfig {

    @Bean
    fun oneTimeTokenService(jdbcOperations: JdbcOperations): OneTimeTokenService {
        return JdbcOneTimeTokenService(jdbcOperations)
    }
}
