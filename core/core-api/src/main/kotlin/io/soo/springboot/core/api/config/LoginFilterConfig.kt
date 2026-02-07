package io.soo.springboot.core.api.config

import com.fasterxml.jackson.databind.ObjectMapper
import io.soo.springboot.core.domain.LocalJsonLoginFilter
import io.soo.springboot.core.api.security.LocalLoginFailureHandler
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.authentication.AuthenticationManager

@Configuration
class LoginFilterConfig(
    private val objectMapper: ObjectMapper,
//    private val localLoginSuccessHandler: LocalLoginSuccessHandler,
    private val localLoginFailureHandler: LocalLoginFailureHandler,
) {

    @Bean
    fun localJsonLoginFilter(authenticationManager: AuthenticationManager): LocalJsonLoginFilter {
        return LocalJsonLoginFilter(objectMapper).apply {
            setAuthenticationManager(authenticationManager)
            setFilterProcessesUrl("/api/v1/auth/login")
//            setAuthenticationSuccessHandler(localLoginSuccessHandler)
            setAuthenticationFailureHandler(localLoginFailureHandler)
        }
    }

    @Bean
    fun localJsonLoginFilterRegistration(localJsonLoginFilter: LocalJsonLoginFilter): FilterRegistrationBean<LocalJsonLoginFilter> {
        return FilterRegistrationBean(localJsonLoginFilter).apply {
            isEnabled = false
        }
    }
}
