package io.soo.springboot.core.api.config

import com.fasterxml.jackson.databind.ObjectMapper
import io.soo.springboot.core.api.security.local.AdminLoginSuccessHandler
import io.soo.springboot.core.api.security.local.LocalJsonLoginFilter
import io.soo.springboot.core.api.security.local.LocalLoginFailureHandler
import io.soo.springboot.core.api.security.local.LocalLoginSuccessHandler
import io.soo.springboot.core.domain.auth.AuthFeature
import io.soo.springboot.core.domain.auth.AuthFeatureGuard
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.security.authentication.AuthenticationManager

@Configuration
class LocalLoginFilterConfig(
    private val objectMapper: ObjectMapper,
    private val authFeatureGuard: AuthFeatureGuard,
    private val localLoginSuccessHandler: LocalLoginSuccessHandler,
    private val adminLoginSuccessHandler: AdminLoginSuccessHandler,
    private val localLoginFailureHandler: LocalLoginFailureHandler,
) {

    @Bean("localJsonLoginFilter")
    fun localJsonLoginFilter(authenticationManager: AuthenticationManager): LocalJsonLoginFilter {
        return LocalJsonLoginFilter(
            objectMapper = objectMapper,
            beforeAttempt = { authFeatureGuard.assertEnabled(AuthFeature.EMAIL) },
        ).apply {
            setAuthenticationManager(authenticationManager)
            setFilterProcessesUrl("/api/v1/auth/local/login")
            setAuthenticationSuccessHandler(localLoginSuccessHandler)
            setAuthenticationFailureHandler(localLoginFailureHandler)
        }
    }

    @Bean("adminJsonLoginFilter")
    fun adminJsonLoginFilter(authenticationManager: AuthenticationManager): LocalJsonLoginFilter {
        return LocalJsonLoginFilter(objectMapper = objectMapper).apply {
            setAuthenticationManager(authenticationManager)
            setFilterProcessesUrl("/api/v1/auth/admin/login")
            setAuthenticationSuccessHandler(adminLoginSuccessHandler)
            setAuthenticationFailureHandler(localLoginFailureHandler)
        }
    }

    @Bean
    fun localJsonLoginFilterRegistration(
        @Qualifier("localJsonLoginFilter") localJsonLoginFilter: LocalJsonLoginFilter,
    ): FilterRegistrationBean<LocalJsonLoginFilter> {
        return FilterRegistrationBean(localJsonLoginFilter).apply {
            isEnabled = false
        }
    }

    @Bean
    fun adminJsonLoginFilterRegistration(
        @Qualifier("adminJsonLoginFilter") adminJsonLoginFilter: LocalJsonLoginFilter,
    ): FilterRegistrationBean<LocalJsonLoginFilter> {
        return FilterRegistrationBean(adminJsonLoginFilter).apply {
            isEnabled = false
        }
    }
}
