package io.soo.springboot.core.api.config

import io.soo.springboot.core.domain.LocalJsonLoginFilter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.security.authentication.dao.DaoAuthenticationProvider
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.security.web.context.SecurityContextRepository

@Configuration
class FilterChainConfig (
    private val securityContextRepository: SecurityContextRepository
) {
    companion object {
        private val PUBLIC_ENDPOINTS = arrayOf(
            "/login/**",
            "/api/v1/auth/login",
            "/api/v1/auth/signup",
            "/error",
            "/actuator/**",
        )

        private val CSRF_IGNORED_ENDPOINTS = arrayOf(
            "/login/**",
            "/api/v1/auth/login",
            "/api/v1/auth/signup",
            "/h2-console/**",
        )

        private const val H2_CONSOLE = "/h2-console/**"
        private const val ADMIN_API = "/api/v1/admin/**"
    }

    @Bean
    @Order(2)
    fun sessionSecurityFilterChain(
        http: HttpSecurity,
        localJsonLoginFilter: LocalJsonLoginFilter,
        daoAuthProvider: DaoAuthenticationProvider,
    ): SecurityFilterChain {
        http.authenticationProvider(daoAuthProvider)
        configureSecurityContext(http)
        configureCsrfAndHeaders(http)
        configureAuthorization(http, localJsonLoginFilter)
        return http.build()
    }

    private fun configureSecurityContext(http: HttpSecurity) {
        http.securityContext {
            it.securityContextRepository(securityContextRepository)
        }
    }

    private fun configureCsrfAndHeaders(http: HttpSecurity) {
        http
            .csrf { csrf ->
                csrf.ignoringRequestMatchers(*CSRF_IGNORED_ENDPOINTS)
            }
            .headers { _ ->
            }
    }

    private fun configureAuthorization(http: HttpSecurity, localJsonLoginFilter: LocalJsonLoginFilter) {
        http.authorizeHttpRequests { auth ->
            // 1) 공개 API
            auth.requestMatchers(*PUBLIC_ENDPOINTS).permitAll()

            // 2) 개발/로컬 편의 (현재는 전부 허용. 필요 시 hasRole("ADMIN")로 변경)
            auth.requestMatchers(ADMIN_API).permitAll()
            auth.requestMatchers(H2_CONSOLE).permitAll()

            // 3) 나머지는 인증 필요
            auth.anyRequest().authenticated()
        }.addFilterAt(localJsonLoginFilter, UsernamePasswordAuthenticationFilter::class.java)
    }

}
