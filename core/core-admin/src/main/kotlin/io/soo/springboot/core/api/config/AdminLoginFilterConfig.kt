package io.soo.springboot.core.api.config

import com.fasterxml.jackson.databind.ObjectMapper
import io.soo.springboot.core.api.security.local.AdminLoginSuccessHandler
import io.soo.springboot.core.api.security.local.LocalJsonLoginFilter
import io.soo.springboot.core.api.security.local.LocalLoginFailureHandler
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.authentication.AuthenticationManager

@Configuration
@ConditionalOnProperty(name = ["app.auth.method.admin.enabled"], havingValue = "true", matchIfMissing = true)
class AdminLoginFilterConfig(
    private val objectMapper: ObjectMapper,
    private val adminLoginSuccessHandler: AdminLoginSuccessHandler,
    private val localLoginFailureHandler: LocalLoginFailureHandler,
) {
    @Bean("adminJsonLoginFilter")
    fun adminJsonLoginFilter(authenticationManager: AuthenticationManager): LocalJsonLoginFilter {
        return LocalJsonLoginFilter(objectMapper).apply {
            setAuthenticationManager(authenticationManager)
            setFilterProcessesUrl("/api/v1/auth/admin/login")
            setAuthenticationSuccessHandler(adminLoginSuccessHandler)
            setAuthenticationFailureHandler(localLoginFailureHandler)
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
